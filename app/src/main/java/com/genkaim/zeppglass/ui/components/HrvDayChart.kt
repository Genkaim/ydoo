package com.genkaim.zeppglass.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

/**
 * One night of HRV samples as a red line with the highest and lowest readings
 * called out on the chart and the start/end clock times under it — the layout
 * the Zepp app uses for 心率变异性.
 */
@Composable
fun HrvDayChart(
    values: List<Float>,
    startLabel: String,
    endLabel: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = HeartRateRed
) {
    if (values.size < 2) {
        Box(modifier)
        return
    }
    val measurer = rememberTextMeasurer()
    val axisStyle = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val valueStyle = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
    val grid = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    val badgeColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.60f)

    Canvas(modifier) {
        val topPad = 14.dp.toPx()
        val axisHeight = 16.dp.toPx()
        val chartHeight = (size.height - topPad - axisHeight).coerceAtLeast(10f)
        val lo = values.min()
        val hi = values.max()
        val span = max(1f, hi - lo)
        fun yOf(v: Float) = topPad + chartHeight - ((v - lo) / span) * chartHeight

        val stepX = size.width / (values.size - 1).coerceAtLeast(1)
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = index * stepX
            val y = yOf(value)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawLine(grid, Offset(0f, yOf(hi)), Offset(size.width, yOf(hi)), 1.dp.toPx())
        drawLine(grid, Offset(0f, yOf(lo)), Offset(size.width, yOf(lo)), 1.dp.toPx())
        drawPath(path, color, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))

        listOf(hi to "max", lo to "min").forEach { (value, label) ->
            val y = yOf(value)
            val text = "$label ${value.toInt()}"
            val layout = measurer.measure(text, valueStyle)
            drawBadgedLabel(
                measurer,
                text,
                valueStyle,
                Offset(
                    (size.width - layout.size.width - 2.dp.toPx()).coerceAtLeast(0f),
                    (y - layout.size.height - 2.dp.toPx()).coerceAtLeast(0f)
                ),
                badgeColor
            )
        }

        val startLayout = measurer.measure(startLabel, axisStyle)
        drawText(startLayout, topLeft = Offset(0f, size.height - axisHeight))
        val endLayout = measurer.measure(endLabel, axisStyle)
        drawText(
            endLayout,
            topLeft = Offset(size.width - endLayout.size.width, size.height - axisHeight)
        )
    }
}

/**
 * Horizontal "slow ⇄ fast" style scale with a marker at [fraction]. Used by
 * the resting-heart-rate and HRV pages under the big value.
 */
@Composable
fun ScaleBar(
    fraction: Float,
    leftLabel: String,
    rightLabel: String,
    modifier: Modifier = Modifier
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val marker = MaterialTheme.colorScheme.onSurface
    Box(
        modifier
            .fillMaxWidth()
            .background(track, RoundedCornerShape(50))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val x = (size.width * fraction.coerceIn(0f, 1f))
                .coerceIn(3.dp.toPx(), size.width - 3.dp.toPx())
            drawCircle(marker, radius = 3.dp.toPx(), center = Offset(x, size.height / 2f))
        }
    }
}


