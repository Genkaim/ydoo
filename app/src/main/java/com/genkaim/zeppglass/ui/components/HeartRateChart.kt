package com.genkaim.zeppglass.ui.components

import androidx.compose.foundation.Canvas
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
import kotlin.math.max

/** Heart-rate red used by the activity chart, readable in both themes. */
val HeartRateRed = Color(0xFFE5484D)

/**
 * Today's heart rate as a red line with the highest and lowest readings called
 * out on the chart itself, so the numbers do not have to be read from a title.
 */
@Composable
fun HeartRateChart(
    values: List<Int>,
    modifier: Modifier = Modifier,
    color: Color = HeartRateRed
) {
    if (values.size < 2) return
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface)
    val grid = LocalZgPalette.current.grid
    val surface = MaterialTheme.colorScheme.surface
    val badgeColor = surface.copy(alpha = 0.60f)

    Canvas(modifier) {
        val topPad = 16.dp.toPx()
        val bottomPad = 16.dp.toPx()
        val chartHeight = (size.height - topPad - bottomPad).coerceAtLeast(10f)
        val lo = values.min()
        val hi = values.max()
        val span = max(1, hi - lo)
        fun yOf(value: Int) = topPad + chartHeight - ((value - lo).toFloat() / span) * chartHeight

        for (i in 0..1) {
            val y = topPad + chartHeight * i
            drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f)
        }

        val stepX = size.width / (values.size - 1).coerceAtLeast(1)

        // Visible guide lines for the highest and lowest reading.
        val guide = color.copy(alpha = 0.72f)
        drawLine(guide, Offset(0f, yOf(hi)), Offset(size.width, yOf(hi)), 1.3.dp.toPx())
        drawLine(guide, Offset(0f, yOf(lo)), Offset(size.width, yOf(lo)), 1.3.dp.toPx())
        val path = Path()
        var prev: Offset? = null
        values.forEachIndexed { index, value ->
            val point = Offset(index * stepX, yOf(value))
            if (index == 0) path.moveTo(point.x, point.y)
            else {
                val p = prev
                if (p != null) {
                    val midX = (p.x + point.x) / 2f
                    path.cubicTo(midX, p.y, midX, point.y, point.x, point.y)
                }
            }
            prev = point
        }
        drawPath(path, color, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))

        val maxIndex = values.indexOf(hi)
        val minIndex = values.indexOf(lo)

        fun marker(index: Int, value: Int, above: Boolean) {
            val center = Offset(index * stepX, yOf(value))
            drawCircle(color, radius = 3.dp.toPx(), center = center)
            drawCircle(surface, radius = 1.2.dp.toPx(), center = center)
            val layout = measurer.measure(
                "${if (above) "最高" else "最低"} $value",
                labelStyle
            )
            val x = (center.x - layout.size.width / 2f)
                .coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
            val y = if (above) {
                (center.y - 8.dp.toPx() - layout.size.height).coerceAtLeast(0f)
            } else {
                (center.y + 8.dp.toPx()).coerceAtMost(size.height - layout.size.height)
            }
            drawBadgedLabel(
                measurer,
                "${if (above) "最高" else "最低"} $value",
                labelStyle,
                Offset(x, y),
                badgeColor
            )
        }

        marker(maxIndex, hi, above = true)
        marker(minIndex, lo, above = false)
    }
}


