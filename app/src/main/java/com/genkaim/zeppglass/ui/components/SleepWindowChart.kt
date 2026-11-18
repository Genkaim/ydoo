package com.genkaim.zeppglass.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.genkaim.zeppglass.data.SleepNight
import com.genkaim.zeppglass.ui.Fmt
import com.genkaim.zeppglass.ui.theme.LocalZgPalette
import java.time.Instant
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Two-week sleep windows: one vertical bar per night, from the moment the band
 * recorded falling asleep to the moment it recorded waking. The y axis is clock
 * time, so the bars show both when the night started and how long it lasted;
 * the x axis is the day. A missing night stays an empty slot, never a zero bar.
 */
@Composable
fun SleepWindowChart(
    nights: List<SleepNight>,
    modifier: Modifier = Modifier
) {
    if (nights.isEmpty()) return
    val palette = LocalZgPalette.current
    val measurer = rememberTextMeasurer()
    val axisStyle = TextStyle(
        fontSize = 9.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val barColor = palette.sleepLight

    // Clock minutes since noon, so a night that crosses midnight stays one
    // continuous bar (23:00 -> 07:00 becomes 660 -> 1140).
    fun clockMinutes(epochSec: Long): Int {
        val time = Instant.ofEpochSecond(epochSec)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
        return ((time.hour * 60 + time.minute) - 12 * 60 + 1440) % 1440
    }

    val windows = remember(nights) {
        nights.map { night ->
            val start = clockMinutes(night.startEpochSec)
            val end = clockMinutes(night.endEpochSec)
            Triple(night.date, start, if (end <= start) end + 1440 else end)
        }
    }
    val minMinute = floor((windows.minOf { it.second } - 20) / 60f) * 60
    val maxMinute = ceil((windows.maxOf { it.third } + 20) / 60f) * 60
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)

    Canvas(modifier) {
        val labelWidth = 40.dp.toPx()
        val axisHeight = 18.dp.toPx()
        val topPad = 6.dp.toPx()
        val rightPad = 4.dp.toPx()
        val plotWidth = (size.width - labelWidth - rightPad).coerceAtLeast(1f)
        val plotHeight = (size.height - axisHeight - topPad).coerceAtLeast(1f)
        val span = (maxMinute - minMinute).coerceAtLeast(60f).toFloat()

        fun yOf(minute: Int): Float =
            topPad + (minute - minMinute) / span * plotHeight

        // Hour grid + y labels, every two hours so they never collide.
        val tickStart = ceil(minMinute / 120f).toInt() * 120
        var tick = tickStart
        while (tick <= maxMinute) {
            val y = yOf(tick)
            drawLine(
                color = gridColor,
                start = Offset(labelWidth, y),
                end = Offset(size.width - rightPad, y),
                strokeWidth = 1.dp.toPx()
            )
            val clock = ((tick + 720) % 1440 + 1440) % 1440
            val text = "%02d:%02d".format(clock / 60, clock % 60)
            val layout = measurer.measure(text, axisStyle)
            drawText(
                layout,
                topLeft = Offset(0f, (y - layout.size.height / 2f))
            )
            tick += 120
        }

        // One bar per night, evenly spaced across the day slots.
        val slot = plotWidth / windows.size
        val barWidth = (slot * 0.38f).coerceIn(5.dp.toPx(), 16.dp.toPx())
        val dateStyle = axisStyle
        windows.forEachIndexed { index, window ->
            val centerX = labelWidth + slot * (index + 0.5f)
            val yStart = yOf(window.second)
            val yEnd = yOf(window.third)
            drawRoundRect(
                color = barColor.copy(alpha = 0.85f),
                topLeft = Offset(centerX - barWidth / 2f, yStart),
                size = Size(barWidth, (yEnd - yStart).coerceAtLeast(2.dp.toPx())),
                cornerRadius = CornerRadius(2.dp.toPx())
            )

            val layout = measurer.measure(Fmt.weekdayShort(window.first), dateStyle)
            val left = (centerX - layout.size.width / 2f)
                .coerceIn(labelWidth, size.width - rightPad - layout.size.width)
            drawText(
                layout,
                topLeft = Offset(left, size.height - layout.size.height)
            )
        }
    }
}
