package tw.kensuke.assetscope.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import tw.kensuke.assetscope.domain.model.AssetType
import tw.kensuke.assetscope.domain.model.AppSettings
import tw.kensuke.assetscope.domain.model.Currency
import tw.kensuke.assetscope.domain.model.ExchangeRates
import tw.kensuke.assetscope.domain.model.Expense
import tw.kensuke.assetscope.domain.model.ExpenseCategory
import tw.kensuke.assetscope.domain.model.Holding
import tw.kensuke.assetscope.domain.model.Institution
import tw.kensuke.assetscope.domain.model.MarketSummary
import tw.kensuke.assetscope.domain.model.PerformanceSummary
import tw.kensuke.assetscope.domain.model.PortfolioInsights
import tw.kensuke.assetscope.domain.model.PortfolioHistory
import tw.kensuke.assetscope.domain.model.PriceHistory
import tw.kensuke.assetscope.domain.model.PaperTradingDashboard
import tw.kensuke.assetscope.domain.model.PaperBot
import tw.kensuke.assetscope.domain.model.Transaction
import tw.kensuke.assetscope.domain.model.TransactionType
import tw.kensuke.assetscope.domain.model.UiLanguage

class LocalPortfolioRepository(
    context: Context,
) : PortfolioRepository {
    private val appContext = context.applicationContext
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutableHoldings = MutableStateFlow(loadHoldings())
    private val mutableExchangeRates = MutableStateFlow(
        ExchangeRates(
            usdToTwd = preferences.getFloat(KEY_USD_TO_TWD, 32.4f).toDouble(),
        ),
    )
    private val mutableAutoSyncFolder = MutableStateFlow(preferences.getString(KEY_SYNC_FOLDER, null))
    private val mutableServerUrl = MutableStateFlow(preferences.getString(KEY_SERVER_URL, null))
    private val mutableInsights = MutableStateFlow(loadInsights())
    private val mutableMarketSummaries = MutableStateFlow(loadMarketSummaries())
    private val mutableAppSettings = MutableStateFlow(loadAppSettings())
    private val mutablePaperTrading = MutableStateFlow<PaperTradingDashboard?>(null)
    private val preferenceListener =
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                KEY_HOLDINGS -> mutableHoldings.value = loadHoldings()
                KEY_SYNC_FOLDER -> {
                    mutableAutoSyncFolder.value = preferences.getString(KEY_SYNC_FOLDER, null)
                }
                KEY_SERVER_URL -> mutableServerUrl.value = preferences.getString(KEY_SERVER_URL, null)
                KEY_INSIGHTS -> mutableInsights.value = loadInsights()
                KEY_MARKET_SUMMARIES -> mutableMarketSummaries.value = loadMarketSummaries()
                KEY_DISPLAY_CURRENCY, KEY_UI_LANGUAGE -> {
                    mutableAppSettings.value = loadAppSettings()
                }
                KEY_USD_TO_TWD -> {
                    mutableExchangeRates.value = mutableExchangeRates.value.copy(
                        usdToTwd = preferences.getFloat(
                            KEY_USD_TO_TWD,
                            32.4f,
                        ).toDouble(),
                    )
                }
            }
        }

    override val holdings: StateFlow<List<Holding>> = mutableHoldings
    override val exchangeRates: StateFlow<ExchangeRates> = mutableExchangeRates
    override val autoSyncFolder: StateFlow<String?> = mutableAutoSyncFolder
    override val serverUrl: StateFlow<String?> = mutableServerUrl
    override val insights: StateFlow<PortfolioInsights> = mutableInsights
    override val marketSummaries: StateFlow<Map<String, MarketSummary>> = mutableMarketSummaries
    override val appSettings: StateFlow<AppSettings> = mutableAppSettings
    override val paperTrading: StateFlow<PaperTradingDashboard?> = mutablePaperTrading

    init {
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    override suspend fun importCsv(content: String): ImportResult = withContext(Dispatchers.IO) {
        val (imported, skipped) = CsvHoldingParser.parse(content)
        if (imported.isNotEmpty()) {
            mutableHoldings.value = imported
            saveHoldings(imported)
            val baseUrl = preferences.getString(KEY_SERVER_URL, null)
            val token = preferences.getString(KEY_SERVER_TOKEN, null)
            if (baseUrl != null && token != null) {
                refreshMarketSummariesSafely(baseUrl, token)
            }
        }
        ImportResult(importedCount = imported.size, skippedCount = skipped)
    }

    override suspend fun configureAutoSync(folderUri: Uri) {
        preferences.edit().putString(KEY_SYNC_FOLDER, folderUri.toString()).apply()
        mutableAutoSyncFolder.value = folderUri.toString()
        AutoSyncWorker.schedule(appContext)
    }

    override suspend fun syncFromConfiguredFolder(): FolderSyncResult {
        val folderUri = mutableAutoSyncFolder.value
            ?: preferences.getString(KEY_SYNC_FOLDER, null)
            ?: error("尚未設定自動同步資料夾")
        val folder = DocumentFile.fromTreeUri(appContext, Uri.parse(folderUri))
            ?: error("無法開啟同步資料夾")
        val latestCsv = folder.listFiles()
            .asSequence()
            .filter { it.isFile && it.name?.endsWith(".csv", ignoreCase = true) == true }
            .maxByOrNull(DocumentFile::lastModified)
            ?: error("同步資料夾內沒有 CSV 檔案")
        val content = appContext.contentResolver.openInputStream(latestCsv.uri)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("無法讀取 ${latestCsv.name}")
        val result = importCsv(content)
        return FolderSyncResult(
            fileName = latestCsv.name ?: "CSV",
            importedCount = result.importedCount,
            skippedCount = result.skippedCount,
        )
    }

    override suspend fun disableAutoSync() {
        preferences.edit().remove(KEY_SYNC_FOLDER).apply()
        mutableAutoSyncFolder.value = null
        AutoSyncWorker.cancel(appContext)
    }

    override suspend fun configureServer(
        baseUrl: String,
        apiToken: String,
    ): ServerSyncResult = withContext(Dispatchers.IO) {
        val normalizedToken = PortfolioApiClient.normalizeApiToken(apiToken)
        val normalizedUrl = baseUrl.trim().trimEnd('/')
        val remote = PortfolioApiClient().fetch(normalizedUrl, normalizedToken)
        require(remote.holdings.isNotEmpty()) { "伺服器目前沒有資產資料" }

        preferences.edit()
            .putString(KEY_SERVER_URL, normalizedUrl)
            .putString(KEY_SERVER_TOKEN, normalizedToken)
            .apply()
        mutableServerUrl.value = normalizedUrl
        applyRemotePortfolio(remote)
        refreshMarketSummariesSafely(normalizedUrl, normalizedToken)
        refreshPaperTradingSafely(normalizedUrl, normalizedToken)
        ServerSyncWorker.schedule(appContext)
        ServerSyncResult(
            importedCount = remote.holdings.size,
            sourceCount = remote.sourceCount,
        )
    }

    override suspend fun syncFromServer(): ServerSyncResult = withContext(Dispatchers.IO) {
        val baseUrl = preferences.getString(KEY_SERVER_URL, null)
            ?: error("尚未設定電腦伺服器")
        val storedToken = preferences.getString(KEY_SERVER_TOKEN, null)
            ?: error("尚未設定 API Token")
        val token = PortfolioApiClient.normalizeApiToken(storedToken)
        if (token != storedToken) {
            preferences.edit().putString(KEY_SERVER_TOKEN, token).apply()
        }
        val remote = PortfolioApiClient().fetch(baseUrl, token)
        require(remote.holdings.isNotEmpty()) { "伺服器目前沒有資產資料" }

        applyRemotePortfolio(remote)
        refreshMarketSummariesSafely(baseUrl, token)
        refreshPaperTradingSafely(baseUrl, token)
        ServerSyncResult(
            importedCount = remote.holdings.size,
            sourceCount = remote.sourceCount,
        )
    }

    override suspend fun loadPriceHistory(
        holding: Holding,
        days: Int,
    ): PriceHistory = withContext(Dispatchers.IO) {
        val baseUrl = preferences.getString(KEY_SERVER_URL, null)
            ?: error("請先連接電腦資產伺服器")
        val token = preferences.getString(KEY_SERVER_TOKEN, null)
            ?: error("尚未設定 API Token")
        PortfolioApiClient().fetchPriceHistory(
            baseUrl = baseUrl,
            apiToken = token,
            holding = holding,
            days = days,
        )
    }

    override suspend fun loadPortfolioHistory(days: Int): PortfolioHistory =
        withContext(Dispatchers.IO) {
            val baseUrl = preferences.getString(KEY_SERVER_URL, null)
                ?: error("請先連接電腦資產伺服器")
            val token = preferences.getString(KEY_SERVER_TOKEN, null)
                ?: error("尚未設定 API Token")
            PortfolioApiClient().fetchPortfolioHistory(
                baseUrl = baseUrl,
                apiToken = token,
                days = days,
            )
        }

    override suspend fun refreshMarketSummaries() = withContext(Dispatchers.IO) {
        val baseUrl = preferences.getString(KEY_SERVER_URL, null)
            ?: return@withContext
        val token = preferences.getString(KEY_SERVER_TOKEN, null)
            ?: return@withContext
        refreshMarketSummariesSafely(baseUrl, token)
    }

    override suspend fun refreshPaperTrading() = withContext(Dispatchers.IO) {
        val baseUrl = preferences.getString(KEY_SERVER_URL, null)
            ?: error("請先連接電腦資產伺服器")
        val token = preferences.getString(KEY_SERVER_TOKEN, null)
            ?: error("尚未設定 API Token")
        mutablePaperTrading.value = PortfolioApiClient().fetchPaperTrading(baseUrl, token)
    }

    override suspend fun loadPaperBot(botId: String): PaperBot = withContext(Dispatchers.IO) {
        val baseUrl = preferences.getString(KEY_SERVER_URL, null)
            ?: error("請先連接電腦資產伺服器")
        val token = preferences.getString(KEY_SERVER_TOKEN, null)
            ?: error("尚未設定 API Token")
        PortfolioApiClient().fetchPaperBot(baseUrl, token, botId)
    }

    override suspend fun setDisplayCurrency(currency: Currency) {
        preferences.edit().putString(KEY_DISPLAY_CURRENCY, currency.name).apply()
        mutableAppSettings.value = mutableAppSettings.value.copy(displayCurrency = currency)
    }

    override suspend fun setUiLanguage(language: UiLanguage) {
        preferences.edit().putString(KEY_UI_LANGUAGE, language.name).apply()
        mutableAppSettings.value = mutableAppSettings.value.copy(language = language)
    }

    private fun refreshMarketSummariesSafely(baseUrl: String, token: String) {
        val summaries = runCatching {
            PortfolioApiClient().fetchMarketSummaries(
                baseUrl = baseUrl,
                apiToken = token,
                holdings = mutableHoldings.value,
            )
        }.getOrElse { return }
        mutableMarketSummaries.value = summaries.associateBy(MarketSummary::key)
        saveMarketSummaries(summaries)
    }

    private fun refreshPaperTradingSafely(baseUrl: String, token: String) {
        mutablePaperTrading.value = runCatching {
            PortfolioApiClient().fetchPaperTrading(baseUrl, token)
        }.getOrNull()
    }

    private fun applyRemotePortfolio(remote: RemotePortfolio) {
        mutableHoldings.value = remote.holdings
        mutableExchangeRates.value = remote.rates
        mutableInsights.value = remote.insights
        saveHoldings(remote.holdings)
        saveInsights(remote.insights)
        preferences.edit()
            .putFloat(KEY_USD_TO_TWD, remote.rates.usdToTwd.toFloat())
            .apply()
    }

    override suspend fun disableServerSync() {
        preferences.edit()
            .remove(KEY_SERVER_URL)
            .remove(KEY_SERVER_TOKEN)
            .apply()
        mutableServerUrl.value = null
        ServerSyncWorker.cancel(appContext)
    }

    override suspend fun resetToSampleData() {
        mutableHoldings.value = sampleHoldings
        saveHoldings(sampleHoldings)
        val baseUrl = preferences.getString(KEY_SERVER_URL, null)
        val token = preferences.getString(KEY_SERVER_TOKEN, null)
        if (baseUrl != null && token != null) {
            withContext(Dispatchers.IO) {
                refreshMarketSummariesSafely(baseUrl, token)
            }
        }
    }

    override fun close() {
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
    }

    private fun loadHoldings(): List<Holding> {
        val stored = preferences.getString(KEY_HOLDINGS, null) ?: return sampleHoldings
        return runCatching {
            val array = JSONArray(stored)
            List(array.length()) { index ->
                array.getJSONObject(index).toHolding()
            }
        }.getOrDefault(sampleHoldings)
    }

    private fun saveHoldings(holdings: List<Holding>) {
        val array = JSONArray()
        holdings.forEach { array.put(it.toJson()) }
        preferences.edit().putString(KEY_HOLDINGS, array.toString()).apply()
    }

    private fun loadMarketSummaries(): Map<String, MarketSummary> {
        val stored = preferences.getString(KEY_MARKET_SUMMARIES, null) ?: return emptyMap()
        return runCatching {
            val array = JSONArray(stored)
            List(array.length()) { index ->
                array.getJSONObject(index).toMarketSummary()
            }.associateBy(MarketSummary::key)
        }.getOrDefault(emptyMap())
    }

    private fun loadAppSettings(): AppSettings = AppSettings(
        displayCurrency = runCatching {
            Currency.valueOf(
                preferences.getString(KEY_DISPLAY_CURRENCY, Currency.TWD.name).orEmpty(),
            )
        }.getOrDefault(Currency.TWD),
        language = runCatching {
            UiLanguage.valueOf(
                preferences.getString(KEY_UI_LANGUAGE, UiLanguage.ZH_TW.name).orEmpty(),
            )
        }.getOrDefault(UiLanguage.ZH_TW),
    )

    private fun saveMarketSummaries(summaries: List<MarketSummary>) {
        val array = JSONArray()
        summaries.forEach { array.put(it.toJson()) }
        preferences.edit().putString(KEY_MARKET_SUMMARIES, array.toString()).apply()
    }

    private fun loadInsights(): PortfolioInsights {
        val stored = preferences.getString(KEY_INSIGHTS, null) ?: return PortfolioInsights()
        return runCatching {
            val root = JSONObject(stored)
            val transactions = root.getJSONArray("transactions")
            PortfolioInsights(
                transactions = List(transactions.length()) { index ->
                    transactions.getJSONObject(index).toTransaction()
                },
                expenses = root.optJSONArray("expenses")?.let { expenses ->
                    List(expenses.length()) { index ->
                        expenses.getJSONObject(index).toExpense()
                    }
                }.orEmpty(),
                performance = root.getJSONObject("performance").toPerformance(),
            )
        }.getOrDefault(PortfolioInsights())
    }

    private fun saveInsights(insights: PortfolioInsights) {
        val transactions = JSONArray()
        insights.transactions.forEach { transactions.put(it.toJson()) }
        val expenses = JSONArray()
        insights.expenses.forEach { expenses.put(it.toJson()) }
        val root = JSONObject()
            .put("transactions", transactions)
            .put("expenses", expenses)
            .put("performance", insights.performance.toJson())
        preferences.edit().putString(KEY_INSIGHTS, root.toString()).apply()
    }

    private fun Holding.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("institution", institution.name)
        put("accountName", accountName)
        put("symbol", symbol)
        put("name", name)
        put("assetType", assetType.name)
        put("currency", currency.name)
        put("quantity", quantity)
        put("averageCost", averageCost)
        put("marketPrice", marketPrice)
    }

    private fun JSONObject.toHolding(): Holding = Holding(
        id = getString("id"),
        institution = Institution.valueOf(getString("institution")),
        accountName = getString("accountName"),
        symbol = getString("symbol"),
        name = getString("name"),
        assetType = AssetType.valueOf(getString("assetType")),
        currency = Currency.valueOf(getString("currency")),
        quantity = getDouble("quantity"),
        averageCost = getDouble("averageCost"),
        marketPrice = getDouble("marketPrice"),
    )

    private fun MarketSummary.toJson(): JSONObject = JSONObject().apply {
        put("institution", institution.name)
        put("symbol", symbol)
        put("currency", currency.name)
        put("latestPrice", latestPrice)
        put("change", change)
        put("changeRate", changeRate)
        put("closes", JSONArray(closes))
        put("source", source)
    }

    private fun JSONObject.toMarketSummary(): MarketSummary {
        val values = getJSONArray("closes")
        return MarketSummary(
            institution = Institution.valueOf(getString("institution")),
            symbol = getString("symbol"),
            currency = Currency.valueOf(getString("currency")),
            latestPrice = getDouble("latestPrice"),
            change = getDouble("change"),
            changeRate = getDouble("changeRate"),
            closes = List(values.length()) { index -> values.getDouble(index) },
            source = getString("source"),
        )
    }

    private fun Transaction.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("institution", institution.name)
        put("accountName", accountName)
        put("symbol", symbol)
        put("name", name)
        put("transactionType", transactionType.name)
        put("currency", currency.name)
        put("quantity", quantity)
        put("price", price)
        put("amount", amount)
        put("realizedProfit", realizedProfit)
        put("tradeDate", tradeDate)
        put("settledDate", settledDate)
    }

    private fun JSONObject.toTransaction(): Transaction = Transaction(
        id = getString("id"),
        institution = Institution.valueOf(getString("institution")),
        accountName = getString("accountName"),
        symbol = getString("symbol"),
        name = getString("name"),
        transactionType = TransactionType.valueOf(getString("transactionType")),
        currency = Currency.valueOf(getString("currency")),
        quantity = getDouble("quantity"),
        price = getDouble("price"),
        amount = getDouble("amount"),
        realizedProfit = getDouble("realizedProfit"),
        tradeDate = getString("tradeDate"),
        settledDate = optString("settledDate")
            .takeIf { it.isNotBlank() && it != "null" },
    )

    private fun Expense.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("institution", institution.name)
        put("transactionDate", transactionDate)
        put("postedDate", postedDate)
        put("merchant", merchant)
        put("category", category.name)
        put("amount", amount)
        put("currency", currency.name)
        put("cardLastFour", cardLastFour)
        put("note", note)
    }

    private fun JSONObject.toExpense(): Expense = Expense(
        id = getString("id"),
        institution = Institution.valueOf(getString("institution")),
        transactionDate = getString("transactionDate"),
        postedDate = optString("postedDate").takeIf { it.isNotBlank() && it != "null" },
        merchant = getString("merchant"),
        category = ExpenseCategory.valueOf(getString("category")),
        amount = getDouble("amount"),
        currency = Currency.valueOf(getString("currency")),
        cardLastFour = optString("cardLastFour"),
        note = optString("note"),
    )

    private fun PerformanceSummary.toJson(): JSONObject = JSONObject().apply {
        put("realizedProfit", realizedProfit)
        put("unrealizedProfit", unrealizedProfit)
        put("dividendIncome", dividendIncome)
        put("totalReturn", totalReturn)
        put("returnRate", returnRate)
        put("totalBuyCost", totalBuyCost)
        put("valuationNote", valuationNote)
    }

    private fun JSONObject.toPerformance(): PerformanceSummary = PerformanceSummary(
        realizedProfit = getDouble("realizedProfit"),
        unrealizedProfit = getDouble("unrealizedProfit"),
        dividendIncome = getDouble("dividendIncome"),
        totalReturn = getDouble("totalReturn"),
        returnRate = getDouble("returnRate"),
        totalBuyCost = getDouble("totalBuyCost"),
        valuationNote = optString("valuationNote"),
    )

    private companion object {
        const val PREFERENCES_NAME = "asset_scope_portfolio"
        const val KEY_HOLDINGS = "holdings"
        const val KEY_SYNC_FOLDER = "sync_folder"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_SERVER_TOKEN = "server_token"
        const val KEY_USD_TO_TWD = "usd_to_twd"
        const val KEY_INSIGHTS = "portfolio_insights"
        const val KEY_MARKET_SUMMARIES = "market_summaries"
        const val KEY_DISPLAY_CURRENCY = "display_currency"
        const val KEY_UI_LANGUAGE = "ui_language"

        val sampleHoldings = listOf(
            Holding(
                id = "ft-vti",
                institution = Institution.FIRSTRade,
                accountName = "Firstrade 個人帳戶",
                symbol = "VTI",
                name = "Vanguard Total Stock Market ETF",
                assetType = AssetType.ETF,
                currency = Currency.USD,
                quantity = 35.0,
                averageCost = 242.5,
                marketPrice = 291.3,
            ),
            Holding(
                id = "ft-aapl",
                institution = Institution.FIRSTRade,
                accountName = "Firstrade 個人帳戶",
                symbol = "AAPL",
                name = "Apple",
                assetType = AssetType.STOCK,
                currency = Currency.USD,
                quantity = 20.0,
                averageCost = 178.0,
                marketPrice = 198.7,
            ),
            Holding(
                id = "sp-0050",
                institution = Institution.SINOPAC_SECURITIES,
                accountName = "永豐證券",
                symbol = "0050",
                name = "元大台灣50",
                assetType = AssetType.ETF,
                currency = Currency.TWD,
                quantity = 2_000.0,
                averageCost = 161.2,
                marketPrice = 185.4,
            ),
            Holding(
                id = "bank-twd",
                institution = Institution.SINOPAC_BANK,
                accountName = "DAWHO",
                symbol = "TWD",
                name = "新台幣活存",
                assetType = AssetType.DEPOSIT,
                currency = Currency.TWD,
                quantity = 1.0,
                averageCost = 280_000.0,
                marketPrice = 280_000.0,
            ),
        )
    }
}
