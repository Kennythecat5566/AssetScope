package tw.kensuke.assetscope.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import tw.kensuke.assetscope.domain.model.Currency
import tw.kensuke.assetscope.domain.model.PaperBot
import tw.kensuke.assetscope.domain.model.PaperBotPerformancePoint
import tw.kensuke.assetscope.domain.model.PaperBotTrade
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun PaperBotDetailDialog(
    bot: PaperBot,
    displayCurrency: Currency,
    usdToTwd: Double,
    onDismiss: () -> Unit,
) {
    val multiplier = if (displayCurrency == Currency.TWD) 1.0 else 1 / usdToTwd
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp,
                    top = 24.dp,
                    end = 20.dp,
                    bottom = 40.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item(key = "header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                botDisplayName(bot),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                botStrategy(bot),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = onDismiss) {
                            Text(uiText("關閉", "Close"))
                        }
                    }
                }
                item(key = "summary") {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                uiText("模擬資產淨值", "Paper net value"),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text(
                                money(bot.netValueTwd * multiplier, displayCurrency),
                                style = MaterialTheme.typography.headlineMedium,
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                DetailMetric(
                                    uiText("總報酬率", "Total return"),
                                    signedPercent(bot.returnRate),
                                )
                                DetailMetric(
                                    uiText("可用現金", "Cash"),
                                    money(bot.cashTwd * multiplier, displayCurrency),
                                )
                                DetailMetric(
                                    uiText("交易次數", "Trades"),
                                    bot.tradeCount.toString(),
                                )
                            }
                        }
                    }
                }
                item(key = "chart") {
                    Column {
                        Text(
                            uiText("資產績效趨勢", "Performance trend"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(8.dp))
                        PerformanceLegend()
                        Spacer(Modifier.height(8.dp))
                        if (bot.performanceHistory.size >= 2) {
                            PaperPerformanceChart(
                                points = bot.performanceHistory,
                                currency = displayCurrency,
                                multiplier = multiplier,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp),
                            )
                            Text(
                                uiText(
                                    "X 軸：日期　Y 軸：同起始資金的資產價值（${displayCurrency.name}）　雙指縮放、左右拖曳",
                                    "X: Date  Y: Rebased value (${displayCurrency.name})  Pinch to zoom and drag",
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        RoundedCornerShape(16.dp),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    uiText(
                                        "目前只有一筆淨值紀錄，累積下一個交易日後顯示趨勢。",
                                        "A second trading-day snapshot is needed to draw the trend.",
                                    ),
                                    modifier = Modifier.padding(24.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                if (bot.positions.isNotEmpty()) {
                    item(key = "positions-title") {
                        Text(
                            uiText("目前持倉", "Current positions"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    items(bot.positions, key = { "position-${it.symbol}" }) { position ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${position.symbol} · ${position.name}",
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        uiText(
                                            "${position.quantity.asQuantity()} 股",
                                            "${position.quantity.asQuantity()} shares",
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    money(
                                        position.marketValueTwd * multiplier,
                                        displayCurrency,
                                    ),
                                )
                            }
                        }
                    }
                }
                item(key = "trades-title") {
                    Column {
                        HorizontalDivider()
                        Spacer(Modifier.height(18.dp))
                        Text(
                            uiText("交易紀錄", "Trade history"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            uiText(
                                "共 ${bot.tradeCount} 筆虛擬成交",
                                "${bot.tradeCount} paper trades",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (bot.recentTrades.isEmpty()) {
                    item(key = "trades-empty") {
                        Text(
                            uiText("尚無交易紀錄", "No trades yet"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(bot.recentTrades, key = PaperBotTrade::id) { trade ->
                        TradeHistoryRow(trade, displayCurrency, multiplier)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailMetric(label: String, value: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PerformanceLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LegendItem(MaterialTheme.colorScheme.primary, uiText("機器人", "Bot"))
        LegendItem(Color(0xFFB47B5D), uiText("台灣加權", "TAIEX"))
        LegendItem(Color(0xFF7D9295), "S&P 500")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .background(color, CircleShape),
        )
        Spacer(Modifier.size(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun TradeHistoryRow(
    trade: PaperBotTrade,
    currency: Currency,
    multiplier: Double,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(15.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${trade.symbol} · ${trade.name}",
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${formatDateTime(trade.timestamp)} · ${
                        trade.quantity.asQuantity()
                    } @ ${money(trade.priceTwd * multiplier, currency)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (trade.side == "BUY") {
                        uiText("買入", "Buy")
                    } else {
                        uiText("賣出", "Sell")
                    },
                    color = if (trade.side == "BUY") Color(0xFFB5655A) else Color(0xFF3A8C78),
                    fontWeight = FontWeight.Bold,
                )
                Text(money(trade.amountTwd * multiplier, currency))
            }
        }
    }
}

@Composable
private fun PaperPerformanceChart(
    points: List<PaperBotPerformancePoint>,
    currency: Currency,
    multiplier: Double,
    modifier: Modifier = Modifier,
) {
    var zoom by remember(points) { mutableFloatStateOf(1f) }
    var panFraction by remember(points) { mutableFloatStateOf(1f) }
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val botColor = MaterialTheme.colorScheme.primary
    val taiwanColor = Color(0xFFB47B5D)
    val usColor = Color(0xFF7D9295)

    Canvas(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                RoundedCornerShape(16.dp),
            )
            .pointerInput(points) {
                detectTransformGestures { _, pan, gestureZoom, _ ->
                    zoom = (zoom * gestureZoom).coerceIn(1f, 15f)
                    panFraction = (panFraction - pan.x / 700f).coerceIn(0f, 1f)
                }
            },
    ) {
        val visibleCount = (points.size / zoom).roundToInt().coerceIn(2, points.size)
        val maxStart = (points.size - visibleCount).coerceAtLeast(0)
        val start = (maxStart * panFraction).roundToInt().coerceIn(0, maxStart)
        val visible = points.subList(start, start + visibleCount)
        val left = 12.dp.toPx()
        val right = 64.dp.toPx()
        val top = 18.dp.toPx()
        val bottom = 36.dp.toPx()
        val width = size.width - left - right
        val height = size.height - top - bottom
        val values = visible.flatMap {
            listOfNotNull(it.botValueTwd, it.taiwanIndexValue, it.usIndexValue)
        }.map { it * multiplier }
        val rawMin = values.min()
        val rawMax = values.max()
        val padding = ((rawMax - rawMin) * 0.08).coerceAtLeast(rawMax * 0.005)
        val minValue = (rawMin - padding).coerceAtLeast(0.0)
        val maxValue = rawMax + padding
        val range = (maxValue - minValue).coerceAtLeast(1.0)
        fun y(value: Double): Float =
            top + ((maxValue - value * multiplier) / range * height).toFloat()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = labelColor.toArgb()
            textSize = 10.dp.toPx()
        }
        repeat(4) { index ->
            val ratio = index / 3.0
            val lineY = top + height * ratio.toFloat()
            val value = maxValue - range * ratio
            drawLine(
                gridColor,
                Offset(left, lineY),
                Offset(size.width - right, lineY),
                1.dp.toPx(),
            )
            drawContext.canvas.nativeCanvas.drawText(
                compactValue(value, currency),
                size.width - right + 5.dp.toPx(),
                lineY + 4.dp.toPx(),
                paint,
            )
        }

        fun drawSeries(
            value: (PaperBotPerformancePoint) -> Double?,
            color: Color,
        ) {
            val path = Path()
            var started = false
            visible.forEachIndexed { index, point ->
                value(point)?.let {
                    val x = left + width * index / visible.lastIndex
                    if (!started) {
                        path.moveTo(x, y(it))
                        started = true
                    } else {
                        path.lineTo(x, y(it))
                    }
                }
            }
            if (started) drawPath(path, color, style = Stroke(2.5.dp.toPx()))
        }
        drawSeries(PaperBotPerformancePoint::botValueTwd, botColor)
        drawSeries(PaperBotPerformancePoint::taiwanIndexValue, taiwanColor)
        drawSeries(PaperBotPerformancePoint::usIndexValue, usColor)

        listOf(0, visible.lastIndex / 2, visible.lastIndex).distinct().forEach { index ->
            val x = left + width * index / visible.lastIndex
            val label = formatChartDate(visible[index].timestamp)
            val labelWidth = paint.measureText(label)
            drawContext.canvas.nativeCanvas.drawText(
                label,
                (x - labelWidth / 2).coerceIn(left, size.width - right - labelWidth),
                size.height - 10.dp.toPx(),
                paint,
            )
        }
    }
}

private fun formatChartDate(value: String): String = runCatching {
    OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("MM/dd"))
}.getOrElse { value.take(10).takeLast(5) }

private fun formatDateTime(value: String): String = runCatching {
    OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"))
}.getOrElse { value.take(16).replace("T", " ") }

private fun money(value: Double, currency: Currency): String = when (currency) {
    Currency.TWD -> "NT$${"%,.0f".format(value)}"
    Currency.USD -> "US$${"%,.2f".format(value)}"
}

private fun compactValue(value: Double, currency: Currency): String {
    val prefix = if (currency == Currency.TWD) "NT$" else "US$"
    return prefix + when {
        value >= 1_000_000 -> "%.1fM".format(value / 1_000_000)
        value >= 1_000 -> "%.0fK".format(value / 1_000)
        else -> "%.0f".format(value)
    }
}

private fun signedPercent(value: Double): String =
    "${if (value >= 0) "+" else ""}${"%.1f".format(value * 100)}%"

private fun Double.asQuantity(): String =
    if (this % 1.0 == 0.0) "%,.0f".format(this) else "%,.2f".format(this)

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).roundToInt(),
    (red * 255).roundToInt(),
    (green * 255).roundToInt(),
    (blue * 255).roundToInt(),
)
