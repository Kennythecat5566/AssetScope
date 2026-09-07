package tw.kensuke.assetscope.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import org.json.JSONArray
import tw.kensuke.assetscope.MainActivity
import tw.kensuke.assetscope.R
import tw.kensuke.assetscope.domain.HoldingNormalizer
import tw.kensuke.assetscope.domain.PortfolioCalculator
import tw.kensuke.assetscope.domain.model.AssetType
import tw.kensuke.assetscope.domain.model.Currency
import tw.kensuke.assetscope.domain.model.ExchangeRates
import tw.kensuke.assetscope.domain.model.Holding
import tw.kensuke.assetscope.domain.model.Institution
import java.text.NumberFormat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class AssetScopeWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    companion object {
        private const val PREFERENCES_NAME = "asset_scope_portfolio"
        private const val KEY_HOLDINGS = "holdings"
        private const val KEY_USD_TO_TWD = "usd_to_twd"
        private const val KEY_DISPLAY_CURRENCY = "display_currency"
        private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, AssetScopeWidgetProvider::class.java),
            )
            if (ids.isNotEmpty()) {
                updateWidgets(context, manager, ids)
            }
        }

        private fun updateWidgets(
            context: Context,
            manager: AppWidgetManager,
            ids: IntArray,
        ) {
            val snapshot = loadSnapshot(context)
            ids.forEach { id ->
                manager.updateAppWidget(id, widgetViews(context, snapshot))
            }
        }

        private fun widgetViews(
            context: Context,
            snapshot: WidgetSnapshot,
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_asset_scope)
            views.setTextViewText(R.id.widget_total_value, snapshot.totalValue)
            views.setTextViewText(R.id.widget_profit, snapshot.profit)
            views.setTextViewText(R.id.widget_return_rate, snapshot.returnRate)
            views.setTextViewText(R.id.widget_updated_at, snapshot.updatedAt)
            views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
            return views
        }

        private fun openAppIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun loadSnapshot(context: Context): WidgetSnapshot {
            val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            val holdings = runCatching {
                val stored = preferences.getString(KEY_HOLDINGS, null).orEmpty()
                val array = JSONArray(stored)
                HoldingNormalizer.normalize(
                    List(array.length()) { index -> array.getJSONObject(index).toHolding() },
                )
            }.getOrDefault(emptyList())

            if (holdings.isEmpty()) {
                return WidgetSnapshot(
                    totalValue = "No data",
                    profit = "Open AssetScope",
                    returnRate = "Sync first",
                    updatedAt = "Tap to start",
                )
            }

            val displayCurrency = runCatching {
                Currency.valueOf(
                    preferences.getString(KEY_DISPLAY_CURRENCY, Currency.TWD.name).orEmpty(),
                )
            }.getOrDefault(Currency.TWD)
            val usdToTwd = preferences.getFloat(KEY_USD_TO_TWD, 32.4f).toDouble()
            val summary = PortfolioCalculator.calculate(
                holdings,
                ExchangeRates(usdToTwd = usdToTwd),
            )
            val total = summary.totalValueTwd.toDisplayCurrency(displayCurrency, usdToTwd)
            val profit = summary.unrealizedProfitTwd.toDisplayCurrency(displayCurrency, usdToTwd)
            return WidgetSnapshot(
                totalValue = total.asMoney(displayCurrency),
                profit = "P/L ${profit.asSignedMoney(displayCurrency)}",
                returnRate = "Return ${summary.returnRate.asPercent()}",
                updatedAt = "Updated ${LocalTime.now().format(timeFormatter)}",
            )
        }

        private fun org.json.JSONObject.toHolding(): Holding = Holding(
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

        private fun Double.toDisplayCurrency(currency: Currency, usdToTwd: Double): Double =
            when (currency) {
                Currency.TWD -> this
                Currency.USD -> this / usdToTwd
            }

        private fun Double.asMoney(currency: Currency): String {
            val format = NumberFormat.getNumberInstance(Locale.US).apply {
                maximumFractionDigits = 0
            }
            return when (currency) {
                Currency.TWD -> "NT$${format.format(this)}"
                Currency.USD -> "$${format.format(this)}"
            }
        }

        private fun Double.asSignedMoney(currency: Currency): String {
            val sign = if (this >= 0) "+" else "-"
            return "$sign${kotlin.math.abs(this).asMoney(currency)}"
        }

        private fun Double.asPercent(): String {
            val sign = if (this >= 0) "+" else "-"
            return "$sign${String.format(Locale.US, "%.1f", kotlin.math.abs(this) * 100)}%"
        }
    }
}

private data class WidgetSnapshot(
    val totalValue: String,
    val profit: String,
    val returnRate: String,
    val updatedAt: String,
)
