package com.genkaim.zeppglass.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.genkaim.zeppglass.data.SleepNight
import com.genkaim.zeppglass.ui.theme.LocalZgPalette
import kotlin.math.max
import kotlin.math.min

@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 8.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: @Composable () -> Unit = {}
) {
    val animated = remember { Animatable(0f) }
    LaunchedEffect(progress) { animated.animateTo(progress.coerceIn(0f, 1f), tween(700)) }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * animated.value,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        content()
    }
}

/** Flat line chart: hairline grid, one accent stroke, no area fill. */
@Composable
fun LineChart(
    values: List<Float?>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    minValue: Float? = null,
    maxValue: Float? = null,
    showGrid: Boolean = true,
    strokeWidth: Dp = 2.dp
) {
    val valid = values.filterNotNull()
    if (valid.isEmpty()) {
        Box(modifier)
        return
    }
    val lo = minValue ?: valid.min()
    val hi = maxValue ?: valid.max()
    val span = max(1f, hi - lo)
    val gridColor = LocalZgPalette.current.grid
    val dotColor = MaterialTheme.colorScheme.surface

    Canvas(modifier) {
        if (showGrid) {
            for (i in 0..3) {
                val y = size.height * i / 3f
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1f)
            }
        }
        val stepX = if (values.size <= 1) 0f else size.width / (values.size - 1)
        fun yOf(v: Float) =
            size.height - ((v - lo) / span) * (size.height - strokeWidth.toPx()) - strokeWidth.toPx() / 2

        val path = Path()
        var prev: Offset? = null
        var started = false
        values.forEachIndexed { index, value ->
            if (value == null) return@forEachIndexed
            val x = index * stepX
            val y = yOf(value)
            if (!started) {
                path.moveTo(x, y)
                started = true
            } else {
                val previous = prev
                if (previous != null) {
                    val midX = (previous.x + x) / 2f
                    path.cubicTo(midX, previous.y, midX, y, x, y)
                }
            }
            prev = Offset(x, y)
        }
        if (!started) return@Canvas
        drawPath(path, color, style = Stroke(strokeWidth.toPx(), cap = StrokeCap.Round))

        val lastIndex = values.indexOfLast { it != null }
        if (lastIndex >= 0) {
            val lastValue = values[lastIndex] ?: return@Canvas
            val center = Offset(lastIndex * stepX, yOf(lastValue))
            drawCircle(color, radius = 3.5.dp.toPx(), center = center)
            drawCircle(dotColor, radius = 1.4.dp.toPx(), center = center)
        }
    }
}

/** Flat bars with a highlighted last value and an optional goal line. */
@Composable
fun BarChart(
    values: List<Float?>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    goal: Float? = null,
    barRadius: Dp = 5.dp,
    maxBarWidth: Dp = 13.dp
) {
    val valid = values.filterNotNull()
    if (valid.isEmpty()) {
        Box(modifier)
        return
    }
    val hi = max(valid.max(), goal ?: 0f).coerceAtLeast(1f)
    val goalColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    Canvas(modifier) {
        val count = values.size.coerceAtLeast(1)
        val slot = size.width / count
        val barWidth = min(slot * 0.45f, maxBarWidth.toPx()).coerceAtLeast(2f)
        val radius = CornerRadius(min(barWidth / 2f, barRadius.toPx()))
        values.forEachIndexed { index, value ->
            if (value == null) return@forEachIndexed
            val centerX = index * slot + slot / 2f
            val h = ((value / hi) * size.height).coerceAtLeast(1f)
            drawRoundRect(
                color = if (index == values.lastIndex) color else color.copy(alpha = 0.62f),
                topLeft = Offset(centerX - barWidth / 2f, size.height - h),
                size = Size(barWidth, h),
                cornerRadius = radius
            )
        }
        goal?.let {
            val y = size.height - (it / hi) * size.height
            var x = 0f
            val dash = 3.dp.toPx()
            while (x < size.width) {
                drawLine(goalColor, Offset(x, y), Offset(min(x + dash, size.width), y), 1.dp.toPx())
                x += dash * 2.2f
            }
        }
    }
}

/** Proportional stacked bar for one night. */
@Composable
fun SleepStageBar(
    night: SleepNight,
    modifier: Modifier = Modifier
) {
    val palette = LocalZgPalette.current
    val stages = night.stages.filter { it.endEpochSec > it.startEpochSec }
    val total = (night.endEpochSec - night.startEpochSec).coerceAtLeast(1)
    val colors = mapOf(
        "deep" to palette.sleepDeep,
        "light" to palette.sleepLight,
        "rem" to palette.sleepRem,
        "awake" to palette.sleepAwake,
        "unknown" to MaterialTheme.colorScheme.onSurfaceVariant
    )
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier) {
        drawRect(track)
        stages.forEach { stage ->
            val startX = ((stage.startEpochSec - night.startEpochSec).toFloat() / total) * size.width
            val endX = ((stage.endEpochSec - night.startEpochSec).toFloat() / total) * size.width
            val width = (endX - startX).coerceAtLeast(0.8f)
            drawRect(
                color = colors[stage.stage] ?: Color.Gray,
                topLeft = Offset(startX.coerceIn(0f, size.width), 0f),
                size = Size(width.coerceAtMost(size.width - startX.coerceAtLeast(0f)), size.height)
            )
        }
    }
}
