package com.genkaim.zeppglass.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/** Milliseconds one full lap takes, with every ring using the same speed. */
private const val LAP_MILLIS = 700f

/**
 * Activity rings drawn as a running track: a stadium (two straights and two
 * semicircular ends) instead of a circle, tilted a little so it reads like the
 * track in the Zepp app. Each progress arc starts at the top centre and runs
 * clockwise, and [icons] puts the activity's own logo on that start point.
 */
@Composable
fun TrackRings(
    values: List<Float>,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    icons: List<ImageVector> = emptyList(),
    strokeWidth: Dp = 17.dp,
    gap: Dp = 6.dp,
    rotateDeg: Float = -18f
) {
    val painters: List<Painter?> = icons.map { rememberVectorPainter(it) }
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    // Not clamped to 1: a value past the goal keeps counting so the ring can show
    // the extra lap on top of the first one.
    // Constant speed: a full lap always takes the same time, so a small value
    // fills quickly and a long one is not rushed through in the same 700 ms.
    val animatedValues = values.mapIndexed { index, value ->
        val target = if (appeared) value.coerceAtLeast(0f) else 0f
        animateFloatAsState(
            targetValue = target,
            animationSpec = tween(
                durationMillis = (target * LAP_MILLIS).toInt().coerceAtLeast(120),
                easing = LinearEasing
            ),
            label = "ring$index"
        ).value
    }
    val badgeColor = MaterialTheme.colorScheme.background
    Canvas(modifier) {
        val stroke = strokeWidth.toPx()
        val gapPx = gap.toPx()
        val baseW = size.width * 0.86f
        val baseH = size.height * 0.60f

        withTransform({ rotate(rotateDeg, pivot = center) }) {
            values.forEachIndexed { index, _ ->
                val inset = stroke / 2 + index * (stroke + gapPx)
                val w = baseW - inset * 2
                val h = baseH - inset * 2
                if (w <= stroke + 4f || h <= stroke + 4f) return@forEachIndexed

                val rect = TrackRect(
                    left = center.x - w / 2f,
                    top = center.y - h / 2f,
                    width = w,
                    height = h
                )
                val full = rect.path(fraction = 1f)
                drawPath(
                    full,
                    color = colors[index].copy(alpha = 0.18f),
                    style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                val progress = animatedValues.getOrElse(index) { 0f }.coerceAtLeast(0f)
                val firstLap = progress.coerceAtMost(1f)
                if (firstLap > 0f) {
                    drawPath(
                        rect.path(fraction = firstLap),
                        color = colors[index],
                        style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
                // Past the goal, the overflow is a second lap drawn just outside
                // the first one: a background-coloured halo separates the two so
                // they do not merge into one fat ring. Drawn after the first lap
                // (so it sits above it) and before the badge (so the logo stays
                // on top).
                if (progress > 1f) {
                    // Same ring, same width: the extra lap runs on top of the
                    // first one, so the only visible mark is a hairline notch at
                    // the head that says "one more lap".
                    val over = (progress - 1f).coerceAtMost(1f)
                    drawPath(
                        rect.path(fraction = over),
                        color = colors[index],
                        style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    val head = rect.pointAt(rect.perimeter * over)
                    val before = rect.pointAt(rect.perimeter * over - 0.5f)
                    val dir = Math.toDegrees(
                        kotlin.math.atan2(
                            (head.y - before.y).toDouble(),
                            (head.x - before.x).toDouble()
                        )
                    ).toFloat()
                    // Only the half that faces forward: it traces the arc's head
                    // instead of drawing a full little circle.
                    val r = stroke * 0.40f
                    drawArc(
                        color = badgeColor,
                        startAngle = dir - 90f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(head.x - r, head.y - r),
                        size = Size(r * 2f, r * 2f),
                        style = Stroke(stroke * 0.14f, cap = StrokeCap.Round)
                    )
                }

                // The logo is exactly as wide as the ring: a badge the width of
                // the stroke, sitting on the start point.
                painters.getOrNull(index)?.let { painter ->
                    val start = rect.pointAt(0f)
                    val badge = stroke / 2f
                    drawCircle(colors[index], radius = badge, center = start)
                    val glyph = stroke * 0.90f
                    translate(left = start.x - glyph / 2f, top = start.y - glyph / 2f) {
                        with(painter) {
                            draw(
                                size = Size(glyph, glyph),
                                colorFilter = ColorFilter.tint(badgeColor)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A stadium outline and the part of it that a progress fraction covers. */
private class TrackRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
) {
    private val radius = height / 2f
    private val straight = (width - height).coerceAtLeast(0f)
    val perimeter = 2f * straight + 2f * PI.toFloat() * radius

    fun path(fraction: Float): Path {
        val path = Path()
        val steps = ceil(240 * fraction.coerceIn(0f, 1f)).toInt().coerceAtLeast(2)
        for (i in 0..steps) {
            val s = perimeter * fraction.coerceIn(0f, 1f) * i / steps
            val point = pointAt(s)
            if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        return path
    }

    /** Point at [s] along the track, starting at the top centre. */
    fun pointAt(s: Float): Offset {
        val cx = left + width / 2f
        val cy = top + radius
        val halfStraight = straight / 2f
        val capLength = PI.toFloat() * radius
        var d = ((s % perimeter) + perimeter) % perimeter

        // Top straight, centre → right.
        if (d <= halfStraight) return Offset(cx + d, top)
        d -= halfStraight

        // Right cap, -90° → +90°.
        if (d <= capLength) {
            val angle = -PI.toFloat() / 2f + (d / capLength) * PI.toFloat()
            return Offset(
                left + width - radius + radius * cos(angle),
                cy + radius * sin(angle)
            )
        }
        d -= capLength

        // Bottom straight, right → left.
        if (d <= straight) {
            return Offset(left + width - radius - d, top + height)
        }
        d -= straight

        // Left cap, +90° → +270°.
        if (d <= capLength) {
            val angle = PI.toFloat() / 2f + (d / capLength) * PI.toFloat()
            return Offset(
                left + radius + radius * cos(angle),
                cy + radius * sin(angle)
            )
        }
        d -= capLength

        // Top straight, left → centre.
        return Offset(left + radius + d, top)
    }
}
