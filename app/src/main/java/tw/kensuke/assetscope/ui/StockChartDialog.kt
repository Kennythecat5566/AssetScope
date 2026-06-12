package tw.kensuke.assetscope.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import tw.kensuke.assetscope.domain.model.Currency
import tw.kensuke.assetscope.domain.model.Holding
import tw.kensuke.assetscope.domain.model.PriceCandle
import tw.kensuke.assetscope.domain.model.PriceHistory
import java.text.NumberFormat
import java.util.Locale

private enum class ChartMode {
    CANDLE,
    TREND,
}

@Composable
fun StockChartDialog(
    holding: Holding,
    history: PriceHistory?,
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
) {
    var mode by remember { mutableStateOf(ChartMode.CANDLE) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${holding.symbol} · ${holding.name}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "DAILY MARKET · ${history?.source ?: "載入中"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    TextButton(onClick = onDismiss) { Text("關閉") }
                }
                Spacer(Modifier.height(18.dp))

                when {
                    loading -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                    error != null -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(error, color = MaterialTheme.colorScheme.error)
                    }
                    history != null -> {
                        HistorySummary(history, holding.averageCost)
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ChartModeButton(
                                text = "K 線",
                                selected = mode == ChartMode.CANDLE,
                                onClick = { mode = ChartMode.CANDLE },
                            )
                            ChartModeButton(
                                text = "趨勢",
                                selected = mode == ChartMode.TREND,
                                onClick = { mode = ChartMode.TREND },
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        PriceChart(
                            candles = history.candles,
                            averageCost = holding.averageCost,
                            mode = mode,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(270.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "${history.candles.first().date} — ${history.candles.last().date} · "
                                + "虛線為持有均價",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistorySummary(history: PriceHistory, averageCost: Double) {
    val candles = history.candles
    val first = candles.first()
    val last = candles.last()
    val change = (last.close / first.close) - 1
    val high = candles.maxOf(PriceCandle::high)
    val low = candles.minOf(PriceCandle::low)
    val color = if (change >= 0) Color(0xFF9A5D50) else Color(0xFF627066)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ChartMetric("最新", last.close.asPrice(history.currency))
        ChartMetric("期間", "${if (change >= 0) "+" else ""}${"%.1f".format(change * 100)}%", color)
        ChartMetric("最高", high.asPrice(history.currency))
        ChartMetric("最低", low.asPrice(history.currency))
    }
    Spacer(Modifier.height(12.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(10.dp))
    Text(
        "持有均價 ${averageCost.asPrice(history.currency)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ChartMetric(label: String, value: String, color: Color = Color.Unspecified) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun ChartModeButton(text: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
    ) {
        Text(text)
    }
}

@Composable
private fun PriceChart(
    candles: List<PriceCandle>,
    averageCost: Double,
    mode: ChartMode,
    modifier: Modifier = Modifier,
) {
    val upColor = Color(0xFFB36B59)
    val downColor = Color(0xFF687866)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val trendColor = MaterialTheme.colorScheme.primary
    val averageColor = MaterialTheme.colorScheme.secondary

    Canvas(
        modifier = modifier.background(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
            RoundedCornerShape(14.dp),
        ),
    ) {
        if (candles.size < 2) return@Canvas
        val padding = 18.dp.toPx()
        val chartWidth = size.width - padding * 2
        val chartHeight = size.height - padding * 2
        val minPrice = minOf(candles.minOf(PriceCandle::low), averageCost) * 0.995
        val maxPrice = maxOf(candles.maxOf(PriceCandle::high), averageCost) * 1.005
        val range = (maxPrice - minPrice).coerceAtLeast(0.01)
        fun y(price: Double): Float =
            padding + ((maxPrice - price) / range * chartHeight).toFloat()

        repeat(4) { index ->
            val lineY = padding + chartHeight * index / 3
            drawLine(
                color = gridColor,
                start = Offset(padding, lineY),
                end = Offset(size.width - padding, lineY),
                strokeWidth = 1.dp.toPx(),
            )
        }
        drawLine(
            color = averageColor,
            start = Offset(padding, y(averageCost)),
            end = Offset(size.width - padding, y(averageCost)),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                floatArrayOf(8.dp.toPx(), 6.dp.toPx()),
            ),
        )

        val step = chartWidth / candles.size
        if (mode == ChartMode.CANDLE) {
            val bodyWidth = (step * 0.58f).coerceAtLeast(2.dp.toPx())
            candles.forEachIndexed { index, candle ->
                val x = padding + step * (index + 0.5f)
                val color = if (candle.close >= candle.open) upColor else downColor
                drawLine(
                    color = color,
                    start = Offset(x, y(candle.high)),
                    end = Offset(x, y(candle.low)),
                    strokeWidth = 1.dp.toPx(),
                )
                val top = minOf(y(candle.open), y(candle.close))
                val bottom = maxOf(y(candle.open), y(candle.close))
                drawRect(
                    color = color,
                    topLeft = Offset(x - bodyWidth / 2, top),
                    size = Size(bodyWidth, (bottom - top).coerceAtLeast(1.5.dp.toPx())),
                )
            }
        } else {
            val path = Path()
            candles.forEachIndexed { index, candle ->
                val x = padding + chartWidth * index / (candles.lastIndex)
                val pointY = y(candle.close)
                if (index == 0) path.moveTo(x, pointY) else path.lineTo(x, pointY)
            }
            drawPath(path, color = trendColor, style = Stroke(width = 2.5.dp.toPx()))
        }
    }
}

private val twdPriceFormatter = NumberFormat.getCurrencyInstance(Locale.TAIWAN).apply {
    maximumFractionDigits = 2
}

private fun Double.asPrice(currency: Currency): String = when (currency) {
    Currency.TWD -> twdPriceFormatter.format(this)
    Currency.USD -> "US$${"%,.2f".format(this)}"
}
