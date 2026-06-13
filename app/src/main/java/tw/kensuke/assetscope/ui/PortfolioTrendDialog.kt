package tw.kensuke.assetscope.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import tw.kensuke.assetscope.domain.model.Currency
import tw.kensuke.assetscope.domain.model.PortfolioHistory
import java.text.NumberFormat
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun PortfolioTrendDialog(
    history: PortfolioHistory?,
    loading: Boolean,
    error: String?,
    displayCurrency: Currency,
    usdToTwd: Double,
    onDismiss: () -> Unit,
) {
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
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            uiText("總資產趨勢", "Net worth trend"),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            uiText("每日同步快照", "Daily sync snapshots"),
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
                    error != null -> Text(error, color = MaterialTheme.colorScheme.error)
                    history != null && history.points.size >= 2 -> {
                        PortfolioTrendChart(
                            history = history,
                            currency = displayCurrency,
                            usdToTwd = usdToTwd,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            uiText(
                                "X 軸：日期時間　Y 軸：資產金額（${displayCurrency.name}）　雙指縮放、左右拖曳檢視",
                                "X: Date and time  Y: Net worth (${displayCurrency.name})  Pinch to zoom and drag to inspect",
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    history != null -> Text(
                        uiText(
                            "目前只有一筆快照。伺服器每次同步會更新當日資料，累積至少兩天後即可顯示趨勢。",
                            "Only one snapshot is available. Sync on at least two different days to display a trend.",
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun PortfolioTrendChart(
    history: PortfolioHistory,
    currency: Currency,
    usdToTwd: Double,
    modifier: Modifier = Modifier,
) {
    var zoom by remember(history) { mutableFloatStateOf(1f) }
    var panFraction by remember(history) { mutableFloatStateOf(1f) }
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val lineColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val multiplier = if (currency == Currency.TWD) 1.0 else 1 / usdToTwd
    val density = LocalDensity.current
    val convertedPoints = remember(history, multiplier) {
        history.points.map { it.timestamp to it.valueTwd * multiplier }
    }
    val textPaint = remember(labelColor, density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = labelColor.toAndroidArgb()
            textSize = with(density) { 10.dp.toPx() }
        }
    }

    Canvas(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
                RoundedCornerShape(14.dp),
            )
            .pointerInput(convertedPoints) {
                detectTransformGestures { _, pan, gestureZoom, _ ->
                    zoom = (zoom * gestureZoom).coerceIn(1f, 15f)
                    panFraction = (panFraction - pan.x / 700f).coerceIn(0f, 1f)
                }
            },
    ) {
        val points = convertedPoints
        val visibleCount = (points.size / zoom).roundToInt().coerceIn(2, points.size)
        val maxStart = (points.size - visibleCount).coerceAtLeast(0)
        val start = (maxStart * panFraction).roundToInt().coerceIn(0, maxStart)
        val visible = points.subList(start, start + visibleCount)
        val left = 12.dp.toPx()
        val right = 66.dp.toPx()
        val top = 18.dp.toPx()
        val bottom = 38.dp.toPx()
        val chartWidth = size.width - left - right
        val chartHeight = size.height - top - bottom
        var rawMin = Double.POSITIVE_INFINITY
        var rawMax = Double.NEGATIVE_INFINITY
        visible.forEach { (_, value) ->
            rawMin = minOf(rawMin, value)
            rawMax = maxOf(rawMax, value)
        }
        val padding = ((rawMax - rawMin) * 0.08).coerceAtLeast(rawMax * 0.005)
        val minValue = (rawMin - padding).coerceAtLeast(0.0)
        val maxValue = rawMax + padding
        val range = (maxValue - minValue).coerceAtLeast(1.0)
        fun y(value: Double): Float =
            top + ((maxValue - value) / range * chartHeight).toFloat()

        repeat(4) { index ->
            val ratio = index / 3.0
            val lineY = top + chartHeight * ratio.toFloat()
            val value = maxValue - range * ratio
            drawLine(
                color = gridColor,
                start = Offset(left, lineY),
                end = Offset(size.width - right, lineY),
                strokeWidth = 1.dp.toPx(),
            )
            drawContext.canvas.nativeCanvas.drawText(
                compactMoney(value, currency),
                size.width - right + 5.dp.toPx(),
                lineY + 4.dp.toPx(),
                textPaint,
            )
        }

        val path = Path()
        visible.forEachIndexed { index, point ->
            val x = left + chartWidth * index / visible.lastIndex
            val pointY = y(point.second)
            if (index == 0) path.moveTo(x, pointY) else path.lineTo(x, pointY)
        }
        drawPath(path, color = lineColor, style = Stroke(width = 3.dp.toPx()))
        visible.forEachIndexed { index, point ->
            val x = left + chartWidth * index / visible.lastIndex
            drawCircle(
                color = lineColor,
                radius = 3.dp.toPx(),
                center = Offset(x, y(point.second)),
            )
        }

        listOf(0, visible.lastIndex / 2, visible.lastIndex).distinct().forEach { index ->
            val x = left + chartWidth * index / visible.lastIndex
            val label = formatTimestamp(visible[index].first)
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

private fun formatTimestamp(value: String): String = runCatching {
    OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("MM/dd"))
}.getOrElse { value.take(10).takeLast(5) }

private fun compactMoney(value: Double, currency: Currency): String {
    val prefix = if (currency == Currency.TWD) "NT$" else "US$"
    val formatted = when {
        value >= 1_000_000 -> "%.1fM".format(value / 1_000_000)
        value >= 1_000 -> "%.0fK".format(value / 1_000)
        else -> NumberFormat.getNumberInstance(Locale.US).format(value)
    }
    return prefix + formatted
}

private fun Color.toAndroidArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).roundToInt(),
    (red * 255).roundToInt(),
    (green * 255).roundToInt(),
    (blue * 255).roundToInt(),
)
