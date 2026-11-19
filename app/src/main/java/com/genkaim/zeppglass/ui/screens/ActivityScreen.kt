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
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.genkaim.zeppglass.data.AppSettings
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
    val today = remember { LocalDate.now() }
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
    val restingHr = data?.readiness?.firstOrNull { it.date == today }?.sleepRhr
        ?: day?.restingHr

    var headerHeight by remember { mutableStateOf(0.dp) }
    val scope = rememberCoroutineScope()

    // Fetch the per-second detail before opening, so the workout page does not
    // draw half of itself and then pop the charts in.
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
                    ActivityHero(
                        steps = day?.steps,
                        goal = day?.stepGoal ?: 7000,
                        calories = day?.calories,
                        activeMinutes = activityMinutes,
                        activeGoal = activityGoal
                    )
                    ActivityStatsCard(
                        steps = day?.steps,
                        activeMinutes = activityMinutes,
                        activeGoal = activityGoal,
                        calories = day?.calories
                    )
                    LoadRecoveryCard(
                        load = load,
                        vo2 = vo2
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
    calories: Double?
) {
    val stepColor = MaterialTheme.colorScheme.primary
    val activeColor = LocalZgPalette.current.sleepRem
    val kcalColor = MaterialTheme.colorScheme.tertiary
    FlatPanel(Modifier.fillMaxWidth()) {
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
                "步数",
                steps?.toString() ?: "--",
                stepColor
            )
            StatBlock(
                Modifier.weight(1f),
                Icons.Rounded.Timer,
                "活动时长",
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
            Text(value, style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace))
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The official 运动状态 block: 7-day EPOC load and VO2 max, with the official
 * load band as the status. Fatigue / fitness / training status are not fields
 * the Zepp cloud returns, so they are not shown here.
 */
@Composable
private fun LoadRecoveryCard(
    load: LoadDay?,
    vo2: Int?
) {
    SectionTitle("运动状态")
    FlatPanel {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "运动负荷",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            load?.weekLoad?.roundToInt()?.toString() ?: "--",
                            style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace)
                        )
                        Text(
                            " EPOC",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                    Text(
                        load?.let { l ->
                            val value = l.weekLoad
                            val min = l.optimalMin
                            val max = l.optimalMax
                            when {
                                value == null -> "还没有负荷"
                                min != null && value < min -> "偏低"
                                max != null && value > max -> "偏高"
                                min != null -> "合适"
                                else -> "7 天累计"
                            }
                        } ?: "还没有负荷",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "最大摄氧量",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            vo2?.toString() ?: "--",
                            style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace)
                        )
                        Text(
                            " ml/kg",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                    Text(
                        vo2Rating(vo2),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
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
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
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
        Column(Modifier.padding(vertical = 4.dp)) {
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
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace)
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






