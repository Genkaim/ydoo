package com.genkaim.zeppglass.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.genkaim.zeppglass.data.SleepNight
import com.genkaim.zeppglass.data.SleepStageSlice
import com.genkaim.zeppglass.ui.theme.LocalZgPalette
import kotlin.math.max
import kotlin.math.min

/** Chinese label for a stage key used by the models. */
fun sleepStageLabel(stage: String): String = when (stage) {
    "awake" -> "清醒"
    "rem" -> "REM"
    "light" -> "浅睡"
    "deep" -> "深睡"
    else -> "未分期"
}

private val ROW_HEIGHT = 30.dp
private val ROW_GAP = 10.dp
private val BAR_HEIGHT = 18.dp
private val LABEL_WIDTH = 42.dp
private val AXIS_HEIGHT = 20.dp

/**
 * Apple-Health style hypnogram: one row per stage with the blocks placed on the
 * night's time axis and thin connectors where the stage changes. There is no
 * grey track behind the rows; tapping a block pops a tooltip next to it.
 */
@Composable
fun SleepStageChart(
    night: SleepNight,
    modifier: Modifier = Modifier,
    selected: SleepStageSlice?,
    onSelect: (SleepStageSlice?) -> Unit
) {
    val palette = LocalZgPalette.current
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val axisStyle = TextStyle(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f))
    val tipStyle = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
    val tipSurface = MaterialTheme.colorScheme.surfaceVariant
    val colors = remember(palette) {
        mapOf(
            "awake" to palette.sleepAwake,
            "rem" to palette.sleepRem,
            "light" to palette.sleepLight,
            "deep" to palette.sleepDeep
        )
    }
    val rows = listOf("awake", "rem", "light", "deep")
    val span = (night.endEpochSec - night.startEpochSec).coerceAtLeast(1L)
    val slices = remember(night) {
        night.stages
            .filter { it.endEpochSec > it.startEpochSec }
            .sortedBy { it.startEpochSec }
    }
    val chartHeight = ROW_HEIGHT * rows.size + ROW_GAP * (rows.size - 1) + AXIS_HEIGHT

    Canvas(
        modifier
            .height(chartHeight)
            .pointerInput(slices, selected) {
                detectTapGestures { offset ->
                    val rowStep = (ROW_HEIGHT + ROW_GAP).toPx()
                    // The axis strip belongs to the labels, not to a stage.
                    if (offset.y > (rowStep * (rows.size - 1) + ROW_HEIGHT.toPx())) return@detectTapGestures
                    val rowIndex = (offset.y / rowStep).toInt().coerceIn(0, rows.size - 1)
                    val rowStepPx = rowStep.toFloat()
                    // Inside the row's bar band only.
                    val within = offset.y - rowIndex * rowStepPx
                    if (within > (ROW_HEIGHT.toPx() + ROW_GAP.toPx())) return@detectTapGestures
                    val stage = rows[rowIndex]
                    val labelWidth = LABEL_WIDTH.toPx()
                    val chartWidth = size.width - labelWidth
                    if (offset.x < labelWidth || chartWidth <= 0f) return@detectTapGestures
                    val t = night.startEpochSec +
                        (((offset.x - labelWidth) / chartWidth) * span).toLong()
                    val hit = slices.firstOrNull {
                        it.stage == stage && t >= it.startEpochSec - 60 && t <= it.endEpochSec + 60
                    }
                    onSelect(if (hit == null || hit == selected) null else hit)
                }
            }
    ) {
        val labelWidth = LABEL_WIDTH.toPx()
        val rowHeight = ROW_HEIGHT.toPx()
        val rowGap = ROW_GAP.toPx()
        val barHeight = BAR_HEIGHT.toPx()
        val chartWidth = (size.width - labelWidth).coerceAtLeast(1f)
        fun xOf(epochSec: Long): Float =
            (labelWidth + ((epochSec - night.startEpochSec).toFloat() / span) * chartWidth)
                // A stage that runs past the recorded end (a nap tail, a slice
                // rounded to the minute) must stop at the chart, not draw over
                // the card edge.
                .coerceIn(labelWidth, labelWidth + chartWidth)
        fun rowCenter(index: Int): Float = index * (rowHeight + rowGap) + rowHeight / 2f
        val stageRow = rows.withIndex().associate { it.value to it.index }

        rows.forEachIndexed { index, key ->
            val layout = measurer.measure(sleepStageLabel(key), labelStyle)
            drawText(
                layout,
                topLeft = Offset(0f, rowCenter(index) - layout.size.height / 2f)
            )
        }

        // Stage-change connectors first, so the blocks sit on top of them.
        slices.zipWithNext { a, b ->
            val rowA = stageRow[a.stage] ?: return@zipWithNext
            val rowB = stageRow[b.stage] ?: return@zipWithNext
            if (rowA == rowB) return@zipWithNext
            val x = xOf(a.endEpochSec)
            // Any selection dims every connector, including the two that touch
            // the chosen block: those used to stay bright, so the block never
            // read as standing on its own.
            val faded = selected != null
            // The connector spans only the space between the two bars: from one
            // bar's edge to the other's. Running it to the row centres left its
            // ends visible beside a very short block, which read as the line
            // poking out of the block's middle.
            // A very short stage is a sliver: the connector must not pass
            // through the bar band, or its end shows beside the sliver as a
            // spike. It therefore runs only across the gap between the two bars,
            // from one bar's edge to the other's.
            val down = rowB > rowA
            val fromY = rowCenter(rowA) + if (down) barHeight / 2f else -barHeight / 2f
            val toY = rowCenter(rowB) + if (down) -barHeight / 2f else barHeight / 2f
            // How far the line leans sideways follows the shorter of the two
            // blocks it joins: a long stage can carry a visible diagonal, a sliver
            // only a hair of it, or the line would look like it belongs to the
            // sliver alone. Decided per connector, from its own two blocks.
            val aWidth = xOf(a.endEpochSec) - xOf(a.startEpochSec)
            val bWidth = xOf(b.endEpochSec) - xOf(b.startEpochSec)
            val slant = (0.6.dp.toPx() + min(aWidth, bWidth) * 0.05f)
                .coerceAtMost(1.2.dp.toPx())
            val startX = (x - slant).coerceIn(xOf(a.startEpochSec), x)
            val endX = (x + slant).coerceIn(x, xOf(b.endEpochSec))
            val from = Offset(startX, fromY)
            val to = Offset(endX, toY)
            val alpha = if (faded) 0.14f else 0.55f
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(
                        (colors[a.stage] ?: Color.Gray).copy(alpha = alpha),
                        (colors[b.stage] ?: Color.Gray).copy(alpha = alpha)
                    ),
                    start = from,
                    end = to
                ),
                start = from,
                end = to,
                strokeWidth = 0.7.dp.toPx()
            )
        }

        slices.forEach { slice ->
            val row = stageRow[slice.stage] ?: return@forEach
            val color = colors[slice.stage] ?: Color.Gray
            val start = xOf(slice.startEpochSec)
            val end = xOf(slice.endEpochSec)
            val dim = selected != null && selected != slice
            // The block is exactly its own span. Padding a short stage out to a
            // minimum width always spilled it into a neighbour — rightwards it ate
            // the connector, leftwards it covered the previous block's tail — so a
            // sliver now stays a sliver (a hairline at worst) and nothing crosses
            // the line between two stages.
            val right = end.coerceAtLeast(start + 1f)
            // Square edges, so a block and its connector read as one shape.
            drawRect(
                color = if (dim) color.copy(alpha = 0.32f) else color,
                topLeft = Offset(start, rowCenter(row) - barHeight / 2f),
                size = Size(right - start, barHeight)
            )
        }

        // Clock axis under the stage rows, so a block's position can be read
        // without tapping it.
        val axisTop = rowCenter(rows.lastIndex) + rowHeight / 2f + 5.dp.toPx()
        listOf(
            night.startEpochSec,
            night.startEpochSec + span / 2,
            night.endEpochSec
        ).forEachIndexed { index, epochSec ->
            val layout = measurer.measure(formatClock(epochSec), axisStyle)
            val x = when (index) {
                0 -> labelWidth
                1 -> labelWidth + (chartWidth - layout.size.width) / 2f
                else -> labelWidth + chartWidth - layout.size.width
            }
            drawText(layout, topLeft = Offset(x, axisTop))
        }

        selected?.let { slice ->
            val row = stageRow[slice.stage] ?: return@let
            val minutes = ((slice.endEpochSec - slice.startEpochSec) / 60).toInt()
            val title = measurer.measure(sleepStageLabel(slice.stage), tipStyle)
            val range = measurer.measure(
                "${formatClock(slice.startEpochSec)}–${formatClock(slice.endEpochSec)} · $minutes 分钟",
                tipStyle
            )
            val padding = 8.dp.toPx()
            val width = max(title.size.width, range.size.width) + padding * 2
            val height = title.size.height + range.size.height + padding * 1.4f
            val cx = (xOf(slice.startEpochSec) + xOf(slice.endEpochSec)) / 2f
            val left = (cx - width / 2f).coerceIn(0f, (size.width - width).coerceAtLeast(0f))
            val above = row == 0
            val top = if (above) {
                rowCenter(row) - barHeight / 2f - height - 6.dp.toPx()
            } else {
                rowCenter(row) - barHeight / 2f - height - 6.dp.toPx()
            }.coerceIn(0f, size.height - height)
            drawRoundRect(
                color = tipSurface,
                topLeft = Offset(left, top),
                size = Size(width, height),
                cornerRadius = CornerRadius(8.dp.toPx())
            )
            drawText(title, topLeft = Offset(left + padding, top + padding * 0.4f))
            drawText(
                range,
                topLeft = Offset(left + padding, top + padding * 0.4f + title.size.height)
            )
        }
    }
}

private fun formatClock(epochSec: Long): String {
    val local = java.time.Instant.ofEpochSecond(epochSec)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalTime()
    return "%02d:%02d".format(local.hour, local.minute)
}
