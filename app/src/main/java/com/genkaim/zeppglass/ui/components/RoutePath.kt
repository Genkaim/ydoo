package com.genkaim.zeppglass.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.genkaim.zeppglass.data.GeoPoint
import com.genkaim.zeppglass.ui.theme.LocalZgPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sin

/**
 * How long the workout page takes to fade in — the track and the cards use the
 * same value, so they arrive as one movement instead of two fades of different
 * lengths. Short enough to still feel immediate.
 */
const val WORKOUT_FADE_MS = 280

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
    val chevronSize = with(LocalDensity.current) { 3.dp.toPx() }

    Box(
        modifier
            // No panel behind the track: the path sits directly on the page.
            .clip(RoundedCornerShape(12.dp))
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val width = constraints.maxWidth.toFloat()
            val height = constraints.maxHeight.toFloat()
            // Room for the finish flag, which stands above its point.
            val padding = with(LocalDensity.current) { 20.dp.toPx() }
            if (points.size >= 2 && width > 0f && height > 0f && width != Int.MAX_VALUE.toFloat()) {
                // Projecting, smoothing and packing the paths is real work on a
                // long track; doing it while composing the page is what made
                // some workouts freeze for a moment before the route showed.
                // It runs on a worker and the track appears a frame later.
                val shape by produceState<TrackShape?>(
                    initialValue = null,
                    points, width, height, padding, chevronSize
                ) {
                    value = withContext(Dispatchers.Default) {
                        fitPath(points, width, height, padding, chevronSize)
                    }
                }
                val ready = shape
                // The worker needs a moment before the first frame can draw the
                // track; fading it in keeps that arrival from looking like a pop.
                val reveal = remember { Animatable(0f) }
                LaunchedEffect(ready) {
                    if (ready != null) reveal.animateTo(1f, tween(WORKOUT_FADE_MS))
                }
                if (ready != null) {
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = reveal.value }
                    ) {
                        drawTrack(ready, ramp, startColor, endColor)
                    }
                }
                // 慢 → 快, so the colours are not a mystery.
                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp, bottom = 10.dp)
                        .graphicsLayer { alpha = reveal.value },
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
 *
 * The coloured paths and the direction chevrons are built here, once, off the
 * main thread. Drawing used to rebuild them on every frame, which is why a long
 * track also stuttered while its page scrolled.
 */
private class TrackShape(
    val points: List<Offset>,
    val speed: List<Float>,
    /** One path per speed bin, with the 0–1 position its colour comes from. */
    val bins: List<TrackBin>,
    val chevrons: List<Path>
)

private class TrackBin(val path: Path, val t: Float)

private fun fitPath(
    track: List<GeoPoint>,
    width: Float,
    height: Float,
    padding: Float,
    chevronSize: Float
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

    // A long ride carries thousands of one-second fixes. The card is a few
    // hundred dp tall, so anything past ~700 points is detail nobody can see,
    // and every extra point costs smoothing passes, paths and memory. The step
    // grows with the track's own length, so short runs keep their shape.
    var length = 0f
    for (i in 1 until projected.size) {
        length += (projected[i] - projected[i - 1]).getDistance()
    }
    val thinStep = maxOf(2.5f, length / 700f)
    val thinned = thin(projected, speed, minStep = thinStep)
    // A wide pass first: a wobble from bad GPS is usually a few fixes long, not
    // one, so it has to be averaged over five points before the narrow passes
    // can flatten what is left. Then two tight passes away from the edges.
    var smoothPoints: List<Offset> = wideLowPass(thinned.first)
    var smoothSpeed: List<Float> = wideLowPassFloat(thinned.second, fallback = 0.5f)
    smoothPoints = lowPass(smoothPoints)
    smoothSpeed = lowPassFloat(smoothSpeed, fallback = 0.5f)
    smoothPoints = lowPass(smoothPoints)
    smoothSpeed = lowPassFloat(smoothSpeed, fallback = 0.5f)
    // Chaikin cuts every corner, but each pass doubles the point count. Two
    // passes already read as a flow at this size; the third is only worth its
    // cost when the thinned track is still short.
    repeat(if (smoothPoints.size > 420) 2 else 3) {
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
    return TrackShape(
        points = smoothPoints,
        speed = smoothSpeed,
        bins = buildBins(smoothPoints, smoothSpeed),
        chevrons = buildChevrons(smoothPoints, chevronSize)
    )
}

/**
 * Eight colour bins instead of one path per segment: one `drawPath` per bin is
 * what keeps scrolling smooth. Each bin keeps its own last segment index —
 * without it, a bin that is used again later would draw a straight chord back to
 * wherever it stopped, which is what put a dozen crossing lines over the track.
 */
private fun buildBins(points: List<Offset>, speed: List<Float>): List<TrackBin> {
    if (points.size < 2) return emptyList()
    val bins = 8
    val paths = Array(bins) { Path() }
    val lastIndex = IntArray(bins) { -2 }
    for (i in 0 until points.size - 1) {
        val value = (speed[i] + speed[i + 1]) / 2f
        val bin = (value * bins).toInt().coerceIn(0, bins - 1)
        if (lastIndex[bin] != i - 1) {
            paths[bin].moveTo(points[i].x, points[i].y)
        }
        paths[bin].lineTo(points[i + 1].x, points[i + 1].y)
        lastIndex[bin] = i
    }
    val out = ArrayList<TrackBin>(bins)
    paths.forEachIndexed { bin, path ->
        if (lastIndex[bin] >= 0) out.add(TrackBin(path, (bin + 0.5f) / bins))
    }
    return out
}

/**
 * One [¼ ½ ¼] pass over the points: each interior point moves to the average of
 * itself and its neighbours, which is what removes the last of the GPS wobble.
 * The two ends stay where they are — the start and the finish must not drift.
 */
private fun lowPass(points: List<Offset>): List<Offset> {
    if (points.size < 3) return points
    val out = ArrayList<Offset>(points.size)
    out.add(points.first())
    for (i in 1 until points.lastIndex) {
        val a = points[i - 1]
        val b = points[i]
        val c = points[i + 1]
        out.add(
            Offset(
                a.x * 0.25f + b.x * 0.5f + c.x * 0.25f,
                a.y * 0.25f + b.y * 0.5f + c.y * 0.25f
            )
        )
    }
    out.add(points.last())
    return out
}

/** The same pass for the speed values that ride along with the points. */
private fun lowPassFloat(values: List<Float>, fallback: Float): List<Float> {
    if (values.size < 3) return values
    val out = ArrayList<Float>(values.size)
    out.add(values.first())
    for (i in 1 until values.lastIndex) {
        out.add(values[i - 1] * 0.25f + values[i] * 0.5f + values[i + 1] * 0.25f)
    }
    out.add(values.last())
    return if (out.isEmpty()) List(values.size) { fallback } else out
}

/**
 * A five-tap pass, [1 2 3 2 1] / 9: the same averaging as [lowPass] but over two
 * fixes on each side. That is what flattens a wobble the GPS produced over a
 * couple of seconds — the tight pass alone still lets it through as a soft bend.
 * The first two and last two points stay put, so neither end drifts.
 */
private fun wideLowPass(points: List<Offset>): List<Offset> {
    if (points.size < 5) return points
    val out = ArrayList<Offset>(points.size)
    out.add(points[0])
    out.add(points[1])
    for (i in 2 until points.size - 2) {
        val a = points[i - 2]
        val b = points[i - 1]
        val c = points[i]
        val d = points[i + 1]
        val e = points[i + 2]
        out.add(
            Offset(
                (a.x + b.x * 2f + c.x * 3f + d.x * 2f + e.x) / 9f,
                (a.y + b.y * 2f + c.y * 3f + d.y * 2f + e.y) / 9f
            )
        )
    }
    out.add(points[points.size - 2])
    out.add(points.last())
    return out
}

/** The same five-tap pass for the speed values. */
private fun wideLowPassFloat(values: List<Float>, fallback: Float): List<Float> {
    if (values.size < 5) return values
    val out = ArrayList<Float>(values.size)
    out.add(values[0])
    out.add(values[1])
    for (i in 2 until values.size - 2) {
        out.add(
            (values[i - 2] + values[i - 1] * 2f + values[i] * 3f +
                values[i + 1] * 2f + values[i + 2]) / 9f
        )
    }
    out.add(values[values.size - 2])
    out.add(values.last())
    return if (out.isEmpty()) List(values.size) { fallback } else out
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

    // The bins were built once, off the main thread; drawing them is a handful
    // of `drawPath` calls per frame instead of thousands of segments.
    val stroke = Stroke(4.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    shape.bins.forEach { bin ->
        drawPath(bin.path, rampColor(ramp, bin.t), style = stroke)
    }
    val arrowStroke = Stroke(1.1.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    shape.chevrons.forEach { chevron ->
        drawPath(chevron, Color.White, style = arrowStroke)
    }

    val start = shape.points.first()
    drawCircle(Color.White, radius = 4.4.dp.toPx(), center = start)
    drawCircle(startColor, radius = 3.dp.toPx(), center = start)
    // The finish is a checkered flag inside a disc, planted on the last fix.
    drawFinishBadge(shape.points.last(), endColor)
}

/**
 * The end point: a filled disc with a little checkered finish flag in it. At
 * this size the flag is a pole and a 3×2 checkerboard; the empty squares are the
 * disc colour, which is what makes it read as a flag rather than a solid block.
 */
private fun DrawScope.drawFinishBadge(at: Offset, color: Color) {
    // Just a little wider than the line itself (4.6 dp), not a big marker.
    val radius = 4.4.dp.toPx()
    drawCircle(Color.White, radius = radius + 0.8.dp.toPx(), center = at)
    drawCircle(color, radius = radius, center = at)

    val poleHeight = 5.2.dp.toPx()
    val poleX = at.x - 1.8.dp.toPx()
    val top = at.y - poleHeight / 2f
    drawLine(
        Color.White,
        start = Offset(poleX, top),
        end = Offset(poleX, top + poleHeight),
        strokeWidth = 1.dp.toPx(),
        cap = StrokeCap.Round
    )

    val square = 1.4.dp.toPx()
    val flagLeft = poleX + 0.6.dp.toPx()
    for (row in 0 until 2) {
        for (col in 0 until 3) {
            if ((row + col) % 2 != 0) continue
            drawRect(
                color = Color.White,
                topLeft = Offset(
                    flagLeft + col * square,
                    top + row * square
                ),
                size = androidx.compose.ui.geometry.Size(square, square)
            )
        }
    }
}

/**
 * Direction chevrons — a small rounded ">" every so often, turned to the
 * direction of travel. White and no wider than the line's own thickness, so it
 * reads as part of the track rather than a marker sitting on top of it.
 *
 * Built with the rest of the shape (the size arrives in pixels) so the draw pass
 * only has to stroke a few ready paths.
 */
private fun buildChevrons(points: List<Offset>, size: Float): List<Path> {
    if (points.size < 12) return emptyList()
    val fractions = listOf(0.18f, 0.42f, 0.66f, 0.88f)
    val out = ArrayList<Path>(fractions.size)
    for (fraction in fractions) {
        val index = (points.size * fraction).toInt().coerceIn(6, points.lastIndex - 6)
        // Tangent over a short window, not the single neighbouring pair: on a
        // smoothed curve two adjacent points are almost the same spot, and the
        // arrow would wobble instead of following the line's direction.
        val from = points[index - 6]
        val to = points[index + 6]
        val dx = to.x - from.x
        val dy = to.y - from.y
        val length = kotlin.math.sqrt(dx * dx + dy * dy)
        if (length < 0.5f) continue
        val ux = dx / length
        val uy = dy / length
        val nx = -uy
        val ny = ux
        val center = points[index]
        // Tip forward, two wings back and to either side: ">" along the line.
        val tip = Offset(center.x + ux * size * 0.45f, center.y + uy * size * 0.45f)
        val backX = center.x - ux * size * 0.45f
        val backY = center.y - uy * size * 0.45f
        // Wings stay inside the line's thickness with a hair of margin, so the
        // chevron never touches the curve's edge.
        val wing = size * 0.42f
        out.add(
            Path().apply {
                moveTo(backX + nx * wing, backY + ny * wing)
                lineTo(tip.x, tip.y)
                lineTo(backX - nx * wing, backY - ny * wing)
            }
        )
    }
    return out
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
