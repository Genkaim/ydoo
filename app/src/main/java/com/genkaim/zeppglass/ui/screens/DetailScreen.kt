package com.genkaim.zeppglass.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.TrendingFlat
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.genkaim.zeppglass.BuildConfig
import com.genkaim.zeppglass.data.AppSettings
import com.genkaim.zeppglass.data.ChargeDay
import com.genkaim.zeppglass.data.DietStore
import com.genkaim.zeppglass.data.GeoPoint
import com.genkaim.zeppglass.data.HealthData
import com.genkaim.zeppglass.data.HealthStore
import com.genkaim.zeppglass.data.bodyCompositionScore
import com.genkaim.zeppglass.data.deurenbergAge
import com.genkaim.zeppglass.data.mifflinBmr
import com.genkaim.zeppglass.data.resolveBodySource
import com.genkaim.zeppglass.data.PaiDay
import com.genkaim.zeppglass.data.morningScore
import com.genkaim.zeppglass.data.ReadinessDay
import com.genkaim.zeppglass.data.SleepNight
import com.genkaim.zeppglass.data.SleepStageSlice
import com.genkaim.zeppglass.data.Spo2Point
import com.genkaim.zeppglass.data.StressDay
import com.genkaim.zeppglass.data.band
import com.genkaim.zeppglass.data.mentalBand
import com.genkaim.zeppglass.data.WeightPoint
import com.genkaim.zeppglass.data.BloodPressure
import com.genkaim.zeppglass.data.BloodPressureBand
import com.genkaim.zeppglass.data.Workout
import com.genkaim.zeppglass.data.WorkoutDetail
import com.genkaim.zeppglass.ui.DetailKind
import com.genkaim.zeppglass.ui.DetailTarget
import com.genkaim.zeppglass.ui.Fmt
import com.genkaim.zeppglass.ui.components.AxisBarChart
import com.genkaim.zeppglass.ui.components.AxisLineChart
import com.genkaim.zeppglass.ui.components.BaselineScaleBar
import com.genkaim.zeppglass.ui.components.BodySourceToggle
import com.genkaim.zeppglass.ui.components.BarChart
import com.genkaim.zeppglass.ui.components.DataRow
import com.genkaim.zeppglass.ui.components.FlatPanel
import com.genkaim.zeppglass.ui.components.HeartRateRed
import com.genkaim.zeppglass.ui.components.HrvDayChart
import com.genkaim.zeppglass.ui.components.LegendDot
import com.genkaim.zeppglass.ui.components.LineChart
import com.genkaim.zeppglass.ui.components.MetricTile
import com.genkaim.zeppglass.ui.components.PaiHowToScale
import com.genkaim.zeppglass.ui.components.PaiHrZoneChart
import com.genkaim.zeppglass.ui.components.PaiOctagon
import com.genkaim.zeppglass.ui.components.PaiWeekBars
import com.genkaim.zeppglass.ui.components.PinnedPageHeader
import com.genkaim.zeppglass.ui.components.ProgressRing
import com.genkaim.zeppglass.ui.components.RoutePath
import com.genkaim.zeppglass.ui.components.RoutePlaceholder
import com.genkaim.zeppglass.ui.components.Rule
import com.genkaim.zeppglass.ui.components.ScaleBand
import com.genkaim.zeppglass.ui.components.ScaleBar
import com.genkaim.zeppglass.ui.components.SignedBarChart
import com.genkaim.zeppglass.ui.components.SectionTitle
import com.genkaim.zeppglass.ui.components.SleepStageBar
import com.genkaim.zeppglass.ui.components.SleepStageChart
import com.genkaim.zeppglass.ui.components.SleepWindowChart
import com.genkaim.zeppglass.ui.components.StatBlock
import com.genkaim.zeppglass.ui.components.StressBandChart
import com.genkaim.zeppglass.ui.components.StressDonut
import com.genkaim.zeppglass.ui.components.StressHigh
import com.genkaim.zeppglass.ui.components.StressMedium
import com.genkaim.zeppglass.ui.components.StressNormal
import com.genkaim.zeppglass.ui.components.StressRelaxed
import com.genkaim.zeppglass.ui.components.TrendLineChart
import com.genkaim.zeppglass.ui.components.WORKOUT_FADE_MS
import com.genkaim.zeppglass.ui.components.sleepStageLabel
import com.genkaim.zeppglass.ui.theme.LocalZgPalette
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * One scrollable detail page per metric family. Every card on the home and
 * Charge tabs lands here, so no number is a dead end.
 */
@Composable
fun DetailScreen(
    settings: AppSettings,
    store: HealthStore,
    target: DetailTarget,
    onClose: () -> Unit,
    onOpen: (DetailTarget) -> Unit = {},
    shoot: Boolean = false,
    photoPath: String? = null
) {
    val data = store.displayData
    val bodySource = settings.resolveBodySource(data?.weights.orEmpty())
    val palette = LocalZgPalette.current
    var headerHeight by remember { mutableStateOf(0.dp) }
    // A workout header is just the title row (no subtitle), so its height is
    // known before layout: 48 dp icon button plus 8/6 dp padding. Measuring it
    // instead would seat the track and the first card 4 dp from the top for one
    // frame and then push them down 62 dp — the height jump on open.
    val headerSlot = if (target.kind == DetailKind.WORKOUT) 62.dp else headerHeight
    // The whole workout page rises into place in one quick fade, so the cards and
    // the track read as arriving together rather than snapping in one by one.
    val reveal = remember { Animatable(if (target.kind == DetailKind.WORKOUT) 0f else 1f) }
    LaunchedEffect(target.kind) {
        if (target.kind == DetailKind.WORKOUT) {
            // Start on the first frame that is actually drawn. On a cold open the
            // composition runs well before the window shows, so a fade begun
            // there is already over by the time the page becomes visible.
            withFrameNanos { }
            reveal.animateTo(1f, tween(WORKOUT_FADE_MS))
        }
    }

    // The workout detail is loaded here, not inside the page content: the track
    // is pinned above the scrolling column, and a payload that arrives after the
    // first frame must not push the page around.
    val workout = if (target.kind == DetailKind.WORKOUT) {
        data?.workouts?.firstOrNull { it.id == target.workoutId }
            ?: data?.workouts?.firstOrNull()
    } else {
        null
    }
    var workoutDetail by remember(workout?.id) { mutableStateOf<WorkoutDetail?>(null) }
    LaunchedEffect(workout?.id) {
        workoutDetail = workout?.let { store.loadWorkoutDetail(it) }
    }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        val scrollState = rememberScrollState()
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(start = 18.dp, end = 18.dp, bottom = 24.dp + navBottom)
                .graphicsLayer { alpha = reveal.value },
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(headerSlot + 4.dp))
            when (target.kind) {
                DetailKind.SLEEP -> SleepDetail(data, target.date, onOpen)
                DetailKind.CHARGE -> ChargeDetail(data, target.date)
                DetailKind.STRESS -> StressDetail(data, target.date)
                DetailKind.READINESS -> ReadinessDetail(data, target.date)
                DetailKind.ACTIVITY -> ActivityDetail(data, target.date ?: LocalDate.now())
                DetailKind.LOAD -> LoadDetail(data, target.date)
                DetailKind.PAI -> PaiDetail(data, target.date)
                DetailKind.WEIGHT -> WeightDetail(data, target.date, bodySource)
                DetailKind.SPO2 -> NightSpo2Detail(data, target.date, withDayStats = true)
                DetailKind.WORKOUT -> WorkoutDetail(
                    workout = workout,
                    detail = workoutDetail,
                    // Before the payload lands, go by the summary: a session with
                    // distance has a track, so its slot is held from frame one.
                    showTrack = if (workoutDetail == null) {
                        (workout?.distanceM ?: 0.0) > 0.0
                    } else {
                        (workoutDetail?.points?.size ?: 0) >= 2
                    },
                    onOpenMore = {
                        onOpen(DetailTarget(DetailKind.WORKOUT_MORE, workoutId = target.workoutId))
                    }
                )
                DetailKind.STRAIN -> StrainDetail(data, target.date)
                DetailKind.VO2 -> Vo2Detail(data)
                DetailKind.RHR -> RhrDetail(data, target.date)
                DetailKind.HRV -> HrvDetail(data, target.date)
                DetailKind.DIET -> DietDetail(
                    data,
                    settings,
                    target.date ?: DietStore.logicalToday(),
                    shoot = shoot,
                    pendingPhoto = photoPath
                )
                DetailKind.SLEEP_HR -> SleepMetricDetail(data, target.date, SleepMetric.HEART_RATE)
                DetailKind.SLEEP_RR -> SleepMetricDetail(data, target.date, SleepMetric.RESPIRATION)
                DetailKind.SLEEP_APNEA -> SleepMetricDetail(data, target.date, SleepMetric.APNEA)
                DetailKind.SKIN_TEMP -> SkinTempDetail(data, target.date)
                DetailKind.MORNING_CHARGE -> MorningChargeDetail(data, target.date)
                DetailKind.NIGHT_SPO2 -> NightSpo2Detail(data, target.date)
                DetailKind.BLOOD_PRESSURE -> BloodPressureDetail(data, target.date)
                DetailKind.WORKOUT_MORE -> WorkoutMoreDetail(data, target.workoutId)
            }
        }

        // The track sits outside the scrolling column: the cards slide up under
        // it while it shrinks and fades, so it never travels with the document.
        val trackPoints = workoutDetail?.points.orEmpty()
        if (target.kind == DetailKind.WORKOUT && trackPoints.size >= 2) {
            PinnedTrack(
                points = trackPoints,
                scroll = scrollState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(start = 18.dp, end = 18.dp, top = headerSlot + 4.dp)
            )
        }

        // Pinned header: opaque behind the title, the page colour fades out
        // below it so the cards scroll underneath instead of meeting a hard line.
        // The body page's date follows the chosen source: it is the day of that
        // source's newest reading, not necessarily the day the page was opened.
        val subtitleText = if (target.kind == DetailKind.WEIGHT) {
            val zone = ZoneId.systemDefault()
            val latest = data?.weights.orEmpty()
                .filter {
                    if (bodySource == "watch") it.weightType != 0
                    else it.weightType == 0
                }
                .maxByOrNull { it.epochMs }
            latest?.let { Fmt.date(Instant.ofEpochMilli(it.epochMs).atZone(zone).toLocalDate()) }
                ?: subtitle(target, data)
        } else {
            subtitle(target, data)
        }
        PinnedPageHeader(
            modifier = Modifier.align(Alignment.TopCenter),
            onHeight = { headerHeight = it }
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
                }
                Column(Modifier.weight(1f)) {
                    Text(title(target), style = MaterialTheme.typography.titleLarge)
                    // Workout pages carry their own numbers; a subtitle there just
                    // crowded the header.
                    if (target.kind != DetailKind.WORKOUT && subtitleText.isNotBlank()) {
                        Text(
                            subtitleText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // The body page's source switch lives in the header, on the right.
                if (target.kind == DetailKind.WEIGHT) {
                    BodySourceToggle(
                        source = bodySource,
                        onChange = { settings.updateBodySource(it) },
                        modifier = Modifier.padding(end = 20.dp)
                    )
                }
            }
        }
    }
}

private fun title(target: DetailTarget): String = when (target.kind) {
    DetailKind.SLEEP -> "睡眠"
    DetailKind.CHARGE -> "Hybrid Charge"
    DetailKind.STRESS -> "压力"
    DetailKind.READINESS -> "准备度"
    DetailKind.ACTIVITY -> "活动"
    DetailKind.LOAD -> "训练负荷"
    DetailKind.PAI -> "PAI"
    DetailKind.WEIGHT -> "身体成分"
    DetailKind.SPO2 -> "血氧"
    DetailKind.WORKOUT -> "运动"
    DetailKind.STRAIN -> "负荷"
    DetailKind.VO2 -> "最大摄氧量"
    DetailKind.RHR -> "静息心率"
    DetailKind.HRV -> "心率变异性"
    DetailKind.DIET -> "饮食"
    DetailKind.SLEEP_HR -> "睡眠心率"
    DetailKind.SLEEP_RR -> "呼吸率"
    DetailKind.SLEEP_APNEA -> "低通气"
    DetailKind.SKIN_TEMP -> "皮肤温度"
    DetailKind.MORNING_CHARGE -> "晨起 Charge"
    DetailKind.NIGHT_SPO2 -> "夜间血氧"
    DetailKind.BLOOD_PRESSURE -> "血压"
    DetailKind.WORKOUT_MORE -> "其他数据"
}

private fun subtitle(target: DetailTarget, data: HealthData?): String = when (target.kind) {
    DetailKind.SLEEP -> target.date?.let(Fmt::date) ?: "最近一晚"
    DetailKind.CHARGE -> target.date?.let(Fmt::date) ?: "最近一天"
    DetailKind.STRESS -> target.date?.let(Fmt::date) ?: "最近一天"
    DetailKind.READINESS -> target.date?.let(Fmt::date) ?: "手表评分，满分 100"
    DetailKind.ACTIVITY -> target.date?.let(Fmt::date) ?: "今天"
    DetailKind.LOAD -> target.date?.let(Fmt::date) ?: "7 天累计负荷与个人合适区间"
    DetailKind.PAI -> target.date?.let(Fmt::date) ?: "个人活动强度积分"
    DetailKind.WEIGHT -> target.date?.let(Fmt::date) ?: "来自体脂秤与手动记录"
    DetailKind.SPO2 -> target.date?.let(Fmt::date) ?: "最近读数"
    DetailKind.WORKOUT -> "运动摘要与 GPS 轨迹"
    DetailKind.STRAIN -> target.date?.let(Fmt::date) ?: "心率、脑力与恢复合成的每日负荷，0–21"
    DetailKind.VO2 -> "手表测算，单位 ml/kg/min"
    DetailKind.RHR -> target.date?.let(Fmt::date) ?: "睡眠静息心率"
    DetailKind.HRV -> target.date?.let(Fmt::date) ?: "全天 rMSSD"
    DetailKind.DIET -> ""
    DetailKind.SLEEP_HR -> target.date?.let(Fmt::date) ?: "最近一晚的睡眠心率，近 7 天趋势"
    DetailKind.SLEEP_RR -> target.date?.let(Fmt::date) ?: "每夜平均呼吸率，近 7 天趋势"
    DetailKind.SLEEP_APNEA -> target.date?.let(Fmt::date) ?: "夜间低通气指数，近 7 天趋势"
    DetailKind.SKIN_TEMP -> target.date?.let(Fmt::date) ?: "手表评分，近 7 天趋势"
    DetailKind.MORNING_CHARGE -> target.date?.let(Fmt::date) ?: "起床时的能量，近 7 天趋势"
    DetailKind.NIGHT_SPO2 -> target.date?.let(Fmt::date) ?: "每夜平均血氧，近 7 天趋势"
    DetailKind.BLOOD_PRESSURE -> target.date?.let(Fmt::date) ?: "收缩压 / 舒张压，mmHg"
    DetailKind.WORKOUT_MORE -> ""
}

// ---------------- sleep ----------------

@Composable
private fun SleepDetail(data: HealthData?, date: LocalDate?, onOpen: (DetailTarget) -> Unit) {
    val palette = LocalZgPalette.current
    val night = (date?.let { data?.nightOn(it) } ?: data?.latestNight())
    if (night == null) {
        Missing("还没有这一晚的睡眠记录")
        return
    }
    val readiness = data?.readiness?.firstOrNull { it.date == night.date }
    var picked by remember(night.date) { mutableStateOf<SleepStageSlice?>(null) }
    var pickedNap by remember(night.date) { mutableStateOf<SleepStageSlice?>(null) }

    // Score, night shape and timeline read as one block: the gap belongs above
    // the first card, while the two cards stay close to each other.
    Column(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Same ring component as Strain and the home rings: big number, then
            // "名称 / 满分" underneath, so the two detail pages read alike.
            ScoreRing(
                progress = (night.score ?: 0) / 100f,
                value = night.score?.toString() ?: "--",
                caption = "睡眠分 / 100",
                color = palette.sleepLight
            )
        }
        // Same breathing room the Charge / activity / load pages leave under
        // their headline block: the ring was sitting almost on the first card.
        Spacer(Modifier.height(34.dp))

        // When it started, how long it lasted: three columns, same shape as the
        // activity page's heart-rate row.
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
                    Icons.Rounded.Schedule,
                    "时长",
                    Fmt.minutes(night.totalMinutes),
                    MaterialTheme.colorScheme.primary
                )
                StatBlock(
                    Modifier.weight(1f),
                    Icons.Rounded.Bedtime,
                    "入睡",
                    Fmt.timeSec(night.startEpochSec),
                    palette.sleepLight
                )
                StatBlock(
                    Modifier.weight(1f),
                    Icons.Rounded.WbSunny,
                    "醒来",
                    Fmt.timeSec(night.endEpochSec),
                    palette.sleepAwake
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        FlatPanel {
            Column(Modifier.padding(16.dp)) {
                // Two stages per row: four across was too tight for a verdict
                // line, and the share of the night is the number that matters
                // ("深睡 1:12" says nothing without knowing it is 22%).
                val sleepTotal = listOfNotNull(
                    night.deepMinutes, night.lightMinutes, night.remMinutes
                ).sum().takeIf { it > 0 }
                val inBed = listOf(
                    night.deepMinutes, night.lightMinutes, night.remMinutes, night.awakeMinutes
                ).mapNotNull { it }.sum()
                fun share(minutes: Int?): Double? =
                    if (minutes == null || sleepTotal == null) null else minutes * 100.0 / sleepTotal
                // The number alone does not say whether a stage is short, so the
                // share of the night follows it in brackets at a smaller size.
                fun shareText(minutes: Int?): String? =
                    share(minutes)?.let { "（${it.roundToInt()}%）" }
                val normalColor = MaterialTheme.colorScheme.onSurface
                val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
                fun verdict(pct: Double?, low: Double, high: Double): Pair<String, Color> = when {
                    pct == null -> "无数据" to mutedColor
                    pct < low -> "偏低" to palette.warn
                    pct > high -> "偏高" to palette.warn
                    else -> "正常" to normalColor
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StageBlock(
                        Modifier.weight(1f),
                        Icons.Rounded.DarkMode,
                        "深睡",
                        Fmt.hoursMinutes(night.deepMinutes),
                        palette.sleepDeep,
                        verdict(share(night.deepMinutes), 10.0, 35.0),
                        suffix = shareText(night.deepMinutes)
                    )
                    StageBlock(
                        Modifier.weight(1f),
                        Icons.Rounded.LightMode,
                        "浅睡",
                        Fmt.hoursMinutes(night.lightMinutes),
                        palette.sleepLight,
                        // Light sleep is whatever is left; a rating on it would
                        // only restate the other three stages.
                        null,
                        suffix = shareText(night.lightMinutes)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StageBlock(
                        Modifier.weight(1f),
                        Icons.Rounded.AutoAwesome,
                        "REM",
                        Fmt.hoursMinutes(night.remMinutes),
                        palette.sleepRem,
                        verdict(share(night.remMinutes), 10.0, 35.0),
                        suffix = shareText(night.remMinutes)
                    )
                    StageBlock(
                        Modifier.weight(1f),
                        Icons.Rounded.Visibility,
                        "清醒",
                        // Minutes stay the headline; the number of times the night
                        // broke is the bracket beside it.
                        Fmt.hoursMinutes(night.awakeMinutes),
                        palette.sleepAwake,
                        verdict(
                            if (night.awakeMinutes == null || inBed <= 0) null
                            else night.awakeMinutes * 100.0 / inBed,
                            0.0, 8.0
                        ),
                        suffix = night.wakeCount?.let { "（$it 次）" }
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("分期时间线", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(
                        "点色块看时段",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(12.dp))
                SleepStageChart(
                    night = night,
                    modifier = Modifier.fillMaxWidth(),
                    selected = picked,
                    onSelect = { picked = it }
                )
                Spacer(Modifier.height(4.dp))
            }
        }
    }

    val napMinutes = night.napMinutes
    if (napMinutes != null) {
        SectionTitle("小睡")
        FlatPanel {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Bedtime,
                        contentDescription = null,
                        tint = palette.sleepLight,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.size(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("小睡 ${Fmt.hoursMinutes(napMinutes)}", style = MaterialTheme.typography.titleSmall)
                        if (night.napStartEpochSec != null && night.napEndEpochSec != null) {
                            Text(
                                "${Fmt.timeSec(night.napStartEpochSec)}–${Fmt.timeSec(night.napEndEpochSec)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                val napStart = night.napStartEpochSec
                val napEnd = night.napEndEpochSec
                if (night.napStages.size > 1 && napStart != null && napEnd != null) {
                    fun napStage(name: String): Int? = night.napStages
                        .filter { it.stage == name }
                        .sumOf { ((it.endEpochSec - it.startEpochSec) / 60).toInt() }
                        .takeIf { it > 0 }
                    val napDeep = napStage("deep")
                    val napLight = napStage("light")
                    val napRem = napStage("rem")
                    val napAwake = napStage("awake")
                    val napNight = SleepNight(
                        date = night.date,
                        startEpochSec = napStart,
                        endEpochSec = napEnd,
                        score = null,
                        deepMinutes = napDeep,
                        lightMinutes = napLight,
                        remMinutes = napRem,
                        awakeMinutes = napAwake,
                        wakeCount = null,
                        restingHr = null,
                        stages = night.napStages
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatBlock(
                            Modifier.weight(1f),
                            Icons.Rounded.DarkMode,
                            "深睡",
                            Fmt.hoursMinutes(napDeep),
                            palette.sleepDeep
                        )
                        StatBlock(
                            Modifier.weight(1f),
                            Icons.Rounded.LightMode,
                            "浅睡",
                            Fmt.hoursMinutes(napLight),
                            palette.sleepLight
                        )
                        StatBlock(
                            Modifier.weight(1f),
                            Icons.Rounded.AutoAwesome,
                            "REM",
                            Fmt.hoursMinutes(napRem),
                            palette.sleepRem
                        )
                        StatBlock(
                            Modifier.weight(1f),
                            Icons.Rounded.Visibility,
                            "清醒",
                            Fmt.hoursMinutes(napAwake),
                            palette.sleepAwake
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    SleepStageChart(
                        night = napNight,
                        modifier = Modifier.fillMaxWidth(),
                        selected = pickedNap,
                        onSelect = { pickedNap = it }
                    )
                }
            }
        }
    }

    // 睡眠数据: the three vitals the watch reports for the night, each opening
    // its own 7-day trend. 夜醒 / 睡眠效率 / 最低血氧 were dropped: they either
    // repeat a number above or say nothing you can act on.
    SectionTitle("睡眠数据")
    val breathing = data?.breathingOn(night.date)
    val respiration = data?.respirationOn(night.date)
    val sleepHr = readiness?.sleepRhr ?: night.restingHr
    val spo2 = data?.spo2?.filter { it.epochMs / 1000 in night.startEpochSec..night.endEpochSec }
    // The rows sit in their own column: the page's 14dp arrangement only applies
    // before the group, so the cards inside keep a clean 16dp (the activity
    // page's spacing) instead of 14 + spacer = 30dp.
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SleepVitalTile(
                label = "睡眠心率",
                value = sleepHr?.toString() ?: "--",
                unit = "bpm",
                tint = palette.sleepRem,
                verdict = when {
                    sleepHr == null -> null
                    sleepHr < 45 || sleepHr > 70 -> "偏离" to palette.warn
                    else -> "正常" to MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.weight(1f),
                onClick = { onOpen(DetailTarget(DetailKind.SLEEP_HR, night.date)) }
            )
            SleepVitalTile(
                label = "呼吸率",
                value = respiration?.let { Fmt.oneDecimal(it) } ?: "--",
                unit = "次/分",
                tint = palette.sleepLight,
                verdict = when {
                    respiration == null -> null
                    respiration < 12.0 || respiration > 20.0 -> "偏离" to palette.warn
                    else -> "正常" to MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.weight(1f),
                onClick = { onOpen(DetailTarget(DetailKind.SLEEP_RR, night.date)) }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val apnea = breathing?.apneaIndex
            SleepVitalTile(
                label = "低通气",
                value = apnea?.let { Fmt.oneDecimal(it) } ?: "--",
                unit = "次/时",
                tint = palette.sleepAwake,
                verdict = when {
                    apnea == null -> null
                    apnea < 5.0 -> "正常" to MaterialTheme.colorScheme.primary
                    apnea < 15.0 -> "偏高" to palette.warn
                    else -> "偏高" to palette.critical
                },
                modifier = Modifier.weight(1f),
                onClick = { onOpen(DetailTarget(DetailKind.SLEEP_APNEA, night.date)) }
            )
            if (!spo2.isNullOrEmpty()) {
                val average = spo2.map { it.value }.average().roundToInt()
                MetricTile(
                    label = "夜间血氧均值",
                    value = average.toString(),
                    unit = "%",
                    verdict = when {
                        average >= 95 -> "正常" to MaterialTheme.colorScheme.onSurface
                        average >= 90 -> "偏低" to palette.warn
                        else -> "低" to palette.critical
                    },
                    modifier = Modifier.weight(1f),
                    onClick = { onOpen(DetailTarget(DetailKind.NIGHT_SPO2, night.date)) }
                )
            }
        }
    }

    val nights = data?.nights?.takeLast(14).orEmpty()
    if (nights.size > 1) {
        SectionTitle("睡眠规律性")
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            FlatPanel {
                Column(Modifier.padding(16.dp)) {
                    val rhythm = sleepRhythm(nights)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RhythmBlock(
                            Modifier.weight(1f),
                            "平均入睡",
                            rhythm?.averageOnset?.let { Fmt.clock(it) } ?: "--",
                            palette.sleepLight
                        )
                        RhythmBlock(
                            Modifier.weight(1f),
                            "平均醒来",
                            rhythm?.averageWake?.let { Fmt.clock(it) } ?: "--",
                            palette.sleepAwake
                        )
                        RhythmBlock(
                            Modifier.weight(1f),
                            "规律性",
                            rhythm?.score?.let { "$it%" } ?: "--",
                            when {
                                rhythm?.score == null -> palette.sleepLight
                                rhythm.score >= 85 -> MaterialTheme.colorScheme.primary
                                rhythm.score >= 70 -> palette.warn
                                else -> palette.critical
                            },
                            verdict = rhythm?.score?.let {
                                when {
                                    it >= 85 -> "正常"
                                    it >= 70 -> "一般"
                                    else -> "偏低"
                                }
                            }
                        )
                    }
                }
            }
            FlatPanel {
                Column(Modifier.padding(16.dp)) {
                    // Seven nights so both clock times fit under every bar;
                    // the averages above still cover the last 14 nights.
                    SleepWindowChart(
                        nights = nights.takeLast(7),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            Fmt.date(nights.takeLast(7).first().date),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            Fmt.date(nights.last().date),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ---------------- charge ----------------

@Composable
private fun ChargeDetail(data: HealthData?, date: LocalDate?) {
    val charge = (date?.let { data?.chargeOn(it) } ?: data?.latestCharge())
    if (charge == null) {
        Missing("这段时间没有 Charge 读数")
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricTile("当前总能量", charge.total?.toString() ?: "--", "/100", Modifier.weight(1f))
        MetricTile("体力", charge.physical?.roundToInt()?.toString() ?: "--", "/100", Modifier.weight(1f))
        MetricTile("精神", charge.mental?.roundToInt()?.toString() ?: "--", "/100", Modifier.weight(1f))
    }

    val samples = charge.samples.filter { it.total != null }
    if (samples.size > 1) {
        SectionTitle("当天曲线", trailing = "${samples.size} 个读数")
        FlatPanel {
            Column(Modifier.padding(16.dp)) {
                LineChart(
                    samples.map { it.total!!.toFloat() },
                    Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    color = MaterialTheme.colorScheme.primary,
                    minValue = 0f,
                    maxValue = 100f
                )
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(Fmt.time(samples.first().epochMs), style = MaterialTheme.typography.labelSmall)
                    Text(Fmt.time(samples.last().epochMs), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }

    // Fourteen calendar days ending on the last day the watch recorded Charge,
    // so a skipped day leaves a gap instead of sliding the line sideways.
    val chargeByDate = data?.chargeDays.orEmpty().associateBy { it.date }
    val lastChargeDate = data?.latestCharge()?.date ?: LocalDate.now()
    val history = (0..13).map { chargeByDate[lastChargeDate.minusDays(13L - it)] }
    if (history.count { it != null } > 1) {
        SectionTitle("近 14 天", trailing = "每日最后一次有效读数")
        FlatPanel {
            Column(Modifier.padding(16.dp)) {
                BarChart(
                    history.map { it?.total?.toFloat() },
                    Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    color = MaterialTheme.colorScheme.primary,
                    goal = 100f
                )
                Spacer(Modifier.height(12.dp))
                LineChart(
                    history.map { it?.physical?.toFloat() },
                    Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    color = MaterialTheme.colorScheme.tertiary,
                    minValue = 0f,
                    maxValue = 100f,
                    showGrid = false
                )
                LineChart(
                    history.map { it?.mental?.toFloat() },
                    Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    minValue = 0f,
                    maxValue = 100f,
                    showGrid = false
                )
            }
        }
    }
}

// ---------------- stress ----------------

@Composable
private fun StressDetail(data: HealthData?, date: LocalDate?) {
    val day = (date?.let { d -> data?.stress?.firstOrNull { it.date == d } })
        ?: data?.stress?.maxByOrNull { it.date }
    val current = day?.curve?.lastOrNull()
    if (day == null || current == null) {
        Missing("这段时间没有压力记录")
        return
    }

    // One card for the reading and its curve: they describe the same moment, and
    // two separate panels made the page look like unrelated pieces of data.
    FlatPanel {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val stressVerdict = when {
                current.second < 40 -> "放松" to MaterialTheme.colorScheme.onSurface
                current.second < 60 -> "正常" to MaterialTheme.colorScheme.onSurface
                current.second < 80 -> "偏高" to LocalZgPalette.current.warn
                else -> "很高" to LocalZgPalette.current.critical
            }
            val abnormal = stressVerdict.first in setOf("偏高", "很高")
            Text(
                "${Fmt.date(day.date)} ${Fmt.time(current.first)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                current.second.toString(),
                style = MaterialTheme.typography.displayMedium,
                color = if (abnormal) stressVerdict.second else MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stressVerdict.first,
                style = MaterialTheme.typography.titleSmall,
                color = stressVerdict.second
            )
            Spacer(Modifier.height(16.dp))
            // Extra side margin on top of the card's own: the time labels on the
            // ends of the axis were sitting right against the panel edge.
            StressBandChart(
                curve = day.curve,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .height(170.dp)
            )
            Spacer(Modifier.height(18.dp))
            // Same side inset as the chart above, so the legend's ends line up
            // with the plot instead of hanging off the card edge.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf(
                    "放松" to StressRelaxed,
                    "正常" to StressNormal,
                    "中等" to StressMedium,
                    "偏高" to StressHigh
                ).forEach { (label, color) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }

    SectionTitle("每日压力")
    // Two cards, not one: the three numbers answer "how hard was the day",
    // the donut answers "how was it spread out". Putting them in one panel made
    // the reading look like a caption of the chart.
    FlatPanel {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatBlock(
                Modifier.weight(1f),
                Icons.Rounded.ArrowUpward,
                "最高",
                day.maximum?.toString() ?: "--",
                StressHigh
            )
            StatBlock(
                Modifier.weight(1f),
                Icons.Rounded.ArrowDownward,
                "最低",
                day.minimum?.toString() ?: "--",
                StressRelaxed
            )
            StatBlock(
                Modifier.weight(1f),
                Icons.Rounded.TrendingFlat,
                "平均",
                day.average?.toString() ?: "--",
                StressNormal
            )
        }
    }

    FlatPanel {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StressDonut(
                    relaxedPct = day.relaxedPct,
                    normalPct = day.normalPct,
                    mediumPct = day.mediumPct,
                    highPct = day.highPct,
                    modifier = Modifier.size(132.dp)
                )
                Spacer(Modifier.width(18.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                        "放松" to (day.relaxedPct to StressRelaxed),
                        "正常" to (day.normalPct to StressNormal),
                        "中等" to (day.mediumPct to StressMedium),
                        "偏高" to (day.highPct to StressHigh)
                    ).forEach { (label, pair) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(pair.second)
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                label,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                pair.first?.let { "${it.roundToInt()}%" } ?: "--",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }
    }

    // A week of daily averages under the day itself: one number says how today
    // went, the line says whether that is normal for this week.
    val week = data?.stress.orEmpty()
        .sortedBy { it.date }
        .takeLast(7)
    if (week.count { it.average != null } >= 2) {
        SectionTitle("近 7 天平均压力")
        FlatPanel {
            TrendLineChart(
                values = week.map { it.average?.toFloat() },
                labels = week.map { Fmt.weekdayShort(it.date) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 12.dp)
                    .height(170.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// ---------------- readiness ----------------

@Composable
private fun ReadinessDetail(data: HealthData?, date: LocalDate?) {
    val source = data ?: run {
        Missing("手表还没有算出准备度")
        return
    }
    val day = if (date != null) {
        source.readiness.lastOrNull { it.date == date }
    } else {
        source.latestReadiness()
    }
    if (day == null) {
        Missing("这一天没有准备度记录")
        return
    }
    val score = day.officialScore

    FlatPanel {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "身心准备度",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            if (score != null) {
                Text(
                    score.toString(),
                    style = MaterialTheme.typography.displayMedium
                )
                Text(
                    "准备度",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text("无数据", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (day.physicalScore == null || day.mentalScore == null)
                        "暂无评分：体力恢复或精力恢复数据不足"
                    else "暂无评分",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    SectionTitle("关键指标")
    val indicators = listOf(
        ReadinessMetric("体力恢复", day.physicalScore?.toString() ?: "--", "分"),
        ReadinessMetric("精力恢复", day.mentalScore?.toString() ?: "--", "分"),
        ReadinessMetric(
            "静息心率",
            day.sleepRhr?.toString() ?: "--",
            "bpm",
            readinessRating(day.rhrScore)
        ),
        ReadinessMetric(
            "心率变异性",
            day.sleepHrv?.toString() ?: "--",
            "ms",
            readinessRating(day.hrvScore)
        ),
        ReadinessMetric(
            "呼吸暂停风险",
            day.ahiScore?.toString() ?: "--",
            "分"
        ),
        ReadinessMetric("皮肤温度", day.skinTempScore?.toString() ?: "--", "分")
    )
    indicators.chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            pair.forEach { metric ->
                ReadinessMetricCard(metric, Modifier.weight(1f))
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }

    // Fourteen calendar days ending on the day being read, so a day the watch
    // did not score is a gap on the axis instead of shifting the whole line.
    val trendDays = (0..13).map { day.date.minusDays(13L - it) }
    val readinessByDate = source.readiness.associateBy { it.date }
    val trendSeries = trendDays.map { readinessByDate[it]?.officialScore?.toFloat() }
    if (trendSeries.count { it != null } > 1) {
        SectionTitle("近 14 天准备度")
        FlatPanel {
            TrendLineChart(
                values = trendSeries,
                labels = trendDays.map { Fmt.weekdayShort(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 12.dp)
                    .height(160.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private data class ReadinessMetric(
    val label: String,
    val value: String,
    val unit: String,
    /** Qualitative word for rest-HR / HRV instead of the 1–100 score. */
    val rating: String? = null
)

/**
 * Word for a readiness component, taken from the watch's own 1–100 component
 * score (rhrScore / hrvScore). The word is a label on that score, so the score
 * is printed next to it and the thresholds stay visible: 80 优秀 / 60 良好 /
 * 40 一般 / 其余 偏低. No score from the watch means no word at all.
 */
private fun readinessRating(score: Int?): String = when {
    score == null -> "暂无分项评分"
    score >= 80 -> "$score 分 · 优秀"
    score >= 60 -> "$score 分 · 良好"
    score >= 40 -> "$score 分 · 一般"
    else -> "$score 分 · 偏低"
}

@Composable
private fun ReadinessMetricCard(metric: ReadinessMetric, modifier: Modifier = Modifier) {
    FlatPanel(modifier) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                metric.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    metric.value,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    metric.unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 3.dp, bottom = 3.dp)
                )
            }
            metric.rating?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/** One label/value row of the readiness key indicators. */
@Composable
private fun KeyIndicator(label: String, value: Int?, unit: String = "分") {    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            if (value == null) "--" else "$value $unit",
            style = MaterialTheme.typography.titleSmall,
            color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

// ---------------- activity ----------------

@Composable
private fun ActivityDetail(data: HealthData?, date: LocalDate) {
    val day = data?.day(date)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricTile("步数", day?.steps?.toString() ?: "--", null, Modifier.weight(1f))
        MetricTile("目标", day?.stepGoal?.toString() ?: "--", null, Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricTile("距离", Fmt.km(day?.distanceM), "km", Modifier.weight(1f))
        MetricTile("消耗", Fmt.int(day?.calories), "kcal", Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val activityText = day?.let { d ->
            val minutes = d.activityMinutes
            when {
                minutes == null -> "--"
                d.activityGoal != null -> "$minutes/${d.activityGoal}"
                else -> minutes.toString()
            }
        } ?: "--"
        MetricTile("活动时长", activityText, "分", Modifier.weight(1f))
        MetricTile("静息心率", day?.restingHr?.toString() ?: "--", "bpm", Modifier.weight(1f))
    }
    val days = (13 downTo 0).map { data?.day(date.minusDays(it.toLong())) }
    val labels = (13 downTo 0).map { Fmt.weekdayShort(date.minusDays(it.toLong())) }
    if (days.any { it != null }) {
        SectionTitle("近 14 天步数")
        FlatPanel {
            AxisBarChart(
                values = days.map { it?.steps?.toFloat() },
                labels = labels,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
                    .height(140.dp),
                color = MaterialTheme.colorScheme.primary,
                goal = day?.stepGoal?.toFloat(),
                valueLabel = { value ->
                    if (value >= 1000) "${(value / 1000).roundToInt()}k" else value.roundToInt().toString()
                }
            )
        }
        SectionTitle("距离与消耗")
        FlatPanel {
            Column(Modifier.padding(14.dp)) {
                AxisLineChart(
                    values = days.map { it?.distanceM?.let { d -> (d / 1000).toFloat() } },
                    labels = labels,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    color = MaterialTheme.colorScheme.tertiary,
                    valueSuffix = " km"
                )
                Spacer(Modifier.height(10.dp))
                AxisLineChart(
                    values = days.map { it?.calories?.toFloat() },
                    labels = labels,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    color = MaterialTheme.colorScheme.primary,
                    valueSuffix = " kcal"
                )
            }
        }
    }
    val hr = data?.heartRate?.filter {
        Instant.ofEpochSecond(it.first).atZone(ZoneId.systemDefault()).toLocalDate() == date
    }.orEmpty()
    if (hr.size > 10) {
        SectionTitle(
            "当天心率",
            trailing = "最低 ${hr.minOf { it.second }} · 平均 ${hr.map { it.second }.average().toInt()} · 最高 ${hr.maxOf { it.second }}"
        )
        FlatPanel {
            AxisLineChart(
                values = hr.map { it.second.toFloat() },
                labels = emptyList(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
                    .height(130.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
    val week = (6 downTo 0).mapNotNull { data?.day(date.minusDays(it.toLong())) }
    if (week.isNotEmpty()) {
        SectionTitle("这 7 天合计")
        FlatPanel {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val steps = week.mapNotNull { it.steps }.takeIf { it.isNotEmpty() }?.sum()
                val distance = week.mapNotNull { it.distanceM }.takeIf { it.isNotEmpty() }?.sum()
                val active = week.mapNotNull { it.activityMinutes }.takeIf { it.isNotEmpty() }?.sum()
                Text(
                    "${steps?.toString() ?: "--"} 步 · ${Fmt.km(distance)} km · ${active?.toString() ?: "--"} 活动时长",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "${week.size} 天有记录，缺失日不补 0。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ---------------- training load ----------------

@Composable
private fun LoadDetail(data: HealthData?, date: LocalDate?) {
    val load = if (date != null) data?.load?.lastOrNull { it.date == date } else data?.latestLoad()
    if (load == null) {
        Missing("还没有训练负荷记录")
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricTile("7 天负荷", load.weekLoad?.roundToInt()?.toString() ?: "--", "WTL", Modifier.weight(1f))
        MetricTile("当天", load.load?.roundToInt()?.toString() ?: "--", "WTL", Modifier.weight(1f))
    }
    val min = load.optimalMin?.roundToInt()
    val max = load.optimalMax?.roundToInt()
    Text(
        if (min != null && max != null) "个人合适区间 $min – $max；超过 ${load.overreaching?.roundToInt() ?: "--"} 属于过量。" else "这次没有取到合适区间。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val days = data?.load?.takeLast(14).orEmpty()
    if (days.size > 1) {
        SectionTitle("近 14 天")
        FlatPanel {
            Column(Modifier.padding(16.dp)) {
                BarChart(
                    days.map { it.weekLoad?.toFloat() },
                    Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    color = MaterialTheme.colorScheme.primary,
                    goal = load.optimalMax?.toFloat()
                )
            }
        }
    }
}

// ---------------- PAI ----------------

@Composable
private fun PaiDetail(data: HealthData?, date: LocalDate?) {
    val source = data ?: run {
        Missing("还没有 PAI 数据")
        return
    }
    val pai = if (date != null) {
        source.pai.lastOrNull { it.date == date }
    } else {
        source.pai.maxByOrNull { it.date }
    }
    if (pai == null) {
        Missing("这一天没有 PAI 数据")
        return
    }
    val total = pai.total ?: pai.daily
    val today = remember { LocalDate.now() }
    val week = remember(source.pai, today) {
        (6 downTo 0).map { offset ->
            val day = today.minusDays(offset.toLong())
            source.pai.lastOrNull { it.date == day }
        }
    }
    val hrDay = remember(source, pai) {
        val zone = ZoneId.systemDefault()
        source.heartRate.filter {
            Instant.ofEpochSecond(it.first).atZone(zone).toLocalDate() == pai.date
        }
    }
    val isToday = pai.date == today

    FlatPanel {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                Fmt.date(pai.date),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                (total ?: 0.0).roundToInt().toString(),
                style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.SemiBold),
                color = com.genkaim.zeppglass.ui.components.PaiAmber
            )
            Text(
                "PAI",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if ((total ?: 0.0) >= 100.0) "达标" else "未达标",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(10.dp))
            PaiWeekBars(
                days = week,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .padding(horizontal = 10.dp)
            )
        }
    }

    SectionTitle(
        (if (isToday) "今日获取 " else Fmt.date(pai.date) + " 获取 ") +
            "${pai.daily?.let { Fmt.oneDecimal(it) } ?: "--"} PAI"
    )
    FlatPanel {
        Column(Modifier.padding(16.dp)) {
            if (hrDay.size > 2) {
                PaiHrZoneChart(
                    values = hrDay,
                    lowHr = pai.lowHr,
                    mediumHr = pai.mediumHr,
                    highHr = pai.highHr,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                )
            } else {
                Text(
                    "今天还没有连续心率采样",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                PaiZoneColumn(
                    value = pai.lowPai,
                    label = "低强度",
                    color = Color(0xFF3FBFAE),
                    minutes = pai.lowMinutes,
                    modifier = Modifier.weight(1f)
                )
                PaiZoneColumn(
                    value = pai.mediumPai,
                    label = "中强度",
                    color = Color(0xFF4C8DF6),
                    minutes = pai.mediumMinutes,
                    modifier = Modifier.weight(1f)
                )
                PaiZoneColumn(
                    value = pai.highPai,
                    label = "高强度",
                    color = Color(0xFF8B5CF6),
                    minutes = pai.highMinutes,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    SectionTitle("如何获取 PAI？")
    FlatPanel {
        Column(Modifier.padding(16.dp)) {
            PaiHowToScale(
                total = total,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "PAI 由心率落在不同强度区间的时间累积；多数日子达到 30 以上，长期保持 100 以上更理想。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PaiZoneColumn(
    value: Double?,
    label: String,
    color: Color,
    minutes: Int?,
    modifier: Modifier = Modifier
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value?.let { Fmt.oneDecimal(it) } ?: "--",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                " PAI",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        minutes?.let {
            Text(
                "$it 分钟",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------------- weight ----------------

@Composable
private fun WeightDetail(data: HealthData?, date: LocalDate?, source: String) {
    // Switching source fades the cards; only the toggle's own pill slides.
    AnimatedContent(
        targetState = source,
        transitionSpec = {
            fadeIn(tween(200)) togetherWith fadeOut(tween(150))
        },
        label = "body-source"
    ) { source ->
        WeightBody(data, date, source)
    }
}

@Composable
private fun WeightBody(data: HealthData?, date: LocalDate?, source: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        val zone = ZoneId.systemDefault()
        val all = data?.weights.orEmpty()
        fun dayOf(point: WeightPoint): LocalDate =
            Instant.ofEpochMilli(point.epochMs).atZone(zone).toLocalDate()
        // The cloud labels each weighing: weightType 0 comes from the 体脂秤, 5
        // from the wearable's body-composition reading, 1/7 are manual entries.
        // The two sources keep their own history, so switching between them can
        // show two different dates.
        val scaleRecords = all.filter { it.weightType == 0 }
        val watchRecords = all.filter { it.weightType != 0 }
        val points = if (source == "watch") watchRecords else scaleRecords
        // The overview card is the selected day only; this page always leads with
        // the most recent reading of the chosen source.
        val latest = date
            ?.let { day -> points.filter { dayOf(it) == day }.maxByOrNull { it.epochMs } }
            ?: points.maxByOrNull { it.epochMs }
        val comp = points.filter { it.hasComposition }.maxByOrNull { it.epochMs }
        val body = data?.body
        val male = body?.isMale ?: true
        val heightM = body?.heightCm?.takeIf { it > 100 }?.div(100.0)
        val bmi = latest?.bmi
        val bodyWeight = latest?.weightKg ?: comp?.weightKg
        // Mifflin-St Jeor from the Zepp profile: used only to rate the scale's
        // metabolism figure, never shown as a reading of its own.
        val bmrEstimate = mifflinBmr(body, bodyWeight)
        // A watch entry has weight and BMI only: a score or body age built from
        // that alone would mislead, so both need a scale reading.
        // The score and body age are judged on the composition reading itself, not
        // on today's weight, so a watch-only day does not mix two dates.
        val compBmi = comp?.bmi ?: bmi
        val score = if (comp == null) null else bodyCompositionScore(
            bmi = compBmi,
            fat = comp.bodyFatPct,
            muscleKg = comp.muscleKg,
            water = comp.bodyWaterPct,
            visceral = comp.visceralFat,
            protein = comp.proteinPct,
            bone = comp.boneMassKg,
            bmr = comp.metabolismKcal,
            bmrEstimate = bmrEstimate,
            heightM = heightM,
            male = male
        )
        val bodyAge = if (comp == null) null else deurenbergAge(compBmi, comp.bodyFatPct, male)
        val ink = MaterialTheme.colorScheme.onSurface
        val warnTone = LocalZgPalette.current.warn
        val criticalTone = LocalZgPalette.current.critical
        val scoreVerdict: Pair<String, Color>? = score?.let {
            when {
                it >= 85 -> "优秀" to ink
                it >= 70 -> "良好" to ink
                it >= 55 -> "一般" to warnTone
                else -> "偏低" to criticalTone
            }
        }
        val scoreAbnormal = scoreVerdict != null && scoreVerdict.first !in setOf("优秀", "良好")
        val actualAge = data?.body?.birthYear
            ?.let { LocalDate.now().year - it }
            ?.takeIf { it in 5..110 }

        // The score and body age come from the newest scale reading close to the
        // current weight, so a watch-only day still carries them.
        run {
            FlatPanel {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp, horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "身体得分",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                score?.toString() ?: "--",
                                style = MaterialTheme.typography.displaySmall,
                                color = if (scoreAbnormal) scoreVerdict!!.second else ink
                            )
                            Text(
                                " 分",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (scoreAbnormal) scoreVerdict!!.second else ink,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        // Both columns reserve the same line for their verdict,
                        // even when one has nothing to say: the two numbers then
                        // sit at the same height instead of one drifting down.
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (scoreVerdict != null) {
                                Text(
                                    scoreVerdict.first,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scoreVerdict.second
                                )
                            }
                        }
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "身体年龄",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                bodyAge?.let { if (it < 18) "<18" else it.toString() } ?: "--",
                                style = MaterialTheme.typography.displaySmall,
                                color = ink
                            )
                            Text(
                                " 岁",
                                style = MaterialTheme.typography.labelSmall,
                                color = ink,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (bodyAge != null && actualAge != null) {
                                Text(
                                    if (bodyAge < 18) "低于 18 岁"
                                    else when {
                                        bodyAge < actualAge -> "比实际小 ${actualAge - bodyAge} 岁"
                                        bodyAge > actualAge -> "比实际大 ${bodyAge - actualAge} 岁"
                                        else -> "与实际年龄相同"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ink
                                )
                            }
                        }
                    }
                }
                if (score == null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "这次读数缺少身体成分，暂时算不出得分",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                }
            }
        }

        fun pct(value: Double?) =
            value?.let { String.format(java.util.Locale.CHINA, "%.1f%%", it) } ?: "--"
        fun kg(value: Double?, digits: Int = 1) =
            value?.let { String.format(java.util.Locale.CHINA, "%.${digits}f kg", it) } ?: "--"
        fun cut(value: Double, digits: Int = 0) =
            String.format(java.util.Locale.CHINA, "%.${digits}f", value)

        // Thresholds follow the bands the Zepp app prints: BMI 18.5/24/28,
        // body fat 11/17/22/27 (male), water 55/65, protein 16/18, visceral fat
        // 10/15. Muscle is judged in kilograms against height.
        val fatLow = if (male) 11.0 else 21.0
        val fatOk = if (male) 17.0 else 24.0
        val fatHigh = if (male) 22.0 else 28.0
        val fatTop = if (male) 27.0 else 32.0
        val muscleLow = heightM?.let { 16.5 * it * it } ?: 49.5
        val muscleHigh = heightM?.let { 19.8 * it * it } ?: 59.4
        val boneLow = if (male) 2.0 else 1.5
        val boneHigh = if (male) 4.0 else 3.5
        val weightMax = maxOf(150.0, (bodyWeight ?: 0.0) + 10.0)
        val muscleMax = maxOf(muscleHigh * 1.3, (bodyWeight ?: 0.0) * 0.8, 60.0)

        val bmiRate = bmi?.let {
            when {
                it < 18.5 -> "偏轻" to BodyTone.WARN
                it < 24.0 -> "正常" to BodyTone.NORMAL
                it < 28.0 -> "偏高" to BodyTone.WARN
                else -> "高" to BodyTone.BAD
            }
        }
        val fatRate = comp?.bodyFatPct?.let {
            when {
                it < fatLow -> "偏低" to BodyTone.WARN
                it < fatOk -> "正常" to BodyTone.NORMAL
                it < fatHigh -> "偏高" to BodyTone.WARN
                it < fatTop -> "高" to BodyTone.WARN
                else -> "高" to BodyTone.BAD
            }
        }
        val muscleRate = comp?.muscleKg?.let {
            when {
                it < muscleLow -> "偏低" to BodyTone.WARN
                it < muscleHigh -> "标准" to BodyTone.NORMAL
                else -> "优秀" to BodyTone.NORMAL
            }
        }
        val waterRate = comp?.bodyWaterPct?.let {
            when {
                it < 55.0 -> "偏低" to BodyTone.WARN
                it <= 65.0 -> "正常" to BodyTone.NORMAL
                else -> "偏高" to BodyTone.WARN
            }
        }
        val boneRate = comp?.boneMassKg?.let {
            when {
                it < boneLow -> "偏低" to BodyTone.WARN
                it <= boneHigh -> "标准" to BodyTone.NORMAL
                else -> "偏高" to BodyTone.WARN
            }
        }
        val visceralRate = comp?.visceralFat?.let {
            when {
                it <= 10.0 -> "正常" to BodyTone.NORMAL
                it <= 15.0 -> "偏高" to BodyTone.WARN
                else -> "高" to BodyTone.BAD
            }
        }
        val bmrRate = comp?.metabolismKcal?.let { bmr ->
            bmrEstimate?.let { est ->
                val diff = (bmr - est) / est
                when {
                    abs(diff) <= 0.05 -> "正常" to BodyTone.NORMAL
                    diff < 0 && diff >= -0.10 -> "偏低" to BodyTone.WARN
                    diff > 0 && diff <= 0.10 -> "偏高" to BodyTone.WARN
                    diff < 0 -> "低" to BodyTone.BAD
                    else -> "高" to BodyTone.BAD
                }
            }
        }
        val proteinRate = comp?.proteinPct?.let {
            when {
                it < 16.0 -> "偏低" to BodyTone.WARN
                it <= 18.0 -> "正常" to BodyTone.NORMAL
                else -> "偏高" to BodyTone.WARN
            }
        }

        // Band cuts and the numbers printed under each bar.
        val bmiBands = listOf(
            Triple(15.0, 18.5, BodyTone.WARN),
            Triple(18.5, 24.0, BodyTone.NORMAL),
            Triple(24.0, 28.0, BodyTone.WARN),
            Triple(28.0, 40.0, BodyTone.BAD)
        )
        val fatBands = listOf(
            Triple(5.0, fatLow, BodyTone.WARN),
            Triple(fatLow, fatOk, BodyTone.NORMAL),
            Triple(fatOk, fatHigh, BodyTone.WARN),
            Triple(fatHigh, fatTop, BodyTone.WARN),
            Triple(fatTop, 40.0, BodyTone.BAD)
        )
        val muscleBands = listOf(
            Triple(20.0, muscleLow, BodyTone.WARN),
            Triple(muscleLow, muscleHigh, BodyTone.NORMAL),
            Triple(muscleHigh, muscleMax, BodyTone.NORMAL)
        )
        val waterBands = listOf(
            Triple(35.0, 55.0, BodyTone.WARN),
            Triple(55.0, 65.0, BodyTone.NORMAL),
            Triple(65.0, 75.0, BodyTone.WARN)
        )
        val boneBands = listOf(
            Triple(1.0, boneLow, BodyTone.WARN),
            Triple(boneLow, boneHigh, BodyTone.NORMAL),
            Triple(boneHigh, 5.0, BodyTone.WARN)
        )
        val visceralBands = listOf(
            Triple(1.0, 10.0, BodyTone.NORMAL),
            Triple(10.0, 15.0, BodyTone.WARN),
            Triple(15.0, 20.0, BodyTone.BAD)
        )
        val proteinBands = listOf(
            Triple(10.0, 16.0, BodyTone.WARN),
            Triple(16.0, 18.0, BodyTone.NORMAL),
            Triple(18.0, 25.0, BodyTone.WARN)
        )
        val bmrBands = bmrEstimate?.let { est ->
            listOf(
                Triple(800.0, est * 0.95, BodyTone.WARN),
                Triple(est * 0.95, est * 1.05, BodyTone.NORMAL),
                Triple(est * 1.05, 2600.0, BodyTone.WARN)
            )
        }.orEmpty()
        val weightBands = if (heightM != null) listOf(
            Triple(30.0, 18.5 * heightM * heightM, BodyTone.WARN),
            Triple(18.5 * heightM * heightM, 24.0 * heightM * heightM, BodyTone.NORMAL),
            Triple(24.0 * heightM * heightM, 28.0 * heightM * heightM, BodyTone.WARN),
            Triple(28.0 * heightM * heightM, weightMax, BodyTone.BAD)
        ) else emptyList()
        val weightTicks = if (heightM != null) listOf(
            18.5 * heightM * heightM to cut(18.5 * heightM * heightM),
            24.0 * heightM * heightM to cut(24.0 * heightM * heightM),
            28.0 * heightM * heightM to cut(28.0 * heightM * heightM)
        ) else emptyList()
        val bmrTicks = bmrEstimate?.let { est ->
            listOf(est * 0.95 to cut(est * 0.95), est * 1.05 to cut(est * 1.05))
        }.orEmpty()

        val metrics = listOf(
            BodyMetric(
                "体重", Icons.Rounded.MonitorWeight, kg(latest?.weightKg), latest?.weightKg,
                bmiRate, 30.0, weightMax, weightBands, weightTicks
            ),
            BodyMetric(
                "BMI", Icons.Rounded.Straighten, Fmt.oneDecimal(bmi), bmi,
                bmiRate, 15.0, 40.0, bmiBands,
                listOf(18.5 to "18.5", 24.0 to "24", 28.0 to "28")
            ),
            BodyMetric(
                "体脂率", Icons.Rounded.Percent, pct(comp?.bodyFatPct), comp?.bodyFatPct,
                fatRate, 5.0, 40.0, fatBands,
                listOf(
                    fatLow to cut(fatLow), fatOk to cut(fatOk),
                    fatHigh to cut(fatHigh), fatTop to cut(fatTop)
                )
            ),
            BodyMetric(
                "肌肉", Icons.Rounded.FitnessCenter, kg(comp?.muscleKg), comp?.muscleKg,
                muscleRate, 20.0, muscleMax, muscleBands,
                listOf(muscleLow to cut(muscleLow, 1), muscleHigh to cut(muscleHigh, 1))
            ),
            BodyMetric(
                "体水分率", Icons.Rounded.WaterDrop, pct(comp?.bodyWaterPct),
                comp?.bodyWaterPct, waterRate, 35.0, 75.0, waterBands,
                listOf(55.0 to "55", 65.0 to "65")
            ),
            BodyMetric(
                "骨量", Icons.Rounded.Spa, kg(comp?.boneMassKg, 2), comp?.boneMassKg,
                boneRate, 1.0, 5.0, boneBands,
                listOf(boneLow to cut(boneLow), boneHigh to cut(boneHigh))
            ),
            BodyMetric(
                "内脏脂肪", Icons.Rounded.Favorite,
                comp?.visceralFat?.roundToInt()?.toString() ?: "--", comp?.visceralFat,
                visceralRate, 1.0, 20.0, visceralBands,
                listOf(10.0 to "10", 15.0 to "15")
            ),
            BodyMetric(
                "基础代谢", Icons.Rounded.LocalFireDepartment,
                comp?.metabolismKcal?.roundToInt()?.let { "$it kcal" } ?: "--",
                comp?.metabolismKcal, bmrRate, 800.0, 2600.0, bmrBands, bmrTicks
            ),
            BodyMetric(
                "蛋白质率", Icons.Rounded.Restaurant, pct(comp?.proteinPct),
                comp?.proteinPct, proteinRate, 10.0, 25.0, proteinBands,
                listOf(16.0 to "16", 18.0 to "18")
            )
        )

        // Two cards: weight and BMI may come from today while the composition
        // figures come from an older scale reading, so each card carries its own
        // date on the right.
        val basicMetrics = metrics.take(2)
        val bodyMetrics = metrics.drop(2)
        SectionTitle("体重与 BMI", trailing = latest?.let { Fmt.date(dayOf(it)) })
        FlatPanel {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                basicMetrics.forEach { BodyMetricRow(it) }
            }
        }
        SectionTitle("其他指标", trailing = comp?.let { Fmt.date(dayOf(it)) })
        FlatPanel {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                bodyMetrics.forEachIndexed { index, metric ->
                    BodyMetricRow(metric)
                    if (index != bodyMetrics.lastIndex) MetricDivider()
                }
            }
        }

        // Three trends in one card, over the last 170 days, each with the change
        // against the previous weighing and against the reading 170 days ago.
        BodyTrendCard(points)

        SectionTitle("计算说明")
        FlatPanel {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "身体得分和身体年龄都是本机算的，不是 Zepp 官方数值。",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "身体得分：从 100 分起扣，按 WHO/ACSM 体成分参考区间计算——BMI 每偏离 18.5–24 一档计 2 分；" +
                        "体脂每偏离标准区间 1% 计 1.5 分；肌肉每低于身高标准 1kg 计 2 分；" +
                        "内脏脂肪每高 1 级计 2 分；蛋白质每低 1% 计 1.5 分；基础代谢每偏离同身高体重年龄的 " +
                        "Mifflin-St Jeor 估算值 1% 计 0.5 分。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "身体年龄：Deurenberg 体脂-年龄公式反推（Br J Nutr, 1991）：" +
                        "年龄 =（体脂率 − 1.2 × BMI + 5.4 + 10.8 × 性别）÷ 0.23，" +
                        "男性性别取 1、女性取 0，结果限制在 18–80 岁。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** How far a body-composition reading is from its healthy band. */
private enum class BodyTone { NORMAL, WARN, BAD }

private class BodyMetric(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val value: String,
    val numeric: Double?,
    val rating: Pair<String, BodyTone>?,
    val min: Double,
    val max: Double,
    val bands: List<Triple<Double, Double, BodyTone>>,
    /** Threshold numbers printed under the bar at the colour cuts. */
    val ticks: List<Pair<Double, String>> = emptyList()
)

/** One body metric: label, rating, value, and a band-split bar at its value. */
@Composable
private fun BodyMetricRow(metric: BodyMetric) {
    val palette = LocalZgPalette.current
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    fun colorOf(tone: BodyTone): Color = when (tone) {
        BodyTone.WARN -> palette.warn
        BodyTone.BAD -> palette.critical
        BodyTone.NORMAL -> primary
    }
    val tone = metric.rating?.second
    val toneColor = if (tone == null || tone == BodyTone.NORMAL) MaterialTheme.colorScheme.onSurface
    else colorOf(tone)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                metric.icon,
                contentDescription = null,
                tint = colorOf(tone ?: BodyTone.NORMAL),
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.size(9.dp))
            Text(
                metric.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            metric.rating?.let {
                Text(
                    it.first,
                    style = MaterialTheme.typography.labelMedium,
                    // Rating and figure share one colour.
                    color = toneColor,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            Text(
                metric.value,
                style = MaterialTheme.typography.titleMedium,
                color = toneColor
            )
        }
        Spacer(Modifier.height(10.dp))
        if (metric.numeric != null) {
            BaselineScaleBar(
                value = metric.numeric,
                baseline = if (metric.bands.isEmpty()) metric.numeric else metric.min,
                min = metric.min,
                max = metric.max,
                bands = metric.bands.map { ScaleBand(it.first, it.second, colorOf(it.third)) },
                tickLabels = metric.ticks,
                markerColor = colorOf(metric.rating?.second ?: BodyTone.NORMAL),
                // Threshold cuts are thick white bars, same as the HRV gauge.
                bandDivider = surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            )
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(9.dp)
                    .clip(RoundedCornerShape(50))
                    .background(palette.grid)
            )
        }
    }
}

/** Thin rule between two metric rows; runs nearly the full card width. */
@Composable
private fun MetricDivider() {
    Box(
        Modifier
            .padding(vertical = 2.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

/** One card with three small trends: weight, body fat and muscle. */
@Composable
private fun BodyTrendCard(points: List<WeightPoint>) {
    val palette = LocalZgPalette.current
    SectionTitle("趋势", trailing = "近 1 个月")
    FlatPanel {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            BodyTrendRow("体重", "kg", points, palette.accent) { it.weightKg }
            MetricDivider()
            BodyTrendRow("体脂", "%", points, palette.warn) { it.bodyFatPct }
            MetricDivider()
            BodyTrendRow("肌肉", "kg", points, palette.sleepLight) { it.muscleKg }
        }
    }
}

@Composable
private fun BodyTrendRow(
    title: String,
    unit: String,
    points: List<WeightPoint>,
    color: Color,
    selector: (WeightPoint) -> Double?
) {
    val zone = ZoneId.systemDefault()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val series = points
        .mapNotNull { point ->
            val value = selector(point) ?: return@mapNotNull null
            point.epochMs to value
        }
        .sortedBy { it.first }
    fun dayOf(epochMs: Long): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()

    // The curve covers one month back from the newest reading of this source.
    val currentPoint = series.lastOrNull()
    val current = currentPoint?.second
    val lastDate = currentPoint?.let { dayOf(it.first) }
    val rangeStart = lastDate?.minusMonths(1)
    val plot = if (rangeStart != null && lastDate != null) {
        series.filter { dayOf(it.first) in rangeStart..lastDate }
    } else {
        series
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = muted,
                modifier = Modifier.weight(1f)
            )
            Text(
                current?.let { Fmt.oneDecimal(it) + " $unit" } ?: "--",
                style = MaterialTheme.typography.titleSmall
            )
        }
        Spacer(Modifier.height(8.dp))
        if (plot.size >= 2 && rangeStart != null && lastDate != null) {
            MiniTrendChart(
                values = plot,
                start = rangeStart,
                end = lastDate,
                color = color,
                valueLabel = { Fmt.oneDecimal(it) + unit },
                current = current,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(
                rangeStart?.let { Fmt.date(it) } ?: "--",
                style = MaterialTheme.typography.labelSmall,
                color = muted
            )
            Spacer(Modifier.weight(1f))
            Text(
                lastDate?.let { Fmt.date(it) } ?: "--",
                style = MaterialTheme.typography.labelSmall,
                color = muted
            )
        }
    }
}

/** Bare polyline for one body metric: no axes, the dates sit under the card. */
@Composable
private fun MiniTrendChart(
    values: List<Pair<Long, Double>>,
    start: LocalDate,
    end: LocalDate,
    color: Color,
    valueLabel: (Double) -> String,
    current: Double?,
    modifier: Modifier = Modifier
) {
    val zone = ZoneId.systemDefault()
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        fontSize = 9.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val badgeColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
    val grid = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)

    Canvas(modifier) {
        // X runs over the whole comparison window, so the curve shows the recent
        // month even when there are only a couple of readings.
        val inset = 4.dp.toPx()
        val startMs = start.atStartOfDay(zone).toInstant().toEpochMilli().toFloat()
        val endMs = end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli().toFloat()
        val xSpan = (endMs - startMs).coerceAtLeast(1f)
        val minV = values.minOf { it.second }
        val maxV = values.maxOf { it.second }
        val pad = ((maxV - minV) * 0.18).coerceAtLeast(0.5)
        val low = (minV - pad).toFloat()
        val spanY = ((maxV + pad).toFloat() - low).coerceAtLeast(0.1f)
        fun xOf(epochMs: Long) =
            (inset + (epochMs.toFloat() - startMs) / xSpan * (size.width - inset * 2f))
                .coerceIn(inset, size.width - inset)
        fun yOf(value: Double) =
            (size.height - (value.toFloat() - low) / spanY * size.height)
                .coerceIn(0f, size.height)

        fun levelLine(
            value: Double,
            lineColor: Color,
            label: String?,
            labelAbove: Boolean
        ) {
            val y = yOf(value)
            drawLine(
                color = lineColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx()
            )
            if (label != null) {
                val layout = measurer.measure(label, labelStyle)
                val gap = 6.dp.toPx()
                val top = if (labelAbove) y - layout.size.height - gap
                else y + gap
                drawText(
                    layout,
                    topLeft = Offset(
                        inset,
                        top.coerceIn(0f, size.height - layout.size.height)
                    )
                )
            }
        }

        // Highest and lowest of the window, labelled above/below their own line
        // on the left; the current level is a plain grey line (its number is
        // already above the chart).
        levelLine(maxV, color.copy(alpha = 0.55f), valueLabel(maxV), labelAbove = true)
        levelLine(minV, color.copy(alpha = 0.55f), valueLabel(minV), labelAbove = false)
        current?.let { levelLine(it, grid, null, labelAbove = true) }

        val path = Path()
        values.forEachIndexed { index, (epochMs, value) ->
            if (index == 0) path.moveTo(xOf(epochMs), yOf(value))
            else path.lineTo(xOf(epochMs), yOf(value))
        }
        drawPath(
            path,
            color = color,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
        val last = values.last()
        drawCircle(color, radius = 3.dp.toPx(), center = Offset(xOf(last.first), yOf(last.second)))
    }
}

// ---------------- SpO2 ----------------

@Composable
private fun Spo2Detail(data: HealthData?, date: LocalDate?) {
    val zone = ZoneId.systemDefault()
    val all: List<Spo2Point> = data?.spo2.orEmpty()
    val points = if (date != null) {
        all.filter { Instant.ofEpochMilli(it.epochMs).atZone(zone).toLocalDate() == date }
    } else {
        all
    }
    if (points.isEmpty()) {
        Missing(if (date != null) "这一天没有血氧读数" else "还没有血氧读数")
        return
    }
    val recent = points.takeLast(40)
    val latestSpo2 = recent.last().value
    val spo2Verdict = when {
        latestSpo2 >= 95 -> "正常" to MaterialTheme.colorScheme.onSurface
        latestSpo2 >= 90 -> "偏低" to LocalZgPalette.current.warn
        else -> "低" to LocalZgPalette.current.critical
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricTile(
            label = "最近",
            value = latestSpo2.toString(),
            unit = "%",
            verdict = spo2Verdict,
            modifier = Modifier.weight(1f)
        )
        MetricTile("平均", recent.map { it.value }.average().roundToInt().toString(), "%", Modifier.weight(1f))
        MetricTile("最低", recent.minOf { it.value }.toString(), "%", Modifier.weight(1f))
    }
    SectionTitle("最近读数", trailing = "自动 / 手动")
    FlatPanel {
        Column(Modifier.padding(16.dp)) {
            val labelEvery = (recent.size / 4).coerceAtLeast(1)
            val axisLabels = recent.indices.map { index ->
                if (index % labelEvery == 0 || index == recent.lastIndex) {
                    Fmt.time(recent[index].epochMs)
                } else ""
            }
            AxisLineChart(
                values = recent.map { it.value.toFloat() },
                labels = axisLabels,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                color = MaterialTheme.colorScheme.tertiary,
                minValue = 85f,
                maxValue = 100f,
                valueSuffix = "%"
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${Fmt.dayAndTime(recent.first().epochMs)} – ${Fmt.dayAndTime(recent.last().epochMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------------- workout ----------------

/**
 * The track, pinned above the scrolling page: it does not travel with the
 * document, it stays where it is while the cards slide up underneath, shrinking
 * and fading back as if it were receding. Reading the scroll inside the layer
 * keeps the page from recomposing while a finger moves.
 */
@Composable
private fun PinnedTrack(points: List<GeoPoint>, scroll: ScrollState, modifier: Modifier = Modifier) {
    val progress by remember {
        derivedStateOf { (scroll.value / 420f).coerceIn(0f, 1f) }
    }
    RoutePath(
        points = points,
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .graphicsLayer {
                val p = progress
                val shrink = 1f - 0.06f * p
                scaleX = shrink
                scaleY = shrink
                // Scroll up, track up: it leaves with the page instead of
                // hanging around, and is gone by the time the cards arrive.
                translationY = -40.dp.toPx() * p
                alpha = 1f - p
            }
            .blur((6f * progress).dp)
    )
}

@Composable
private fun WorkoutDetail(
    workout: Workout?,
    detail: WorkoutDetail?,
    showTrack: Boolean,
    onOpenMore: () -> Unit
) {
    if (workout == null) {
        Missing("还没有运动记录")
        return
    }
    val date = java.time.Instant.ofEpochSecond(workout.endEpochSec)
        .atZone(java.time.ZoneId.systemDefault()).toLocalDate()

    // The track itself is drawn by the page above this scrolling column; its
    // slot is reserved here so nothing shifts when the payload lands.
    if (showTrack) Spacer(Modifier.height(270.dp))

    // The three numbers people look for first, laid out like the heart-rate
    // strip on the activity page. A strength session has no distance, so that
    // slot shows its average heart rate instead.
    val distanceKm = workout.distanceM?.takeIf { it > 0.0 }
    val showsDistance = distanceKm != null
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
                Icons.Rounded.Timer,
                "时长",
                Fmt.duration(workout.durationSec),
                MaterialTheme.colorScheme.primary
            )
            StatBlock(
                Modifier.weight(1f),
                Icons.Rounded.LocalFireDepartment,
                "消耗",
                Fmt.int(workout.calories),
                LocalZgPalette.current.warn,
                unit = "kcal"
            )
            if (showsDistance) {
                StatBlock(
                    Modifier.weight(1f),
                    Icons.Rounded.Straighten,
                    "距离",
                    Fmt.km(distanceKm),
                    LocalZgPalette.current.accent,
                    unit = "km"
                )
            } else {
                StatBlock(
                    Modifier.weight(1f),
                    Icons.Rounded.Favorite,
                    "平均心率",
                    workout.avgHr?.roundToInt()?.toString() ?: "--",
                    MaterialTheme.colorScheme.tertiary,
                    unit = "bpm"
                )
            }
        }
    }

    // Everything else the watch attached to the session. Only the values that
    // are actually there get a tile: a missing field is left out rather than
    // printed as a dash, because this grid is about what the watch measured.
    val extras = workoutStats(workout, showsDistance)
    // The grid reads as pairs. An odd tile would sit alone in half a row, so the
    // last one moves into 其他数据 together with the raw watch fields.
    val paired = extras.size - extras.size % 2
    extras.take(paired).chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            pair.forEach { stat ->
                MetricTile(stat.label, stat.value, stat.unit, Modifier.weight(1f))
            }
        }
    }
    val hasMore = extras.size > paired

    // 训练效果: the watch's two 0–5 scales. The ring is cut into the rating
    // zones, each with its own faint colour, and a needle marks where this
    // session landed — that zone then takes its full colour.
    if (workout.aerobicTe != null || workout.anaerobicTe != null) {
        FlatPanel {
            Column(Modifier.padding(16.dp)) {
                Text("训练效果", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(22.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EffectGauge("有氧", workout.aerobicTe, Modifier.weight(1f))
                    EffectGauge("无氧", workout.anaerobicTe, Modifier.weight(1f))
                }
            }
        }
    }

    // The entry sits under the last card: a pill-shaped tap target with no
    // fill, so the press ripple still follows the capsule.
    if (hasMore) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { onOpenMore() }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "其他数据",
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
        }
    }

    // Only curves the watch actually recorded get a chart: an indoor session
    // has no speed line, a flat one has no shape to show. Each metric is its own
    // group: a stats card (average / max / min) and the curve below it.
    val series = detail?.series.orEmpty()
    series.forEach { line ->
        val isHeart = line.label == "心率"
        val tint = workoutSeriesTint(line.label)
        val real = line.values.filterNotNull()
        SectionTitle(line.label)
        FlatPanel {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SeriesStat(
                    Modifier.weight(1f),
                    "平均",
                    real.average().roundToInt().toString(),
                    line.unit,
                    tint
                )
                SeriesStat(
                    Modifier.weight(1f),
                    "最高",
                    real.max().roundToInt().toString(),
                    line.unit,
                    tint
                )
                SeriesStat(
                    Modifier.weight(1f),
                    "最低",
                    real.min().roundToInt().toString(),
                    line.unit,
                    tint
                )
            }
        }
        FlatPanel {
            AxisLineChart(
                values = line.values,
                labels = workoutTimeLabels(line.values.size, workout.durationSec),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 12.dp)
                    .height(150.dp),
                color = tint
            )
        }
        // Where the session sat on the watch's HR scale. The summary carries the
        // zone seconds even when the per-second curve is missing, so this card
        // can stand on its own.
        if (isHeart) HeartZoneCard(workout)
    }

    // Every remaining raw field the detail payload carried, with the keys that
    // have a known meaning translated. Unknown keys are still shown under their
    // own name, so a new watch metric appears without a code change.
}

/**
 * 心率区间, laid out like the Zepp app: one row per zone, highest first, with the
 * bpm band under its name and the share + duration on the right. The bars are a
 * share of the whole session, so a zone that took 79% of the time reads as such.
 *
 * The summary sends six buckets whose second number is the bucket's *ceiling*;
 * the first one is everything under 100 bpm, which Zepp does not show as a zone,
 * so only the five named bands are drawn. Each bucket's seconds sit one slot
 * later than its name starts, which is why `seconds[index + 1]` is read.
 */
@Composable
private fun HeartZoneCard(workout: Workout) {
    val seconds = workout.hrZoneSeconds
    if (seconds.size < 5 || seconds.sum() <= 0) return
    val palette = LocalZgPalette.current
    val zones = listOf(
        Triple("热身放松", 100, 119),
        Triple("脂肪燃烧", 120, 139),
        Triple("心肺强化", 140, 159),
        Triple("耐力强化", 160, 179),
        Triple("无氧极限", 180, 200)
    )
    val colors = listOf(palette.sleepRem, palette.accent, palette.sleepLight, palette.warn, palette.critical)
    val total = seconds.sum().coerceAtLeast(1)
    FlatPanel {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("心率区间", style = MaterialTheme.typography.titleSmall)
            // Descending, the way the app lists them: hardest zone on top.
            zones.indices.reversed().forEach { index ->
                val (name, low, high) = zones[index]
                val value = seconds.getOrElse(index + 1) { 0 }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "$low-$high 次/分",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text("${value * 100 / total}%", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.width(14.dp))
                        Text(Fmt.duration(value), style = MaterialTheme.typography.labelMedium)
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(palette.grid)
                    ) {
                        if (value > 0) {
                            Box(
                                Modifier
                                    .fillMaxWidth(value.toFloat() / total)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(colors[index])
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One tile of the workout grid: what it is called, the reading, its unit. */
private data class WorkoutStat(val label: String, val value: String, val unit: String?)

/**
 * The workout's secondary numbers, in display order. Shared by the page and by
 * 其他数据, which shows exactly the tail this list leaves over.
 */
private fun workoutStats(workout: Workout, showsDistance: Boolean): List<WorkoutStat> {
    val out = mutableListOf<WorkoutStat>()
    workout.paceSecPerKm?.let { out.add(WorkoutStat("配速", Fmt.pace(it), "/km")) }
    // 平均心率 only earns a tile when the strip above does not already show it.
    if (showsDistance) {
        workout.avgHr?.let { out.add(WorkoutStat("平均心率", it.roundToInt().toString(), "bpm")) }
    }
    workout.maxHr?.let { out.add(WorkoutStat("最高心率", it.roundToInt().toString(), "bpm")) }
    workout.load?.let { out.add(WorkoutStat("训练负荷", it.roundToInt().toString(), null)) }
    workout.steps?.let { out.add(WorkoutStat("步数", it.toString(), "步")) }
    workout.avgCadence?.let { out.add(WorkoutStat("平均步频", it.roundToInt().toString(), "spm")) }
    workout.maxCadence?.let { out.add(WorkoutStat("最高步频", it.roundToInt().toString(), "spm")) }
    workout.altitudeGainM?.let { out.add(WorkoutStat("累计爬升", it.roundToInt().toString(), "m")) }
    workout.altitudeLossM?.let { out.add(WorkoutStat("累计下降", it.roundToInt().toString(), "m")) }
    workout.vo2Max?.let { out.add(WorkoutStat("最大摄氧量", it.toString(), "ml/kg/min")) }
    return out
}

/** The workout's leftover tiles: the one that did not fit a pair in the grid. */
@Composable
private fun WorkoutMoreDetail(data: HealthData?, workoutId: String?) {
    val workout = data?.workouts?.firstOrNull { it.id == workoutId }
        ?: data?.workouts?.firstOrNull()
    if (workout == null) {
        Missing("还没有运动记录")
        return
    }

    val distanceKm = workout.distanceM?.takeIf { it > 0.0 }
    val stats = workoutStats(workout, showsDistance = distanceKm != null)
    val leftovers = stats.drop(stats.size - stats.size % 2)
    if (leftovers.isEmpty()) {
        Text(
            "这次运动没有额外的数据。",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    leftovers.chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            pair.forEach { stat ->
                MetricTile(stat.label, stat.value, stat.unit, Modifier.weight(1f))
            }
        }
    }
}

/** One rating zone of the 0–5 training-effect scale. */
private data class TeZone(val from: Double, val to: Double, val label: String, val color: Color)

/** The five bands the watch's own app colours the training-effect ring with. */
@Composable
private fun teZones(): List<TeZone> {
    val palette = LocalZgPalette.current
    val grey = MaterialTheme.colorScheme.onSurfaceVariant
    return listOf(
        TeZone(0.0, 1.0, "无效果", grey),
        TeZone(1.0, 2.0, "轻松", palette.sleepLight),
        TeZone(2.0, 3.0, "维持体能", palette.accent),
        TeZone(3.0, 4.0, "改善体能", palette.warn),
        TeZone(4.0, 5.0, "大幅提升", palette.critical)
    )
}

/**
 * 训练效果 gauge.
 *
 * The ring is cut into the rating bands, each drawn in its own faint colour, and
 * a needle points at the session's score: the band the needle sits in takes its
 * full colour, so the reading is "you are here" rather than a filled bar.
 */
@Composable
private fun EffectGauge(label: String, value: Double?, modifier: Modifier = Modifier) {
    val palette = LocalZgPalette.current
    val surface = MaterialTheme.colorScheme.surface
    val zones = teZones()
    val score = value?.coerceIn(0.0, 5.0)
    val active = zones.lastOrNull { score != null && score >= it.from && (score < it.to || it.to >= 5.0) }
        ?: zones.first()

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(108.dp)
        ) {
            val strokeDp = 10.dp
            // The dial is inset from the sides and the top; the bottom needs no
            // padding because a 220° arc already stops ~0.34·r above the box
            // bottom, which is exactly the space the name sits in.
            val sidePad = 18.dp
            val topPad = 2.dp
            val usableWidth = (maxWidth - sidePad * 2).coerceAtLeast(24.dp)
            val usableHeight = (maxHeight - topPad).coerceAtLeast(24.dp)
            val radius = minOf(
                usableWidth / 2f - strokeDp / 2f,
                (usableHeight - strokeDp) / 1.34f
            )
            val centerX = maxWidth / 2f
            val centerY = topPad + radius + strokeDp / 2f
            Canvas(Modifier.fillMaxSize()) {
                val stroke = strokeDp.toPx()
                val radius = radius.toPx()
                if (radius <= 0f) return@Canvas
                val start = 160f
                val sweep = 220f
                val center = Offset(centerX.toPx(), centerY.toPx())
                val arcSize = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
                val topLeft = Offset(center.x - radius, center.y - radius)
                fun angleOf(value: Double): Float =
                    start + sweep * (value / 5.0).coerceIn(0.0, 1.0).toFloat()

                // One rounded curve per band, edge to edge: the bands are split
                // afterwards by background-coloured ticks, not by gaps.
                // Base bands are drawn with flat ends — only the two tips of the
                // whole ring get a round cap. The highlighted band is a pill of
                // its own, rounded on both sides so it stands out from the base.
                fun band(
                    fromValue: Double,
                    toValue: Double,
                    tint: Color,
                    roundBoth: Boolean = false
                ) {
                    val from = angleOf(fromValue)
                    val to = angleOf(toValue)
                    if (to <= from) return
                    drawArc(
                        color = tint,
                        startAngle = from,
                        sweepAngle = to - from,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(
                            stroke,
                            cap = if (roundBoth) StrokeCap.Round else StrokeCap.Butt
                        )
                    )
                    if (roundBoth) return
                    fun capAt(angle: Float) {
                        val a = Math.toRadians(angle.toDouble())
                        drawCircle(
                            color = tint,
                            radius = stroke / 2f,
                            center = Offset(
                                center.x + (kotlin.math.cos(a) * radius).toFloat(),
                                center.y + (kotlin.math.sin(a) * radius).toFloat()
                            )
                        )
                    }
                    if (fromValue <= 0.0) capAt(from)
                    if (toValue >= 5.0) capAt(to)
                }

                zones.forEach { zone ->
                    // A flat mix of band colour over the card, not a translucent
                    // fill: the end caps are painted on top of the arc, and two
                    // translucent layers would show that overlap.
                    band(zone.from, zone.to, androidx.compose.ui.graphics.lerp(surface, zone.color, 0.20f))
                }

                // Thin surface-coloured cuts between the bands, drawn under the
                // highlighted band: its own colour reaches the border, the
                // neighbour stops a hair short of it.
                listOf(1.0, 2.0, 3.0, 4.0).forEach { border ->
                    val theta = Math.toRadians(angleOf(border).toDouble())
                    val cos = kotlin.math.cos(theta).toFloat()
                    val sin = kotlin.math.sin(theta).toFloat()
                    val inner = radius - stroke * 0.75f
                    val outer = radius + stroke * 0.75f
                    drawLine(
                        color = surface,
                        start = Offset(center.x + cos * inner, center.y + sin * inner),
                        end = Offset(center.x + cos * outer, center.y + sin * outer),
                        strokeWidth = stroke * 0.55f,
                        cap = StrokeCap.Butt
                    )
                }

                if (score != null) {
                    band(active.from, active.to, active.color, roundBoth = true)
                }

                if (score != null) {
                    // The marker, same shape as the one under resting heart rate
                    // and HRV: a rounded triangle with a surface halo, sitting
                    // inside the ring and pointing out at it.
                    val theta = Math.toRadians(angleOf(score).toDouble())
                    val tip = Offset(
                        center.x + (kotlin.math.cos(theta) * (radius - stroke / 2f)).toFloat(),
                        center.y + (kotlin.math.sin(theta) * (radius - stroke / 2f)).toFloat()
                    )
                    val markerWidth = 12.dp.toPx()
                    val markerHeight = 10.dp.toPx()
                    val edge = 1.5.dp.toPx()
                    val halo = 2.dp.toPx()
                    val marker = Path().apply {
                        moveTo(-markerWidth / 2f, markerHeight)
                        lineTo(markerWidth / 2f, markerHeight)
                        lineTo(0f, 0f)
                        close()
                    }
                    rotate(degrees = Math.toDegrees(theta).toFloat() + 90f, pivot = tip) {
                        translate(tip.x, tip.y) {
                            drawPath(
                                marker,
                                color = surface,
                                style = Stroke(
                                    width = edge * 2f + halo * 2f,
                                    join = StrokeJoin.Round,
                                    cap = StrokeCap.Round
                                )
                            )
                            drawPath(marker, color = active.color)
                            drawPath(
                                marker,
                                color = active.color,
                                style = Stroke(
                                    width = edge * 2f,
                                    join = StrokeJoin.Round,
                                    cap = StrokeCap.Round
                                )
                            )
                        }
                    }
                }
            }
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = (centerY - 22.dp).coerceAtLeast(0.dp)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    Fmt.oneDecimal(score),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    if (score == null) "--" else active.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (score == null) MaterialTheme.colorScheme.onSurfaceVariant
                    else active.color
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        // 有氧 / 无氧 names the ring, not the reading, so it sits under it.
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Colour per curve; anything the watch adds later falls back to the muted ink. */
@Composable
private fun workoutSeriesTint(label: String): Color = when {
    label.contains("心率") -> HeartRateRed
    label.contains("速度") || label.contains("配速") -> MaterialTheme.colorScheme.primary
    label.contains("步频") || label.contains("划频") -> MaterialTheme.colorScheme.tertiary
    label.contains("海拔") -> LocalZgPalette.current.warn
    label.contains("功率") -> MaterialTheme.colorScheme.secondary
    label.contains("温度") -> LocalZgPalette.current.warn
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** Seconds become a clock, metres become kilometres; everything else stays raw. */
private fun workoutFieldValue(key: String, raw: String): String {
    val number = raw.toDoubleOrNull() ?: return raw
    val lower = key.lowercase()
    return when {
        lower.contains("duration") || lower.endsWith("time") -> Fmt.duration(number.roundToInt())
        lower.contains("distance") -> Fmt.km(number) + " km"
        lower.contains("pace") -> Fmt.pace(number)
        else -> raw
    }
}

/** One average / max / min block, laid out like the activity page's stats. */
@Composable
private fun SeriesStat(
    modifier: Modifier,
    label: String,
    value: String,
    unit: String?,
    tint: Color
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(tint)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge
            )
            if (unit != null) {
                Text(
                    " $unit",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
        }
    }
}

/** Two clock labels at the ends of a per-second curve. */
private fun workoutTimeLabels(count: Int, durationSec: Int): List<String> {
    if (count < 2) return emptyList()
    fun clock(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)
    val labels = MutableList(count) { "" }
    labels[0] = clock(0)
    labels[count - 1] = clock(durationSec.coerceAtLeast(0))
    return labels
}

// ---------------- VO2 max ----------------

@Composable
private fun Vo2Detail(data: HealthData?) {
    val days = data?.days.orEmpty()
    val latestDay = days.lastOrNull { it.vo2Max != null }
    val latestWorkout = data?.workouts?.firstOrNull { it.vo2Max != null }
    val latest = data?.vo2Max ?: latestDay?.vo2Max ?: latestWorkout?.vo2Max
    if (latest == null) {
        Missing("手表还没有给出最大摄氧量")
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricTile("最大摄氧量", latest.toString(), "ml/kg/min", Modifier.weight(1f))
        MetricTile(
            "来源",
            latestDay?.let { Fmt.date(it.date) } ?: (latestWorkout?.let { "${it.name} ${Fmt.date(java.time.Instant.ofEpochSecond(it.endEpochSec).atZone(java.time.ZoneId.systemDefault()).toLocalDate())}" } ?: "手表最近一次测算"),
            null,
            Modifier.weight(1f)
        )
    }
    // Fourteen calendar days ending on the last estimate, so the gaps between
    // re-estimates show as gaps rather than as evenly spaced points.
    val vo2End = latestDay?.date ?: java.time.LocalDate.now()
    val vo2ByDate = days.associateBy { it.date }
    val history = (0..13).map { vo2ByDate[vo2End.minusDays(13L - it)] }
    if (history.count { it?.vo2Max != null } > 1) {
        SectionTitle("近 14 天")
        FlatPanel {
            Column(Modifier.padding(14.dp)) {
                AxisLineChart(
                    values = history.map { it?.vo2Max?.toFloat() },
                    labels = (0..13).map { Fmt.weekdayShort(vo2End.minusDays(13L - it)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
    val workouts = data?.workouts?.filter { it.vo2Max != null }.orEmpty().take(8)
    if (workouts.isNotEmpty()) {
        SectionTitle("运动记录里的值", trailing = "手表在一次运动后测算")
        FlatPanel {
            Column(Modifier.padding(16.dp)) {
                workouts.forEach { workout ->
                    DataRow(
                        "${Fmt.dayAndTime(workout.endEpochSec * 1000)} ${workout.name}",
                        "${workout.vo2Max} ml/kg/min"
                    )
                }
            }
        }
    }
    Text(
        "数值由手表根据静息心率与运动表现估算；本机没有这个字段时显示 --。",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ScoreRing(
    progress: Float,
    value: String,
    caption: String,
    color: Color,
    size: Dp = 156.dp,
    valueColor: Color = Color.Unspecified
) {
    ProgressRing(
        progress.coerceIn(0f, 1f),
        Modifier.size(size),
        strokeWidth = 10.dp,
        color = color,
        trackColor = color.copy(alpha = 0.16f)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                value,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Medium,
                color = valueColor
            )
            Text(
                caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StrainDetail(data: HealthData?, date: LocalDate?) {
    val source = data ?: run {
        Missing("还没有足够的心率采样来估算负荷")
        return
    }
    // Today is still in progress until the night ends: after midnight the load
    // keeps adding to the day being closed instead of resetting (see
    // HealthData.strainDateNow and StrainCalculator).
    // The overview opens the day that is still accumulating (yesterday until the
    // night ends), so opening from a card with today's date must look that day up
    // rather than the empty calendar day — that mismatch was the "has a value but
    // no samples" page.
    val strain = when {
        date != null && date != LocalDate.now() -> source.strainOn(date)
        else -> source.strainOn(source.strainDateNow())
    }
    if (strain == null || strain.strain == null) {
        Missing("这一天还没有足够的心率采样来估算负荷")
        return
    }
    // Over 15 of 21 the ring and its number both take the alert colour.
    val high = (strain.strain ?: 0.0) >= 15.0
    val ringColor = if (high) LocalZgPalette.current.critical else MaterialTheme.colorScheme.tertiary
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ScoreRing(
            progress = ((strain.strain ?: 0.0) / 21.0).toFloat(),
            value = String.format("%.1f", strain.strain ?: 0.0),
            caption = "负荷 / 21",
            color = ringColor,
            valueColor = ringColor
        )
        Spacer(Modifier.height(12.dp))
        Text(
            strain.band,
            style = MaterialTheme.typography.titleSmall,
            color = ringColor
        )
        Spacer(Modifier.height(18.dp))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricTile("心率负荷", strain.trimp?.roundToInt()?.toString() ?: "--", null, Modifier.weight(1f))
        MetricTile("采样分钟", strain.sampleMinutes.toString(), "min", Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricTile("平均心率", strain.averageHr?.toString() ?: "--", "bpm", Modifier.weight(1f))
        MetricTile("最高心率", strain.maxHr?.toString() ?: "--", "bpm", Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricTile(
            "脑力负荷",
            strain.mental?.roundToInt()?.toString() ?: "--",
            "/100",
            Modifier.weight(1f)
        )
        MetricTile(
            "恢复系数",
            strain.recovery?.let { String.format("×%.2f", it) } ?: "--",
            null,
            Modifier.weight(1f)
        )
    }
    val zones = strain.zoneMinutes
    if (zones.isNotEmpty()) {
        SectionTitle("强度区间分钟", trailing = "轻 – Z5")
        FlatPanel {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                zones.forEachIndexed { index, minutes ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            listOf("轻", "Z1", "Z2", "Z3", "Z4", "Z5").getOrElse(index) { "Z$index" },
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.width(26.dp)
                        )
                        Box(
                            Modifier
                                .weight(1f)
                                .height(6.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth((minutes / 120f).coerceIn(0f, 1f))
                                    .height(6.dp)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp))
                            )
                        }
                        Text(
                            "$minutes min",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
    // Fourteen calendar days ending on the day being read.
    val strainDays = (0..13).map { strain.date.minusDays(13L - it) }
    val strainByDate = data.strain.associateBy { it.date }
    val strainSeries = strainDays.map { strainByDate[it]?.strain?.toFloat() }
    if (strainSeries.count { it != null } > 1) {
        SectionTitle("近 14 天")
        FlatPanel {
            com.genkaim.zeppglass.ui.components.AxisBarChart(
                values = strainSeries,
                labels = strainDays.map { Fmt.weekdayShort(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
                    .height(150.dp),
                color = MaterialTheme.colorScheme.primary,
                valueLabel = { String.format("%.1f", it) }
            )
        }
    }
    SectionTitle("负荷反映了什么")
    FlatPanel {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "它反映的是这一天身体总共承担了多少，而不只是练了多少。一次训练当然算，赶路、搬家、" +
                    "站着开会讲一下午也算——只要心率被持续推高，身体就在付出。分数按你自己的心率范围算：" +
                    "同样的配速，心肺好的人得分更低，所以它适合和自己比，不适合和别人比。",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "大部分分数来自心率：离静息越远、停留越久，加得越多。持续的中高强度比几次短暂飙升更重，" +
                    "所以一段长时间的轻松骑，可能比几分钟冲刺更有分量。手环采不到间歇的峰值，运动记录会补上这一段；" +
                    "同一时段只取较高的一次，一场训练不会被算两遍。步数只是很小的日常活动量，久坐的一天靠它拉不起来。",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "它也想反映那些心率看不出来的部分。脑力劳动几乎不抬心率，却会让人整天紧绷：全天的压力和精力消耗" +
                    "合成 0 到 100 的脑力负荷，折算成一小块加进总分，最多相当于一次中等训练的三分之一。" +
                    "恢复状态同样算在里面——HRV 低于自己的基线、静息心率偏高，说明身体还没缓过来，" +
                    "同样的活动会被算得更重；两项都正常时不做任何加减。",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "分数不是线性的：越往上，每加一分需要的付出越多，所以上午一场、下午一场不会简单相加。" +
                    "10 以下偏轻，10 到 14 是适度，15 以上偏高，18 以上很少见，通常只在比赛或长距离挑战时出现。" +
                    "它没有标准答案：分数高而准备度低，第二天适合轻松一点；连续几天偏高，说明恢复跟不上。",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}


// ---------------- resting heart rate / HRV ----------------

@Composable
private fun RhrDetail(data: HealthData?, date: LocalDate?) {
    val source = data ?: run {
        Missing("还没有静息心率")
        return
    }
    val day = if (date != null) {
        source.readiness.lastOrNull { it.date == date }
    } else {
        source.latestReadiness()
    }
    val value = day?.sleepRhr
    if (day == null || value == null) {
        Missing("这一天没有静息心率")
        return
    }
    // Seven calendar days ending on the day being read. Keeping the last seven
    // *records* instead stretched the window back over the days the watch did
    // not score, so the axis started too early and the newest point sat on the
    // left whenever the list came back newest-first.
    val rhrDays = (0..6).map { day.date.minusDays(6L - it) }
    val rhrByDate = source.readiness.associateBy { it.date }
    val rhrSeries = rhrDays.map { rhrByDate[it]?.sleepRhr?.toFloat() }

    FlatPanel {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                Fmt.date(day.date),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            val rhrVerdict = when {
                day.rhrScore == null -> null
                day.rhrScore >= 80 -> "优秀" to MaterialTheme.colorScheme.onSurface
                day.rhrScore >= 60 -> "良好" to MaterialTheme.colorScheme.onSurface
                day.rhrScore >= 40 -> "一般" to LocalZgPalette.current.warn
                else -> "偏低" to LocalZgPalette.current.critical
            }
            Row(verticalAlignment = Alignment.Bottom) {
                val abnormal = rhrVerdict != null && rhrVerdict.first in setOf("一般", "偏低")
                Text(
                    value.toString(),
                    style = MaterialTheme.typography.displayMedium,
                    color = if (abnormal) rhrVerdict.second else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    " 次/分",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (abnormal) rhrVerdict.second else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            if (rhrVerdict != null) {
                Text(
                    rhrVerdict.first,
                    style = MaterialTheme.typography.titleSmall,
                    color = rhrVerdict.second
                )
            }
            // Centre is the personal baseline, the fill runs up to today: right
            // of the centre reads higher than usual, left reads lower. A day
            // without a baseline of its own borrows the nearest day's, so the
            // gauge is there on every date instead of only on today.
            val rhrGaugeBase = day.rhrBaseline ?: source.readiness
                .filter { it.rhrBaseline != null }
                .minByOrNull { abs(it.date.toEpochDay() - day.date.toEpochDay()) }
                ?.rhrBaseline
            rhrGaugeBase?.let { base ->
                Spacer(Modifier.height(14.dp))
                BaselineScaleBar(
                    value = value.toDouble(),
                    baseline = base.toDouble(),
                    min = base * 0.6,
                    max = base * 1.4,
                    fillColor = MaterialTheme.colorScheme.primary,
                    leftLabel = "低",
                    centerLabel = "基线 ${base}次/分",
                    rightLabel = "高",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                )
            }
        }
    }

    if (rhrSeries.count { it != null } > 1) {
        SectionTitle("近 7 天")
        FlatPanel {
            TrendLineChart(
                values = rhrSeries,
                labels = rhrDays.map { Fmt.weekdayShort(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 12.dp)
                    .height(170.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }

    SectionTitle("什么是静息心率？")
    FlatPanel {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "静息心率是清醒、身体完全放松、平躺休息时测得的每分钟心跳次数（bpm）。数值越低，通常说明心肺适应越好；熬夜、酒精、生病或高负荷会让它升高。",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun HrvDetail(data: HealthData?, date: LocalDate?) {
    val source = data ?: run {
        Missing("还没有心率变异性")
        return
    }
    val day = if (date != null) {
        source.readiness.lastOrNull { it.date == date }
    } else {
        source.latestReadiness()
    }
    val value = day?.sleepHrv
    if (day == null || value == null) {
        Missing("这一晚没有心率变异性")
        return
    }
    val night = source.nightOn(day.date)
    // Select by the actual sleep window: the watch buckets HRV by UTC day, so
    // grouping the samples by local date would cut the first half of the night.
    val nightSamples = night?.let { n ->
        source.hrv.filter { it.epochMs / 1000 in n.startEpochSec..n.endEpochSec }
    }.orEmpty()
    val hrvDays = (0..6).map { day.date.minusDays(6L - it) }
    val hrvByDate = source.readiness.associateBy { it.date }
    val hrvSeries = hrvDays.map { hrvByDate[it]?.sleepHrv?.toFloat() }

    FlatPanel {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                Fmt.date(day.date),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            val hrvVerdict = when {
                day.hrvScore == null -> null
                day.hrvScore >= 80 -> "优秀" to MaterialTheme.colorScheme.onSurface
                day.hrvScore >= 60 -> "良好" to MaterialTheme.colorScheme.onSurface
                day.hrvScore >= 40 -> "一般" to LocalZgPalette.current.warn
                else -> "偏低" to LocalZgPalette.current.critical
            }
            Row(verticalAlignment = Alignment.Bottom) {
                val abnormal = hrvVerdict != null && hrvVerdict.first in setOf("一般", "偏低")
                Text(
                    value.toString(),
                    style = MaterialTheme.typography.displayMedium,
                    color = if (abnormal) hrvVerdict.second else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    " 毫秒",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (abnormal) hrvVerdict.second else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            if (hrvVerdict != null) {
                Text(
                    hrvVerdict.first,
                    style = MaterialTheme.typography.titleSmall,
                    color = hrvVerdict.second
                )
            }
            // Same gauge as resting heart rate, and the same fallback: dates
            // without their own baseline use the nearest one so the bar is
            // always there.
            val hrvGaugeBase = day.hrvBaseline ?: source.readiness
                .filter { it.hrvBaseline != null }
                .minByOrNull { abs(it.date.toEpochDay() - day.date.toEpochDay()) }
                ?.hrvBaseline
            hrvGaugeBase?.let { base ->
                Spacer(Modifier.height(14.dp))
                BaselineScaleBar(
                    value = value.toDouble(),
                    baseline = base.toDouble(),
                    min = base * 0.6,
                    max = base * 1.4,
                    fillColor = MaterialTheme.colorScheme.primary,
                    leftLabel = "低",
                    centerLabel = "基线 ${base}毫秒",
                    rightLabel = "高",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                )
            }
        }
    }

    if (night != null) {
        SectionTitle("夜间时间线")
        FlatPanel {
            if (nightSamples.size > 2) {
                Column(Modifier.padding(16.dp)) {
                    HrvDayChart(
                        values = nightSamples.map { it.value.toFloat() },
                        startLabel = Fmt.timeSec(night.startEpochSec),
                        endLabel = Fmt.timeSec(night.endEpochSec),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                    )
                }
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        "这一晚没有连续 HRV 采样",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (hrvSeries.count { it != null } > 1) {
        SectionTitle("近 7 天")
        FlatPanel {
            TrendLineChart(
                values = hrvSeries,
                labels = hrvDays.map { Fmt.weekdayShort(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 12.dp)
                    .height(170.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }

    SectionTitle("什么是心率变异性？")
    FlatPanel {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "HRV（rMSSD）是相邻心跳间隔的细微差异，单位毫秒。数值高通常说明恢复好、压力低；持续偏低往往意味着疲劳、睡眠不足或生病。",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun Missing(text: String) {
    FlatPanel {
        Column(Modifier.padding(18.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "没有采样时不会用 0 或上一天的值顶替。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}












// ---------------- sleep stage / vital blocks ----------------

/**
 * One sleep stage: coloured duration plus a one-word verdict underneath.
 * The verdict compares the stage's share of the night against the usual band,
 * which is the part of a stage number people cannot judge by themselves.
 */
@Composable
private fun StageBlock(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    tint: Color,
    verdict: Pair<String, Color>?,
    suffix: String? = null
) {
    Column(modifier, horizontalAlignment = Alignment.Start) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
            Spacer(Modifier.size(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(5.dp))
        // Value and verdict share one line: the rating reads as a gloss on the
        // number instead of adding a second row that makes cards uneven.
        Row(verticalAlignment = Alignment.Bottom) {
            val abnormal = verdict?.first?.let { it == "偏低" || it == "偏高" } == true
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                color = if (abnormal) verdict!!.second else MaterialTheme.colorScheme.onSurface
            )
            if (suffix != null) {
                // The share is a gloss on the duration, not a second number:
                // smaller type, in brackets, right after the time.
                Text(
                    suffix,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (abnormal) verdict!!.second else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 2.dp, bottom = 3.dp)
                )
            }
            if (verdict != null) {
                Text(
                    verdict.first,
                    style = MaterialTheme.typography.labelSmall,
                    color = verdict.second,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 4.dp, bottom = 3.dp)
                )
            }
        }
    }
}

/** A sleep vital with its rating; opens the 7-day trend when there is one. */
@Composable
private fun SleepVitalTile(
    label: String,
    value: String,
    unit: String,
    tint: Color,
    verdict: Pair<String, Color>?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)?
) {
    // Not MetricTile: there the value is always ink-coloured and the caption
    // always grey, while these cards are meant to carry the metric's colour
    // and a coloured verdict.
    val hasValue = value.isNotBlank() && value != "--"
    FlatPanel(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        onClick = onClick.takeIf { hasValue }
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                val abnormal = verdict != null && verdict.first != "正常"
                Text(
                    value,
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (abnormal) verdict.second else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    " $unit",
                    style = MaterialTheme.typography.labelSmall,
                    // The unit is part of the reading, so it takes the same
                    // colour as the number and its verdict.
                    color = if (abnormal) verdict.second else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
                if (verdict != null) {
                    Text(
                        verdict.first,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (verdict.first == "正常") {
                            MaterialTheme.colorScheme.onSurface
                        } else verdict.second,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 4.dp, bottom = 3.dp)
                    )
                }
            }
        }
    }
}

/** One column of the 睡眠规律性 header: label, big value, optional verdict. */
@Composable
private fun RhythmBlock(
    modifier: Modifier,
    label: String,
    value: String,
    tint: Color,
    verdict: String? = null
) {
    Column(modifier, horizontalAlignment = Alignment.Start) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            val abnormal = verdict != null && verdict != "正常"
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                color = if (abnormal) tint else MaterialTheme.colorScheme.onSurface
            )
            if (verdict != null) {
                Text(
                    verdict,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (abnormal) tint else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 4.dp, bottom = 3.dp)
                )
            }
        }
    }
}

/**
 * Sleep timing over a window of nights, all expressed in minutes since 18:00 so
 * a night that crosses midnight stays one monotonic number.
 */
private class SleepRhythm(
    val averageOnset: Int,
    val averageWake: Int,
    /** 100 minus the typical distance from the average bedtime. */
    val score: Int
)

/** Minutes of deviation that take the regularity score to zero. */
private const val REGULARITY_TOLERANCE_MIN = 110.0

private fun sleepRhythm(nights: List<SleepNight>): SleepRhythm? {
    // A nap or a broken recording is not a bedtime: under three hours the row
    // is left out instead of dragging the average.
    val usable = nights.filter { it.totalMinutes >= 180 }
    if (usable.isEmpty()) return null
    val zone = ZoneId.systemDefault()
    fun minutesSinceEvening(epochSec: Long): Int {
        val time = Instant.ofEpochSecond(epochSec).atZone(zone).toLocalTime()
        return ((time.hour * 60 + time.minute) - 18 * 60 + 1440) % 1440
    }
    val onsets = usable.map { minutesSinceEvening(it.startEpochSec) }
    // Wake times past 18:00 belong to the next evening; add a day so an early
    // riser and a late one land on the same scale.
    val wakes = usable.map { minutesSinceEvening(it.endEpochSec) }
    val onsetMean = onsets.average()
    // Mean absolute distance, not standard deviation: squaring made one late
    // night dominate the whole week, so a steady sleeper who stayed up once
    // read as irregular. Each night now contributes its own share.
    val spread = onsets.sumOf { kotlin.math.abs(it - onsetMean) } / onsets.size
    // Back to clock minutes for display: the offset was only there to keep the
    // arithmetic monotonic across midnight.
    fun toClock(minutesSinceEvening: Int): Int = (minutesSinceEvening + 18 * 60) % 1440
    return SleepRhythm(
        averageOnset = toClock(onsetMean.roundToInt()),
        averageWake = toClock(wakes.average().roundToInt()),
        score = (100 - spread * 100.0 / REGULARITY_TOLERANCE_MIN).roundToInt().coerceIn(0, 100)
    )
}

// ---------------- morning charge ----------------

@Composable
private fun MorningChargeDetail(data: HealthData?, date: LocalDate?) {
    val source = data ?: run {
        Missing("还没有 Charge 数据")
        return
    }
    fun morningOn(target: LocalDate): Int? =
        source.chargeOn(target)?.morningScore(source.nightOn(target)?.endEpochSec)
    val day = date ?: source.chargeDays.maxByOrNull { it.date }?.date
    if (day == null) {
        Missing("还没有 Charge 数据")
        return
    }
    val value = morningOn(day)
    if (value == null) {
        Missing("这一天没有晨起 Charge")
        return
    }
    val palette = LocalZgPalette.current
    val ink = MaterialTheme.colorScheme.onSurface
    val verdict = when {
        value >= 80 -> "优秀" to ink
        value >= 60 -> "一般" to palette.warn
        else -> "偏低" to palette.critical
    }
    FlatPanel {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                Fmt.date(day),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            val abnormal = verdict.first != "优秀"
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value.toString(),
                    style = MaterialTheme.typography.displayMedium,
                    color = if (abnormal) verdict.second else ink
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(verdict.first, style = MaterialTheme.typography.titleSmall, color = verdict.second)
        }
    }

    val days = (0..6).map { day.minusDays(6L - it) }
    val series = days.map { morningOn(it)?.toFloat() }
    if (series.count { it != null } > 1) {
        SectionTitle("近 7 天")
        FlatPanel {
            TrendLineChart(
                values = series,
                labels = days.map { Fmt.weekdayShort(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 12.dp)
                    .height(170.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }

    SectionTitle("这个分数")
    FlatPanel {
        Column(Modifier.padding(16.dp)) {
            Text(
                "晨起 Charge 是醒来时的能量值（0–100），取起床前后的 Charge 读数；它反映昨晚恢复给今天留下的精力，白天会随活动下降。",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

// ---------------- night blood oxygen ----------------

@Composable
private fun NightSpo2Detail(data: HealthData?, date: LocalDate?, withDayStats: Boolean = false) {
    val source = data ?: run {
        Missing("还没有血氧数据")
        return
    }
    // The samples inside one night's sleep window; the day's stats and the
    // headline average both come from this same set, so they always agree.
    fun pointsOn(target: LocalDate): List<Spo2Point> {
        val night = source.nightOn(target) ?: return emptyList()
        return source.spo2.filter { it.epochMs / 1000 in night.startEpochSec..night.endEpochSec }
    }
    fun averageOn(target: LocalDate): Int? {
        val points = pointsOn(target)
        return if (points.isEmpty()) null else points.map { it.value }.average().roundToInt()
    }
    val day = date ?: source.nights.maxByOrNull { it.date }?.date
    if (day == null) {
        Missing("还没有血氧数据")
        return
    }
    val value = averageOn(day)
    if (value == null) {
        Missing("这一晚没有血氧记录")
        return
    }
    val palette = LocalZgPalette.current
    val ink = MaterialTheme.colorScheme.onSurface
    val verdict = when {
        value >= 95 -> "正常" to ink
        value >= 90 -> "偏低" to palette.warn
        else -> "低" to palette.critical
    }
    FlatPanel {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                Fmt.date(day),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            val abnormal = verdict.first != "正常"
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value.toString(),
                    style = MaterialTheme.typography.displayMedium,
                    color = if (abnormal) verdict.second else ink
                )
                Text(
                    " %",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (abnormal) verdict.second else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(verdict.first, style = MaterialTheme.typography.titleSmall, color = verdict.second)
        }
    }

    if (withDayStats) {
        val points = pointsOn(day)
        if (points.isNotEmpty()) {
            val values = points.map { it.value }
            SectionTitle("日统计")
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
                        Icons.Rounded.Schedule,
                        "日平均值",
                        values.average().roundToInt().toString(),
                        MaterialTheme.colorScheme.primary,
                        unit = "%"
                    )
                    StatBlock(
                        Modifier.weight(1f),
                        Icons.Rounded.ArrowUpward,
                        "日最高值",
                        values.max().toString(),
                        MaterialTheme.colorScheme.tertiary,
                        unit = "%"
                    )
                    StatBlock(
                        Modifier.weight(1f),
                        Icons.Rounded.ArrowDownward,
                        "日最低值",
                        values.min().toString(),
                        palette.sleepRem,
                        unit = "%"
                    )
                }
            }
            // The night's samples as one bar per hour, the same time chart the
            // blood pressure page uses: from that hour's lowest to its highest
            // reading, coloured by the lowest (the hour is as good as its worst).
            FlatPanel {
                Spo2NightChart(
                    points = points,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                        .height(170.dp)
                )
            }
        }
    }

    val days = (0..6).map { day.minusDays(6L - it) }
    val series = days.map { averageOn(it)?.toFloat() }
    if (series.count { it != null } > 1) {
        SectionTitle("近 7 天")
        FlatPanel {
            TrendLineChart(
                values = series,
                labels = days.map { Fmt.weekdayShort(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 12.dp)
                    .height(170.dp),
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

// ---------------- skin temperature ----------------

@Composable
private fun SkinTempDetail(data: HealthData?, date: LocalDate?) {
    val source = data ?: run {
        Missing("还没有皮肤温度数据")
        return
    }
    val day = date?.let { d ->
        source.readiness.firstOrNull { it.date == d && it.skinTempScore != null }
    } ?: source.readiness.lastOrNull { it.skinTempScore != null }
    val value = day?.skinTempScore
    val delta = day?.skinTempDeltaC
    if (day == null || (value == null && delta == null)) {
        Missing("手表还没有给出皮肤温度")
        return
    }
    val palette = LocalZgPalette.current
    val ink = MaterialTheme.colorScheme.onSurface
    // Rated on the watch's own 0–100 skin-temperature score, with the same
    // thresholds as the HRV and resting-heart-rate pages (80 优秀 / 60 良好 /
    // 40 一般). The +-℃ figure stays the headline because it is what changed.
    val verdict: Pair<String, Color> = when {
        value != null -> when {
            value >= 80 -> "优秀" to ink
            value >= 60 -> "良好" to ink
            value >= 40 -> "一般" to palette.warn
            else -> "偏低" to palette.critical
        }
        // No score from the watch: fall back to the deviation bands.
        delta != null -> when {
            abs(delta) <= 0.5 -> "正常" to ink
            abs(delta) <= 1.0 -> (if (delta > 0) "偏高" else "偏低") to palette.warn
            else -> (if (delta > 0) "偏高" else "偏低") to palette.critical
        }
        else -> "正常" to ink
    }
    FlatPanel {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                Fmt.date(day.date),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            val abnormal = verdict.first !in setOf("正常", "优秀", "良好")
            // The official app prints the night's deviation from the personal
            // baseline with one decimal ("-0.2℃"), and rounds to a bare 0.0 on a
            // night that sits right on it.
            val hero = delta?.let { Fmt.signedOneDecimal(it) } ?: value.toString()
            val heroUnit = if (delta != null) " ℃" else " 分"
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    hero,
                    style = MaterialTheme.typography.displayMedium,
                    color = if (abnormal) verdict.second else ink
                )
                Text(
                    heroUnit,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (abnormal) verdict.second else ink,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Text(verdict.first, style = MaterialTheme.typography.titleSmall, color = verdict.second)
            if (delta != null) {
                Spacer(Modifier.height(14.dp))
                // Fixed ±1.5℃ scale split at the ±0.5 and ±1 thresholds, the way
                // the Zepp app draws it: only the normal stretch is filled, and
                // the marker takes the rating colour when tonight left it.
                BaselineScaleBar(
                    value = delta,
                    baseline = 0.0,
                    min = -1.5,
                    max = 1.5,
                    markerColor = if (abnormal) verdict.second else palette.accent,
                    bands = listOf(
                        ScaleBand(-1.5, -1.0, palette.grid),
                        ScaleBand(-1.0, -0.5, palette.grid),
                        ScaleBand(-0.5, 0.5, palette.accent),
                        ScaleBand(0.5, 1.0, palette.grid),
                        ScaleBand(1.0, 1.5, palette.grid)
                    ),
                    tickLabels = listOf(
                        -1.0 to "-1℃",
                        -0.5 to "-0.5℃",
                        0.5 to "+0.5℃",
                        1.0 to "+1℃"
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                )
            }
        }
    }

    val recent = (0..6).map { day.date.minusDays(6L - it) }
    // Deviations around zero: above the line means warmer than the personal
    // baseline, below means colder. Days without a calibrated reading stay empty
    // instead of borrowing the score.
    val series = recent.map { d ->
        source.readiness.firstOrNull { it.date == d }?.skinTempDeltaC?.toFloat()
    }
    if (series.count { it != null } > 1) {
        SectionTitle("近 7 天")
        FlatPanel {
            SignedBarChart(
                values = series,
                labels = recent.map { Fmt.weekdayShort(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 12.dp)
                    .height(170.dp),
                color = palette.warn,
                decimals = 1
            )
        }
    }
}

// ---------------- blood pressure ----------------

@Composable
private fun BloodPressureDetail(data: HealthData?, date: LocalDate?) {
    val palette = LocalZgPalette.current
    val ink = MaterialTheme.colorScheme.onSurface
    val zone = java.time.ZoneId.systemDefault()
    // TEMP: a few made-up readings so the chart can be eyeballed while the
    // account has no blood pressure at all. Debug builds only; the real data
    // always wins once the account has any.
    fun previewReadings(day: LocalDate): List<BloodPressure> {
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        fun at(hour: Int, minute: Int) = start + (hour * 60L + minute) * 60_000L
        // Twelve readings every hour, so the chart shows what a dense day looks
        // like; the numbers wander in a realistic band instead of a flat line.
        return buildList {
            for (hour in 0 until 24) {
                for (i in 0 until 12) {
                    val systolic = 108 + ((hour * 7 + i * 3) % 44)
                    val diastolic = 68 + ((hour * 5 + i * 2) % 26)
                    add(BloodPressure(at(hour, i * 5), systolic, diastolic))
                }
            }
        }
    }

    val readings = data?.bloodPressure.orEmpty().ifEmpty {
        if (BuildConfig.DEBUG) previewReadings(date ?: LocalDate.now()) else emptyList()
    }
    fun readingsOn(day: LocalDate): List<BloodPressure> = readings.filter {
        java.time.Instant.ofEpochMilli(it.epochMs).atZone(zone).toLocalDate() == day
    }
    val chosenDay = date ?: LocalDate.now()
    val dayReadings = readingsOn(chosenDay)
    // An empty today is the normal case: a cuff reading happens now and then,
    // not every day, so the page falls back to the newest day that has one.
    val newestDay = readings.maxByOrNull { it.epochMs }
        ?.let { java.time.Instant.ofEpochMilli(it.epochMs).atZone(zone).toLocalDate() }
    val shownDay = if (dayReadings.isEmpty()) newestDay ?: chosenDay else chosenDay
    val shownReadings = if (dayReadings.isEmpty()) readingsOn(shownDay) else dayReadings
    val latest = shownReadings.maxByOrNull { it.epochMs }

    fun tone(band: BloodPressureBand): Color = when (band) {
        BloodPressureBand.NORMAL -> palette.accent
        BloodPressureBand.ELEVATED -> palette.warn
        BloodPressureBand.HIGH -> palette.critical
    }

    fun word(band: BloodPressureBand): String = when (band) {
        BloodPressureBand.NORMAL -> "正常"
        BloodPressureBand.ELEVATED -> "正常高值"
        BloodPressureBand.HIGH -> "偏高"
    }

    // Top: the newest reading of the shown day, systolic over diastolic.
    FlatPanel {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                latest?.let { "${Fmt.date(shownDay)} ${Fmt.time(it.epochMs)}" } ?: "云端还没有血压记录",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            val abnormal = latest != null && latest.band != BloodPressureBand.NORMAL
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    latest?.systolic?.toString() ?: "--",
                    style = MaterialTheme.typography.displayMedium,
                    color = if (abnormal) tone(latest!!.band) else ink
                )
                Text(
                    " / ",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (abnormal) tone(latest!!.band) else ink,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Text(
                    latest?.diastolic?.toString() ?: "--",
                    style = MaterialTheme.typography.headlineLarge,
                    color = if (abnormal) tone(latest!!.band) else ink,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    " mmHg",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (abnormal) tone(latest!!.band) else ink,
                    modifier = Modifier.padding(start = 2.dp, bottom = 8.dp)
                )
            }
            if (latest != null) {
                Text(
                    word(latest.band),
                    style = MaterialTheme.typography.titleSmall,
                    color = tone(latest.band)
                )
            }
        }
    }

    if (readings.isEmpty()) {
        FlatPanel {
            Column(Modifier.padding(16.dp)) {
                Text("还没有血压数据", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "血压来自 Zepp 里的手动记录（健康 → 血压），手表的传感器不测血压。" +
                        "在那边记一条之后，这里会出现当天的每小时范围和一周的曲线。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    // Middle: the day's readings on a 24-hour axis. Each reading is its own
    // short bar from diastolic to systolic, so the gaps between measurements
    // stay visible instead of being filled by an hour-wide column.
    SectionTitle(if (shownDay == LocalDate.now()) "今天各小时" else "${Fmt.date(shownDay)} 各小时")
    FlatPanel {
        Column(Modifier.padding(12.dp)) {
            HourlyBloodPressureChart(
                readings = shownReadings,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf(
                    "收缩压" to MaterialTheme.colorScheme.primary,
                    "舒张压" to palette.accent
                ).forEach { (label, color) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (shownReadings.size <= 1) {
                Text(
                    "这一天只有一次记录，所以是一段很短的柱子。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Bottom: a week of both numbers, one line each.
    val weekDays = (0..6).map { shownDay.minusDays(6L - it) }
    fun dayMean(day: LocalDate, pick: (BloodPressure) -> Int): Float? =
        readingsOn(day).takeIf { it.isNotEmpty() }
            ?.map(pick)?.average()?.toFloat()

    val systolicWeek = weekDays.map { dayMean(it) { p -> p.systolic } }
    val diastolicWeek = weekDays.map { dayMean(it) { p -> p.diastolic } }
    val weekLabels = weekDays.map { Fmt.weekdayShort(it) }

    if (systolicWeek.count { it != null } >= 1) {
        SectionTitle("近 7 天 · 收缩压")
        FlatPanel {
            AxisLineChart(
                values = systolicWeek,
                labels = weekLabels,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 12.dp)
                    .height(150.dp),
                color = palette.critical,
                minValue = 80f,
                maxValue = 180f
            )
        }
    }
    if (diastolicWeek.count { it != null } >= 1) {
        SectionTitle("近 7 天 · 舒张压")
        FlatPanel {
            AxisLineChart(
                values = diastolicWeek,
                labels = weekLabels,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 12.dp)
                    .height(150.dp),
                color = palette.warn,
                minValue = 40f,
                maxValue = 120f
            )
        }
    }

    Text(
        "收缩压 ≥ 120 或舒张压 ≥ 80 记为正常高值，≥ 140 / 90 记为偏高（中国高血压指南）。" +
            "一次读数受情绪、刚运动完和测量姿势影响，图上更值得看的是同一时段的趋势。",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * One column per hour on a 24-hour axis, with the two pressures as two colours:
 * systolic on the left of the slot, diastolic on the right. Inside each column
 * the values are cut into vertical segments — ranges no reading covered stay
 * empty, so an hour that jumped from 95 to 150 shows two blocks, not one band.
 */
@Composable
private fun HourlyBloodPressureChart(
    readings: List<BloodPressure>,
    modifier: Modifier = Modifier
) {
    if (readings.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val axisStyle = TextStyle(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val palette = LocalZgPalette.current
    val systolicColor = MaterialTheme.colorScheme.primary
    val diastolicColor = palette.accent
    val lo = 40f
    val hi = 180f
    val zone = ZoneId.systemDefault()

    // Sorted values merged while the jump between neighbours is small; a bigger
    // jump is a range with no reading in it and becomes a gap in the column.
    fun segments(values: List<Int>): List<Pair<Int, Int>> {
        val out = mutableListOf<Pair<Int, Int>>()
        values.sorted().forEach { value ->
            val last = out.lastOrNull()
            if (last != null && value - last.second <= 4) {
                out[out.lastIndex] = last.first to value
            } else {
                out.add(value to value)
            }
        }
        return out
    }

    val hourly = (0 until 24).map { hour ->
        val inHour = readings.filter {
            Instant.ofEpochMilli(it.epochMs).atZone(zone).hour == hour
        }
        segments(inHour.map { it.systolic }) to segments(inHour.map { it.diastolic })
    }

    Canvas(modifier) {
        val axisWidth = 30.dp.toPx()
        val axisHeight = 16.dp.toPx()
        val topPad = 10.dp.toPx()
        val bottomPad = 6.dp.toPx()
        val left = axisWidth
        val chartWidth = (size.width - axisWidth).coerceAtLeast(1f)
        val chartHeight = (size.height - axisHeight - topPad - bottomPad).coerceAtLeast(8f)
        fun yOf(value: Float): Float = topPad + chartHeight - ((value - lo) / (hi - lo)) * chartHeight

        listOf(60, 100, 140, 180).forEach { tick ->
            val y = yOf(tick.toFloat())
            drawLine(palette.grid, Offset(left, y), Offset(size.width, y), 1f)
            val layout = measurer.measure(tick.toString(), axisStyle)
            drawText(
                layout,
                topLeft = Offset(0f, (y - layout.size.height / 2f).coerceAtLeast(0f))
            )
        }

        val slot = chartWidth / 24f
        val gap = 1.5.dp.toPx()
        val halfWidth = ((slot * 0.46f - gap) / 2f).coerceAtLeast(1.5.dp.toPx())
        val radius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx())

        fun drawSegments(segments: List<Pair<Int, Int>>, centerX: Float, color: Color) {
            segments.forEach { (lowest, highest) ->
                val top = yOf(highest.toFloat())
                val bottom = yOf(lowest.toFloat())
                drawRoundRect(
                    color = color.copy(alpha = 0.70f),
                    topLeft = Offset(centerX - halfWidth / 2f, top),
                    size = androidx.compose.ui.geometry.Size(
                        halfWidth,
                        (bottom - top).coerceAtLeast(2.dp.toPx())
                    ),
                    cornerRadius = radius
                )
            }
        }

        hourly.forEachIndexed { hour, pair ->
            val slotCenter = left + slot * hour + slot / 2f
            val systolicX = slotCenter - halfWidth / 2f - gap / 2f
            val diastolicX = slotCenter + halfWidth / 2f + gap / 2f
            drawSegments(pair.first, systolicX, systolicColor)
            drawSegments(pair.second, diastolicX, diastolicColor)
        }

        listOf(0, 6, 12, 18, 24).forEach { hour ->
            val layout = measurer.measure(hour.toString(), axisStyle)
            val centerX = left + chartWidth * hour / 24f
            drawText(
                layout,
                topLeft = Offset(
                    (centerX - layout.size.width / 2f)
                        .coerceIn(axisWidth, size.width - layout.size.width),
                    size.height - axisHeight / 2f - layout.size.height / 2f
                )
            )
        }
    }
}

/**
 * One column per hour, on a 24-hour axis. Inside a column the readings are cut
 * into vertical segments: value ranges that no sample covered are left empty, so
 * an hour that jumped from 92% to 98% shows two blocks instead of one solid
 * band. One colour for the whole chart — the 7-day line above carries the trend.
 */
@Composable
private fun Spo2NightChart(points: List<Spo2Point>, modifier: Modifier = Modifier) {
    if (points.isEmpty()) return
    val zone = ZoneId.systemDefault()
    // For each hour, the values sorted and merged into runs: neighbouring values
    // within 1% belong to the same block of data, a bigger jump is a gap.
    val hourly = (0 until 24).map { hour ->
        val values = points
            .filter { Instant.ofEpochMilli(it.epochMs).atZone(zone).hour == hour }
            .map { it.value }
            .sorted()
        val segments = mutableListOf<Pair<Int, Int>>()
        values.forEach { value ->
            val last = segments.lastOrNull()
            if (last != null && value - last.second <= 1) {
                segments[segments.lastIndex] = last.first to value
            } else {
                segments.add(value to value)
            }
        }
        segments
    }
    val measurer = rememberTextMeasurer()
    val axisStyle = TextStyle(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val palette = LocalZgPalette.current
    val tint = MaterialTheme.colorScheme.tertiary
    val lo = kotlin.math.floor(points.minOf { it.value }.toFloat() - 1f)
    val hi = kotlin.math.ceil(points.maxOf { it.value }.toFloat() + 1f)

    Canvas(modifier) {
        val axisWidth = 30.dp.toPx()
        val axisHeight = 16.dp.toPx()
        val topPad = 10.dp.toPx()
        val bottomPad = 6.dp.toPx()
        val left = axisWidth
        val chartWidth = (size.width - axisWidth).coerceAtLeast(1f)
        val chartHeight = (size.height - axisHeight - topPad - bottomPad).coerceAtLeast(8f)
        fun yOf(value: Float): Float = topPad + chartHeight - ((value - lo) / (hi - lo)) * chartHeight

        listOf(hi, (hi + lo) / 2f, lo).forEach { tick ->
            val y = yOf(tick)
            drawLine(palette.grid, Offset(left, y), Offset(size.width, y), 1f)
            val layout = measurer.measure(tick.roundToInt().toString(), axisStyle)
            drawText(
                layout,
                topLeft = Offset(0f, (y - layout.size.height / 2f).coerceAtLeast(0f))
            )
        }

        val slot = chartWidth / 24f
        val barWidth = (slot * 0.46f).coerceAtLeast(2.dp.toPx())
        hourly.forEachIndexed { hour, segments ->
            val centerX = left + slot * hour + slot / 2f
            segments.forEach { (lowest, highest) ->
                val top = yOf(highest.toFloat())
                val bottom = yOf(lowest.toFloat())
                drawRoundRect(
                    color = tint.copy(alpha = 0.55f),
                    topLeft = Offset(centerX - barWidth / 2f, top),
                    size = androidx.compose.ui.geometry.Size(
                        barWidth,
                        (bottom - top).coerceAtLeast(2.dp.toPx())
                    ),
                    cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx())
                )
            }
        }

        listOf(0, 6, 12, 18, 24).forEach { hour ->
            val layout = measurer.measure(hour.toString(), axisStyle)
            val centerX = left + chartWidth * hour / 24f
            drawText(
                layout,
                topLeft = Offset(
                    (centerX - layout.size.width / 2f)
                        .coerceIn(axisWidth, size.width - layout.size.width),
                    size.height - axisHeight / 2f - layout.size.height / 2f
                )
            )
        }
    }
}

// ---------------- sleep vitals with a 7-day trend ----------------
private enum class SleepMetric(val unit: String) {
    HEART_RATE("bpm"),
    RESPIRATION("次/分"),
    APNEA("次/时")
}

/**
 * One sleep vital, one page: today's reading, its rating, and the last seven
 * nights as a line. Values come from the readiness record (心率),
 * `RespiratoryRate` (呼吸率) and the blood-oxygen `odi` stream (低通气).
 */
@Composable
private fun SleepMetricDetail(data: HealthData?, date: LocalDate?, metric: SleepMetric) {
    val source = data ?: run {
        Missing("还没有这一晚的数据")
        return
    }
    val night = date?.let { source.nightOn(it) } ?: source.latestNight()
    val day = night?.date ?: date ?: LocalDate.now()
    val readiness = source.readiness.firstOrNull { it.date == day }

    fun valueOn(target: LocalDate): Double? = when (metric) {
        SleepMetric.HEART_RATE ->
            source.readiness.firstOrNull { it.date == target }?.sleepRhr?.toDouble()
                ?: source.nightOn(target)?.restingHr?.toDouble()
        SleepMetric.RESPIRATION -> source.respirationOn(target)
        SleepMetric.APNEA -> source.breathingOn(target)?.apneaIndex
    }

    val value = valueOn(day)
    if (value == null) {
        Missing("这一晚没有这项记录")
        return
    }
    val verdict = when (metric) {
        SleepMetric.HEART_RATE -> if (value in 45.0..70.0) "正常" to MaterialTheme.colorScheme.primary
        else "偏离" to LocalZgPalette.current.warn
        SleepMetric.RESPIRATION -> if (value in 12.0..20.0) "正常" to MaterialTheme.colorScheme.primary
        else "偏离" to LocalZgPalette.current.warn
        SleepMetric.APNEA -> when {
            value < 5.0 -> "正常" to MaterialTheme.colorScheme.primary
            value < 15.0 -> "偏高" to LocalZgPalette.current.warn
            else -> "偏高" to LocalZgPalette.current.critical
        }
    }
    // Each vital keeps the colour it has on the sleep page.
    val color = when (metric) {
        SleepMetric.HEART_RATE -> LocalZgPalette.current.sleepRem
        SleepMetric.RESPIRATION -> LocalZgPalette.current.sleepLight
        SleepMetric.APNEA -> LocalZgPalette.current.sleepAwake
    }

    FlatPanel {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                Fmt.date(day),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            val abnormal = verdict.first != "正常"
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    Fmt.oneDecimal(value),
                    style = MaterialTheme.typography.displayMedium,
                    color = if (abnormal) verdict.second else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    " ${metric.unit}",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (abnormal) verdict.second else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Text(
                verdict.first,
                style = MaterialTheme.typography.titleSmall,
                color = if (abnormal) verdict.second else MaterialTheme.colorScheme.onSurface
            )
        }
    }

    // Seven calendar days ending on the night being read, whether or not the
    // watch had an activity record for each of them.
    val recent = (0..6).map { day.minusDays(6L - it) }
    val series = recent.map { valueOn(it)?.toFloat() }
    if (series.count { it != null } > 1) {
        SectionTitle("近 7 天")
        FlatPanel {
            TrendLineChart(
                values = series,
                labels = recent.map { Fmt.weekdayShort(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 12.dp)
                    .height(170.dp),
                color = color
            )
        }
    }

    SectionTitle("这些数字是什么？")
    FlatPanel {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                when (metric) {
                    SleepMetric.HEART_RATE ->
                        "睡眠心率是整晚心跳的平均值，通常比清醒静息心率再低几次。熬夜、酒精、生病或高强度训练后它会偏高。"
                    SleepMetric.RESPIRATION ->
                        "呼吸率是每分钟呼吸次数，由手表的逐分钟呼吸数据取当晚平均。"
                    SleepMetric.APNEA ->
                        "低通气指数是每小时呼吸变浅或暂停的次数，来自手表的夜间血氧记录（AHI，取不到时退回 ODI）。"
                },
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
