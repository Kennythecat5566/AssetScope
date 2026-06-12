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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import tw.kensuke.assetscope.domain.model.Currency
import tw.kensuke.assetscope.domain.model.Holding
import tw.kensuke.assetscope.domain.model.PriceCandle
import tw.kensuke.assetscope.domain.model.PriceHistory
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

private enum class ChartMode {
    CANDLE,
    TREND,
}

private enum class ChartPeriod(val label: String, val days: Int) {
    DAILY("日線", 30),
    MONTHLY("月線", 90),
    QUARTERLY("季線", 180),
    YEARLY("年線", 250),
}

@Composable
fun StockChartDialog(
    holding: Holding,
    history: PriceHistory?,
    loading: Boolean,
    error: String?,
    displayCurrency: Currency,
    usdToTwd: Double,
    selectedDays: Int,
    onPeriodChange: (Int) -> Unit,
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
                            "日線 · ${history?.source ?: "載入中"}",
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
                    history != null && history.candles.isNotEmpty() -> {
                        val multiplier = conversionMultiplier(
                            source = history.currency,
                            target = displayCurrency,
                            usdToTwd = usdToTwd,
                        )
                        HistorySummary(
                            history = history,
                            averageCost = holding.averageCost,
                            displayCurrency = displayCurrency,
                            multiplier = multiplier,
                        )
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
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ChartPeriod.entries.forEach { period ->
                                ChartPeriodButton(
                                    period = period,
                                    selected = selectedDays == period.days,
                                    onClick = { onPeriodChange(period.days) },
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        PriceChart(
                            candles = history.candles,
                            averageCost = holding.averageCost,
                            mode = mode,
                            currency = displayCurrency,
                            multiplier = multiplier,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "X 軸：日期　Y 軸：價格（${displayCurrency.name}）"
                                + "　雙指縮放、左右拖曳檢視",
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
private fun ChartPeriodButton(
    period: ChartPeriod,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 10.dp,
            vertical = 4.dp,
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (selected) {
                MaterialTheme.colorScheme.onSecondary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
    ) {
        Text(period.label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun HistorySummary(
    history: PriceHistory,
    averageCost: Double,
    displayCurrency: Currency,
    multiplier: Double,
) {
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
        ChartMetric("最新", (last.close * multiplier).asPrice(displayCurrency))
        ChartMetric("期間", "${if (change >= 0) "+" else ""}${"%.1f".format(change * 100)}%", color)
        ChartMetric("最高", (high * multiplier).asPrice(displayCurrency))
        ChartMetric("最低", (low * multiplier).asPrice(displayCurrency))
    }
    Spacer(Modifier.height(12.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(10.dp))
    Text(
        "持有均價 ${(averageCost * multiplier).asPrice(displayCurrency)}",
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
    currency: Currency,
    multiplier: Double,
    modifier: Modifier = Modifier,
) {
    var zoom by remember(candles, mode) { mutableFloatStateOf(1f) }
    var panFraction by remember(candles, mode) { mutableFloatStateOf(1f) }
    val upColor = Color(0xFFB36B59)
    val downColor = Color(0xFF687866)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val trendColor = MaterialTheme.colorScheme.primary
    val averageColor = MaterialTheme.colorScheme.secondary

    Canvas(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
                RoundedCornerShape(14.dp),
            )
            .pointerInput(candles, mode) {
                detectTransformGestures { _, pan, gestureZoom, _ ->
                    zoom = (zoom * gestureZoom).coerceIn(1f, 15f)
                    panFraction = (panFraction - pan.x / 700f).coerceIn(0f, 1f)
                }
            },
    ) {
        if (candles.size < 2) return@Canvas
        val visibleCount = (candles.size / zoom).roundToInt().coerceIn(5, candles.size)
        val maxStart = (candles.size - visibleCount).coerceAtLeast(0)
        val start = (maxStart * panFraction).roundToInt().coerceIn(0, maxStart)
        val visible = candles.subList(start, start + visibleCount)
        val left = 12.dp.toPx()
        val right = 58.dp.toPx()
        val top = 16.dp.toPx()
        val bottom = 34.dp.toPx()
        val chartWidth = size.width - left - right
        val chartHeight = size.height - top - bottom
        val convertedAverage = averageCost * multiplier
        val minPrice = minOf(
            visible.minOf(PriceCandle::low) * multiplier,
            convertedAverage,
        ) * 0.995
        val maxPrice = maxOf(
            visible.maxOf(PriceCandle::high) * multiplier,
            convertedAverage,
        ) * 1.005
        val range = (maxPrice - minPrice).coerceAtLeast(0.01)
        fun y(price: Double): Float =
            top + ((maxPrice - price) / range * chartHeight).toFloat()

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = labelColor.toArgb()
            textSize = 10.dp.toPx()
        }
        repeat(4) { index ->
            val ratio = index / 3.0
            val lineY = top + chartHeight * ratio.toFloat()
            val value = maxPrice - range * ratio
            drawLine(
                color = gridColor,
                start = Offset(left, lineY),
                end = Offset(size.width - right, lineY),
                strokeWidth = 1.dp.toPx(),
            )
            drawContext.canvas.nativeCanvas.drawText(
                axisMoney(value, currency),
                size.width - right + 5.dp.toPx(),
                lineY + 4.dp.toPx(),
                textPaint,
            )
        }
        drawLine(
            color = averageColor,
            start = Offset(left, y(convertedAverage)),
            end = Offset(size.width - right, y(convertedAverage)),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                floatArrayOf(8.dp.toPx(), 6.dp.toPx()),
            ),
        )

        val step = chartWidth / visible.size
        if (mode == ChartMode.CANDLE) {
            val bodyWidth = (step * 0.58f).coerceAtLeast(2.dp.toPx())
            visible.forEachIndexed { index, candle ->
                val x = left + step * (index + 0.5f)
                val color = if (candle.close >= candle.open) upColor else downColor
                drawLine(
                    color = color,
                    start = Offset(x, y(candle.high * multiplier)),
                    end = Offset(x, y(candle.low * multiplier)),
                    strokeWidth = 1.dp.toPx(),
                )
                val bodyTop = minOf(y(candle.open * multiplier), y(candle.close * multiplier))
                val bodyBottom = maxOf(y(candle.open * multiplier), y(candle.close * multiplier))
                drawRect(
                    color = color,
                    topLeft = Offset(x - bodyWidth / 2, bodyTop),
                    size = Size(bodyWidth, (bodyBottom - bodyTop).coerceAtLeast(1.5.dp.toPx())),
                )
            }
        } else {
            val path = Path()
            visible.forEachIndexed { index, candle ->
                val x = left + chartWidth * index / visible.lastIndex
                val pointY = y(candle.close * multiplier)
                if (index == 0) path.moveTo(x, pointY) else path.lineTo(x, pointY)
            }
            drawPath(path, color = trendColor, style = Stroke(width = 2.5.dp.toPx()))
        }

        listOf(0, visible.lastIndex / 2, visible.lastIndex).distinct().forEach { index ->
            val x = left + chartWidth * index / visible.lastIndex
            val label = visible[index].date.takeLast(5)
            val width = textPaint.measureText(label)
            drawContext.canvas.nativeCanvas.drawText(
                label,
                (x - width / 2).coerceIn(left, size.width - right - width),
                size.height - 10.dp.toPx(),
                textPaint,
            )
        }
    }
}

private fun conversionMultiplier(
    source: Currency,
    target: Currency,
    usdToTwd: Double,
): Double = when {
    source == target -> 1.0
    source == Currency.USD -> usdToTwd
    else -> 1 / usdToTwd
}

private fun axisMoney(value: Double, currency: Currency): String = when (currency) {
    Currency.TWD -> "NT$${NumberFormat.getNumberInstance(Locale.TAIWAN).format(value)}"
    Currency.USD -> "US$${"%,.2f".format(value)}"
}

private val twdPriceFormatter = NumberFormat.getCurrencyInstance(Locale.TAIWAN).apply {
    maximumFractionDigits = 2
}

private fun Double.asPrice(currency: Currency): String = when (currency) {
    Currency.TWD -> twdPriceFormatter.format(this)
    Currency.USD -> "US$${"%,.2f".format(this)}"
}

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).roundToInt(),
    (red * 255).roundToInt(),
    (green * 255).roundToInt(),
    (blue * 255).roundToInt(),
)
