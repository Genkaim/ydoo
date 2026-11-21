package com.genkaim.zeppglass.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.genkaim.zeppglass.data.AppSettings
import com.genkaim.zeppglass.data.ChargeDay
import com.genkaim.zeppglass.data.ChargeSample
import com.genkaim.zeppglass.data.HealthStore
import com.genkaim.zeppglass.data.morningSample
import com.genkaim.zeppglass.ui.DetailKind
import com.genkaim.zeppglass.ui.DetailTarget
import com.genkaim.zeppglass.ui.Fmt
import com.genkaim.zeppglass.ui.components.AxisBarChart
import com.genkaim.zeppglass.ui.components.AxisLineChart
import com.genkaim.zeppglass.ui.components.ChargeDayChart
import com.genkaim.zeppglass.ui.components.ChargeDayChartIconLift
import com.genkaim.zeppglass.ui.components.ChargeSpan
import com.genkaim.zeppglass.ui.components.ChargeSpanKind
import com.genkaim.zeppglass.ui.components.chargeSpanIcon
import com.genkaim.zeppglass.ui.components.chargeSpans
import com.genkaim.zeppglass.ui.components.EmptyCard
import com.genkaim.zeppglass.ui.components.FlatPanel
import com.genkaim.zeppglass.ui.components.PageHeader
import com.genkaim.zeppglass.ui.components.PinnedPageHeader
import com.genkaim.zeppglass.ui.components.ProgressRing
import com.genkaim.zeppglass.ui.components.SectionTitle
import com.genkaim.zeppglass.ui.components.TrendLineChart
import com.genkaim.zeppglass.ui.theme.LocalZgPalette
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun ChargeScreen(
    settings: AppSettings,
    store: HealthStore,
    modifier: Modifier = Modifier
) {
    val data = store.displayData
    val today = remember { LocalDate.now() }
    val todayCharge = data?.chargeOn(today) ?: data?.latestCharge()
    // Fixed two-week window keeps the weekday axis aligned even when the band
    // skipped a day; a missing day stays null instead of becoming 0.
    val dates = remember(today) { (13 downTo 0).map { today.minusDays(it.toLong()) } }
    val labels = remember(dates) { dates.map { Fmt.weekdayShort(it) } }
    // The two-week comparison is the morning reading, not the current evening
    // value, so days are compared at the same point in their cycle.
    val morning = remember(data, dates) {
        dates.map { date ->
            data?.chargeOn(date)?.morningSample(data?.nightOn(date)?.endEpochSec)
        }
    }


    var headerHeight by remember { mutableStateOf(0.dp) }
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(headerHeight + 4.dp))
            Column(
                Modifier.padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when {
                    !settings.isLoggedIn -> EmptyCard(
                        title = "还没有连接 Zepp",
                        body = "连接后才能读到手表算出的 Charge 分数。"
                    )
                    todayCharge == null -> EmptyCard(
                        title = "这段时间没有 Charge 数据",
                        body = "Charge 由手表在佩戴时计算；没戴表、或那天手表没有算出分数，这里就留空，不会补 0。",
                        actionLabel = "重试同步",
                        onAction = { store.refresh() }
                    )
                    else -> {
                        // The chart marks each stretch with an icon; the card
                        // below spells out the same stretches. Both read from
                        // one list so the two can never disagree.
                        val daySleep = data?.nightOn(todayCharge.date)
                        val dayWorkouts = data?.workouts.orEmpty().filter { workout ->
                            java.time.Instant.ofEpochSecond(workout.endEpochSec)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toLocalDate() == todayCharge.date
                        }
                        val spans = chargeSpans(
                            samples = todayCharge.samples,
                            sleep = daySleep,
                            workouts = dayWorkouts
                        )
                        val dayStartSec = todayCharge.date
                            .atStartOfDay(java.time.ZoneId.systemDefault())
                            .toEpochSecond()

                        ChargeHero(todayCharge)
                        SectionTitle("当天曲线")
                        FlatPanel {
                            // The canvas is taller than the visible plot by the
                            // icon lift, and the card's top padding shrinks by
                            // the same amount: the icons rise, the bands, line
                            // and hour labels stay exactly where they were.
                            Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                                ChargeDayChart(
                                    date = todayCharge.date,
                                    samples = todayCharge.samples,
                                    sleep = daySleep,
                                    workouts = dayWorkouts,
                                    untilEpochSec = if (todayCharge.date == today)
                                        java.time.Instant.now().epochSecond else null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 14.dp - ChargeDayChartIconLift)
                                        .height(170.dp + ChargeDayChartIconLift)
                                )
                            }
                        }
                        SectionTitle("今日活动")
                        FlatPanel {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                                if (spans.isEmpty()) {
                                    // Same inset as the rows below it, so the
                                    // empty card has the width of every other card.
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 14.dp)
                                    ) {
                                        Text(
                                            "暂无今日活动",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    // Newest first: the stretch that just ended
                                    // matters more than last night's.
                                    spans.asReversed().forEachIndexed { index, span ->
                                        if (index > 0) {
                                            Box(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .height(1.dp)
                                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                            )
                                        }
                                        ChargeSpanRow(span, dayStartSec)
                                    }
                                }
                            }
                        }
                        SectionTitle("近两周晨起数据")
                        FlatPanel {
                            TrendLineChart(
                                values = morning.map { it?.total?.toFloat() },
                                labels = labels,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 12.dp)
                                    .height(150.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        SplitTrend(morning, labels)
                        Text(
                            "Charge 是手表根据心率、活动与恢复算出的能量估计，属于参考值，不是医学指标。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(Modifier.height(96.dp))
            }
        }
        PinnedPageHeader(onHeight = { headerHeight = it }) {
            PageHeader(title = "Hybrid Charge", subtitle = "体力与精神的当日能量")
        }
    }
}

/**
 * One line in the 今日活动 card: what the stretch was, from when to when, how
 * long it lasted, and how much Charge it moved. The delta is the same number the
 * chart's icon stands for — sleep adds, a workout spends.
 */
@Composable
private fun ChargeSpanRow(span: ChargeSpan, dayStartSec: Long) {
    val palette = LocalZgPalette.current
    val tint = if (span.kind == ChargeSpanKind.WORKOUT) palette.workout else palette.sleepLight
    val from = if (span.startEpochSec < dayStartSec) {
        "昨 ${Fmt.timeSec(span.startEpochSec)}"
    } else {
        Fmt.timeSec(span.startEpochSec)
    }
    val to = Fmt.timeSec(span.endEpochSec)
    val minutes = ((span.endEpochSec - span.startEpochSec) / 60).toInt()
    val delta = span.delta
    val deltaText = when {
        delta == null -> "—"
        delta > 0 -> "+$delta"
        delta < 0 -> "−${-delta}"
        else -> "0"
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(36.dp)
                .background(tint.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                chargeSpanIcon(span.kind),
                contentDescription = span.label,
                tint = tint,
                modifier = Modifier.size(21.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(span.label, style = MaterialTheme.typography.bodyMedium)
            Text(
                "$from – $to · ${Fmt.minutes(minutes)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            deltaText,
            style = MaterialTheme.typography.titleMedium,
            color = if (delta == null) MaterialTheme.colorScheme.onSurfaceVariant else tint
        )
    }
}

@Composable
private fun ChargeHero(charge: ChargeDay) {
    // Three columns: 精力 (left arc), the Hybrid Charge ring in the middle,
    // 体力 (right arc). The side gauges are separate from the centre ring, so
    // nothing sits on top of anything else.
    val crit = LocalZgPalette.current.critical
    val warnTone = LocalZgPalette.current.warn
    fun tier(value: Double?, base: Color): Color = when {
        value == null -> base
        // Amber below 30, red below 5: 50 was high enough that a normal
        // afternoon dip already looked like a warning.
        value < 5.0 -> crit
        value < 30.0 -> warnTone
        else -> base
    }
    // 体力 and 精力 keep their own colour whatever the reading: light blue for
    // the body, deeper blue for the mind. Only the combined score still steps
    // down to amber and red.
    val physicalColor = LocalZgPalette.current.chargePhysical
    val mentalColor = LocalZgPalette.current.chargeMental
    val totalColor = tier(charge.total?.toDouble(), MaterialTheme.colorScheme.primary)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(190.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            ChargeSideValue(
                value = charge.mental?.roundToInt()?.toString() ?: "--",
                label = "精力",
                color = mentalColor,
                alignment = Alignment.End,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            )
            ChargeSideGauge(
                progress = ((charge.mental ?: 0.0) / 100.0).toFloat(),
                color = mentalColor,
                left = true,
                modifier = Modifier
                    .width(32.dp)
                    .height(130.dp)
            )
            Spacer(Modifier.width(6.dp))
            ProgressRing(
                (charge.total ?: 0) / 100f,
                Modifier.size(148.dp),
                strokeWidth = 11.dp,
                color = totalColor,
                trackColor = totalColor.copy(alpha = 0.18f)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        charge.total?.toString() ?: "--",
                        style = MaterialTheme.typography.displaySmall,
                        color = totalColor
                    )
                    Text(
                        "HYBRID\nCHARGE",
                        style = MaterialTheme.typography.labelSmall,
                        color = totalColor,
                        textAlign = TextAlign.Center,
                        lineHeight = 13.sp
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            ChargeSideGauge(
                progress = ((charge.physical ?: 0.0) / 100.0).toFloat(),
                color = physicalColor,
                left = false,
                modifier = Modifier
                    .width(32.dp)
                    .height(130.dp)
            )
            ChargeSideValue(
                value = charge.physical?.roundToInt()?.toString() ?: "--",
                label = "体力",
                color = physicalColor,
                alignment = Alignment.Start,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            )
        }
    }
}

/** The coloured number and label outside a side gauge. */
@Composable
private fun ChargeSideValue(
    value: String,
    label: String,
    color: Color,
    alignment: Alignment.Horizontal,
    modifier: Modifier = Modifier
) {
    Column(modifier, horizontalAlignment = alignment) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            color = color,
            maxLines = 1
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = color
        )
    }
}

/**
 * A tall parenthesis-like arc: pale track, coloured progress from the bottom
 * up. Drawn as a quadratic Bezier so it can be narrow and still curve like the
 * arcs in the official overview.
 */
@Composable
private fun ChargeSideGauge(
    progress: Float,
    color: Color,
    left: Boolean,
    modifier: Modifier = Modifier
) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val animated by animateFloatAsState(
        targetValue = if (appeared) progress.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(700),
        label = "chargeGauge"
    )
    Canvas(modifier) {
        val stroke = 9.dp.toPx()
        val edge = if (left) size.width - stroke / 2f else stroke / 2f
        val bulge = size.width - stroke
        val controlX = if (left) edge - 2f * bulge else edge + 2f * bulge
        val top = stroke / 2f
        val bottom = size.height - stroke / 2f
        val middle = size.height / 2f

        fun arcPath(from: Float, to: Float): Path {
            val path = Path()
            val steps = (48 * (to - from)).toInt().coerceAtLeast(2)
            for (i in 0..steps) {
                val t = from + (to - from) * i / steps
                val mt = 1f - t
                val x = mt * mt * edge + 2f * mt * t * controlX + t * t * edge
                val y = mt * mt * top + 2f * mt * t * middle + t * t * bottom
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            return path
        }

        drawPath(
            arcPath(0f, 1f),
            color = color.copy(alpha = 0.20f),
            style = Stroke(stroke, cap = StrokeCap.Round)
        )
        if (animated > 0f) {
            // Charging reads bottom → top, so the filled part grows upward. A
            // reported 0 still gets a thin sliver: an empty path read as "no
            // reading" instead of "measured zero", which is a different claim.
            drawPath(
                arcPath(1f - animated, 1f),
                color = color,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        } else if (progress <= 0f) {
            drawPath(
                arcPath(0.985f, 1f),
                color = color,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun SplitBar(label: String, value: Double?, color: Color) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(
                value?.roundToInt()?.toString() ?: "--",
                style = MaterialTheme.typography.labelLarge
            )
        }
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(5.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))
        ) {
            Box(
                Modifier
                    // A measured 0 keeps a hair of colour so it reads as a zero
                    // value rather than a missing one.
                    .fillMaxWidth(
                        if (value == null) 0f
                        else (value.toFloat().coerceIn(0f, 100f) / 100f).coerceAtLeast(0.02f)
                    )
                    .height(5.dp)
                    .background(color, RoundedCornerShape(3.dp))
            )
        }
    }
}

@Composable
private fun SplitTrend(
    series: List<ChargeSample?>,
    labels: List<String>
) {
    SectionTitle("体力", trailing = "晨起")
    FlatPanel {
        TrendLineChart(
            values = series.map { it?.physical?.toFloat() },
            labels = labels,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp)
                .height(140.dp),
            color = LocalZgPalette.current.chargePhysical
        )
    }
    SectionTitle("精力", trailing = "晨起")
    FlatPanel {
        TrendLineChart(
            values = series.map { it?.mental?.toFloat() },
            labels = labels,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp)
                .height(140.dp),
            color = LocalZgPalette.current.chargeMental
        )
    }
}



