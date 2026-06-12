package tw.kensuke.assetscope.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import tw.kensuke.assetscope.domain.model.Currency
import tw.kensuke.assetscope.domain.model.Holding
import tw.kensuke.assetscope.domain.model.PriceCandle
import tw.kensuke.assetscope.domain.model.PriceHistory
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class ChartPeriod(val days: Int) {
    DAILY(30),
    MONTHLY(90),
    QUARTERLY(180),
    YEARLY(250),
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
    val selectedPeriod = ChartPeriod.entries.firstOrNull { it.days == selectedDays }
        ?: ChartPeriod.MONTHLY
    val selectedIndex = ChartPeriod.entries.indexOf(selectedPeriod)
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .pointerInput(selectedDays) {
                        awaitEachGesture {
                            awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Initial,
                            )
                            var horizontalDrag = 0f
                            var multiTouch = false
                            var pointerPressed: Boolean
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                multiTouch = multiTouch || event.changes.size > 1
                                event.changes.firstOrNull()?.let { change ->
                                    horizontalDrag +=
                                        change.position.x - change.previousPosition.x
                                }
                                pointerPressed = event.changes.any { it.pressed }
                            } while (pointerPressed)

                            if (!multiTouch && abs(horizontalDrag) >= 80.dp.toPx()) {
                                val nextIndex = if (horizontalDrag < 0) {
                                    (selectedIndex + 1).coerceAtMost(
                                        ChartPeriod.entries.lastIndex,
                                    )
                                } else {
                                    (selectedIndex - 1).coerceAtLeast(0)
                                }
                                if (nextIndex != selectedIndex) {
                                    onPeriodChange(ChartPeriod.entries[nextIndex].days)
                                }
                            }
                        }
                    },
            ) {
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
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${selectedPeriod.label()} · ${
                                history?.source ?: uiText("載入中", "Loading")
                            }",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    TextButton(onClick = onDismiss) { Text(uiText("關閉", "Close")) }
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            ChartPeriod.entries.forEach { period ->
                                ChartPeriodButton(
                                    period = period,
                                    selected = selectedDays == period.days,
                                    onClick = { onPeriodChange(period.days) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        PriceChart(
                            candles = history.candles,
                            averageCost = holding.averageCost,
                            currency = displayCurrency,
                            multiplier = multiplier,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            uiText(
                                "X 軸：日期　Y 軸：價格（${displayCurrency.name}）　雙指縮放、左右拖曳檢視",
                                "X: Date  Y: Price (${displayCurrency.name})  Pinch to zoom and drag to inspect",
                            ),
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
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
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
        Text(
            when (period) {
                ChartPeriod.DAILY -> uiText("日線", "1 month")
                ChartPeriod.MONTHLY -> uiText("月線", "3 months")
                ChartPeriod.QUARTERLY -> uiText("季線", "6 months")
                ChartPeriod.YEARLY -> uiText("年線", "1 year")
            },
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun ChartPeriod.label(): String = when (this) {
    ChartPeriod.DAILY -> uiText("日線", "1 month")
    ChartPeriod.MONTHLY -> uiText("月線", "3 months")
    ChartPeriod.QUARTERLY -> uiText("季線", "6 months")
    ChartPeriod.YEARLY -> uiText("年線", "1 year")
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

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ChartMetric(
                uiText("最新", "Latest"),
                (last.close * multiplier).asPrice(displayCurrency),
                modifier = Modifier.weight(1f),
            )
            ChartMetric(
                uiText("期間", "Period"),
                "${if (change >= 0) "+" else ""}${"%.1f".format(change * 100)}%",
                color,
                Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ChartMetric(
                uiText("最高", "High"),
                (high * multiplier).asPrice(displayCurrency),
                modifier = Modifier.weight(1f),
            )
            ChartMetric(
                uiText("最低", "Low"),
                (low * multiplier).asPrice(displayCurrency),
                modifier = Modifier.weight(1f),
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(10.dp))
    Text(
        "${uiText("持有均價", "Average cost")} ${
            (averageCost * multiplier).asPrice(displayCurrency)
        }",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ChartMetric(
    label: String,
    value: String,
    color: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PriceChart(
    candles: List<PriceCandle>,
    averageCost: Double,
    currency: Currency,
    multiplier: Double,
    modifier: Modifier = Modifier,
) {
    var zoom by remember(candles) { mutableFloatStateOf(1f) }
    var panFraction by remember(candles) { mutableFloatStateOf(1f) }
    val upColor = Color(0xFFB36B59)
    val downColor = Color(0xFF687866)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val averageColor = MaterialTheme.colorScheme.secondary

    Canvas(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
                RoundedCornerShape(14.dp),
            )
            .pointerInput(candles) {
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
        val right = 86.dp.toPx()
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
    Currency.TWD -> "NT$${"%,.0f".format(value)}"
    Currency.USD -> "US$${"%,.2f".format(value)}"
}

private val twdPriceFormatter = NumberFormat.getCurrencyInstance(Locale.TAIWAN).apply {
    minimumFractionDigits = 0
    maximumFractionDigits = 0
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
