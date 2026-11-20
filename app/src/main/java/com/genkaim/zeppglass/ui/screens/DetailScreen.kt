package com.genkaim.zeppglass.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.genkaim.zeppglass.data.AppSettings
import com.genkaim.zeppglass.data.ChargeDay
import com.genkaim.zeppglass.data.DietStore
import com.genkaim.zeppglass.data.HealthData
import com.genkaim.zeppglass.data.HealthStore
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
import com.genkaim.zeppglass.data.Workout
import com.genkaim.zeppglass.data.WorkoutDetail
import com.genkaim.zeppglass.ui.DetailKind
import com.genkaim.zeppglass.ui.DetailTarget
import com.genkaim.zeppglass.ui.Fmt
import com.genkaim.zeppglass.ui.components.AxisBarChart
import com.genkaim.zeppglass.ui.components.AxisLineChart
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
import com.genkaim.zeppglass.ui.components.ScaleBar
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
import com.genkaim.zeppglass.ui.components.sleepStageLabel
import com.genkaim.zeppglass.ui.theme.LocalZgPalette
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
    val palette = LocalZgPalette.current
    var headerHeight by remember { mutableStateOf(0.dp) }
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(headerHeight + 4.dp))
            when (target.kind) {
                DetailKind.SLEEP -> SleepDetail(data, target.date, onOpen)
                DetailKind.CHARGE -> ChargeDetail(data, target.date)
                DetailKind.STRESS -> StressDetail(data, target.date)
                DetailKind.READINESS -> ReadinessDetail(data, target.date)
                DetailKind.ACTIVITY -> ActivityDetail(data, target.date ?: LocalDate.now())
                DetailKind.LOAD -> LoadDetail(data, target.date)
                DetailKind.PAI -> PaiDetail(data, target.date)
                DetailKind.WEIGHT -> WeightDetail(data, target.date)
                DetailKind.SPO2 -> NightSpo2Detail(data, target.date, withDayStats = true)
                DetailKind.WORKOUT -> WorkoutDetail(data, target.workoutId, store)
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
            }
        }

        // Pinned header: opaque behind the title, the page colour fades out
        // below it so the cards scroll underneath instead of meeting a hard line.
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
                    if (target.kind != DetailKind.WORKOUT && subtitle(target, data).isNotBlank()) {
                        Text(
                            subtitle(target, data),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
    DetailKind.WEIGHT -> "体重与身体成分"
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

    // Zepp leads with the latest 5-minute sample, not the daily average; this is
    // also the number the home card shows. It sits in the same white card as the
    // rest of the page's readings.
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
        }
    }

    FlatPanel {
        Column(Modifier.padding(16.dp)) {
            StressBandChart(
                curve = day.curve,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
            )
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth(),
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
            Spacer(Modifier.height(16.dp))
            Rule(Modifier.padding(vertical = 4.dp))
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                listOf(
                    "最高压力值" to day.maximum,
                    "最低压力值" to day.minimum,
                    "平均压力值" to day.average
                ).forEach { (label, value) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            value?.toString() ?: "--",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
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
private fun WeightDetail(data: HealthData?, date: LocalDate?) {
    val zone = ZoneId.systemDefault()
    val points = data?.weights.orEmpty()
    val latest = if (date != null) {
        // The card opens for "today", but the last weighing may be days earlier:
        // show the newest reading on or before that day instead of an empty page.
        points.filter { Instant.ofEpochMilli(it.epochMs).atZone(zone).toLocalDate() <= date }
            .maxByOrNull { it.epochMs }
            ?: points.maxByOrNull { it.epochMs }
    } else {
        points.maxByOrNull { it.epochMs }
    }
    if (latest == null) {
        Missing("还没有称重记录")
        return
    }
    val ink = MaterialTheme.colorScheme.onSurface
    val warnTone = LocalZgPalette.current.warn
    val criticalTone = LocalZgPalette.current.critical
    // The newest composition reading close to the current weight, same rule as
    // the diet page: a shared scale can hold someone else's much lighter data.
    val comp = points
        .filter { it.bodyFatPct != null || it.skeletalMusclePct != null || it.skeletalMuscleKg != null }
        .filter {
            it.weightKg == null || latest.weightKg == null ||
                kotlin.math.abs(it.weightKg - latest.weightKg) <= 8.0
        }
        .maxByOrNull { it.epochMs }
    val bmi = latest.bmi
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricTile("体重", Fmt.oneDecimal(latest.weightKg), "kg", Modifier.weight(1f))
        MetricTile(
            label = "BMI",
            value = Fmt.oneDecimal(bmi),
            unit = null,
            verdict = bmi?.let {
                when {
                    it < 18.5 -> "偏轻" to warnTone
                    it < 24.0 -> "正常" to ink
                    it < 28.0 -> "偏高" to warnTone
                    else -> "肥胖" to criticalTone
                }
            },
            modifier = Modifier.weight(1f)
        )
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val fat = comp?.bodyFatPct
        MetricTile(
            label = "体脂",
            value = fat?.let { Fmt.oneDecimal(it) } ?: "--",
            unit = "%",
            verdict = fat?.let {
                when {
                    it < 15.0 -> "偏低" to warnTone
                    it <= 25.0 -> "正常" to ink
                    it <= 30.0 -> "偏高" to warnTone
                    else -> "偏高" to criticalTone
                }
            },
            modifier = Modifier.weight(1f)
        )
        val musclePct = comp?.skeletalMusclePct
        MetricTile(
            label = "骨骼肌",
            value = comp?.skeletalMuscleKg?.let { Fmt.oneDecimal(it) } ?: "--",
            unit = "kg",
            verdict = musclePct?.let {
                if (it < 40.0) "偏低" to warnTone else "正常" to ink
            },
            modifier = Modifier.weight(1f)
        )
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

@Composable
private fun WorkoutDetail(data: HealthData?, workoutId: String?, store: HealthStore) {
    val workout: Workout? = data?.workouts?.firstOrNull { it.id == workoutId }
        ?: data?.workouts?.firstOrNull()
    if (workout == null) {
        Missing("还没有运动记录")
        return
    }
    val date = java.time.Instant.ofEpochSecond(workout.endEpochSec)
        .atZone(java.time.ZoneId.systemDefault()).toLocalDate()

    // The list payload has no track, so the detail is fetched once per workout.
    var detail by remember(workout.id) { mutableStateOf<WorkoutDetail?>(null) }
    var detailLoading by remember(workout.id) { mutableStateOf(true) }
    LaunchedEffect(workout.id) {
        detailLoading = true
        detail = store.loadWorkoutDetail(workout)
        detailLoading = false
    }

    // Nothing is drawn until the per-second payload is in hand: showing the
    // tiles first and dropping the charts in a second later was the flicker.
    if (detail == null && detailLoading) {
        FlatPanel {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    "正在读取这次运动的逐点数据…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricTile("时长", Fmt.duration(workout.durationSec), null, Modifier.weight(1f))
        MetricTile("距离", Fmt.km(workout.distanceM), "km", Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricTile("配速", workout.paceSecPerKm?.let { Fmt.pace(it) } ?: "--", "/km", Modifier.weight(1f))
        MetricTile("消耗", Fmt.int(workout.calories), "kcal", Modifier.weight(1f))
    }

    // Everything else the watch attached to the session. Only the values that
    // are actually there get a tile: a missing field is left out rather than
    // printed as a dash, because this grid is about what the watch measured.
    val extras = buildList {
        workout.avgHr?.let { add(Triple("平均心率", it.roundToInt().toString(), "bpm")) }
        workout.maxHr?.let { add(Triple("最高心率", it.roundToInt().toString(), "bpm")) }
        workout.load?.let { add(Triple("训练负荷", it.roundToInt().toString(), null)) }
        workout.steps?.let { add(Triple("步数", it.toString(), "步")) }
        workout.avgCadence?.let { add(Triple("平均步频", it.roundToInt().toString(), "spm")) }
        workout.maxCadence?.let { add(Triple("最高步频", it.roundToInt().toString(), "spm")) }
        workout.altitudeGainM?.let { add(Triple("累计爬升", it.roundToInt().toString(), "m")) }
        workout.altitudeLossM?.let { add(Triple("累计下降", it.roundToInt().toString(), "m")) }
        workout.vo2Max?.let { add(Triple("最大摄氧量", it.toString(), "ml/kg/min")) }
    }
    extras.chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            pair.forEach { (label, value, unit) ->
                MetricTile(label, value, unit, Modifier.weight(1f))
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }

    // Only curves the watch actually recorded get a chart: an indoor session
    // has no speed line, a flat one has no shape to show. Each metric is its own
    // group: a stats card (average / max / min) and the curve below it.
    val series = detail?.series.orEmpty()
    series.forEach { line ->
        val tint = workoutSeriesTint(line.label)
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
                    line.values.average().roundToInt().toString(),
                    line.unit,
                    tint
                )
                SeriesStat(
                    Modifier.weight(1f),
                    "最高",
                    line.values.max().roundToInt().toString(),
                    line.unit,
                    tint
                )
                SeriesStat(
                    Modifier.weight(1f),
                    "最低",
                    line.values.min().roundToInt().toString(),
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
    }
    if (series.isEmpty() && detailLoading) {
        FlatPanel {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    "正在读取这次运动的逐点数据…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Only the track itself: no map tile, no raw field tables.
    SectionTitle("轨迹", trailing = if (detail?.points.isNullOrEmpty()) null else "${detail!!.points.size} 个坐标")
    val points = detail?.points.orEmpty()
    when {
        points.size >= 2 -> {
            RoutePath(
                points = points,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )
            Text(
                "按经纬度等比例绘制，没有底图；起点为橙色，终点为绿色。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        detailLoading -> RoutePlaceholder(
            "正在读取这次运动的 GPS…",
            Modifier
                .fillMaxWidth()
                .height(120.dp)
        )
        else -> RoutePlaceholder(
            "这次运动没有 GPS 坐标",
            Modifier
                .fillMaxWidth()
                .height(120.dp)
        )
    }

    // Every remaining raw field the detail payload carried, with the keys that
    // have a known meaning translated. Unknown keys are still shown under their
    // own name, so a new watch metric appears without a code change.
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

/** Chinese label for the payload keys whose meaning is known. */
private fun workoutFieldLabel(key: String): String = when (key) {
    "distance" -> "距离"
    "duration", "duration_time", "sport_time" -> "时长"
    "calories", "calorie", "total_calories" -> "消耗"
    "avg_heart_rate", "average_heart_rate" -> "平均心率"
    "max_heart_rate" -> "最高心率"
    "min_heart_rate" -> "最低心率"
    "steps", "total_steps" -> "步数"
    "cadence", "avg_cadence" -> "步频"
    "max_cadence" -> "最高步频"
    "pace", "avg_pace" -> "配速"
    "altitude", "ascent", "total_ascent" -> "累计爬升"
    "descent", "total_descent" -> "累计下降"
    "load", "training_load", "training_effect" -> "训练负荷"
    "vo2_max", "vo2max" -> "最大摄氧量"
    "city" -> "城市"
    "location", "address" -> "地点"
    "device", "device_name" -> "设备"
    "sport", "sport_type", "type" -> "运动类型"
    else -> key
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
        // The day ends with the night, not at midnight, so before sleeping this
        // is still yesterday's number growing.
        val inProgress = (date == null || date == LocalDate.now()) &&
            source.strainDateNow() != LocalDate.now()
        if (inProgress) {
            Spacer(Modifier.height(4.dp))
            Text(
                "进行中 · 截至 " + java.time.LocalTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
            Spacer(Modifier.height(16.dp))
            BaselineRow("基线", day.rhrBaseline, "次/分", value)
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
            Spacer(Modifier.height(16.dp))
            BaselineRow("基线", day.hrvBaseline, "毫秒", value)
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
                Text(
                    "这一晚没有连续 HRV 采样",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
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

/** Official baseline with the actual day's value compared against it. */
@Composable
private fun BaselineRow(label: String, baseline: Int?, unit: String, value: Int) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        if (baseline == null) {
            Text(
                "暂未给出",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "$baseline $unit",
                    style = MaterialTheme.typography.titleSmall
                )
                val diff = value - baseline
                Text(
                    when {
                        diff < 0 -> "比基线低 ${-diff} $unit"
                        diff > 0 -> "比基线高 $diff $unit"
                        else -> "与基线相同"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
    if (day == null || value == null) {
        Missing("手表还没有给出皮肤温度")
        return
    }
    val palette = LocalZgPalette.current
    val ink = MaterialTheme.colorScheme.onSurface
    val verdict = when {
        value >= 75 -> "正常" to ink
        value >= 50 -> "一般" to palette.warn
        else -> "偏低" to palette.critical
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
            val abnormal = verdict.first != "正常"
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value.toString(),
                    style = MaterialTheme.typography.displayMedium,
                    color = if (abnormal) verdict.second else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    " 分",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (abnormal) verdict.second else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Text(verdict.first, style = MaterialTheme.typography.titleSmall, color = verdict.second)
        }
    }

    val recent = (0..6).map { day.date.minusDays(6L - it) }
    val series = recent.map { d ->
        source.readiness.firstOrNull { it.date == d }?.skinTempScore?.toFloat()
    }
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
                color = palette.warn
            )
        }
    }

    SectionTitle("这个分数")
    FlatPanel {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "皮肤温度分是手表把夜间手腕温度和自己的基线比较后给出的 0–100 分：越接近满分，说明当晚越贴近个人基线；" +
                    "明显偏高或偏低时分数会下降。它是相对评分，不是绝对体温。",
                style = MaterialTheme.typography.bodySmall
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
