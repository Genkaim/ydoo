package com.genkaim.zeppglass.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import kotlin.math.max

// Zepp's four stress bands; the same colours label the chart and the pie.
val StressRelaxed = Color(0xFF4C8DF6)
val StressNormal = Color(0xFF35A97C)
val StressMedium = Color(0xFFE0A93B)
val StressHigh = Color(0xFFE06C3B)

fun stressBandColor(value: Int): Color = when {
    value <= 39 -> StressRelaxed
    value <= 59 -> StressNormal
    value <= 79 -> StressMedium
    else -> StressHigh
}

/**
 * The day's stress samples as thin bars, coloured by band, with the newest
 * sample marked. The x axis is the full 00:00–23:59 day so a gap in wearing the
 * band stays visible instead of squeezing the measured hours together.
 */
@Composable
fun StressBandChart(
    curve: List<Pair<Long, Int>>,
    modifier: Modifier = Modifier
) {
    if (curve.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val axisStyle = TextStyle(
        fontSize = 9.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    val markerLine = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    val points = remember(curve) {
        val zone = ZoneId.systemDefault()
        curve.sortedBy { it.first }.map { (epochMs, value) ->
            val time = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalTime()
            Triple((time.hour * 60 + time.minute) / 1440f, epochMs, value)
        }
    }

    Canvas(modifier) {
        val rightPad = 30.dp.toPx()
        val axisHeight = 16.dp.toPx()
        val topPad = 4.dp.toPx()
        val plotWidth = (size.width - rightPad).coerceAtLeast(1f)
        val plotHeight = (size.height - axisHeight - topPad).coerceAtLeast(1f)

        fun yOf(value: Int): Float = topPad + (1f - value / 100f) * plotHeight

        // Grid and value labels on the right, as in the Zepp stress page.
        listOf(100, 75, 50, 25, 0).forEach { value ->
            val y = yOf(value)
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(plotWidth, y),
                strokeWidth = 1.dp.toPx()
            )
            val layout = measurer.measure(value.toString(), axisStyle)
            drawText(
                layout,
                topLeft = Offset(size.width - layout.size.width, y - layout.size.height / 2f)
            )
        }

        val slot = plotWidth / 288f
        val barWidth = max(slot * 0.7f, 1.2.dp.toPx())
        points.forEach { (fraction, _, value) ->
            val x = fraction * plotWidth
            drawRect(
                color = stressBandColor(value),
                topLeft = Offset(x - barWidth / 2f, yOf(value)),
                size = Size(barWidth, (yOf(0) - yOf(value)).coerceAtLeast(1.dp.toPx()))
            )
        }

        // Newest sample: a thin guide down from the top and a dot on the value.
        points.lastOrNull()?.let { (fraction, _, value) ->
            val x = fraction * plotWidth
            drawLine(
                color = markerLine,
                start = Offset(x, topPad),
                end = Offset(x, yOf(value)),
                strokeWidth = 1.dp.toPx()
            )
            drawCircle(Color.White, 5.dp.toPx(), Offset(x, yOf(value)))
            drawCircle(StressHigh, 3.5.dp.toPx(), Offset(x, yOf(value)))
        }

        listOf("00:00" to 0f, "23:59" to 1f).forEach { (label, fraction) ->
            val layout = measurer.measure(label, axisStyle)
            val left = (fraction * plotWidth - if (fraction == 0f) 0f else layout.size.width.toFloat())
                .coerceIn(0f, plotWidth - layout.size.width)
            drawText(
                layout,
                topLeft = Offset(left, size.height - layout.size.height)
            )
        }
    }
}

/**
 * Share of the day spent in each stress band. Drawn as a donut so the legend's
 * percentages stay the source of truth; a zero band simply draws nothing.
 */
@Composable
fun StressDonut(
    relaxedPct: Double?,
    normalPct: Double?,
    mediumPct: Double?,
    highPct: Double?,
    modifier: Modifier = Modifier
) {
    val parts = listOf(
        StressRelaxed to (relaxedPct ?: 0.0),
        StressNormal to (normalPct ?: 0.0),
        StressMedium to (mediumPct ?: 0.0),
        StressHigh to (highPct ?: 0.0)
    )
    val total = parts.sumOf { it.second }
    Canvas(modifier) {
        if (total <= 0.0) return@Canvas
        val stroke = 16.dp.toPx()
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        var start = -90f
        parts.forEach { (color, pct) ->
            if (pct <= 0.0) return@forEach
            val sweep = (pct / total * 360.0).toFloat()
            drawArc(
                color = color,
                startAngle = start + 2.4f,
                sweepAngle = (sweep - 4.8f).coerceAtLeast(0.8f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            start += sweep
        }
    }
}
