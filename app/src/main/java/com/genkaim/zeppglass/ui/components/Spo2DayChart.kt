package com.genkaim.zeppglass.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.genkaim.zeppglass.data.Spo2Point
import com.genkaim.zeppglass.ui.theme.LocalZgPalette
import java.time.Instant
import java.time.ZoneId
import kotlin.math.floor

/**
 * One day of blood oxygen on a 00:00–23:59 axis, the same reading as the
 * official app's day chart: every sample is a short bar at its clock time.
 * A gap means the watch recorded nothing then, never a zero bar.
 */
@Composable
fun Spo2DayChart(
    points: List<Spo2Point>,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val axisStyle = TextStyle(
        fontSize = 9.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val grid = LocalZgPalette.current.grid
    // The official chart paints blood oxygen in a soft red; keep the bars pale
    // so a low reading stands out without shouting.
    val barColor = MaterialTheme.colorScheme.error.copy(alpha = 0.35f)

    val zone = ZoneId.systemDefault()
    // Minutes since midnight: the x axis is the clock, not the sample order.
    val slots = points.map { point ->
        val time = Instant.ofEpochMilli(point.epochMs).atZone(zone).toLocalTime()
        (time.hour * 60 + time.minute) to point.value
    }
    val lo = floor((slots.minOf { it.second } - 1) / 5f).toInt() * 5
    val hi = maxOf(100, slots.maxOf { it.second })

    Canvas(modifier) {
        val labelWidth = 34.dp.toPx()
        val axisHeight = 16.dp.toPx()
        val topPad = 4.dp.toPx()
        val plotWidth = (size.width - labelWidth).coerceAtLeast(1f)
        val plotHeight = (size.height - axisHeight - topPad).coerceAtLeast(1f)
        val span = (hi - lo).coerceAtLeast(5).toFloat()

        fun yOf(value: Int): Float =
            topPad + (hi - value) / span * plotHeight

        // Horizontal grid every 5% with the percentage on the right.
        var value = lo
        while (value <= hi) {
            val y = yOf(value)
            drawLine(
                color = grid,
                start = Offset(0f, y),
                end = Offset(plotWidth, y),
                strokeWidth = 1.dp.toPx()
            )
            val layout = measurer.measure("$value%", axisStyle)
            drawText(
                layout,
                topLeft = Offset(
                    plotWidth + 4.dp.toPx(),
                    (y - layout.size.height / 2f).coerceIn(0f, size.height - layout.size.height)
                )
            )
            value += 5
        }

        // Bottom endpoints: 00:00 on the left, 23:59 on the right.
        val leftLabel = measurer.measure("00:00", axisStyle)
        val rightLabel = measurer.measure("23:59", axisStyle)
        drawText(leftLabel, topLeft = Offset(0f, size.height - axisHeight + 2.dp.toPx()))
        drawText(
            rightLabel,
            topLeft = Offset(
                (plotWidth - rightLabel.size.width).coerceAtLeast(0f),
                size.height - axisHeight + 2.dp.toPx()
            )
        )

        // One short bar per sample, standing on its own value.
        val barWidth = 3.dp.toPx()
        val barHeight = 10.dp.toPx()
        slots.forEach { (minute, sample) ->
            val centerX = minute / 1440f * plotWidth
            val centerY = yOf(sample)
            drawRoundRect(
                color = barColor,
                topLeft = Offset(
                    (centerX - barWidth / 2f).coerceIn(0f, plotWidth - barWidth),
                    (centerY - barHeight / 2f).coerceAtLeast(topPad)
                ),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(1.5f.dp.toPx())
            )
        }
    }
}
