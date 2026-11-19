package com.genkaim.zeppglass.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.genkaim.zeppglass.data.ChargeSample
import com.genkaim.zeppglass.data.SleepNight
import com.genkaim.zeppglass.data.Workout
import com.genkaim.zeppglass.ui.theme.LocalZgPalette
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * One day of Hybrid Charge on a fixed 00:00–24:00 axis.
 *
 * The line alone does not say why it moved, so the night's sleep window and the
 * day's workouts are drawn as bands: the rise lines up with 睡眠, the drop with
 * 运动. The axis is labelled 0/8/16/24 so a flat morning is not mistaken for a
 * missing afternoon.
 */
@Composable
fun ChargeDayChart(
    date: LocalDate,
    samples: List<ChargeSample>,
    sleep: SleepNight?,
    workouts: List<Workout>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    /** Draw only up to this instant (today); null means the whole day. */
    untilEpochSec: Long? = null
) {
    val zone = ZoneId.systemDefault()
    val palette = LocalZgPalette.current
    val measurer = rememberTextMeasurer()
    val axisStyle = TextStyle(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val tagStyle = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val grid = palette.grid
    // Two band colors only: one for the time that added Charge (sleep, nap),
    // one for the time that spent it (workouts). They have to be told apart at
    // a glance — sleep reads blue, a workout reads warm. Both stay light so the
    // curve on top of them is still readable.
    val plusColor = palette.sleepLight
    val minusColor = palette.workout
    val dotColor = MaterialTheme.colorScheme.surface
    val valueStyle = TextStyle(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface)
    val badgeColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.60f)

    val dayStart = date.atStartOfDay(zone).toEpochSecond()
    val dayEnd = dayStart + 86_400L
    val limitEpoch = (untilEpochSec ?: dayEnd).coerceIn(dayStart, dayEnd)
    val daySpan = 24f * 3600f
    // Use the unrounded component average: the integer total steps down one
    // point at a time and would be drawn as a staircase.
    val points = samples.filter { it.curveValue != null }.sortedBy { it.epochMs }

    Canvas(modifier) {
        val axisHeight = 16.dp.toPx()
        val topPad = 12.dp.toPx()
        val plotHeight = (size.height - axisHeight - topPad).coerceAtLeast(10f)
        fun xOf(epochSec: Float) = ((epochSec - dayStart) / daySpan) * size.width
        fun yOf(value: Float) = topPad + plotHeight - (value / 100f) * plotHeight

        // Change across a sleep / workout band, e.g. "运动 -4": the bands say
        // when it happened, the number says how much Charge it took.
        fun valueNear(epochSec: Long): Float? = points
            .minByOrNull { kotlin.math.abs(it.epochMs / 1000 - epochSec) }
            ?.curveValue
            ?.toFloat()
        fun deltaText(start: Long, end: Long): String {
            val before = valueNear(start) ?: return ""
            val after = valueNear(end) ?: return ""
            val delta = (after - before).roundToInt()
            return when {
                delta > 0 -> " +$delta"
                delta < 0 -> " −${-delta}"
                else -> ""
            }
        }

        // Grid
        for (i in 0..2) {
            val y = topPad + plotHeight * i / 2f
            drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f)
        }

        // Sleep window (may start on the previous evening). The label sits above
        // the tinted block, and the tint stays light enough to read the curve.
        sleep?.let { night ->
            val from = maxOf(night.startEpochSec, dayStart.toLong())
            val to = minOf(night.endEpochSec, dayStart.toLong() + 86400L)
            if (to > from) {
                val x0 = xOf(from.toFloat())
                val x1 = xOf(to.toFloat())
                val bandTop = 14.dp.toPx()
                drawRoundRect(
                    color = plusColor.copy(alpha = 0.12f),
                    topLeft = Offset(x0, bandTop),
                    size = Size(
                        (x1 - x0).coerceAtLeast(2f),
                        (size.height - axisHeight - bandTop).coerceAtLeast(2f)
                    ),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )
                val text = "睡眠${deltaText(from, to)}"
                val layout = measurer.measure(text, tagStyle)
                drawLabel(
                    measurer,
                    text,
                    tagStyle,
                    ((x0 + x1) / 2f - layout.size.width / 2f)
                        .coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f)),
                    1.dp.toPx()
                )
            }
        }

        // Daytime nap: same band idea as the night, in its own slot on the axis.
        val napFrom = sleep?.napStartEpochSec
        val napTo = sleep?.napEndEpochSec
        if (napFrom != null && napTo != null && napTo > napFrom) {
            val from = maxOf(napFrom, dayStart)
            val to = minOf(napTo, dayStart + 86400L)
            if (to > from) {
                val x0 = xOf(from.toFloat())
                val x1 = xOf(to.toFloat())
                val bandTop = 14.dp.toPx()
                drawRoundRect(
                    color = plusColor.copy(alpha = 0.12f),
                    topLeft = Offset(x0, bandTop),
                    size = Size(
                        (x1 - x0).coerceAtLeast(2f),
                        (size.height - axisHeight - bandTop).coerceAtLeast(2f)
                    ),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )
                val text = "小睡${deltaText(from, to)}"
                val layout = measurer.measure(text, tagStyle)
                drawLabel(
                    measurer,
                    text,
                    tagStyle,
                    ((x0 + x1) / 2f - layout.size.width / 2f)
                        .coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f)),
                    1.dp.toPx()
                )
            }
        }

        // Workouts of the day: the same full-height band as sleep, in its own
        // colour, so the two kinds of time never read as the same block. The
        // tag goes at the bottom of the block because 睡眠 already owns the top.
        workouts.forEach { workout ->
            val from = maxOf(workout.startEpochSec, dayStart.toLong())
            val to = minOf(workout.endEpochSec, dayStart.toLong() + 86400L)
            if (to > from) {
                val x0 = xOf(from.toFloat())
                val x1 = xOf(to.toFloat())
                val bandTop = 14.dp.toPx()
                val bandHeight = (size.height - axisHeight - bandTop).coerceAtLeast(2f)
                drawRoundRect(
                    color = minusColor.copy(alpha = 0.12f),
                    topLeft = Offset(x0, bandTop),
                    size = Size((x1 - x0).coerceAtLeast(2f), bandHeight),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )
                val text = "运动${deltaText(from, to)}"
                val layout = measurer.measure(text, tagStyle)
                drawLabel(
                    measurer,
                    text,
                    tagStyle,
                    ((x0 + x1) / 2f - layout.size.width / 2f)
                        .coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f)),
                    (bandTop + bandHeight - layout.size.height - 2.dp.toPx()).coerceAtLeast(0f)
                )
            }
        }

        // Real readings only: each stretch of consecutive samples becomes one
        // line, and a gap in wearing the band stays a gap. The old version held
        // the ends flat to 00:00 / now, which drew data the watch never sent.
        if (points.isNotEmpty()) {
            val visible = points.filter { it.epochMs / 1000 <= limitEpoch }
            if (visible.isNotEmpty()) {
                fun offsetOf(sample: ChargeSample) = Offset(
                    xOf(Instant.ofEpochMilli(sample.epochMs).epochSecond.toFloat()),
                    yOf(sample.curveValue!!.toFloat())
                )
                var segment = mutableListOf<Offset>()
                fun flush() {
                    when (segment.size) {
                        0 -> Unit
                        1 -> drawCircle(color, 2.dp.toPx(), segment.first())
                        else -> drawPath(
                            smoothPath(segment),
                            color,
                            style = Stroke(2.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    segment = mutableListOf()
                }
                visible.forEachIndexed { index, sample ->
                    if (index > 0 && sample.epochMs - visible[index - 1].epochMs > 15 * 60_000L) {
                        flush()
                    }
                    segment.add(offsetOf(sample))
                }
                flush()

                val last = offsetOf(visible.last())
                drawCircle(color, radius = 2.6.dp.toPx(), center = last)
                drawCircle(dotColor, radius = 1.3.dp.toPx(), center = last)
                val lastTotal = visible.lastOrNull { it.total != null }?.total
                    ?: visible.last().curveValue?.roundToInt()
                val text = lastTotal?.toString() ?: "--"
                val layout = measurer.measure(text, valueStyle)
                drawBadgedLabel(
                    measurer,
                    text,
                    valueStyle,
                    Offset(
                        last.x.coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f)),
                        (last.y - 10.dp.toPx() - layout.size.height).coerceAtLeast(0f)
                    ),
                    badgeColor
                )
            }
        }

        // 0 / 8 / 16 / 24
        val labels = listOf("0时", "8时", "16时", "24时")
        labels.forEachIndexed { index, label ->
            val layout = measurer.measure(label, axisStyle)
            val x = (size.width * index / 3f - layout.size.width / 2f)
                .coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
            drawText(layout, topLeft = Offset(x, size.height - axisHeight / 2f - layout.size.height / 2f))
        }
    }
}

/**
 * Monotone cubic (Fritsch–Carlson) through the points, written as cubic Bezier
 * segments. A plain Catmull-Rom overshoots on steep falls and can even loop
 * back in x when readings are unevenly spaced, which made the descending part
 * look thicker than the rest; this interpolation keeps x monotone and never
 * overshoots, so the stroke stays one even width.
 */
private fun smoothPath(points: List<Offset>): Path {
    val pts = mutableListOf<Offset>()
    points.sortedBy { it.x }.forEach { point ->
        if (pts.isEmpty() || point.x > pts.last().x) pts.add(point)
    }
    val path = Path()
    if (pts.isEmpty()) return path
    path.moveTo(pts[0].x, pts[0].y)
    if (pts.size < 3) {
        for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
        return path
    }

    val n = pts.size
    val dx = DoubleArray(n - 1) { (pts[it + 1].x - pts[it].x).toDouble() }
    val slopes = DoubleArray(n - 1) { (pts[it + 1].y - pts[it].y).toDouble() / dx[it] }
    val tangents = DoubleArray(n)
    tangents[0] = slopes[0]
    tangents[n - 1] = slopes[n - 2]
    for (i in 1 until n - 1) {
        tangents[i] = if (slopes[i - 1] * slopes[i] <= 0.0) 0.0
        else (slopes[i - 1] + slopes[i]) / 2.0
    }
    for (i in 0 until n - 1) {
        if (slopes[i] == 0.0) {
            tangents[i] = 0.0
            tangents[i + 1] = 0.0
            continue
        }
        val a = tangents[i] / slopes[i]
        val b = tangents[i + 1] / slopes[i]
        val h = a * a + b * b
        if (h > 9.0) {
            val t = 3.0 / kotlin.math.sqrt(h)
            tangents[i] = t * a * slopes[i]
            tangents[i + 1] = t * b * slopes[i]
        }
    }

    for (i in 0 until n - 1) {
        val h = dx[i]
        val c1x = pts[i].x + h / 3.0
        val c1y = pts[i].y + tangents[i] * h / 3.0
        val c2x = pts[i + 1].x - h / 3.0
        val c2y = pts[i + 1].y - tangents[i + 1] * h / 3.0
        path.cubicTo(
            c1x.toFloat(), c1y.toFloat(),
            c2x.toFloat(), c2y.toFloat(),
            pts[i + 1].x, pts[i + 1].y
        )
    }
    return path
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLabel(
    measurer: androidx.compose.ui.text.TextMeasurer,
    text: String,
    style: TextStyle,
    x: Float,
    y: Float
) {
    val layout = measurer.measure(text, style)
    drawText(
        layout,
        topLeft = Offset(
            x.coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f)),
            y
        )
    )
}



