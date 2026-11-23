package com.genkaim.zeppglass.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    // Each ring keeps its own animation so the duration follows the distance it
    // still has to travel: a small top-up is quick, a full lap takes LAP_MILLIS,
    // and the speed on screen stays the same either way. Values past 1 are not
    // clamped, so the extra lap can be drawn.
    val animated = values.map { remember { Animatable(0f) } }
    // How far the over-goal mark has travelled out of the start point: 0 = not
    // shown, 1 = at its final position.
    val markTravel = values.map { remember { Animatable(0f) } }
    LaunchedEffect(values) {
        values.forEachIndexed { index, value ->
            launch {
                val target = value.coerceIn(0f, 1f)
                val distance = abs(target - animated[index].value)
                val durationMs = (distance * LAP_MILLIS).toInt().coerceAtLeast(60)
                // The mark starts while the last part of the lap is still filling.
                launch {
                    if (value > 1f) {
                        delay((durationMs * 0.55f).toLong())
                        markTravel[index].animateTo(
                            targetValue = 1f,
                            animationSpec = tween(420, easing = FastOutSlowInEasing)
                        )
                    } else {
                        markTravel[index].snapTo(0f)
                    }
                }
                if (distance <= 0.001f) {
                    animated[index].snapTo(target)
                } else {
                    animated[index].animateTo(
                        targetValue = target,
                        animationSpec = tween(
                            durationMillis = durationMs,
                            easing = FastOutSlowInEasing
                        )
                    )
                }

            }
        }
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
                val progress = animated.getOrNull(index)?.value ?: 0f
                // Arc length the badge covers, as a fraction of the lap.
                val badgeFrac = ((stroke * 1.3f) / rect.perimeter).coerceAtMost(0.45f)
                // Very close to the goal the head would slide under the badge:
                // stop the arc just short of it, except on a completed lap.
                val firstLap = if (progress >= 1f) {
                    1f
                } else {
                    progress.coerceAtMost(1f - badgeFrac)
                }
                if (firstLap > 0f) {
                    drawPath(
                        rect.path(fraction = firstLap),
                        color = colors[index],
                        style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
                // Past the goal there is nothing left to draw on this track: it is
                // already full. The overshoot is marked at a position computed
                // before the animation runs, and the mark rides the arc head until
                // it reaches that position — so the animation ends where the mark
                // already is, with no last-moment jump and no second lap.
                val realValue = values.getOrElse(index) { 0f }
                val travel = markTravel.getOrNull(index)?.value ?: 0f
                if (realValue > 1f && travel > 0f) {
                    val over = (realValue - 1f).coerceAtMost(1f)
                    // A whole extra lap ends on the start point, where the badge
                    // is; wrap it to zero.
                    val wrapped = if (over >= 1f) 0f else over
                    // The badge disc (0.5 stroke) plus the mark's own outer radius
                    // (0.57 + 0.07) is what the mark has to clear; a little margin
                    // keeps the edge from touching.
                    val clearFrac = ((stroke * 1.3f) / rect.perimeter).coerceAtMost(0.45f)
                    val finalMark = when {
                        wrapped < clearFrac -> clearFrac
                        wrapped > 1f - clearFrac -> 1f - clearFrac
                        else -> wrapped
                    }
                    val markAt = finalMark * travel
                    val head = rect.pointAt(rect.perimeter * markAt)
                    val before = rect.pointAt(rect.perimeter * markAt - 0.5f)
                    val dir = Math.toDegrees(
                        kotlin.math.atan2(
                            (head.y - before.y).toDouble(),
                            (head.x - before.x).toDouble()
                        )
                    ).toFloat()
                    val r = stroke * 0.57f
                    drawArc(
                        color = badgeColor.copy(alpha = travel),
                        startAngle = dir - 90f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(head.x - r, head.y - r),
                        size = Size(r * 2f, r * 2f),
                        style = Stroke(stroke * 0.14f, cap = StrokeCap.Round)
                    )
                }

                // The logo is exactly as wide as the ring: a badge the width of
                // the stroke, sitting on the start point. Once the ring has gone
                // past one lap the badge's fill is dropped — the ring is there
                // anyway — and the glyph is knocked out of the colour instead.
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
