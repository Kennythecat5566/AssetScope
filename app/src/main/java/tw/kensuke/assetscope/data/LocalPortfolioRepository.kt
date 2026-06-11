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
import tw.kensuke.assetscope.domain.model.Currency
import tw.kensuke.assetscope.domain.model.ExchangeRates
import tw.kensuke.assetscope.domain.model.Holding
import tw.kensuke.assetscope.domain.model.Institution

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
    private val preferenceListener =
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                KEY_HOLDINGS -> mutableHoldings.value = loadHoldings()
                KEY_SYNC_FOLDER -> {
                    mutableAutoSyncFolder.value = preferences.getString(KEY_SYNC_FOLDER, null)
                }
                KEY_SERVER_URL -> mutableServerUrl.value = preferences.getString(KEY_SERVER_URL, null)
            }
        }

    override val holdings: StateFlow<List<Holding>> = mutableHoldings
    override val exchangeRates: StateFlow<ExchangeRates> = mutableExchangeRates
    override val autoSyncFolder: StateFlow<String?> = mutableAutoSyncFolder
    override val serverUrl: StateFlow<String?> = mutableServerUrl

    init {
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    override suspend fun importCsv(content: String): ImportResult {
        val (imported, skipped) = CsvHoldingParser.parse(content)
        if (imported.isNotEmpty()) {
            mutableHoldings.value = imported
            saveHoldings(imported)
        }
        return ImportResult(importedCount = imported.size, skippedCount = skipped)
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
        require(apiToken.length >= 16) { "API Token 至少需要 16 個字元" }
        val normalizedUrl = baseUrl.trim().trimEnd('/')
        val remote = PortfolioApiClient().fetch(normalizedUrl, apiToken)
        require(remote.holdings.isNotEmpty()) { "伺服器目前沒有資產資料" }

        preferences.edit()
            .putString(KEY_SERVER_URL, normalizedUrl)
            .putString(KEY_SERVER_TOKEN, apiToken)
            .apply()
        mutableServerUrl.value = normalizedUrl
        applyRemotePortfolio(remote)
        ServerSyncWorker.schedule(appContext)
        ServerSyncResult(
            importedCount = remote.holdings.size,
            sourceCount = remote.sourceCount,
        )
    }

    override suspend fun syncFromServer(): ServerSyncResult = withContext(Dispatchers.IO) {
        val baseUrl = preferences.getString(KEY_SERVER_URL, null)
            ?: error("尚未設定電腦伺服器")
        val token = preferences.getString(KEY_SERVER_TOKEN, null)
            ?: error("尚未設定 API Token")
        val remote = PortfolioApiClient().fetch(baseUrl, token)
        require(remote.holdings.isNotEmpty()) { "伺服器目前沒有資產資料" }

        applyRemotePortfolio(remote)
        ServerSyncResult(
            importedCount = remote.holdings.size,
            sourceCount = remote.sourceCount,
        )
    }

    private fun applyRemotePortfolio(remote: RemotePortfolio) {
        mutableHoldings.value = remote.holdings
        mutableExchangeRates.value = remote.rates
        saveHoldings(remote.holdings)
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

    private companion object {
        const val PREFERENCES_NAME = "asset_scope_portfolio"
        const val KEY_HOLDINGS = "holdings"
        const val KEY_SYNC_FOLDER = "sync_folder"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_SERVER_TOKEN = "server_token"
        const val KEY_USD_TO_TWD = "usd_to_twd"

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
