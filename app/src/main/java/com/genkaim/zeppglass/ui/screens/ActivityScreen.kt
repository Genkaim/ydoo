package com.genkaim.zeppglass.ui.screens

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.genkaim.zeppglass.data.AppSettings
import com.genkaim.zeppglass.data.ExertionDay
import com.genkaim.zeppglass.data.HealthData
import com.genkaim.zeppglass.data.HealthStore
import com.genkaim.zeppglass.data.LoadDay
import com.genkaim.zeppglass.data.LoadModel
import com.genkaim.zeppglass.ui.DetailKind
import com.genkaim.zeppglass.ui.DetailTarget
import com.genkaim.zeppglass.ui.Fmt
import com.genkaim.zeppglass.ui.components.AxisBarChart
import com.genkaim.zeppglass.ui.components.AxisLineChart
import com.genkaim.zeppglass.ui.components.EmptyCard
import com.genkaim.zeppglass.ui.components.FlatPanel
import com.genkaim.zeppglass.ui.components.MetricTile
import com.genkaim.zeppglass.ui.components.PageHeader
import com.genkaim.zeppglass.ui.components.PinnedPageHeader
import com.genkaim.zeppglass.ui.components.SectionTitle
import com.genkaim.zeppglass.ui.components.SignedBarChart
import com.genkaim.zeppglass.ui.components.StatBlock
import com.genkaim.zeppglass.ui.components.TrackRings
import com.genkaim.zeppglass.ui.components.TrendLineChart
import com.genkaim.zeppglass.ui.theme.LocalZgPalette
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** Colours for the fatigue / fitness trend lines (red and teal in Zepp). */
private val FatigueRed = Color(0xFFE05B5B)
private val FitnessTeal = Color(0xFF2FB3B8)

@Composable
fun ActivityScreen(
    settings: AppSettings,
    store: HealthStore,
    modifier: Modifier = Modifier,
    onOpenDetail: (DetailTarget) -> Unit
) {    val data = store.displayData
    val zone = remember { ZoneId.systemDefault() }
    // A minute ticker keeps the page on the current day: a page left open past
    // midnight used to keep yesterday until the app was restarted.
    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            val now = LocalDate.now()
            if (now != today) today = now
        }
    }
    val day = data?.day(today) ?: data?.latestDay()
    val labels = remember(data) {
        (13 downTo 0).map { offset ->
            Fmt.weekdayShort(today.minusDays(offset.toLong()))
        }
    }
    val days14 = remember(data) {
        (13 downTo 0).map { offset -> data?.day(today.minusDays(offset.toLong())) }
    }
    val weekDays = remember(data) {
        (6 downTo 0).mapNotNull { offset -> data?.day(today.minusDays(offset.toLong())) }
    }
    val weekSlots = remember(data) {
        (6 downTo 0).map { offset -> data?.day(today.minusDays(offset.toLong())) }
    }
    val labels7 = remember(today) {
        (6 downTo 0).map { offset -> Fmt.weekdayShort(today.minusDays(offset.toLong())) }
    }
    val load = data?.latestLoad()
    val vo2 = data?.vo2Max
        ?: data?.days?.lastOrNull { it.vo2Max != null }?.vo2Max
        ?: data?.workouts?.firstOrNull { it.vo2Max != null }?.vo2Max
    val pai = data?.pai?.maxByOrNull { it.date }
    // 活动时长 comes from DailyHealth: the official value excludes walking, and
    // it is the one with a daily goal.
    val activityMinutes = day?.activityMinutes
    val activityGoal = day?.activityGoal
    val hrToday = remember(data) {
        data?.heartRate
            ?.filter {
                Instant.ofEpochSecond(it.first).atZone(zone).toLocalDate() == today
            }
            ?.map { it.second }
            .orEmpty()
    }
    // A resting heart rate the watch did not measure comes back as 0 (or 255),
    // which is not a reading: both are dropped so the card shows -- instead of a
    // physiologically impossible 0 bpm.
    val restingHr = (data?.readiness?.firstOrNull { it.date == today }?.sleepRhr
        ?: day?.restingHr)?.takeIf { it in 20..200 }

    var headerHeight by remember { mutableStateOf(0.dp) }
    val scope = rememberCoroutineScope()

    // Load the per-second detail before opening: the page then arrives complete
    // — track, curves and all — instead of drawing half of itself and popping
    // the rest in a moment later. The preload above usually makes this instant.
    fun openWorkout(id: String) {
        val workout = data?.workouts?.firstOrNull { it.id == id }
        scope.launch {
            workout?.let { store.loadWorkoutDetail(it) }
            onOpenDetail(DetailTarget(DetailKind.WORKOUT, workoutId = id))
        }
    }

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
                if (!settings.isLoggedIn) {
                    EmptyCard(
                        title = "还没有连接 Zepp",
                        body = "连接后这里会显示步数、心率曲线、运动与负荷。"
                    )
                } else if (data == null) {
                    EmptyCard(
                        title = "正在同步数据",
                        body = "第一次同步要拉取最近 5 周的手环明细。没有拿到数据时这里不会编数字。",
                        actionLabel = "重试",
                        onAction = { store.refresh() }
                    )
                } else {
                    val advice = data.exertionOn(today) ?: data.exertion.lastOrNull()
                    // Minutes actually spent inside the heart-rate band Zepp
                    // suggests for today. The band data is one sample per minute,
                    // so counting samples is counting minutes.
                    val bandMinutes = remember(data, today, advice?.planHrLow, advice?.planHrHigh) {
                        val low = advice?.planHrLow
                        val high = advice?.planHrHigh
                        if (data == null || low == null || high == null) {
                            null
                        } else {
                            val start = today.atStartOfDay(zone).toInstant().toEpochMilli() / 1000
                            val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() / 1000
                            data.heartRate.count { it.first in start until end && it.second in low..high }
                        }
                    }
                    val planMinutes = advice?.planMinutes
                    ActivityHero(
                        steps = day?.steps,
                        goal = day?.stepGoal ?: 7000,
                        calories = day?.calories,
                        activeMinutes = bandMinutes ?: activityMinutes,
                        activeGoal = planMinutes ?: activityGoal
                    )
                    ActivityStatsCard(
                        steps = day?.steps,
                        activeMinutes = bandMinutes ?: activityMinutes,
                        activeGoal = planMinutes ?: activityGoal,
                        calories = day?.calories,
                        bandBased = bandMinutes != null && planMinutes != null,
                        advice = advice
                    )
                    LoadVo2Card(
                        load = load,
                        vo2 = vo2,
                        advice = advice,
                        onClick = { onOpenDetail(DetailTarget(DetailKind.LOAD, today)) },
                        onStatusClick = { onOpenDetail(DetailTarget(DetailKind.TRAINING_STATUS)) }
                    )

                    if (hrToday.size > 10) {
                        HeartRateCard(hrToday, restingHr)
                    }

                    SectionTitle("近 7 天步数")
                    val weekSteps = weekSlots.mapNotNull { it?.steps }.takeIf { it.isNotEmpty() }
                    if (weekSteps != null) {
                        FlatPanel {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp, horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StatBlock(
                                    Modifier.weight(1f),
                                    Icons.Rounded.DirectionsWalk,
                                    "平均步数",
                                    weekSteps.average().roundToInt().toString(),
                                    MaterialTheme.colorScheme.primary
                                )
                                StatBlock(
                                    Modifier.weight(1f),
                                    Icons.Rounded.ArrowUpward,
                                    "最高",
                                    weekSteps.max().toString(),
                                    MaterialTheme.colorScheme.tertiary
                                )
                                StatBlock(
                                    Modifier.weight(1f),
                                    Icons.Rounded.ArrowDownward,
                                    "最低",
                                    weekSteps.min().toString(),
                                    LocalZgPalette.current.sleepRem
                                )
                            }
                        }
                    }
                    FlatPanel {
                        TrendLineChart(
                            values = weekSlots.map { it?.steps?.toFloat() },
                            labels = labels7,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 12.dp)
                                .height(150.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    RecentWorkoutList(data, ::openWorkout)
                }
                Spacer(Modifier.height(96.dp))
            }
        }
        PinnedPageHeader(onHeight = { headerHeight = it }) {
            PageHeader(
                title = "活动",
                subtitle = "${Fmt.date(today)} · ${Fmt.weekday(today)}"
            )
        }
    }
}

@Composable
private fun ActivityHero(
    steps: Int?,
    goal: Int,
    calories: Double?,
    activeMinutes: Int?,
    activeGoal: Int?
) {
    val palette = LocalZgPalette.current
    val stepsProgress = if (goal > 0) (steps ?: 0).toFloat() / goal else 0f
    val kcalProgress = ((calories ?: 0.0) / 500.0).toFloat()
    // The official goal is the denominator; without one fall back to a 60-minute
    // scale so the arc still moves, but no goal is ever printed as if official.
    val activeProgress = ((activeMinutes ?: 0).toFloat() /
        (activeGoal?.takeIf { it > 0 } ?: 60).toFloat())
    val stepColor = MaterialTheme.colorScheme.primary
    val kcalColor = MaterialTheme.colorScheme.tertiary
    val activeColor = palette.sleepRem
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TrackRings(
            values = listOf(stepsProgress, kcalProgress, activeProgress),
            colors = listOf(stepColor, kcalColor, activeColor),
            icons = listOf(
                Icons.Rounded.DirectionsWalk,
                Icons.Rounded.LocalFireDepartment,
                Icons.Rounded.Timer
            ),
            modifier = Modifier.size(240.dp),
            strokeWidth = 17.dp,
            gap = 6.dp
        )
    }
}

@Composable
private fun ActivityStatsCard(
    steps: Int?,
    activeMinutes: Int?,
    activeGoal: Int?,
    calories: Double?,
    bandBased: Boolean,
    advice: ExertionDay?
) {
    val stepColor = MaterialTheme.colorScheme.primary
    val activeColor = LocalZgPalette.current.sleepRem
    val kcalColor = MaterialTheme.colorScheme.tertiary
    FlatPanel(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 15.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatBlock(
                    Modifier.weight(1f),
                    Icons.Rounded.DirectionsWalk,
                    "步数",
                    steps?.toString() ?: "--",
                    stepColor
                )
                StatBlock(
                    Modifier.weight(1f),
                    Icons.Rounded.Timer,
                    if (bandBased) "区间时长" else "活动时长",
                    when {
                        activeMinutes == null -> "--"
                        activeGoal != null -> "$activeMinutes/$activeGoal"
                        else -> activeMinutes.toString()
                    },
                    activeColor,
                    unit = "分"
                )
                StatBlock(
                    Modifier.weight(1f),
                    Icons.Rounded.LocalFireDepartment,
                    "千卡",
                    Fmt.int(calories),
                    kcalColor
                )
            }
            // The day's advice lives in the same card as the totals: it is the
            // target those numbers are measured against, not a separate report.
            if (advice != null) {
                val points = when {
                    advice.totalScore == null && advice.targetScore == null -> null
                    advice.targetScore != null && advice.targetScore > 0 ->
                        "${advice.totalScore ?: 0}/${advice.targetScore}"
                    else -> advice.totalScore?.toString()
                }
                val hr = if (advice.planHrLow != null && advice.planHrHigh != null) {
                    "${advice.planHrLow}-${advice.planHrHigh}"
                } else {
                    null
                }
                MetricsDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 15.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatBlock(
                        Modifier.weight(1f),
                        Icons.Rounded.Star,
                        "活动点数",
                        points ?: "--",
                        MaterialTheme.colorScheme.primary
                    )
                    StatBlock(
                        Modifier.weight(1f),
                        Icons.Rounded.Favorite,
                        "建议心率",
                        hr ?: "--",
                        LocalZgPalette.current.sleepRem,
                        unit = if (hr == null) null else "bpm"
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroStat(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(5.dp))
            Text(value, style = MaterialTheme.typography.labelMedium)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 运动状态 in one card with two tap zones: 运动负荷 + 最大摄氧量 on top opens the
 * load history, 疲劳 / 体能 / 训练状态 below opens the training-status page.
 *
 * Out-of-range numbers colour their value, unit and verdict together; the
 * neutral cases stay in ink, and only a genuinely tired day (TSB < -10) gets a
 * sentence.
 */
@Composable
private fun LoadVo2Card(
    load: LoadDay?,
    vo2: Int?,
    advice: ExertionDay?,
    onClick: () -> Unit,
    onStatusClick: () -> Unit
) {
    val warn = LocalZgPalette.current.warn
    val critical = LocalZgPalette.current.critical
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val weekLoad = load?.weekLoad
    val minOk = load?.optimalMin
    val maxOk = load?.optimalMax
    val loadVerdict: Pair<String, Color> = when {
        weekLoad == null -> "还没有负荷" to muted
        minOk != null && weekLoad < minOk -> "偏低" to warn
        maxOk != null && weekLoad > maxOk -> "偏高" to critical
        minOk != null -> "合适" to ink
        else -> "7 天累计" to muted
    }
    val loadColor = if (loadVerdict.first in setOf("偏低", "偏高")) loadVerdict.second else ink
    val rating = vo2Rating(vo2)
    val vo2Color = when (rating) {
        "偏低", "一般" -> warn
        else -> ink
    }
    SectionTitle("运动状态")
    FlatPanel {
        Column(Modifier.fillMaxWidth()) {
            // Each tap zone stops exactly on the hairline: the upper one ends
            // where the line starts, the lower one begins where it ends, so a
            // pressed tint never spills past the divider.
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 15.dp, bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("运动负荷", style = MaterialTheme.typography.labelSmall, color = muted)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                weekLoad?.roundToInt()?.toString() ?: "--",
                                style = MaterialTheme.typography.titleLarge,
                                color = loadColor
                            )
                            Text(
                                " EPOC",
                                style = MaterialTheme.typography.labelSmall,
                                color = loadColor,
                                modifier = Modifier.padding(bottom = 3.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                loadVerdict.first,
                                style = MaterialTheme.typography.labelSmall,
                                color = loadColor,
                                modifier = Modifier.padding(bottom = 3.dp)
                            )
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text("最大摄氧量", style = MaterialTheme.typography.labelSmall, color = muted)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                vo2?.toString() ?: "--",
                                style = MaterialTheme.typography.titleLarge,
                                color = vo2Color
                            )
                            Text(
                                " ml/kg",
                                style = MaterialTheme.typography.labelSmall,
                                color = vo2Color,
                                modifier = Modifier.padding(bottom = 3.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                rating,
                                style = MaterialTheme.typography.labelSmall,
                                color = vo2Color,
                                modifier = Modifier.padding(bottom = 3.dp)
                            )
                        }
                    }
                }
            }
            if (advice != null) {
                Box(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onStatusClick)
                ) {
                    val status = advice.status
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 15.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatBlock(
                                Modifier.weight(1f),
                                Icons.Rounded.LocalFireDepartment,
                                "疲劳",
                                advice.fatigue?.roundToInt()?.toString() ?: "--",
                                warn
                            )
                            StatBlock(
                                Modifier.weight(1f),
                                Icons.Rounded.Favorite,
                                "体能",
                                advice.fitness?.roundToInt()?.toString() ?: "--",
                                MaterialTheme.colorScheme.primary
                            )
                            StatBlock(
                                Modifier.weight(1f),
                                Icons.Rounded.Timer,
                                "训练状态",
                                status?.let { (if (it > 0) "+" else "") + it.roundToInt() } ?: "--",
                                MaterialTheme.colorScheme.tertiary
                            )
                        }
                        if (status != null && status < -10) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "偏疲劳，建议降低强度",
                                style = MaterialTheme.typography.labelSmall,
                                color = warn
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The same hairline the body-composition metric rows use between items, so a
 * card split into two blocks reads like those lists rather than like two cards
 * stacked on each other.
 */
@Composable
private fun MetricsDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(vertical = 2.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

/** Zepp-style VO2 max rating (52 shows 良好 in the official app). */
private fun vo2Rating(value: Int?): String = when {
    value == null -> "还没测算"
    value >= 55 -> "优秀"
    value >= 50 -> "良好"
    value >= 45 -> "一般"
    else -> "偏低"
}

@Composable
private fun HeartRateCard(values: List<Int>, restingHr: Int?) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("今日心率", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
    }
    if (values.isNotEmpty()) {
        FlatPanel {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatBlock(
                    Modifier.weight(1f),
                    Icons.Rounded.Favorite,
                    "静息心率",
                    restingHr?.toString() ?: "--",
                    MaterialTheme.colorScheme.primary,
                    unit = "bpm"
                )
                StatBlock(
                    Modifier.weight(1f),
                    Icons.Rounded.ArrowUpward,
                    "最高心率",
                    values.max().toString(),
                    MaterialTheme.colorScheme.tertiary,
                    unit = "bpm"
                )
                StatBlock(
                    Modifier.weight(1f),
                    Icons.Rounded.ArrowDownward,
                    "最低心率",
                    values.min().toString(),
                    LocalZgPalette.current.sleepRem,
                    unit = "bpm"
                )
            }
        }
    }
    FlatPanel {
        com.genkaim.zeppglass.ui.components.HeartRateChart(
            values = values,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .height(150.dp)
        )
    }
}

@Composable
private fun HeartStat(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = color
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RecentWorkoutList(data: HealthData?, onOpen: (String) -> Unit) {
    val workouts = data?.workouts?.take(5).orEmpty()
    if (workouts.isEmpty()) return
    SectionTitle("最近运动")
    FlatPanel {
        // No padding around the list: the first and last row carry the card's
        // edge, so their pressed tint runs all the way to the rounded corners.
        Column {
            workouts.forEach { workout ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(workout.id) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.DirectionsRun,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(workout.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${Fmt.dayAndTime(workout.endEpochSec * 1000)} · ${Fmt.duration(workout.durationSec)}" +
                                if (workout.distanceM > 100) " · ${Fmt.km(workout.distanceM)} km" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekSummary(days: List<com.genkaim.zeppglass.data.DayStat>) {
    if (days.isEmpty()) return
    val steps = days.mapNotNull { it.steps }.takeIf { it.isNotEmpty() }?.sum()
    val distance = days.mapNotNull { it.distanceM }.takeIf { it.isNotEmpty() }?.sum()
    val active = days.mapNotNull { it.activeMinutes }.takeIf { it.isNotEmpty() }?.sum()
    val calories = days.mapNotNull { it.calories }.takeIf { it.isNotEmpty() }?.sum()
    SectionTitle("近 7 天合计", trailing = "${days.size} 天有记录")
    FlatPanel {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WeekStat("步数", steps?.let { Fmt.int(it.toDouble()) } ?: "--", null, Modifier.weight(1f))
                WeekStat("距离", Fmt.km(distance), "km", Modifier.weight(1f))
                WeekStat("活动分钟", active?.toString() ?: "--", "min", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WeekStat("日均步数", steps?.let { Fmt.int(it.toDouble() / days.size) } ?: "--", "步", Modifier.weight(1f))
                WeekStat("消耗", calories?.let { Fmt.int(it) } ?: "--", "kcal", Modifier.weight(1f))
                WeekStat("有记录天数", days.size.toString(), "天", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun WeekStat(label: String, value: String, unit: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge
            )
            if (unit != null) {
                Text(
                    unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp, bottom = 3.dp)
                )
            }
        }
    }
}
