package com.genkaim.zeppglass.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.genkaim.zeppglass.data.PaiDay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Zepp's PAI accent: warm amber against the page's greys. */
val PaiAmber = Color(0xFFF0B43C)
private val PaiPale = Color(0xFFF8E7BC)
private val PaiTrack = Color(0x14000000)

private fun DrawScope.octagonPath(center: Offset, radius: Float): Path {
    val path = Path()
    val points = (0 until 8).map { index ->
        // Start at 22.5° so the octagon has flat top and bottom edges.
        val angle = Math.toRadians(22.5 + index * 45.0)
        Offset(
            center.x + (radius * kotlin.math.cos(angle)).toFloat(),
            center.y + (radius * kotlin.math.sin(angle)).toFloat()
        )
    }
    points.forEachIndexed { index, point ->
        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    path.close()
    return path
}

/** The big eight-sided PAI badge with the running total in the middle. */
@Composable
fun PaiOctagon(value: Int, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outer = min(size.width, size.height) / 2f - 4.dp.toPx()
            drawPath(
                octagonPath(center, outer),
                PaiPale,
                style = Stroke(2.dp.toPx())
            )
            drawPath(
                octagonPath(center, outer * 0.86f),
                PaiPale,
                style = Stroke(1.5.dp.toPx())
            )
            drawPath(octagonPath(center, outer * 0.72f), PaiAmber)
        }
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value.toString(),
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = Color.White
            )
            Text(
                "PAI",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.92f)
            )
        }
    }
}

/**
 * Seven days of daily PAI as thin capsule bars with a 0–100 scale. Missing
 * days keep an empty track; today is labelled and marked with a triangle.
 */
@Composable
fun PaiWeekBars(
    days: List<PaiDay?>,
    modifier: Modifier = Modifier
) {
    if (days.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val axisStyle = TextStyle(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val todayStyle = TextStyle(
        fontSize = 10.sp,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold
    )
    val grid = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val todayInk = MaterialTheme.colorScheme.onSurface
    // On a dark page the standard amber reads as a muddy brown, so the bars get
    // a lighter tint there; the light theme keeps the official colour.
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val barColor = if (dark) Color(0xFFFFD37A) else PaiAmber
    val trackColor = if (dark) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else PaiTrack
    val today = remember { LocalDate.now() }
    val formatter = remember { DateTimeFormatter.ofPattern("M/d") }

    Canvas(modifier) {
        val topPad = 8.dp.toPx()
        val axisHeight = 22.dp.toPx()
        val plotHeight = (size.height - topPad - axisHeight).coerceAtLeast(8f)
        fun yOf(value: Float) = topPad + plotHeight - (value / 100f) * plotHeight

        val count = days.size.coerceAtLeast(1)
        val slot = size.width / count
        val barWidth = min(slot * 0.48f, 12.dp.toPx()).coerceAtLeast(4.dp.toPx())
        days.forEachIndexed { index, day ->
            val centerX = slot * (index + 0.5f)
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(centerX - barWidth / 2f, topPad),
                size = Size(barWidth, plotHeight),
                cornerRadius = CornerRadius(barWidth / 2f)
            )
            val daily = day?.daily?.toFloat() ?: 0f
            if (daily > 0f) {
                val height = (daily / 100f * plotHeight).coerceAtLeast(4.dp.toPx())
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(centerX - barWidth / 2f, topPad + plotHeight - height),
                    size = Size(barWidth, height),
                    cornerRadius = CornerRadius(barWidth / 2f)
                )
            }

            val slotDate = today.minusDays((count - 1 - index).toLong())
            val isToday = slotDate == today
            val label = if (isToday) "今日" else slotDate.format(formatter)
            val layout = measurer.measure(label, if (isToday) todayStyle else axisStyle)
            val left = (centerX - layout.size.width / 2f)
                .coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
            drawText(
                layout,
                topLeft = Offset(left, size.height - axisHeight + 2.dp.toPx())
            )
        }
    }
}

/**
 * Today's heart rate with the three PAI zone limits as dashed guide lines, so
 * the answer to "why did I earn so little PAI" is visible: the curve sits
 * under the lower limits.
 */
@Composable
fun PaiHrZoneChart(
    values: List<Pair<Long, Int>>,
    lowHr: Int?,
    mediumHr: Int?,
    highHr: Int?,
    modifier: Modifier = Modifier
) {
    if (values.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val axisStyle = TextStyle(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val lowColor = Color(0xFF3FBFAE)
    val mediumColor = Color(0xFF4C8DF6)
    val highColor = Color(0xFF8B5CF6)
    val lineColor = Color(0xFF8A93A6)
    val fillColor = Color(0x148A93A6)
    val zone = remember { ZoneId.systemDefault() }
    val points = remember(values) {
        values.sortedBy { it.first }.map { (epochSec, hr) ->
            val time = Instant.ofEpochSecond(epochSec).atZone(zone).toLocalTime()
            Triple((time.hour * 60 + time.minute) / 1440f, epochSec, hr)
        }
    }

    Canvas(modifier) {
        val rightPad = 30.dp.toPx()
        val axisHeight = 16.dp.toPx()
        val topPad = 8.dp.toPx()
        val plotWidth = (size.width - rightPad).coerceAtLeast(1f)
        val plotHeight = (size.height - axisHeight - topPad).coerceAtLeast(8f)
        val lo = 40f
        val hi = max(
            (points.maxOfOrNull { it.third } ?: 100).toFloat(),
            (highHr ?: 0).toFloat()
        ).coerceAtLeast(100f) + 8f
        fun yOf(hr: Float) = topPad + (1f - (hr - lo) / (hi - lo)) * plotHeight

        // Zone guides first, under the curve.
        listOf(
            Triple(lowHr, "低", lowColor),
            Triple(mediumHr, "中", mediumColor),
            Triple(highHr, "高", highColor)
        ).forEach { (limit, label, color) ->
            if (limit == null) return@forEach
            val y = yOf(limit.toFloat())
            var x = 0f
            val dash = 3.dp.toPx()
            while (x < plotWidth) {
                drawLine(color.copy(alpha = 0.65f), Offset(x, y), Offset(min(x + dash, plotWidth), y), 1.dp.toPx())
                x += dash * 2.4f
            }
            val layout = measurer.measure("$limit", axisStyle.copy(color = color))
            drawText(
                layout,
                topLeft = Offset(size.width - layout.size.width, y - layout.size.height / 2f)
            )
        }

        // Area under the heart-rate curve.
        if (points.size > 1) {
            val path = Path()
            val area = Path()
            points.forEachIndexed { index, point ->
                val x = point.first * plotWidth
                val y = yOf(point.third.toFloat())
                if (index == 0) {
                    path.moveTo(x, y)
                    area.moveTo(x, topPad + plotHeight)
                    area.lineTo(x, y)
                } else {
                    path.lineTo(x, y)
                    area.lineTo(x, y)
                }
            }
            area.lineTo(points.last().first * plotWidth, topPad + plotHeight)
            area.close()
            drawPath(area, fillColor)
            drawPath(path, lineColor, style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round))
        }

        listOf("00", "04", "08", "12", "16", "20", "24").forEachIndexed { index, label ->
            val x = plotWidth * index / 6f
            val layout = measurer.measure(label, axisStyle)
            drawText(
                layout,
                topLeft = Offset(
                    (x - layout.size.width / 2f).coerceIn(0f, (plotWidth - layout.size.width).coerceAtLeast(0f)),
                    size.height - axisHeight + 1.dp.toPx()
                )
            )
        }
        val bpm = measurer.measure("BPM", axisStyle)
        drawText(
            bpm,
            topLeft = Offset(size.width - bpm.size.width, size.height - axisHeight + 1.dp.toPx())
        )
    }
}

/** "如何获取 PAI" scale: 0–100 track with the current total as the marker. */
@Composable
fun PaiHowToScale(total: Double?, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val numberStyle = TextStyle(
        fontSize = 12.sp,
        color = Color.White,
        fontWeight = FontWeight.SemiBold
    )
    val tickStyle = TextStyle(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val track = Color(0x14000000)
    Canvas(modifier) {
        val midY = size.height * 0.42f
        val left = 26.dp.toPx()
        val right = size.width - 10.dp.toPx()
        val width = (right - left).coerceAtLeast(1f)
        val fraction = ((total ?: 0.0) / 100.0).coerceIn(0.0, 1.0).toFloat()
        val radius = 16.dp.toPx()
        val markerX = (left + width * fraction).coerceIn(left + radius, right - radius)
        val stroke = 5.dp.toPx()

        drawLine(
            color = track,
            start = Offset(left, midY),
            end = Offset(right, midY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = PaiAmber,
            start = Offset(left, midY),
            end = Offset(markerX, midY),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )

        listOf(30, 50, 100).forEach { mark ->
            val x = left + width * (mark / 100f)
            drawCircle(Color(0x33000000), 3.dp.toPx(), Offset(x, midY))
            val layout = measurer.measure(mark.toString(), tickStyle)
            drawText(
                layout,
                topLeft = Offset(
                    (x - layout.size.width / 2f).coerceIn(left, right - layout.size.width),
                    midY + 10.dp.toPx()
                )
            )
        }

        val center = Offset(markerX, midY)
        drawPath(octagonPath(center, radius), PaiAmber)
        val text = measurer.measure((total ?: 0.0).roundToInt().toString(), numberStyle)
        drawText(
            text,
            topLeft = Offset(center.x - text.size.width / 2f, center.y - text.size.height / 2f)
        )
    }
}
