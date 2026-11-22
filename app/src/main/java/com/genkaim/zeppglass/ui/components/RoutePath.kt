package com.genkaim.zeppglass.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.genkaim.zeppglass.data.GeoPoint
import com.genkaim.zeppglass.ui.theme.LocalZgPalette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sin

/**
 * The workout's GPS track, drawn on its own: no map tiles, no network, nothing
 * that needs a licence or a quota.
 *
 * The shape is fitted with the same equal-scale Web-Mercator projection the map
 * used, so a track fills its card exactly. The line is coloured by how fast the
 * watch was moving at that point, and a few arrowheads show which way the route
 * went — a trace on a plain panel has neither on its own.
 */
@Composable
fun RoutePath(
    points: List<GeoPoint>,
    modifier: Modifier = Modifier
) {
    val palette = LocalZgPalette.current
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val startColor = MaterialTheme.colorScheme.tertiary
    val endColor = MaterialTheme.colorScheme.primary
    val ramp = listOf(palette.sleepLight, palette.accent, palette.warn, palette.critical)

    Box(
        modifier
            // No panel behind the track: the path sits directly on the page.
            .clip(RoundedCornerShape(12.dp))
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val width = constraints.maxWidth.toFloat()
            val height = constraints.maxHeight.toFloat()
            if (points.size >= 2 && width > 0f && height > 0f && width != Int.MAX_VALUE.toFloat()) {
                val shape = remember(points, width, height) {
                    fitPath(points, width, height, padding = 24f)
                }
                Canvas(Modifier.fillMaxSize()) {
                    drawTrack(shape, ramp, startColor, endColor)
                }
                // 慢 → 快, so the colours are not a mystery.
                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("慢", style = MaterialTheme.typography.labelSmall, color = ink)
                    Box(
                        Modifier
                            .width(46.dp)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Brush.horizontalGradient(ramp))
                    )
                    Text("快", style = MaterialTheme.typography.labelSmall, color = ink)
                }
            }
        }
    }
}

/** Empty-state box for a workout without GPS fixes. */
@Composable
fun RoutePlaceholder(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * The projected track: one canvas point and one relative speed per fix, in the
 * same order as the input. Speed is 0–1, where 0 is the slowest tenth of the
 * session and 1 the fastest.
 */
private class TrackShape(
    val points: List<Offset>,
    val speed: List<Float>
)

private fun fitPath(
    track: List<GeoPoint>,
    width: Float,
    height: Float,
    padding: Float
): TrackShape {
    // Web-Mercator at zoom 0: one 256 px tile for the whole world. Only the
    // relative positions matter here, so any fixed zoom level does.
    val world = track.map { worldPx(it.lat, it.lon, 0) }
    val minX = world.minOf { it.x }
    val maxX = world.maxOf { it.x }
    val minY = world.minOf { it.y }
    val maxY = world.maxOf { it.y }
    val spanX = (maxX - minX).coerceAtLeast(1e-6f)
    val spanY = (maxY - minY).coerceAtLeast(1e-6f)
    // One scale for both axes: a track keeps its true proportions.
    val scale = min(
        (width - padding * 2).coerceAtLeast(1f) / spanX,
        (height - padding * 2).coerceAtLeast(1f) / spanY
    )
    val offsetX = (width - spanX * scale) / 2f
    val offsetY = (height - spanY * scale) / 2f
    val projected = world.map { point ->
        Offset(
            offsetX + (point.x - minX) * scale,
            offsetY + (point.y - minY) * scale
        )
    }

    // Speed from the distance between fixes. The payload's fixes are one per
    // second, so meters between them is meters per second; smoothing over five
    // seconds keeps the colour from flickering with GPS noise.
    val rawSpeed = ArrayList<Float>(track.size)
    for (i in track.indices) {
        val a = track[maxOf(i - 1, 0)]
        val b = track[minOf(i + 1, track.lastIndex)]
        rawSpeed.add(distanceMeters(a, b).toFloat())
    }
    val averaged = rawSpeed.indices.map { i ->
        val from = maxOf(0, i - 2)
        val to = minOf(rawSpeed.lastIndex, i + 2)
        rawSpeed.subList(from, to + 1).average().toFloat()
    }
    val sorted = averaged.sorted()
    val low = sorted[(sorted.size * 0.10).toInt().coerceIn(0, sorted.lastIndex)]
    val high = sorted[(sorted.size * 0.90).toInt().coerceIn(0, sorted.lastIndex)]
    val speed = averaged.map { value ->
        if (high - low < 1e-3f) 0.5f else ((value - low) / (high - low)).coerceIn(0f, 1f)
    }

    // Two Chaikin passes round every corner. They run on the projected points,
    // and each new point carries an interpolated speed, so the colours follow
    // the same curve the line does.
    val thinned = thin(projected, speed, minStep = 1.4f)
    var smoothPoints: List<Offset> = thinned.first
    var smoothSpeed: List<Float> = thinned.second
    repeat(2) {
        if (smoothPoints.size >= 3) {
            val nextPoints = ArrayList<Offset>(smoothPoints.size * 2)
            val nextSpeed = ArrayList<Float>(smoothPoints.size * 2)
            nextPoints.add(smoothPoints.first())
            nextSpeed.add(smoothSpeed.first())
            for (i in 0 until smoothPoints.size - 1) {
                val a = smoothPoints[i]
                val b = smoothPoints[i + 1]
                nextPoints.add(Offset(a.x * 0.75f + b.x * 0.25f, a.y * 0.75f + b.y * 0.25f))
                nextSpeed.add(smoothSpeed[i] * 0.75f + smoothSpeed[i + 1] * 0.25f)
                nextPoints.add(Offset(a.x * 0.25f + b.x * 0.75f, a.y * 0.25f + b.y * 0.75f))
                nextSpeed.add(smoothSpeed[i] * 0.25f + smoothSpeed[i + 1] * 0.75f)
            }
            nextPoints.add(smoothPoints.last())
            nextSpeed.add(smoothSpeed.last())
            smoothPoints = nextPoints
            smoothSpeed = nextSpeed
        }
    }
    return TrackShape(smoothPoints, smoothSpeed)
}

/**
 * Drops fixes closer than [minStep] pixels: they only add jitter, not shape.
 * The speed values are carried along, so both lists stay in step.
 */
private fun thin(
    points: List<Offset>,
    speed: List<Float>,
    minStep: Float
): Pair<List<Offset>, List<Float>> {
    if (points.size < 3) return points to speed
    val outPoints = ArrayList<Offset>(points.size)
    val outSpeed = ArrayList<Float>(points.size)
    outPoints.add(points.first())
    outSpeed.add(speed.first())
    var last = points.first()
    for (i in 1 until points.size) {
        val point = points[i]
        if (i == points.lastIndex || (point - last).getDistance() >= minStep) {
            outPoints.add(point)
            outSpeed.add(speed[i])
            last = point
        }
    }
    return outPoints to outSpeed
}

/** Great-circle distance in metres, near enough over a few hundred metres. */
private fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
    val dLat = (b.lat - a.lat) * 111_320.0
    val dLon = (b.lon - a.lon) * 111_320.0 * cos(Math.toRadians((a.lat + b.lat) / 2.0))
    return kotlin.math.sqrt(dLat * dLat + dLon * dLon)
}

private fun rampColor(ramp: List<Color>, t: Float): Color {
    if (ramp.isEmpty()) return Color.Gray
    if (ramp.size == 1) return ramp.first()
    val scaled = t.coerceIn(0f, 1f) * (ramp.size - 1)
    val index = scaled.toInt().coerceIn(0, ramp.size - 2)
    val local = scaled - index
    return androidx.compose.ui.graphics.lerp(ramp[index], ramp[index + 1], local)
}

private fun DrawScope.drawTrack(
    shape: TrackShape,
    ramp: List<Color>,
    startColor: Color,
    endColor: Color
) {
    if (shape.points.size < 2) return

    // A white halo under everything, so the colours stay readable in both
    // themes; it follows the same smoothed line as the colours.
    val outline = Path()
    outline.moveTo(shape.points.first().x, shape.points.first().y)
    for (i in 1 until shape.points.size) {
        outline.lineTo(shape.points[i].x, shape.points[i].y)
    }
    drawPath(
        outline,
        Color.White.copy(alpha = 0.85f),
        style = Stroke(5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // A line per pair of smoothed points, each in its own speed colour. Round
    // caps make the joints seamless.
    val stroke = 3.dp.toPx()
    for (i in 0 until shape.points.size - 1) {
        drawLine(
            color = rampColor(ramp, (shape.speed[i] + shape.speed[i + 1]) / 2f),
            start = shape.points[i],
            end = shape.points[i + 1],
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }

    drawArrows(shape.points)

    val start = shape.points.first()
    val end = shape.points.last()
    drawCircle(Color.White, radius = 6.dp.toPx(), center = start)
    drawCircle(startColor, radius = 4.dp.toPx(), center = start)
    drawCircle(Color.White, radius = 6.dp.toPx(), center = end)
    drawCircle(endColor, radius = 4.dp.toPx(), center = end)
}

/**
 * Arrowheads at a few points along the way, each turned to the direction of
 * travel there. They sit on the line in white with a hairline edge so they read
 * on top of every speed colour.
 */
private fun DrawScope.drawArrows(points: List<Offset>) {
    if (points.size < 8) return
    val fractions = listOf(0.18f, 0.42f, 0.66f, 0.88f)
    val size = 5.5.dp.toPx()
    for (fraction in fractions) {
        val index = (points.size * fraction).toInt().coerceIn(1, points.lastIndex - 1)
        val from = points[index - 1]
        val to = points[index + 1]
        val dx = to.x - from.x
        val dy = to.y - from.y
        val length = kotlin.math.sqrt(dx * dx + dy * dy)
        if (length < 0.5f) continue
        val ux = dx / length
        val uy = dy / length
        val tip = Offset(points[index].x + ux * size, points[index].y + uy * size)
        val backX = points[index].x - ux * size
        val backY = points[index].y - uy * size
        val arrow = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(backX - uy * size * 0.8f, backY + ux * size * 0.8f)
            lineTo(backX + uy * size * 0.8f, backY - ux * size * 0.8f)
            close()
        }
        drawPath(arrow, Color.White)
        drawPath(
            arrow,
            Color.Black.copy(alpha = 0.18f),
            style = Stroke(width = 1f, join = StrokeJoin.Round)
        )
    }
}

/** Web-Mercator pixel position of a coordinate at [zoom], in a 256 px tile grid. */
private fun worldPx(lat: Double, lon: Double, zoom: Int): Offset {
    val size = 256.0 * (1 shl zoom)
    val clampedLat = lat.coerceIn(-85.05112878, 85.05112878)
    val sinLat = sin(clampedLat * PI / 180.0)
    val x = (lon + 180.0) / 360.0 * size
    val y = (0.5 - ln((1 + sinLat) / (1 - sinLat)) / (4 * PI)) * size
    return Offset(x.toFloat(), y.toFloat())
}
