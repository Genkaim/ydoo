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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.genkaim.zeppglass.ui.theme.LocalZgPalette

/** A coloured stretch of a scale, expressed in the scale's own units. */
data class ScaleBand(val from: Double, val to: Double, val color: Color)

/**
 * Baseline gauge under a reading, drawn the way the Zepp app draws it: the
 * middle of the scale is the personal baseline, the right end is higher, the
 * left end lower, and a small triangle marks today.
 *
 * Two shapes, because the two kinds of numbers behave differently:
 * - [fillColor] set: one solid fill from the low end to today's value, with
 *   低 / 基线 / 高 printed under the bar (resting heart rate, HRV).
 * - [bands] set: the track is split into separate stretches by small gaps and
 *   [tickLabels] print the thresholds (skin temperature: ±0.5℃, ±1℃).
 */
@Composable
fun BaselineScaleBar(
    value: Double,
    baseline: Double,
    min: Double,
    max: Double,
    modifier: Modifier = Modifier,
    fillColor: Color? = null,
    bands: List<ScaleBand> = emptyList(),
    tickLabels: List<Pair<Double, String>> = emptyList(),
    leftLabel: String? = null,
    centerLabel: String? = null,
    rightLabel: String? = null,
    markerColor: Color? = null,
    /** When set, band thresholds are thick bars in this colour instead of gaps. */
    bandDivider: Color? = null
) {
    if (max <= min) return
    val palette = LocalZgPalette.current
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        fontSize = 10.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val centerStyle = TextStyle(
        fontSize = 10.sp,
        color = MaterialTheme.colorScheme.onSurface
    )
    val trackColor = palette.grid
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f)
    val markerPaint = markerColor ?: fillColor ?: MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface

    Canvas(modifier) {
        // Generous marker: the arrow overlaps the bar and carries a thin
        // surface-coloured ring so it stays separate from the fill under it.
        val halo = 2.dp.toPx()
        val edge = 1.5.dp.toPx()
        val markerHeight = 10.dp.toPx()
        val markerWidth = 12.dp.toPx()
        val barHeight = 9.dp.toPx()
        val baseY = halo + edge
        val apexY = baseY + markerHeight
        val barTop = apexY - 3.dp.toPx()
        val barBottom = barTop + barHeight
        // Every stretch is a pill: 50% of the track height, so the two ends of a
        // filled bar and each skin-temperature segment share the same rounding.
        val corner = barHeight / 2f
        fun xOf(v: Double): Float {
            val t = ((v - min) / (max - min)).coerceIn(0.0, 1.0)
            return (size.width * t).toFloat()
        }

        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, barTop),
            size = Size(size.width, barHeight),
            cornerRadius = CornerRadius(corner)
        )

        if (fillColor != null) {
            // Solid reading: filled from the low end up to today's value.
            val end = xOf(value).coerceAtLeast(corner * 2f)
            drawRoundRect(
                color = fillColor,
                topLeft = Offset(0f, barTop),
                size = Size(end, barHeight),
                cornerRadius = CornerRadius(corner)
            )
        }

        if (bands.isNotEmpty()) {
            // Zone scale. With a divider colour the stretches sit flush and each
            // threshold is a thick white bar, matching the HRV gauge; without it
            // a 3dp gap separates them instead.
            val gap = if (bandDivider == null && bands.size > 1) 1.5.dp.toPx() else 0f
            // With a divider the coloured stretches stop half a bar short of the
            // cut and the white bar fills that gap, so no colour can peek out
            // around its edges.
            val halfDivider = if (bandDivider != null) 1.dp.toPx() else 0f
            bands.forEachIndexed { index, band ->
                var x1 = xOf(band.from)
                var x2 = xOf(band.to)
                if (index > 0) x1 += gap
                if (index < bands.lastIndex) x2 -= gap
                if (bandDivider != null) {
                    if (index > 0) x1 += halfDivider
                    if (index < bands.lastIndex) x2 -= halfDivider
                }
                if (x2 > x1) {
                    if (bandDivider != null) {
                        // Flush zones: only the two outer ends of the whole bar
                        // are rounded, every inner cut is square.
                        val rect = androidx.compose.ui.geometry.RoundRect(
                            left = x1,
                            top = barTop,
                            right = x2,
                            bottom = barBottom,
                            topLeftCornerRadius = if (index == 0) CornerRadius(corner) else CornerRadius.Zero,
                            bottomLeftCornerRadius = if (index == 0) CornerRadius(corner) else CornerRadius.Zero,
                            topRightCornerRadius = if (index == bands.lastIndex) CornerRadius(corner) else CornerRadius.Zero,
                            bottomRightCornerRadius = if (index == bands.lastIndex) CornerRadius(corner) else CornerRadius.Zero
                        )
                        drawPath(Path().apply { addRoundRect(rect) }, band.color)
                    } else {
                        // Gapped zones stay individual pills.
                        drawRoundRect(
                            color = band.color,
                            topLeft = Offset(x1, barTop),
                            size = Size(x2 - x1, barHeight),
                            cornerRadius = CornerRadius(corner)
                        )
                    }
                }
            }
            if (bandDivider != null) {
                bands.drop(1).forEach { band ->
                    val x = xOf(band.from)
                    drawRect(
                        color = bandDivider,
                        topLeft = Offset(x - halfDivider, barTop),
                        size = Size(halfDivider * 2f, barHeight)
                    )
                }
            }
            tickLabels.forEach { (tick, _) ->
                val x = xOf(tick)
                drawLine(
                    tickColor,
                    Offset(x, barBottom + 1.dp.toPx()),
                    Offset(x, barBottom + 3.dp.toPx()),
                    1.dp.toPx()
                )
            }
        } else {
            // Baseline mark: a white notch cut into the fill, the point the whole
            // scale is read against.
            val centerX = xOf(baseline)
            drawLine(
                surface,
                Offset(centerX, barTop),
                Offset(centerX, barBottom),
                2.dp.toPx()
            )
        }

        // Today's value: a rounded arrow pointing down, its tip overlapping the
        // bar. Drawn as a white ring first, then the fill plus a round-joined
        // stroke of the same colour, which rounds the triangle's corners.
        val valueX = xOf(value)
        val marker = Path().apply {
            moveTo(valueX - markerWidth / 2f, baseY)
            lineTo(valueX + markerWidth / 2f, baseY)
            lineTo(valueX, apexY)
            close()
        }
        val coloredEdge = edge * 2f
        drawPath(
            marker,
            color = surface,
            style = Stroke(
                width = coloredEdge + halo * 2f,
                join = StrokeJoin.Round,
                cap = StrokeCap.Round
            )
        )
        drawPath(marker, color = markerPaint)
        drawPath(
            marker,
            color = markerPaint,
            style = Stroke(width = coloredEdge, join = StrokeJoin.Round, cap = StrokeCap.Round)
        )

        // Labels under the track.
        val labelTop = barBottom + 5.dp.toPx()
        fun drawCentered(text: String, x: Float, style: TextStyle = labelStyle) {
            val layout = measurer.measure(text, style)
            val left = (x - layout.size.width / 2f)
                .coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
            drawText(layout, topLeft = Offset(left, labelTop))
        }
        leftLabel?.let { drawText(measurer.measure(it, labelStyle), topLeft = Offset(0f, labelTop)) }
        rightLabel?.let {
            val layout = measurer.measure(it, labelStyle)
            drawText(layout, topLeft = Offset(size.width - layout.size.width, labelTop))
        }
        if (bands.isNotEmpty()) {
            tickLabels.forEach { (tick, text) -> drawCentered(text, xOf(tick)) }
        } else if (centerLabel != null) {
            drawCentered(centerLabel, xOf(baseline), centerStyle)
        }
    }
}
