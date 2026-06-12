package tw.kensuke.assetscope.data

import org.json.JSONArray
import org.json.JSONObject
import tw.kensuke.assetscope.domain.model.AssetType
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
import tw.kensuke.assetscope.domain.model.PortfolioHistoryPoint
import tw.kensuke.assetscope.domain.model.PriceCandle
import tw.kensuke.assetscope.domain.model.PriceHistory
import tw.kensuke.assetscope.domain.model.PaperBot
import tw.kensuke.assetscope.domain.model.PaperBotPosition
import tw.kensuke.assetscope.domain.model.PaperBotTrade
import tw.kensuke.assetscope.domain.model.PaperBotEquityPoint
import tw.kensuke.assetscope.domain.model.PaperTradingDashboard
import tw.kensuke.assetscope.domain.model.Transaction
import tw.kensuke.assetscope.domain.model.TransactionType
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI

data class RemotePortfolio(
    val holdings: List<Holding>,
    val rates: ExchangeRates,
    val sourceCount: Int,
    val insights: PortfolioInsights,
)

class PortfolioApiClient {
    fun fetch(baseUrl: String, apiToken: String): RemotePortfolio {
        val normalizedUrl = validateAndNormalizeBaseUrl(baseUrl)
        val normalizedToken = normalizeApiToken(apiToken)
        val connection = URI("$normalizedUrl/api/v1/portfolio")
            .toURL()
            .openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $normalizedToken")

            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            require(status in 200..299) {
                errorMessage(status, body)
            }
            parse(body)
        } finally {
            connection.disconnect()
        }
    }

    fun fetchPriceHistory(
        baseUrl: String,
        apiToken: String,
        holding: Holding,
        days: Int,
    ): PriceHistory {
        val normalizedUrl = validateAndNormalizeBaseUrl(baseUrl)
        val token = normalizeApiToken(apiToken)
        val connection = URI(
            "$normalizedUrl/api/v1/history/${holding.institution.name}/${holding.symbol}?days=$days",
        ).toURL().openConnection() as HttpURLConnection

        return try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            require(status in 200..299) { errorMessage(status, body) }
            JSONObject(body).toPriceHistory()
        } finally {
            connection.disconnect()
        }
    }

    fun fetchPortfolioHistory(
        baseUrl: String,
        apiToken: String,
        days: Int,
    ): PortfolioHistory {
        val normalizedUrl = validateAndNormalizeBaseUrl(baseUrl)
        val token = normalizeApiToken(apiToken)
        val connection = URI(
            "$normalizedUrl/api/v1/portfolio/history?days=$days",
        ).toURL().openConnection() as HttpURLConnection

        return try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            require(status in 200..299) { errorMessage(status, body) }
            val points = JSONObject(body).getJSONArray("points")
            PortfolioHistory(
                points = List(points.length()) { index ->
                    points.getJSONObject(index).let {
                        PortfolioHistoryPoint(
                            timestamp = it.getString("timestamp"),
                            valueTwd = it.getDouble("value_twd"),
                        )
                    }
                },
            )
        } finally {
            connection.disconnect()
        }
    }

    fun fetchPaperTrading(
        baseUrl: String,
        apiToken: String,
    ): PaperTradingDashboard {
        val normalizedUrl = validateAndNormalizeBaseUrl(baseUrl)
        val token = normalizeApiToken(apiToken)
        val connection = URI("$normalizedUrl/api/v1/paper-trading")
            .toURL().openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 90_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            require(status in 200..299) { errorMessage(status, body) }
            JSONObject(body).toPaperTrading()
        } finally {
            connection.disconnect()
        }
    }

    fun fetchMarketSummaries(
        baseUrl: String,
        apiToken: String,
        holdings: List<Holding>,
    ): List<MarketSummary> {
        val marketHoldings = holdings.filter {
            it.assetType == AssetType.STOCK || it.assetType == AssetType.ETF
        }
        if (marketHoldings.isEmpty()) return emptyList()

        val normalizedUrl = validateAndNormalizeBaseUrl(baseUrl)
        val token = normalizeApiToken(apiToken)
        val connection = URI("$normalizedUrl/api/v1/market/summaries")
            .toURL()
            .openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 8_000
            connection.readTimeout = 90_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            val items = JSONArray()
            marketHoldings.forEach { holding ->
                items.put(
                    JSONObject()
                        .put("institution", holding.institution.name)
                        .put("symbol", holding.symbol),
                )
            }
            connection.outputStream.bufferedWriter().use {
                it.write(JSONObject().put("items", items).toString())
            }
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            require(status in 200..299) { errorMessage(status, body) }
            val summaries = JSONObject(body).getJSONArray("summaries")
            List(summaries.length()) { index ->
                summaries.getJSONObject(index).toMarketSummary()
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(body: String): RemotePortfolio {
        val root = JSONObject(body)
        require(root.getInt("schema_version") in 1..3) { "不支援的伺服器資料版本" }
        val exchangeRates = root.getJSONObject("exchange_rates")
        val rate = exchangeRates.getDouble("usd_to_twd")
        val holdingArray = root.getJSONArray("holdings")
        val holdings = List(holdingArray.length()) { index ->
            holdingArray.getJSONObject(index).toHolding()
        }
        val transactions = root.optJSONArray("transactions")?.let { array ->
            List(array.length()) { index -> array.getJSONObject(index).toTransaction() }
        }.orEmpty()
        val expenses = root.optJSONArray("expenses")?.let { array ->
            List(array.length()) { index -> array.getJSONObject(index).toExpense() }
        }.orEmpty()
        val performance = root.optJSONObject("performance")?.toPerformance()
            ?: PerformanceSummary()
        return RemotePortfolio(
            holdings = holdings,
            rates = ExchangeRates(
                usdToTwd = rate,
                updatedAt = exchangeRates.optString("updated_at")
                    .takeIf { it.isNotBlank() && it != "null" },
                source = exchangeRates.optString("source", "configured"),
            ),
            sourceCount = root.getJSONArray("sources").length(),
            insights = PortfolioInsights(
                transactions = transactions,
                expenses = expenses,
                performance = performance,
            ),
        )
    }

    private fun JSONObject.toHolding(): Holding = Holding(
        id = getString("id"),
        institution = Institution.valueOf(getString("institution")),
        accountName = getString("account_name"),
        symbol = getString("symbol"),
        name = getString("name"),
        assetType = AssetType.valueOf(getString("asset_type")),
        currency = Currency.valueOf(getString("currency")),
        quantity = getDouble("quantity"),
        averageCost = getDouble("average_cost"),
        marketPrice = getDouble("market_price"),
    )

    private fun JSONObject.toTransaction(): Transaction = Transaction(
        id = getString("id"),
        institution = Institution.valueOf(getString("institution")),
        accountName = getString("account_name"),
        symbol = getString("symbol"),
        name = getString("name"),
        transactionType = TransactionType.valueOf(getString("transaction_type")),
        currency = Currency.valueOf(getString("currency")),
        quantity = getDouble("quantity"),
        price = getDouble("price"),
        amount = getDouble("amount"),
        realizedProfit = getDouble("realized_profit"),
        tradeDate = getString("trade_date"),
        settledDate = optString("settled_date").takeIf { it.isNotBlank() && it != "null" },
    )

    private fun JSONObject.toExpense(): Expense = Expense(
        id = getString("id"),
        institution = Institution.valueOf(getString("institution")),
        transactionDate = getString("transaction_date"),
        postedDate = optString("posted_date").takeIf { it.isNotBlank() && it != "null" },
        merchant = getString("merchant"),
        category = ExpenseCategory.valueOf(getString("category")),
        amount = getDouble("amount"),
        currency = Currency.valueOf(getString("currency")),
        cardLastFour = optString("card_last_four"),
        note = optString("note"),
    )

    private fun JSONObject.toPerformance(): PerformanceSummary = PerformanceSummary(
        realizedProfit = getDouble("realized_profit"),
        unrealizedProfit = getDouble("unrealized_profit"),
        dividendIncome = getDouble("dividend_income"),
        totalReturn = getDouble("total_return"),
        returnRate = getDouble("return_rate"),
        totalBuyCost = getDouble("total_buy_cost"),
        valuationNote = optString("valuation_note"),
    )

    private fun JSONObject.toPriceHistory(): PriceHistory {
        val candleArray = getJSONArray("candles")
        return PriceHistory(
            symbol = getString("symbol"),
            currency = Currency.valueOf(getString("currency")),
            source = getString("source"),
            candles = List(candleArray.length()) { index ->
                candleArray.getJSONObject(index).let { candle ->
                    PriceCandle(
                        date = candle.getString("date"),
                        open = candle.getDouble("open"),
                        high = candle.getDouble("high"),
                        low = candle.getDouble("low"),
                        close = candle.getDouble("close"),
                        volume = candle.getDouble("volume"),
                    )
                }
            },
        )
    }

    private fun JSONObject.toMarketSummary(): MarketSummary {
        val closeArray = getJSONArray("closes")
        return MarketSummary(
            institution = Institution.valueOf(getString("institution")),
            symbol = getString("symbol"),
            currency = Currency.valueOf(getString("currency")),
            latestPrice = getDouble("latest_price"),
            change = getDouble("change"),
            changeRate = getDouble("change_rate"),
            closes = List(closeArray.length()) { index -> closeArray.getDouble(index) },
            source = getString("source"),
        )
    }

    private fun JSONObject.toPaperTrading(): PaperTradingDashboard {
        val botArray = getJSONArray("bots")
        return PaperTradingDashboard(
            generatedAt = getString("generated_at"),
            paperOnly = getBoolean("paper_only"),
            bots = List(botArray.length()) { index ->
                val bot = botArray.getJSONObject(index)
                val positions = bot.getJSONArray("positions")
                val trades = bot.getJSONArray("recent_trades")
                val equity = bot.getJSONArray("equity_history")
                PaperBot(
                    id = bot.getString("id"),
                    name = bot.getString("name"),
                    strategy = bot.getString("strategy"),
                    paperOnly = bot.getBoolean("paper_only"),
                    initialCashTwd = bot.getDouble("initial_cash_twd"),
                    cashTwd = bot.getDouble("cash_twd"),
                    netValueTwd = bot.getDouble("net_value_twd"),
                    totalReturnTwd = bot.getDouble("total_return_twd"),
                    returnRate = bot.getDouble("return_rate"),
                    tradeCount = bot.getInt("trade_count"),
                    lastRunAt = bot.optString("last_run_at")
                        .takeIf { it.isNotBlank() && it != "null" },
                    positions = List(positions.length()) { positionIndex ->
                        positions.getJSONObject(positionIndex).let {
                            PaperBotPosition(
                                symbol = it.getString("symbol"),
                                name = it.getString("name"),
                                quantity = it.getDouble("quantity"),
                                averageCostTwd = it.getDouble("average_cost_twd"),
                                marketPriceTwd = it.getDouble("market_price_twd"),
                                marketValueTwd = it.getDouble("market_value_twd"),
                                unrealizedProfitTwd = it.getDouble("unrealized_profit_twd"),
                            )
                        }
                    },
                    recentTrades = List(trades.length()) { tradeIndex ->
                        trades.getJSONObject(tradeIndex).let {
                            PaperBotTrade(
                                id = it.getString("id"),
                                timestamp = it.getString("timestamp"),
                                botId = it.getString("bot_id"),
                                symbol = it.getString("symbol"),
                                name = it.getString("name"),
                                side = it.getString("side"),
                                quantity = it.getDouble("quantity"),
                                priceTwd = it.getDouble("price_twd"),
                                amountTwd = it.getDouble("amount_twd"),
                                reason = it.getString("reason"),
                            )
                        }
                    },
                    equityHistory = List(equity.length()) { equityIndex ->
                        equity.getJSONObject(equityIndex).let {
                            PaperBotEquityPoint(
                                timestamp = it.getString("timestamp"),
                                netValueTwd = it.getDouble("net_value_twd"),
                            )
                        }
                    },
                )
            },
        )
    }

    private fun validateAndNormalizeBaseUrl(value: String): String {
        val normalized = value.trim().trimEnd('/')
        val uri = URI(normalized)
        require(uri.scheme == "http" || uri.scheme == "https") {
            "伺服器網址必須使用 http:// 或 https://"
        }
        require(!uri.host.isNullOrBlank()) { "伺服器網址格式錯誤" }
        if (uri.scheme == "http") {
            require(isPrivateHost(uri.host)) {
                "HTTP 僅允許私人區網位址；公開網址請使用 HTTPS"
            }
        }
        return normalized
    }

    companion object {
        fun errorMessage(status: Int, body: String): String {
            val detail = runCatching {
                JSONObject(body).optString("detail").trim()
            }.getOrNull().orEmpty()
            if (detail.isNotBlank()) return detail

            val plainText = body
                .replace(Regex("<[^>]+>"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(160)
            return when {
                status >= 500 -> "伺服器內部錯誤（HTTP $status），請檢查電腦伺服器"
                plainText.isNotBlank() -> "$plainText（HTTP $status）"
                else -> "伺服器回應 HTTP $status"
            }
        }

        fun normalizeApiToken(value: String): String {
            val normalized = value.trim()
            require(normalized.length >= 16) { "API Token 至少需要 16 個字元" }
            require(normalized.none(Char::isISOControl)) {
                "API Token 含有無效的換行或控制字元，請重新貼上"
            }
            return normalized
        }

        internal fun isPrivateAddress(address: ByteArray?, host: String = ""): Boolean {
            if (host == "localhost") return true
            if (address == null || address.size != 4) return false
            val first = address[0].toInt() and 0xff
            val second = address[1].toInt() and 0xff
            return first == 10 ||
                first == 127 ||
                (first == 100 && second in 64..127) ||
                (first == 172 && second in 16..31) ||
                (first == 192 && second == 168)
        }
    }

    private fun isPrivateHost(host: String): Boolean = isPrivateAddress(
        runCatching { InetAddress.getByName(host).address }.getOrNull(),
        host,
    )
}
