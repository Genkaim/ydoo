package com.genkaim.zeppglass.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.genkaim.zeppglass.ui.theme.LocalZgPalette
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * Chart style shared by every screen: thin capsule bars that never fill their
 * slot, a hairline grid, a weekday axis under the plot and a single value label
 * so the chart stays readable at phone width.
 */

/** Bar chart with a weekday axis and a value label above each bar. */
@Composable
fun AxisBarChart(
    values: List<Float?>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    goal: Float? = null,
    valueSuffix: String = "",
    highlightLast: Boolean = true,
    showValues: Boolean = true,
    valueLabel: ((Float) -> String)? = null,
    maxBarWidth: Dp = 13.dp
) {
    val valid = values.filterNotNull()
    if (valid.isEmpty()) {
        Box(modifier)
        return
    }
    val measurer = rememberTextMeasurer()
    val textStyle = TextStyle(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val valueStyle = TextStyle(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface)
    val grid = LocalZgPalette.current.grid
    val hi = max(valid.max(), goal ?: 0f).coerceAtLeast(1f)
    val goalColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    val badgeColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.60f)

    Canvas(modifier) {
        val axisHeight = 16.dp.toPx()
        val valueHeight = 13.dp.toPx()
        val axisWidth = 30.dp.toPx()
        val chartTop = valueHeight
        val chartHeight = (size.height - axisHeight - valueHeight).coerceAtLeast(8f)
        val chartWidth = (size.width - axisWidth).coerceAtLeast(1f)
        for (i in 0..2) {
            val y = chartTop + chartHeight * i / 2f
            drawLine(grid, Offset(axisWidth, y), Offset(size.width, y), 1f)
            val tick = (hi * (1f - i / 2f)).roundToInt()
            val layout = measurer.measure(tick.toString(), textStyle)
            drawText(
                layout,
                topLeft = Offset(0f, (y - layout.size.height / 2f).coerceAtLeast(0f))
            )
        }

        val count = values.size.coerceAtLeast(1)
        val slot = chartWidth / count
        // Bars keep a wide gap: 45% of their slot at most, so a 7-day chart
        // never turns into a wall of blocks.
        val barWidth = min(slot * 0.45f, maxBarWidth.toPx()).coerceAtLeast(2f)
        val radius = CornerRadius(min(barWidth / 2f, 5.dp.toPx()))

        values.forEachIndexed { index, value ->
            val centerX = axisWidth + index * slot + slot / 2f
            if (value == null) return@forEachIndexed
            val h = ((value / hi) * chartHeight).coerceAtLeast(1f)
            val isLast = index == values.lastIndex
            val barColor = if (highlightLast && !isLast) color.copy(alpha = 0.62f) else color
            drawRoundRect(
                color = barColor,
                topLeft = Offset(centerX - barWidth / 2f, chartTop + chartHeight - h),
                size = Size(barWidth, h),
                cornerRadius = radius
            )
            if (showValues) {
                // Sit right on top of the bar instead of in one shared row at
                // the top of the chart.
                val layout = measurer.measure(
                    valueLabel?.invoke(value) ?: "${value.roundToInt()}$valueSuffix",
                    valueStyle
                )
                drawLabel(
                    measurer,
                    valueLabel?.invoke(value) ?: "${value.roundToInt()}$valueSuffix",
                    valueStyle,
                    centerX,
                    (chartTop + chartHeight - h - layout.size.height / 2f - 3.dp.toPx())
                        .coerceAtLeast(layout.size.height / 2f),
                    center = true,
                    badge = badgeColor
                )
            }
        }

        labels.forEachIndexed { index, label ->
            if (index < count && label.isNotEmpty()) {
                drawLabel(
                    measurer, label, textStyle,
                    axisWidth + index * slot + slot / 2f,
                    size.height - axisHeight / 2f,
                    center = true
                )
            }
        }

        goal?.let {
            val y = chartTop + chartHeight - (it / hi) * chartHeight
            var x = axisWidth
            val dash = 3.dp.toPx()
            while (x < size.width) {
                drawLine(
                    goalColor,
                    Offset(x, y),
                    Offset(min(x + dash, size.width), y),
                    1.dp.toPx()
                )
                x += dash * 2.2f
            }
        }
    }
}

/** Line chart with a hairline grid, a soft curve and one dot on the last point. */
@Composable
fun AxisLineChart(
    values: List<Float?>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    minValue: Float? = null,
    maxValue: Float? = null,
    valueSuffix: String = "",
    showGrid: Boolean = true,
    /** true draws a soft curve; false joins the readings with straight lines. */
    smooth: Boolean = true
) {
    val valid = values.filterNotNull()
    if (valid.isEmpty()) {
        Box(modifier)
        return
    }
    val measurer = rememberTextMeasurer()
    val textStyle = TextStyle(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val valueStyle = TextStyle(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface)
    val grid = LocalZgPalette.current.grid
    val badgeColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.60f)
    val lo = minValue ?: valid.min()
    val hi = maxValue ?: valid.max()
    val span = max(1f, hi - lo)
    val dotColor = MaterialTheme.colorScheme.surface

    Canvas(modifier) {
        val axisHeight = if (labels.isEmpty()) 2.dp.toPx() else 16.dp.toPx()
        val topPad = 10.dp.toPx()
        val bottomPad = 6.dp.toPx()
        val axisWidth = 30.dp.toPx()
        val chartWidth = (size.width - axisWidth).coerceAtLeast(1f)
        val chartHeight = (size.height - axisHeight - topPad - bottomPad).coerceAtLeast(8f)
        if (showGrid) {
            for (i in 0..2) {
                val y = topPad + chartHeight * i / 2f
                drawLine(grid, Offset(axisWidth, y), Offset(size.width, y), 1f)
                val tick = hi + (lo - hi) * i / 2f
                val text = "${tick.roundToInt()}$valueSuffix"
                val layout = measurer.measure(text, textStyle)
                drawText(
                    layout,
                    topLeft = Offset(0f, (y - layout.size.height / 2f).coerceAtLeast(0f))
                )
            }
        }
        val stepX = if (values.size <= 1) 0f else chartWidth / (values.size - 1)
        fun yOf(v: Float) = topPad + chartHeight - ((v - lo) / span) * chartHeight

        val path = Path()
        var prev: Offset? = null
        var started = false
        values.forEachIndexed { index, value ->
            if (value == null) return@forEachIndexed
            val point = Offset(axisWidth + index * stepX, yOf(value))
            if (!started) {
                path.moveTo(point.x, point.y)
                started = true
            } else {
                val p = prev
                if (p != null) {
                    if (smooth) {
                        val midX = (p.x + point.x) / 2f
                        path.cubicTo(midX, p.y, midX, point.y, point.x, point.y)
                    } else {
                        path.lineTo(point.x, point.y)
                    }
                }
            }
            prev = point
        }
        if (!started) return@Canvas
        drawPath(path, color, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))

        val lastValueIndex = values.indexOfLast { it != null }
        if (lastValueIndex >= 0) {
            val lastValue = values[lastValueIndex] ?: return@Canvas
            val center = Offset(axisWidth + lastValueIndex * stepX, yOf(lastValue))
            drawCircle(color, radius = 3.4.dp.toPx(), center = center)
            drawCircle(dotColor, radius = 1.5.dp.toPx(), center = center)
            drawLabel(
                measurer,
                "${lastValue.roundToInt()}$valueSuffix",
                valueStyle,
                center.x,
                center.y - 11.dp.toPx(),
                center = true,
                badge = badgeColor
            )
        }

        // Axis labels are drawn per slot, so a day without a value still shows
        // its weekday instead of leaving a hole in the axis.
        if (labels.isNotEmpty()) {
            labels.forEachIndexed { index, label ->
                if (label.isNotEmpty() && index < values.size) {
                    drawLabel(
                        measurer, label, textStyle,
                        axisWidth + index * stepX,
                        size.height - axisHeight / 2f,
                        center = true
                    )
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLabel(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    x: Float,
    y: Float,
    center: Boolean,
    badge: Color? = null
) {
    val layout = measurer.measure(text, style)
    val maxX = (size.width - layout.size.width).coerceAtLeast(0f)
    val dx = (if (center) x - layout.size.width / 2f else x).coerceIn(0f, maxX)
    val dy = y - layout.size.height / 2f
    if (badge != null) {
        drawBadgedLabel(measurer, text, style, Offset(dx, dy), badge)
    } else {
        drawText(layout, topLeft = Offset(dx, dy))
    }
}


