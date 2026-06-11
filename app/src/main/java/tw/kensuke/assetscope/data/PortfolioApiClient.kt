package tw.kensuke.assetscope.data

import org.json.JSONObject
import tw.kensuke.assetscope.domain.model.AssetType
import tw.kensuke.assetscope.domain.model.Currency
import tw.kensuke.assetscope.domain.model.ExchangeRates
import tw.kensuke.assetscope.domain.model.Holding
import tw.kensuke.assetscope.domain.model.Institution
import tw.kensuke.assetscope.domain.model.PerformanceSummary
import tw.kensuke.assetscope.domain.model.PortfolioInsights
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
        val connection = URI("$normalizedUrl/api/v1/portfolio")
            .toURL()
            .openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $apiToken")

            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            require(status in 200..299) {
                JSONObject(body.ifBlank { "{}" }).optString("detail", "伺服器回應 HTTP $status")
            }
            parse(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(body: String): RemotePortfolio {
        val root = JSONObject(body)
        require(root.getInt("schema_version") in 1..2) { "不支援的伺服器資料版本" }
        val rate = root.getJSONObject("exchange_rates").getDouble("usd_to_twd")
        val holdingArray = root.getJSONArray("holdings")
        val holdings = List(holdingArray.length()) { index ->
            holdingArray.getJSONObject(index).toHolding()
        }
        val transactions = root.optJSONArray("transactions")?.let { array ->
            List(array.length()) { index -> array.getJSONObject(index).toTransaction() }
        }.orEmpty()
        val performance = root.optJSONObject("performance")?.toPerformance()
            ?: PerformanceSummary()
        return RemotePortfolio(
            holdings = holdings,
            rates = ExchangeRates(usdToTwd = rate),
            sourceCount = root.getJSONArray("sources").length(),
            insights = PortfolioInsights(
                transactions = transactions,
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

    private fun JSONObject.toPerformance(): PerformanceSummary = PerformanceSummary(
        realizedProfit = getDouble("realized_profit"),
        unrealizedProfit = getDouble("unrealized_profit"),
        dividendIncome = getDouble("dividend_income"),
        totalReturn = getDouble("total_return"),
        returnRate = getDouble("return_rate"),
        totalBuyCost = getDouble("total_buy_cost"),
        valuationNote = optString("valuation_note"),
    )

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

    private fun isPrivateHost(host: String): Boolean {
        if (host == "localhost") return true
        val address = runCatching { InetAddress.getByName(host).address }.getOrNull() ?: return false
        if (address.size != 4) return false
        val first = address[0].toInt() and 0xff
        val second = address[1].toInt() and 0xff
        return first == 10 ||
            first == 127 ||
            (first == 172 && second in 16..31) ||
            (first == 192 && second == 168)
    }
}
