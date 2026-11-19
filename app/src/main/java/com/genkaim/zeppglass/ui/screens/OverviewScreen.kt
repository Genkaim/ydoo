package com.genkaim.zeppglass.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.genkaim.zeppglass.data.AppSettings
import com.genkaim.zeppglass.data.DailySummary
import com.genkaim.zeppglass.data.DietStore
import com.genkaim.zeppglass.data.HealthData
import com.genkaim.zeppglass.data.HealthStore
import com.genkaim.zeppglass.data.LoadDay
import com.genkaim.zeppglass.data.SleepNight
import com.genkaim.zeppglass.data.morningScore
import com.genkaim.zeppglass.ui.DetailKind
import com.genkaim.zeppglass.ui.DetailTarget
import com.genkaim.zeppglass.ui.Fmt
import com.genkaim.zeppglass.ui.components.FlatPanel
import com.genkaim.zeppglass.ui.components.LineChart
import com.genkaim.zeppglass.ui.components.MetricTile
import com.genkaim.zeppglass.ui.components.PageHeader
import com.genkaim.zeppglass.ui.components.ProgressRing
import com.genkaim.zeppglass.ui.components.Rule
import com.genkaim.zeppglass.ui.components.SectionTitle
import com.genkaim.zeppglass.ui.components.SleepStageBar
import com.genkaim.zeppglass.ui.components.TypingDots
import com.genkaim.zeppglass.ui.tabs.AppTab
import com.genkaim.zeppglass.ui.theme.LocalZgPalette
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Home. The header keeps the greeting, a day switcher (today by default) and a
 * sliding calendar; everything below is scoped to the selected day. Today's
 * readings obey the three-hour rule, past days are history and never expire.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    settings: AppSettings,
    store: HealthStore,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit,
    onLogin: () -> Unit,
    onOpenTab: (AppTab) -> Unit,
    onOpenDetail: (DetailTarget) -> Unit
) {
    val today = remember { LocalDate.now() }
    var selectedText by rememberSaveable { mutableStateOf(today.toString()) }
    val date = remember(selectedText) {
        runCatching { LocalDate.parse(selectedText) }.getOrDefault(today)
    }
    val isToday = date == today
    var calendarOpen by remember { mutableStateOf(false) }

    val state = store.state
    val raw = state.data
    // A past day is history: it does not go stale. Today's snapshot follows the
    // three-hour rule and falls back to "--" until a fresh sync lands.
    val data = if (isToday) store.displayData else raw
    val zone = remember { ZoneId.systemDefault() }
    fun dateOf(epochMs: Long): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()

    val dayStat = data?.day(date)
    val night = data?.nightOn(date)
    val charge = data?.chargeOn(date)
    val strain = data?.strainOn(date)
    val readiness = data?.readiness?.lastOrNull { it.date == date }
    val stress = data?.stress?.firstOrNull { it.date == date }
    val pai = data?.pai?.firstOrNull { it.date == date }
    val load = data?.load?.firstOrNull { it.date == date }
    val vo2 = dayStat?.vo2Max
        ?: data?.workouts?.firstOrNull { dateOf(it.endEpochSec * 1000) == date && it.vo2Max != null }?.vo2Max
    val weight = data?.weights?.filter { dateOf(it.epochMs) <= date }?.maxByOrNull { it.epochMs }
    val spo2 = data?.spo2?.lastOrNull { dateOf(it.epochMs) == date }
    val morningCharge = charge?.morningScore(night?.endEpochSec)
    // Same number the detail page leads with: average over the sleep window, not
    // the last single sample.
    val nightSpo2 = night?.let { n ->
        val points = data?.spo2.orEmpty().filter { it.epochMs / 1000 in n.startEpochSec..n.endEpochSec }
        if (points.isEmpty()) null else points.map { it.value }.average().roundToInt()
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { DietStore.attach(context) }

    // Workout detail is fetched before the page opens, so the charts are already
    // there on the first frame.
    fun openWorkout(id: String) {
        val workout = data?.workouts?.firstOrNull { it.id == id }
        scope.launch {
            workout?.let { store.loadWorkoutDetail(it) }
            onOpenDetail(DetailTarget(DetailKind.WORKOUT, workoutId = id))
        }
    }
    val dietMeals = DietStore.mealsOn(date)
    val dietKcal = dietMeals.sumOf { it.calories }

    LaunchedEffect(Unit) { DailySummary.attach(settings) }

    // One summary per logical day (05:00 boundary), and only for today.
    LaunchedEffect(raw?.fetchedAt, state.stale, settings.aiApiKey, isToday) {
        if (raw != null && !state.stale && isToday && settings.aiApiKey.isNotBlank()) {
            DailySummary.ensureToday(settings, raw)
        }
    }

    // Header in normal flow, content below it. Measuring the header and offsetting
    // the content by that height made the page drop for a frame once the header's
    // height changed (spinner appearing/disappearing), so the header no longer
    // overlays the list.
    Column(modifier.fillMaxSize()) {
        HomeHeader(
            date = date,
            today = today,
            expanded = calendarOpen,
            loggedIn = settings.isLoggedIn,
            onPrevious = { selectedText = date.minusDays(1).toString() },
            onNext = { if (date < today) selectedText = date.plusDays(1).toString() },
            onToggle = { calendarOpen = !calendarOpen },
            onLogin = onLogin,
            onOpenSettings = onOpenSettings
        )
        AnimatedVisibility(
            visible = calendarOpen,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            MonthCalendar(
                selected = date,
                today = today,
                onPick = {
                    selectedText = it.toString()
                    calendarOpen = false
                }
            )
        }

        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { store.refresh() },
            state = pullState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            // The page never moves with the finger. Pulling only reveals this
            // small dial under the header, and it withdraws the moment the finger
            // lifts; the dial itself reports the request.
            indicator = {
                val fraction = pullState.distanceFraction.coerceIn(0f, 1f)
                when {
                    state.refreshing -> PullDial(
                        progress = null,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                    )
                    fraction > 0.02f -> PullDial(
                        progress = fraction,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                    )
                }
            }
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Column(
                    Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (settings.isLoggedIn && isToday && data != null && state.stale) {
                        StaleBanner(onRefresh = { store.refresh() })
                    }
                    when {
                        !settings.isLoggedIn -> ConnectCard(onLogin)
                        data == null -> EmptyStateCard(
                            hasError = state.error != null,
                            loading = state.loading || state.refreshing,
                            error = state.error,
                            onRetry = { store.refresh() },
                            onLogin = onLogin
                        )
                        else -> {
                            TopRings(
                                sleepScore = night?.score,
                                chargeScore = charge?.total,
                                strain = strain?.strain,
                                onSleep = { onOpenDetail(DetailTarget(DetailKind.SLEEP, date)) },
                                onCharge = { onOpenTab(AppTab.CHARGE) },
                                onStrain = { onOpenDetail(DetailTarget(DetailKind.STRAIN, date)) }
                            )

                            // Same gap under the three rings on every date; the
                            // AI card only exists today, which used to make the
                            // spacing below the rings jump between days.
                            Spacer(Modifier.height(24.dp))

                            if (isToday) {
                                AiSummaryCard(
                                    text = DailySummary.text,
                                    generating = DailySummary.generating,
                                    error = DailySummary.error,
                                    generatedAt = DailySummary.generatedAt,
                                    configured = settings.aiApiKey.isNotBlank(),
                                    stale = state.stale,
                                    onRefresh = { DailySummary.regenerate(settings, raw) },
                                    onOpenSettings = onOpenSettings
                                )
                            }

                            ActivityCard(
                                title = if (isToday) "今日活动" else "${Fmt.date(date)} 活动",
                                steps = dayStat?.steps,
                                goal = dayStat?.stepGoal ?: 7000,
                                distance = dayStat?.distanceM,
                                calories = dayStat?.calories,
                                activeMinutes = dayStat?.activityMinutes,
                                activeGoal = dayStat?.activityGoal,
                                onClick = { onOpenTab(AppTab.ACTIVITY) }
                            )

                            DietCard(
                                title = if (isToday) "今日饮食" else "${Fmt.date(date)} 饮食",
                                target = DietStore.calorieTarget(raw, date, settings.dietGoalOrCut),
                                intake = dietKcal,
                                meals = dietMeals.size,
                                onClick = { onOpenDetail(DetailTarget(DetailKind.DIET, date)) }
                            )

                            val ink = MaterialTheme.colorScheme.onSurface
                            val warnTone = LocalZgPalette.current.warn
                            val criticalTone = LocalZgPalette.current.critical
                            fun scoreVerdict(score: Int?): Pair<String, Color>? = when {
                                score == null -> null
                                score >= 75 -> "正常" to ink
                                score >= 50 -> "一般" to warnTone
                                else -> "偏低" to criticalTone
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                MetricTile(
                                    label = "晨起 Charge",
                                    value = morningCharge?.toString() ?: "--",
                                    unit = null,
                                    icon = Icons.Rounded.Bolt,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f),
                                    verdict = morningCharge?.let {
                                        when {
                                            it >= 80 -> "优秀" to ink
                                            it >= 60 -> "偏低" to warnTone
                                            else -> "偏低" to criticalTone
                                        }
                                    },
                                    onClick = { onOpenDetail(DetailTarget(DetailKind.MORNING_CHARGE, date)) }
                                )
                                MetricTile(
                                    label = "静息心率",
                                    value = readiness?.sleepRhr?.toString() ?: "--",
                                    unit = "bpm",
                                    icon = Icons.Rounded.Favorite,
                                    tint = LocalZgPalette.current.sleepRem,
                                    modifier = Modifier.weight(1f),
                                    verdict = scoreVerdict(readiness?.rhrScore),
                                    onClick = { onOpenDetail(DetailTarget(DetailKind.RHR, date)) }
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                MetricTile(
                                    label = "PAI",
                                    value = pai?.total?.let { Fmt.oneDecimal(it) } ?: "--",
                                    unit = null,
                                    icon = Icons.Rounded.ShowChart,
                                    tint = LocalZgPalette.current.accent,
                                    modifier = Modifier.weight(1f),
                                    verdict = pai?.total?.let {
                                        if (it >= 100.0) "达标" to ink else "未达标" to ink
                                    },
                                    onClick = { onOpenDetail(DetailTarget(DetailKind.PAI, date)) }
                                )
                                MetricTile(
                                    label = "HRV",
                                    value = readiness?.sleepHrv?.toString() ?: "--",
                                    unit = "ms",
                                    icon = Icons.Rounded.MonitorHeart,
                                    modifier = Modifier.weight(1f),
                                    verdict = readiness?.hrvScore?.let {
                                        when {
                                            it >= 80 -> "优秀" to ink
                                            it >= 60 -> "良好" to ink
                                            it >= 40 -> "一般" to warnTone
                                            else -> "偏低" to criticalTone
                                        }
                                    },
                                    onClick = { onOpenDetail(DetailTarget(DetailKind.HRV, date)) }
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                val stressNow = stress?.curve?.lastOrNull()?.second
                                MetricTile(
                                    label = "压力",
                                    value = stressNow?.toString() ?: "--",
                                    unit = null,
                                    icon = Icons.Rounded.SelfImprovement,
                                    tint = LocalZgPalette.current.sleepAwake,
                                    modifier = Modifier.weight(1f),
                                    verdict = stressNow?.let {
                                        when {
                                            it < 40 -> "放松" to ink
                                            it < 60 -> "正常" to ink
                                            it < 80 -> "偏高" to warnTone
                                            else -> "很高" to criticalTone
                                        }
                                    },
                                    onClick = { onOpenDetail(DetailTarget(DetailKind.STRESS, date)) }
                                )
                                MetricTile(
                                    label = "皮肤温度",
                                    value = readiness?.skinTempScore?.toString() ?: "--",
                                    unit = readiness?.skinTempScore?.let { "分" },
                                    icon = Icons.Rounded.Thermostat,
                                    tint = LocalZgPalette.current.warn,
                                    modifier = Modifier.weight(1f),
                                    verdict = scoreVerdict(readiness?.skinTempScore),
                                    onClick = { onOpenDetail(DetailTarget(DetailKind.SKIN_TEMP, date)) }
                                )
                            }

                            LastNightCard(
                                night = night,
                                onClick = { onOpenDetail(DetailTarget(DetailKind.SLEEP, date)) }
                            )

                            ChargeCurveCard(
                                charge = charge,
                                onClick = { onOpenTab(AppTab.CHARGE) }
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                MetricTile(
                                    label = "准备度",
                                    value = readiness?.officialScore?.toString() ?: "--",
                                    unit = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f),
                                    verdict = scoreVerdict(readiness?.officialScore),
                                    onClick = { onOpenDetail(DetailTarget(DetailKind.READINESS, date)) }
                                )
                                MetricTile(
                                    label = "血氧",
                                    value = (nightSpo2 ?: spo2?.value)?.toString() ?: "--",
                                    unit = "%",
                                    modifier = Modifier.weight(1f),
                                    verdict = (nightSpo2 ?: spo2?.value)?.let {
                                        when {
                                            it >= 95 -> "正常" to ink
                                            it >= 90 -> "偏低" to warnTone
                                            else -> "低" to criticalTone
                                        }
                                    },
                                    onClick = { onOpenDetail(DetailTarget(DetailKind.SPO2, date)) }
                                )
                            }

                            RecentWorkouts(
                                data = data,
                                date = date,
                                today = today,
                                onOpen = ::openWorkout
                            )

                            if (isToday) {
                                state.error?.let { message ->
                                    FlatPanel {
                                        Column(Modifier.padding(16.dp)) {
                                            Text("这次同步没有完成", style = MaterialTheme.typography.titleSmall)
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                message,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            TextButton(onClick = { store.refresh() }) { Text("重试") }
                                        }
                                    }
                                }
                                val warnings = raw?.warnings.orEmpty()
                                if (state.error.isNullOrBlank() && warnings.isNotEmpty()) {
                                    Text(
                                        "部分数据没同步上：" + warnings.take(2).joinToString("；"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                Text(
                                    "这是历史记录，不受 3 小时时效限制；没有采样的项目显示 --。",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(104.dp))
                }
            }
        }
    }
}

/**
 * Small dial that hangs under the header while the page is being pulled. It has
 * its own surface so it stays readable over the rings behind it.
 */
@Composable
private fun PullDial(progress: Float?, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(32.dp)
            .background(MaterialTheme.colorScheme.surface, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (progress == null) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp
            )
        } else {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f + 0.55f * progress),
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            )
        }
    }
}

/**
 * Home header: no title. The day switcher takes its place and is centred on
 * the screen, with the connect entry and settings on the right.
 */
@Composable
private fun HomeHeader(
    date: LocalDate,
    today: LocalDate,
    expanded: Boolean,
    loggedIn: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggle: () -> Unit,
    onLogin: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 4.dp)
    ) {
        Row(
            Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrevious) {
                Icon(Icons.Rounded.ChevronLeft, contentDescription = "前一天", modifier = Modifier.size(22.dp))
            }
            Surface(
                onClick = onToggle,
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Row(
                    Modifier.padding(start = 16.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (date == today) "今天"
                        else "${Fmt.date(date)} · ${Fmt.weekday(date)}",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "选择日期",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            IconButton(onClick = onNext, enabled = date < today) {
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = "后一天",
                    modifier = Modifier.size(22.dp),
                    tint = if (date < today) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
            }
        }

        Row(
            Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!loggedIn) {
                Surface(
                    onClick = onLogin,
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Text(
                        "连接",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
                Spacer(Modifier.width(2.dp))
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = "设置", modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Compact month grid that slides down under the header. */
@Composable
private fun MonthCalendar(
    selected: LocalDate,
    today: LocalDate,
    onPick: (LocalDate) -> Unit
) {
    var month by remember(selected) { mutableStateOf(selected.withDayOfMonth(1)) }
    val first = month
    val offset = first.dayOfWeek.value - 1
    val cells: List<LocalDate?> = buildList {
        repeat(offset) { add(null) }
        for (day in 1..first.lengthOfMonth()) add(first.withDayOfMonth(day))
    }
    val rows = cells.chunked(7)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { month = month.minusMonths(1) }) {
                Icon(Icons.Rounded.ChevronLeft, contentDescription = "上个月", modifier = Modifier.size(18.dp))
            }
            Text(
                "${month.year} 年 ${month.monthValue} 月",
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { month = month.plusMonths(1) },
                enabled = month < today.withDayOfMonth(1)
            ) {
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = "下个月",
                    modifier = Modifier.size(18.dp),
                    tint = if (month < today.withDayOfMonth(1)) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                for (index in 0 until 7) {
                    val day = row.getOrNull(index)
                    Box(
                        Modifier
                            .weight(1f)
                            .padding(2.dp)
                            .aspectRatio(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        if (day == null) return@Box
                        val future = day.isAfter(today)
                        val isSelected = day == selected
                        val isToday = day == today
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isSelected -> MaterialTheme.colorScheme.primary
                                        isToday -> MaterialTheme.colorScheme.surfaceVariant
                                        else -> Color.Transparent
                                    }
                                )
                                .clickable(enabled = !future) { onPick(day) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                day.dayOfMonth.toString(),
                                style = MaterialTheme.typography.labelLarge,
                                color = when {
                                    isSelected -> MaterialTheme.colorScheme.onPrimary
                                    future -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun ConnectCard(onLogin: () -> Unit) {
    FlatPanel {
        Column(Modifier.padding(18.dp)) {
            Text("连接你的 Zepp 账号", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "用 Zepp 邮箱或手机号登录一次，之后就能看到步数、准备度、Charge 与睡眠。数据只保存在这台手机上。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            androidx.compose.material3.Button(onClick = onLogin) {
                Text("连接 Zepp")
            }
        }
    }
}

/**
 * Shown only when there is no snapshot at all. Syncing runs in the background,
 * so this is an empty state, not a loading dialog.
 */
@Composable
private fun EmptyStateCard(
    hasError: Boolean,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onLogin: () -> Unit
) {
    FlatPanel {
        Column(Modifier.padding(18.dp)) {
            Text(
                when {
                    // A first sync is not "no data": saying so sent readers to
                    // the retry button while the request was still running.
                    loading && !hasError -> "正在同步数据"
                    hasError -> "数据没能加载"
                    else -> "还没有同步到数据"
                },
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    loading && !hasError ->
                        "第一次同步要拉 35 天的手环明细，通常几秒；完成后这里会自动更新。"
                    else -> error
                        ?: "同步在后台进行，完成后这里会自动更新；右上角的小圈表示正在刷新。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row {
                // Nothing to retry while the first answer is on its way.
                if (!loading || hasError) {
                    TextButton(onClick = onRetry) { Text("重试") }
                }
                TextButton(onClick = onLogin) { Text("重新连接") }
            }
        }
    }
}

/** Tells the reader why every number is `--` and offers the fix. */
@Composable
private fun StaleBanner(onRefresh: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.Schedule,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "数据超过 3 小时没更新，数值先显示 --",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onRefresh) { Text("刷新") }
    }
}

/**
 * Daily AI paragraph under the rings: generated once per 05:00-based day, with
 * a manual refresh, an error retry and an animated "still writing" state.
 */
@Composable
private fun AiSummaryCard(
    text: String,
    generating: Boolean,
    error: String?,
    generatedAt: Long,
    configured: Boolean,
    stale: Boolean,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit
) {
    FlatPanel(Modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("AI 摘要", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (generating) {
                    TypingDots(Modifier.padding(end = 8.dp))
                } else if (generatedAt > 0 && error == null) {
                    Text(
                        Fmt.time(generatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
                // A 24dp tap box keeps the header row the same height as its
                // text, so the card's top padding matches the side padding.
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable(enabled = configured && !stale) { onRefresh() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = "重新生成摘要",
                        modifier = Modifier.size(17.dp),
                        tint = if (configured && !stale) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }
            if (text.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium)
            } else if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                if (!configured) {
                    TextButton(onClick = onOpenSettings) { Text("去设置") }
                }
            } else if (!generating) {
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        !configured -> "配置 AI 接入点后，每天 5 点后打开 App 会自动生成一条摘要。"
                        stale -> "等这次同步完成后再生成今天的摘要。"
                        else -> "今天的摘要还没生成，点右上角刷新可以马上生成。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Three rings with the score inside: sleep, Hybrid Charge and Strain (0–21).
 * Stress and training load live in the tiles below.
 */
@Composable
private fun TopRings(
    sleepScore: Int?,
    chargeScore: Int?,
    strain: Double?,
    onSleep: () -> Unit,
    onCharge: () -> Unit,
    onStrain: () -> Unit
) {
    val palette = LocalZgPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        RingCell(
            modifier = Modifier.weight(1f),
            label = "睡眠",
            value = sleepScore?.toString() ?: "--",
            progress = (sleepScore ?: 0) / 100f,
            // A poor night turns amber, a clearly bad one turns red: the ring
            // itself says how the night went, not only the number inside it.
            tint = scoreTint(sleepScore, palette.sleepLight, palette.warn, palette.critical),
            onClick = onSleep
        )
        RingCell(
            modifier = Modifier.weight(1f),
            label = "Charge",
            value = chargeScore?.toString() ?: "--",
            progress = (chargeScore ?: 0) / 100f,
            tint = chargeTint(chargeScore),
            size = 104.dp,
            onClick = onCharge
        )
        StrainRingCell(
            modifier = Modifier.weight(1f),
            value = strain,
            onClick = onStrain
        )
    }
}

/**
 * Score bands for the two 0–100 rings. Under 60 is the red band, 60–74 the
 * amber one; above that the ring keeps its own colour.
 */
private fun scoreTint(score: Int?, base: Color, warn: Color, critical: Color): Color = when {
    score == null -> base
    score < 60 -> critical
    score < 75 -> warn
    else -> base
}

/** Charge turns red at 5 or below and amber below 50; from 50 up it is primary. */
@Composable
private fun chargeTint(score: Int?): Color {
    val palette = LocalZgPalette.current
    return when {
        score == null -> MaterialTheme.colorScheme.primary
        score <= 5 -> palette.critical
        score < 50 -> palette.warn
        else -> MaterialTheme.colorScheme.primary
    }
}

/** 15 of 21 is the watch's "high load" threshold; the ring marks it. */
private const val STRAIN_HIGH = 15f
private const val STRAIN_MAX = 21f

/**
 * The strain ring, drawn as two coloured segments: everything up to 15 in the
 * normal accent, the part above 15 in red, with a horizontal tick sitting on
 * the ring exactly at 15 so the threshold is visible before it is crossed.
 */
@Composable
private fun StrainRingCell(
    modifier: Modifier,
    value: Double?,
    onClick: () -> Unit
) {
    val palette = LocalZgPalette.current
    val normal = MaterialTheme.colorScheme.tertiary
    val progress = ((value ?: 0.0) / STRAIN_MAX).toFloat()
    val animated = remember { Animatable(0f) }
    LaunchedEffect(progress) { animated.animateTo(progress.coerceIn(0f, 1f), tween(700)) }

    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = value != null, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.padding(start = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "负荷",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(13.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 88.dp.toPx() * 0.095f
                val inset = stroke / 2
                val topLeft = Offset(inset, inset)
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val strokeStyle = Stroke(stroke, cap = StrokeCap.Round)
                drawArc(
                    color = normal.copy(alpha = 0.16f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = strokeStyle
                )
                val filled = 360f * animated.value
                val thresholdSweep = 360f * (STRAIN_HIGH / STRAIN_MAX)
                // Below the threshold, then above it: the two segments keep
                // their own colour instead of the whole ring turning red.
                val lowSweep = minOf(filled, thresholdSweep)
                if (lowSweep > 0f) {
                    drawArc(
                        color = normal,
                        startAngle = -90f,
                        sweepAngle = lowSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = strokeStyle
                    )
                }
                if (filled > thresholdSweep) {
                    drawArc(
                        color = palette.critical,
                        startAngle = -90f + thresholdSweep,
                        sweepAngle = filled - thresholdSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = strokeStyle
                    )
                }
            }
            Text(
                value?.let { String.format("%.1f", it) } ?: "--",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun RingCell(
    modifier: Modifier,
    label: String,
    value: String,
    progress: Float,
    tint: Color,
    size: Dp = 88.dp,
    onClick: () -> Unit
) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            // Same rule as the tiles: no reading, no page to open.
            .clickable(enabled = value != "--", onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            // The chevron sits to the right of the word; the start padding
            // balances it so the label is optically centred over the ring.
            modifier = Modifier.padding(start = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(13.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        ProgressRing(
            progress.coerceIn(0f, 1f),
            Modifier.size(size),
            // Thicker than the old 6dp: the number inside carries the reading,
            // and a thin arc read as a hairline next to the cards below.
            strokeWidth = size * 0.095f,
            color = tint,
            trackColor = tint.copy(alpha = 0.16f)
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/** Today's diet as a compact entry point: photo → AI estimate → score. */
@Composable
private fun DietCard(
    title: String,
    target: Int?,
    intake: Int,
    meals: Int,
    onClick: () -> Unit
) {
    FlatPanel(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        onClick = onClick
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(LocalZgPalette.current.accentSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Restaurant,
                    contentDescription = null,
                    tint = LocalZgPalette.current.accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    "已摄入 $intake kcal",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    target?.let { (it - intake).coerceAtLeast(0).toString() } ?: "--",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    "kcal 今日剩余",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Today's (or the selected day's) activity as a ring. */
@Composable
private fun ActivityCard(
    title: String,
    steps: Int?,
    goal: Int,
    distance: Double?,
    calories: Double?,
    activeMinutes: Int?,
    activeGoal: Int?,
    onClick: () -> Unit
) {
    FlatPanel(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        onClick = onClick
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        steps?.toString() ?: "--",
                        style = MaterialTheme.typography.displaySmall
                    )
                    Text(
                        " / $goal 步",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 5.dp, start = 4.dp)
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    ActivityStat("${Fmt.km(distance)} km", "距离")
                    ActivityStat("${Fmt.int(calories)} kcal", "消耗")
                    ActivityStat(
                        activeMinutes?.let { m ->
                            if (activeGoal != null) "$m/$activeGoal" else "$m"
                        } ?: "--",
                        "活动时长"
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            ActivityRings(
                stepsProgress = if (goal > 0) (steps ?: 0).toFloat() / goal else 0f,
                caloriesProgress = ((calories ?: 0.0) / 500.0).toFloat(),
                activeProgress = ((activeMinutes ?: 0).toFloat() /
                    (activeGoal?.takeIf { it > 0 } ?: 60).toFloat()),
                modifier = Modifier.size(92.dp)
            )
        }
    }
}

@Composable
private fun ActivityStat(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.labelLarge)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ActivityRings(
    stepsProgress: Float,
    caloriesProgress: Float,
    activeProgress: Float,
    modifier: Modifier = Modifier
) {
    val palette = LocalZgPalette.current
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        palette.sleepRem
    )
    val values = listOf(stepsProgress, caloriesProgress, activeProgress)
        .map { it.coerceIn(0f, 1f) }
    Box(modifier) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val stroke = 5.dp.toPx()
            values.forEachIndexed { index, value ->
                val inset = stroke / 2 + index * (stroke + 3.dp.toPx())
                drawArc(
                    color = colors[index].copy(alpha = 0.14f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(
                        size.width - inset * 2,
                        size.height - inset * 2
                    ),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
                drawArc(
                    color = colors[index],
                    startAngle = -90f,
                    sweepAngle = 360f * value,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(
                        size.width - inset * 2,
                        size.height - inset * 2
                    ),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
            }
        }
    }
}

@Composable
private fun LastNightCard(night: SleepNight?, onClick: () -> Unit) {
    if (night == null) return
    FlatPanel(onClick = onClick) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Bedtime,
                    contentDescription = null,
                    tint = LocalZgPalette.current.sleepLight,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.size(6.dp))
                Text("这一晚睡眠", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    "${Fmt.hoursMinutes(night.totalMinutes)} · ${night.score?.let { "$it 分" } ?: "--"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            SleepStageBar(
                night,
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
            )
            val nap = night.napMinutes
            if (nap != null) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Bedtime,
                        contentDescription = null,
                        tint = LocalZgPalette.current.sleepLight,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "小睡 ${Fmt.hoursMinutes(nap)}",
                        style = MaterialTheme.typography.labelMedium
                    )
                    if (night.napStartEpochSec != null && night.napEndEpochSec != null) {
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${Fmt.timeSec(night.napStartEpochSec)}–${Fmt.timeSec(night.napEndEpochSec)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChargeCurveCard(charge: com.genkaim.zeppglass.data.ChargeDay?, onClick: () -> Unit) {
    if (charge == null || charge.samples.isEmpty()) return
    val samples = charge.samples.filter { it.curveValue != null }
    if (samples.size < 2) return
    FlatPanel(onClick = onClick) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("身体电量", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    "体力 ${charge.physical?.roundToInt() ?: "--"} · 精神 ${charge.mental?.roundToInt() ?: "--"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            LineChart(
                samples.map { it.curveValue!!.toFloat() },
                Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                color = MaterialTheme.colorScheme.primary,
                minValue = 0f,
                maxValue = 100f,
                showGrid = false
            )
        }
    }
}

@Composable
private fun RecentWorkouts(
    data: HealthData?,
    date: LocalDate,
    today: LocalDate,
    onOpen: (String) -> Unit
) {
    val zone = remember { ZoneId.systemDefault() }
    val workouts = data?.workouts.orEmpty()
        .filter { workout ->
            if (date == today) true
            else Instant.ofEpochSecond(workout.endEpochSec).atZone(zone).toLocalDate() == date
        }
        .take(3)
    if (workouts.isEmpty()) return
    SectionTitle(if (date == today) "最近运动" else "${Fmt.date(date)} 运动")
    FlatPanel {
        Column(Modifier.padding(vertical = 4.dp)) {
            workouts.forEachIndexed { index, workout ->
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
                    Spacer(Modifier.size(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(workout.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${Fmt.dayAndTime(workout.endEpochSec * 1000)} · ${Fmt.duration(workout.durationSec)}" +
                                if (workout.distanceM > 100) " · ${Fmt.km(workout.distanceM)} km" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
                if (index != workouts.lastIndex) Rule(Modifier.padding(start = 46.dp, end = 16.dp))
            }
        }
    }
}










