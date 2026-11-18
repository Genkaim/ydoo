package com.genkaim.zeppglass.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.genkaim.zeppglass.data.GeoPoint
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/**
 * Draws a GPS track as a fitted polyline.
 *
 * There is no map tile service behind this app, so the route is drawn on a
 * neutral panel: the shape, start and finish are real, the background is not a
 * map. Longitude is compressed by cos(latitude) so the track keeps its real
 * east/west proportions instead of looking stretched.
 */
@Composable
fun RoutePath(
    points: List<GeoPoint>,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
) {
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val startColor = MaterialTheme.colorScheme.tertiary
    val endColor = color
    val dotColor = MaterialTheme.colorScheme.surface
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(surface)
    ) {
        if (points.size < 2) return@Box
        Canvas(Modifier.fillMaxSize()) {
            val latMin = points.minOf { it.lat }
            val latMax = points.maxOf { it.lat }
            val lonMin = points.minOf { it.lon }
            val lonMax = points.maxOf { it.lon }
            val midLat = (latMin + latMax) / 2.0
            val lonScale = max(0.2, cos(Math.toRadians(midLat)))
            val spanX = max(1e-6, (lonMax - lonMin) * lonScale)
            val spanY = max(1e-6, latMax - latMin)
            val pad = 16.dp.toPx()
            val scale = min(
                (size.width - pad * 2) / spanX.toFloat(),
                (size.height - pad * 2) / spanY.toFloat()
            )
            val contentW = (spanX * scale).toFloat()
            val contentH = (spanY * scale).toFloat()
            val offsetX = (size.width - contentW) / 2f
            val offsetY = (size.height - contentH) / 2f

            fun project(point: GeoPoint): Offset {
                val x = offsetX + (((point.lon - lonMin) * lonScale) * scale).toFloat()
                // Latitude grows north, the canvas grows down.
                val y = offsetY + contentH - ((point.lat - latMin) * scale).toFloat()
                return Offset(x, y)
            }

            val path = Path()
            points.forEachIndexed { index, point ->
                val projected = project(point)
                if (index == 0) path.moveTo(projected.x, projected.y)
                else path.lineTo(projected.x, projected.y)
            }
            drawPath(
                path,
                color,
                style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            drawCircle(startColor, radius = 4.dp.toPx(), center = project(points.first()))
            drawCircle(endColor, radius = 4.dp.toPx(), center = project(points.last()))
            drawCircle(
                dotColor,
                radius = 1.8.dp.toPx(),
                center = project(points.last())
            )
        }
    }
}

/** Empty-state box for a workout without GPS fixes. */
@Composable
fun RoutePlaceholder(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
