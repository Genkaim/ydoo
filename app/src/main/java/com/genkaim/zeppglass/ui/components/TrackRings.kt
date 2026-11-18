package com.genkaim.zeppglass.ui.components

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
    val animatedValues = values.mapIndexed { index, value ->
        animateFloatAsState(
            targetValue = if (appeared) value.coerceIn(0f, 1f) else 0f,
            animationSpec = tween(700),
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
                val progress = animatedValues.getOrElse(index) { 0f }.coerceIn(0f, 1f)
                if (progress > 0f) {
                    drawPath(
                        rect.path(fraction = progress),
                        color = colors[index],
                        style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
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
    private val perimeter = 2f * straight + 2f * PI.toFloat() * radius

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
