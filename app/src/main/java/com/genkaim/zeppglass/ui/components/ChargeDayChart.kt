package com.genkaim.zeppglass.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
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
import kotlin.math.roundToInt

/**
 * One day of Hybrid Charge on a fixed 00:00–24:00 axis.
 *
 * The line alone does not say why it moved, so the night's sleep window and the
 * day's workouts are drawn as bands: the rise lines up with 睡眠, the drop with
 * 运动. Each band is marked with a small icon instead of a text tag — a moon for
 * sleep, a running figure for a workout — and the figures (start, end, how much Charge
 * moved) are listed in the card under the chart, where there is room to read
 * them. The axis is labelled 0/8/16/24 so a flat morning is not mistaken for a
 * missing afternoon.
 */
enum class ChargeSpanKind { SLEEP, NAP, WORKOUT }

/**
 * One stretch of a day with a start, an end and how much Charge it moved.
 *
 * Both the chart band and the list under it read from this, so the icon on the
 * axis and the line in the card can never disagree.
 */
data class ChargeSpan(
    val kind: ChargeSpanKind,
    val label: String,
    val startEpochSec: Long,
    val endEpochSec: Long,
    /**
     * Charge gained (`+`) or spent (`−`) across the stretch, measured between
     * the readings that bracket it. Null when the band has no reading on one
     * side — a number is then not invented.
     */
    val delta: Int?
)

/**
 * Builds the day's sleep, nap and workout stretches, in time order.
 *
 * The delta is measured from the last reading at or before the start to the
 * first reading at or after the end: a short session often falls between two
 * samples, and nearest-to-both-ends is then the same reading, which measures 0.
 */
fun chargeSpans(
    samples: List<ChargeSample>,
    sleep: SleepNight?,
    workouts: List<Workout>
): List<ChargeSpan> {
    val points = samples.filter { it.curveValue != null }.sortedBy { it.epochMs }
    fun deltaOf(start: Long, end: Long): Int? {
        if (points.isEmpty()) return null
        val before = (points.lastOrNull { it.epochMs / 1000 <= start } ?: points.first())
            .curveValue ?: return null
        val after = (points.firstOrNull { it.epochMs / 1000 >= end } ?: points.last())
            .curveValue ?: return null
        return (after - before).roundToInt()
    }

    val spans = mutableListOf<ChargeSpan>()
    sleep?.let { night ->
        if (night.endEpochSec > night.startEpochSec) {
            spans += ChargeSpan(
                kind = ChargeSpanKind.SLEEP,
                label = "睡眠",
                startEpochSec = night.startEpochSec,
                endEpochSec = night.endEpochSec,
                delta = deltaOf(night.startEpochSec, night.endEpochSec)
            )
        }
        val napFrom = night.napStartEpochSec
        val napTo = night.napEndEpochSec
        if (napFrom != null && napTo != null && napTo > napFrom) {
            spans += ChargeSpan(
                kind = ChargeSpanKind.NAP,
                label = "小睡",
                startEpochSec = napFrom,
                endEpochSec = napTo,
                delta = deltaOf(napFrom, napTo)
            )
        }
    }
    workouts.forEach { workout ->
        if (workout.endEpochSec > workout.startEpochSec) {
            spans += ChargeSpan(
                kind = ChargeSpanKind.WORKOUT,
                label = workout.name,
                startEpochSec = workout.startEpochSec,
                endEpochSec = workout.endEpochSec,
                delta = deltaOf(workout.startEpochSec, workout.endEpochSec)
            )
        }
    }
    return spans.sortedBy { it.startEpochSec }
}

/**
 * Pulls the icon for a stretch. Sleep and naps share the moon, workouts a runner:
 * the colour already separates the two, the shape says which is which without
 * relying on colour alone.
 */
fun chargeSpanIcon(kind: ChargeSpanKind): ImageVector =
    if (kind == ChargeSpanKind.WORKOUT) Icons.Rounded.DirectionsRun else Icons.Rounded.Bedtime

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

    val sleepIcon = rememberVectorPainter(chargeSpanIcon(ChargeSpanKind.SLEEP))
    val workoutIcon = rememberVectorPainter(chargeSpanIcon(ChargeSpanKind.WORKOUT))

    val dayStart = date.atStartOfDay(zone).toEpochSecond()
    val dayEnd = dayStart + 86_400L
    val limitEpoch = (untilEpochSec ?: dayEnd).coerceIn(dayStart, dayEnd)
    val daySpan = 24f * 3600f
    // Use the unrounded component average: the integer total steps down one
    // point at a time and would be drawn as a staircase.
    val points = samples.filter { it.curveValue != null }.sortedBy { it.epochMs }
    val spans = chargeSpans(samples, sleep, workouts)

    Canvas(modifier) {
        // The icons sit above the plot, and moving them up must not move the
        // band, the curve or the axis: the caller hands over a canvas that is
        // ICON_LIFT taller and a card whose top padding shrank by the same
        // amount, so every other y here is shifted down by the lift and lands
        // exactly where it was on screen.
        val axisHeight = 16.dp.toPx()
        val topPad = ChargeDayChartIconLift.toPx() + 12.dp.toPx()
        val plotHeight = (size.height - axisHeight - topPad).coerceAtLeast(10f)
        fun xOf(epochSec: Float) = ((epochSec - dayStart) / daySpan) * size.width
        fun yOf(value: Float) = topPad + plotHeight - (value / 100f) * plotHeight

        // Nothing may be drawn past the last reading: a band that runs on after
        // the line ends reads as if it happened in the future ("运动" sticking
        // out to the right of 现在). Bands are clipped to that instant.
        val lastSampleSec = points.lastOrNull { it.epochMs / 1000 <= limitEpoch }
            ?.let { it.epochMs / 1000 }
        fun measuredEnd(end: Long) = lastSampleSec?.let { minOf(end, it) } ?: end

        // Grid
        for (i in 0..2) {
            val y = topPad + plotHeight * i / 2f
            drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f)
        }

        // Sleep, naps and workouts: one band each, full height, in one of two
        // colours. A moon marks the sleep stretches, a running figure the workouts;
        // the numbers that used to ride on the tag now live in the card under
        // the chart, where a short session has room to spell out its times.
        spans.forEach { span ->
            val from = maxOf(span.startEpochSec, dayStart)
            val to = measuredEnd(minOf(span.endEpochSec, dayStart + 86400L))
            if (to > from) {
                val x0 = xOf(from.toFloat())
                val x1 = xOf(to.toFloat())
                val isWorkout = span.kind == ChargeSpanKind.WORKOUT
                val tint = if (isWorkout) minusColor else plusColor
                val bandTop = ChargeDayChartIconLift.toPx() + 17.dp.toPx()
                drawRoundRect(
                    color = tint.copy(alpha = 0.12f),
                    topLeft = Offset(x0, bandTop),
                    size = Size(
                        (x1 - x0).coerceAtLeast(2f),
                        (size.height - axisHeight - bandTop).coerceAtLeast(2f)
                    ),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )
                // A very short session is only a few pixels wide; the icon is
                // kept whole and centred on it rather than shrunk to nothing.
                with(if (isWorkout) workoutIcon else sleepIcon) {
                    val side = 15.dp.toPx()
                    val left = ((x0 + x1) / 2f - side / 2f)
                        .coerceIn(0f, (size.width - side).coerceAtLeast(0f))
                    translate(left, 0f) {
                        draw(Size(side, side), colorFilter = ColorFilter.tint(tint))
                    }
                }
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
/**
 * Space the chart keeps above its plot for the sleep / workout icons. The
 * caller gives the canvas this much extra height and takes it off the card's
 * top padding, so widening it lifts the icons and leaves everything else in
 * the card where it was.
 */
val ChargeDayChartIconLift = 6.dp

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
