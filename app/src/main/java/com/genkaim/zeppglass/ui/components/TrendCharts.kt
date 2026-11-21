package com.genkaim.zeppglass.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.genkaim.zeppglass.ui.theme.LocalZgPalette
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Straight-segment trend line in the style of the Zepp app: hollow markers with
 * the value printed above each point, a left value axis and weekday labels.
 * The vertical range always comes from the data, so small changes stay visible.
 */
@Composable
fun TrendLineChart(
    values: List<Float?>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    valueLabel: ((Float) -> String)? = null,
    showValues: Boolean = true,
    suggestedStep: Float? = null
) {
    val valid = values.filterNotNull()
    if (valid.isEmpty()) {
        Box(modifier)
        return
    }
    val measurer = rememberTextMeasurer()
    val axisStyle = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val valueStyle = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
    val grid = LocalZgPalette.current.grid
    val track = MaterialTheme.colorScheme.surface
    val badgeColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.60f)

    // A readable range: a little padding, rounded out to a step of 5 or 10.
    val rawLo = valid.min()
    val rawHi = valid.max()
    val span = max(1f, rawHi - rawLo)
    val step = suggestedStep ?: when {
        span <= 10f -> 2f
        span <= 30f -> 5f
        span <= 60f -> 10f
        span <= 200f -> 20f
        else -> 50f
    }
    val lo = floor((rawLo - span * 0.15f) / step) * step
    val hi = ceil((rawHi + span * 0.2f) / step) * step
    val range = max(step, hi - lo)

    Canvas(modifier) {
        val axisWidth = 30.dp.toPx()
        val axisHeight = 16.dp.toPx()
        val topPad = 16.dp.toPx()
        val chartWidth = (size.width - axisWidth).coerceAtLeast(10f)
        val chartHeight = (size.height - axisHeight - topPad).coerceAtLeast(10f)
        fun yOf(v: Float) = topPad + chartHeight - ((v - lo) / range) * chartHeight

        // Grid + y labels (lo / mid / hi)
        listOf(hi, (hi + lo) / 2f, lo).forEach { value ->
            val y = yOf(value)
            drawLine(grid, Offset(axisWidth, y), Offset(size.width, y), 1f)
            val layout = measurer.measure(value.roundToInt().toString(), axisStyle)
            drawText(layout, topLeft = Offset(0f, y - layout.size.height / 2f))
        }

        val count = values.size.coerceAtLeast(2)
        val stepX = chartWidth / (count - 1)
        fun xOf(index: Int) = axisWidth + index * stepX

        val path = Path()
        var started = false
        values.forEachIndexed { index, value ->
            if (value == null) return@forEachIndexed
            val point = Offset(xOf(index), yOf(value))
            if (!started) {
                path.moveTo(point.x, point.y)
                started = true
            } else {
                path.lineTo(point.x, point.y)
            }
        }
        if (started) {
            drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }

        values.forEachIndexed { index, value ->
            if (value == null) return@forEachIndexed
            val center = Offset(xOf(index), yOf(value))
            drawCircle(track, radius = 4.dp.toPx(), center = center)
            drawCircle(color, radius = 4.dp.toPx(), center = center, style = Stroke(2.dp.toPx()))
            if (showValues) {
                val text = valueLabel?.invoke(value) ?: value.roundToInt().toString()
                val layout = measurer.measure(text, valueStyle)
                val x = (center.x - layout.size.width / 2f)
                    .coerceIn(axisWidth, (size.width - layout.size.width).coerceAtLeast(axisWidth))
                val y = (center.y - layout.size.height - 5.dp.toPx())
                    .coerceAtLeast(0f)
                drawBadgedLabel(measurer, text, valueStyle, Offset(x, y), badgeColor)
            }
        }

        labels.forEachIndexed { index, label ->
            if (index >= count || label.isEmpty()) return@forEachIndexed
            val layout = measurer.measure(label, axisStyle)
            val x = (xOf(index) - layout.size.width / 2f)
                .coerceIn(axisWidth, (size.width - layout.size.width).coerceAtLeast(axisWidth))
            drawText(layout, topLeft = Offset(x, size.height - axisHeight / 2f - layout.size.height / 2f))
        }
    }
}

/**
 * Bars around a zero line, for training status (TSB). Positive bars go up,
 * negative bars go down; the value axis is labelled on the left.
 */
@Composable
fun SignedBarChart(
    values: List<Float?>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.tertiary,
    /** Decimal places on the axis and the bar labels; 0 keeps the integer look. */
    decimals: Int = 0
) {
    val valid = values.filterNotNull()
    if (valid.isEmpty()) {
        Box(modifier)
        return
    }
    val measurer = rememberTextMeasurer()
    val axisStyle = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val grid = LocalZgPalette.current.grid
    val badgeColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.60f)
    val maxAbs = max(abs(valid.max()), abs(valid.min()))
    val bound = if (decimals == 0) {
        ceil(maxAbs.coerceAtLeast(5f) / 5f) * 5f
    } else {
        // Tenths-scale readings (skin temperature) would be flattened by the
        // fixed ±5 training-status scale, so pick the tightest round bound.
        listOf(0.5f, 1f, 2f, 5f, 10f, 20f, 50f).firstOrNull { it >= maxAbs }
            ?: (ceil(maxAbs / 50f) * 50f)
    }
    fun rounded(v: Float): Float =
        if (decimals == 0) v.roundToInt().toFloat()
        else Math.round(v * 10f) / 10f
    fun plain(v: Float): String =
        if (decimals == 0) v.roundToInt().toString()
        else String.format(java.util.Locale.CHINA, "%.${decimals}f", rounded(v))
    fun signedLabel(v: Float): String {
        val r = rounded(v)
        return if (decimals > 0 && r > 0f) "+" + plain(r) else plain(r)
    }

    Canvas(modifier) {
        val axisWidth = 30.dp.toPx()
        val axisHeight = 16.dp.toPx()
        val topPad = 10.dp.toPx()
        val chartWidth = (size.width - axisWidth).coerceAtLeast(10f)
        val chartHeight = (size.height - axisHeight - topPad).coerceAtLeast(10f)
        val zeroY = topPad + chartHeight / 2f
        val half = chartHeight / 2f
        fun yOf(v: Float) = zeroY - (v / bound) * half

        listOf(bound, 0f, -bound).forEach { value ->
            val y = yOf(value)
            drawLine(grid, Offset(axisWidth, y), Offset(size.width, y), 1f)
            val layout = measurer.measure(plain(value), axisStyle)
            drawText(layout, topLeft = Offset(0f, y - layout.size.height / 2f))
        }

        val count = values.size.coerceAtLeast(1)
        val slot = chartWidth / count
        val barWidth = (slot * 0.34f).coerceIn(4f, 14.dp.toPx())
        values.forEachIndexed { index, value ->
            if (value == null) return@forEachIndexed
            val centerX = axisWidth + slot * index + slot / 2f
            val y = yOf(value)
            val top = minOf(y, zeroY)
            val height = abs(zeroY - y).coerceAtLeast(1.5f)
            drawRoundRect(
                color = if (value >= 0) color else color.copy(alpha = 0.45f),
                topLeft = Offset(centerX - barWidth / 2f, top),
                size = androidx.compose.ui.geometry.Size(barWidth, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
            )
            val text = signedLabel(value)
            val layout = measurer.measure(text, axisStyle)
            val ly = if (value >= 0) top - layout.size.height - 2.dp.toPx()
            else top + height + 2.dp.toPx()
            val lx = (centerX - layout.size.width / 2f)
                .coerceIn(axisWidth, (size.width - layout.size.width).coerceAtLeast(axisWidth))

            drawBadgedLabel(
                measurer,
                text,
                axisStyle,
                Offset(lx, ly.coerceIn(0f, size.height - layout.size.height)),
                badgeColor
            )
            labels.getOrNull(index)?.let { label ->
                if (label.isNotEmpty()) {
                    val lb = measurer.measure(label, axisStyle)
                    val x = (centerX - lb.size.width / 2f)
                        .coerceIn(axisWidth, (size.width - lb.size.width).coerceAtLeast(axisWidth))
                    drawText(lb, topLeft = Offset(x, size.height - axisHeight / 2f - lb.size.height / 2f))
                }
            }
        }
    }
}


