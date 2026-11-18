package com.genkaim.zeppglass.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.genkaim.zeppglass.data.AppSettings
import com.genkaim.zeppglass.data.HealthData
import com.genkaim.zeppglass.data.HealthStore
import com.genkaim.zeppglass.ui.Fmt
import com.genkaim.zeppglass.ui.components.DataRow
import com.genkaim.zeppglass.ui.components.FlatPanel
import com.genkaim.zeppglass.ui.components.SectionTitle

/**
 * "全部数据": one page that lists every field the app fetched, table by table.
 *
 * Cards and details only show what fits a chart; this page is the audit view,
 * so nothing the sync collected is invisible: missing values stay `--`.
 */
@Composable
fun AllDataScreen(
    settings: AppSettings,
    store: HealthStore,
    onBack: () -> Unit
) {
    val state = store.state
    val data = store.displayData

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
            }
            Column(Modifier.weight(1f)) {
                Text("全部数据", style = MaterialTheme.typography.titleLarge)
                Text(
                    "本机拿到什么就显示什么，缺的写 --",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SyncSection(
                connected = settings.isLoggedIn,
                lastSyncedAt = state.lastSyncedAt,
                data = data,
                raw = state.data,
                stale = state.stale,
                warnings = data?.warnings.orEmpty(),
                error = state.error
            )
            if (data != null) {
                DailySection(data)
                SleepSection(data)
                ChargeSection(data)
                ReadinessSection(data)
                StressSection(data)
                PaiSection(data)
                LoadSection(data)
                WeightSection(data)
                Spo2Section(data)
                WorkoutSection(data)
                StrainSection(data)
            }
            Text(
                "原始报文快照保存在应用私有目录（health-snapshot.json），不会上传。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SyncSection(
    connected: Boolean,
    lastSyncedAt: Long?,
    data: HealthData?,
    raw: HealthData?,
    stale: Boolean,
    warnings: List<String>,
    error: String?
) {
    SectionTitle("同步状态")
    FlatPanel {
        Column(Modifier.padding(16.dp)) {
            DataRow("账号", if (connected) "已连接" else "未连接")
            DataRow("上次同步", lastSyncedAt?.let { Fmt.dayAndTime(it) } ?: "--")
            DataRow(
                "数据状态",
                if (stale) "超过 3 小时，数值已显示为 --" else if (data != null) "正常" else "还没有数据",
                mono = false
            )
            DataRow("每日活动", raw?.days?.size?.toString() ?: "--")
            DataRow("睡眠", raw?.nights?.size?.toString() ?: "--")
            DataRow("Charge", raw?.chargeDays?.size?.toString() ?: "--")
            DataRow("准备度", raw?.readiness?.size?.toString() ?: "--")
            DataRow("压力", raw?.stress?.size?.toString() ?: "--")
            DataRow("PAI", raw?.pai?.size?.toString() ?: "--")
            DataRow("训练负荷", raw?.load?.size?.toString() ?: "--")
            DataRow("体重", raw?.weights?.size?.toString() ?: "--")
            DataRow("血氧", raw?.spo2?.size?.toString() ?: "--")
            DataRow("运动", raw?.workouts?.size?.toString() ?: "--")
            DataRow("心率采样", raw?.heartRate?.size?.toString() ?: "--")
            if (error != null) DataRow("同步错误", error, mono = false)
            if (warnings.isNotEmpty()) {
                DataRow("部分失败", "${warnings.size} 项", mono = false)
                warnings.forEachIndexed { index, warning ->
                    Text(
                        "${index + 1}. $warning",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DailySection(data: HealthData) {
    val days = data.days.takeLast(14)
    if (days.isEmpty()) return
    SectionTitle("每日活动", trailing = "最近 ${days.size} 天")
    FlatPanel {
        Column(Modifier.padding(16.dp)) {
            days.forEach { day ->
                DataRow(
                    Fmt.date(day.date) + " " + Fmt.weekdayShort(day.date),
                    buildString {
                        append("${day.steps ?: "--"} 步")
                        append(" · ${Fmt.km(day.distanceM)} km")
                        append(" · ${Fmt.int(day.calories)} kcal")
                        append(" · ${day.activityMinutes ?: "--"} 分活动")
                        append(" · 静息 ${day.restingHr ?: "--"}")
                        day.vo2Max?.let { append(" · 摄氧 $it") }
                    }
                )
            }
        }
    }
}

@Composable
private fun SleepSection(data: HealthData) {
    val nights = data.nights.takeLast(10)
    if (nights.isEmpty()) return
    SectionTitle("睡眠", trailing = "最近 ${nights.size} 晚")
    FlatPanel {
        Column(Modifier.padding(16.dp)) {
            nights.forEach { night ->
                DataRow(
                    Fmt.date(night.date),
                    "${Fmt.hoursMinutes(night.totalMinutes)} · ${night.score ?: "--"} 分" +
                        " · 深 ${night.deepMinutes ?: "--"} 浅 ${night.lightMinutes ?: "--"}" +
                        " REM ${night.remMinutes ?: "--"} 醒 ${night.awakeMinutes ?: "--"}" +
                        " · 夜醒 ${night.wakeCount ?: "--"} · 心率 ${night.restingHr ?: "--"}"
                )
            }
        }
    }
}

@Composable
private fun ChargeSection(data: HealthData) {
    val days = data.chargeDays.takeLast(14)
    if (days.isEmpty()) return
    SectionTitle("Hybrid Charge", trailing = "最近 ${days.size} 天")
    FlatPanel {
        Column(Modifier.padding(16.dp)) {
            days.forEach { day ->
                DataRow(
                    Fmt.date(day.date),
                    "总分 ${day.total ?: "--"} · 体力 ${day.physical?.let { Fmt.oneDecimal(it) } ?: "--"}" +
                        " · 精神 ${day.mental?.let { Fmt.oneDecimal(it) } ?: "--"}" +
                        " · ${day.samples.size} 个采样"
                )
            }
        }
    }
}

@Composable
private fun ReadinessSection(data: HealthData) {
    val days = data.readiness.takeLast(14)
    if (days.isEmpty()) return
    SectionTitle("准备度", trailing = "最近 ${days.size} 天")
    FlatPanel {
        Column(Modifier.padding(16.dp)) {
            days.forEach { day ->
                DataRow(
                    Fmt.date(day.date),
                    "准备度 ${day.score ?: "--"} · HRV 分 ${day.hrvScore ?: "--"}" +
                        " · 静息分 ${day.rhrScore ?: "--"} · 睡眠 HRV ${day.sleepHrv ?: "--"} ms" +
                        " · 睡眠心率 ${day.sleepRhr ?: "--"} bpm" +
                        " · 呼吸 ${day.ahiScore ?: "--"} · 皮温 ${day.skinTempScore ?: "--"}" +
                        " · 体力 ${day.physicalScore ?: "--"} · 精神 ${day.mentalScore ?: "--"}"
                )
            }
        }
    }
}

@Composable
private fun StressSection(data: HealthData) {
    val days = data.stress.takeLast(14)
    if (days.isEmpty()) return
    SectionTitle("压力", trailing = "最近 ${days.size} 天")
    FlatPanel {
        Column(Modifier.padding(16.dp)) {
            days.forEach { day ->
                DataRow(
                    Fmt.date(day.date),
                    "平均 ${day.average ?: "--"} · 最低 ${day.minimum ?: "--"} · 最高 ${day.maximum ?: "--"}" +
                        " · 放松 ${day.relaxedPct?.let { Fmt.oneDecimal(it) } ?: "--"}%" +
                        " · 正常 ${day.normalPct?.let { Fmt.oneDecimal(it) } ?: "--"}%" +
                        " · 中等 ${day.mediumPct?.let { Fmt.oneDecimal(it) } ?: "--"}%" +
                        " · 偏高 ${day.highPct?.let { Fmt.oneDecimal(it) } ?: "--"}%" +
                        " · ${day.curve.size} 个采样"
                )
            }
        }
    }
}

@Composable
private fun PaiSection(data: HealthData) {
    val days = data.pai.takeLast(14)
    if (days.isEmpty()) return
    SectionTitle("PAI", trailing = "最近 ${days.size} 天")
    FlatPanel {
        Column(Modifier.padding(16.dp)) {
            days.forEach { day ->
                DataRow(
                    Fmt.date(day.date),
                    "当日 ${day.daily?.let { Fmt.oneDecimal(it) } ?: "--"} · 累计 ${day.total?.let { Fmt.oneDecimal(it) } ?: "--"}" +
                        " · 低 ${day.lowMinutes ?: "--"} 中 ${day.mediumMinutes ?: "--"} 高 ${day.highMinutes ?: "--"} 分钟"
                )
            }
        }
    }
}

@Composable
private fun LoadSection(data: HealthData) {
    val days = data.load.takeLast(14)
    if (days.isEmpty()) return
    SectionTitle("训练负荷", trailing = "最近 ${days.size} 天")
    FlatPanel {
        Column(Modifier.padding(16.dp)) {
            days.forEach { day ->
                DataRow(
                    Fmt.date(day.date),
                    "当天 ${day.load?.let { Fmt.oneDecimal(it) } ?: "--"} · 7 天 ${day.weekLoad?.let { Fmt.oneDecimal(it) } ?: "--"}" +
                        " · 合适 ${day.optimalMin?.let { Fmt.oneDecimal(it) } ?: "--"}–${day.optimalMax?.let { Fmt.oneDecimal(it) } ?: "--"}" +
                        " · 过量 ${day.overreaching?.let { Fmt.oneDecimal(it) } ?: "--"}"
                )
            }
        }
    }
}

@Composable
private fun WeightSection(data: HealthData) {
    val points = data.weights.takeLast(12)
    if (points.isEmpty()) return
    SectionTitle("体重与身体成分", trailing = "最近 ${points.size} 次")
    FlatPanel {
        Column(Modifier.padding(16.dp)) {
            points.forEach { point ->
                DataRow(
                    Fmt.dayAndTime(point.epochMs),
                    "体重 ${point.weightKg?.let { Fmt.oneDecimal(it) } ?: "--"} kg" +
                        " · BMI ${point.bmi?.let { Fmt.oneDecimal(it) } ?: "--"}" +
                        " · 体脂 ${point.bodyFatPct?.let { Fmt.oneDecimal(it) } ?: "--"}%"
                )
            }
        }
    }
}

@Composable
private fun Spo2Section(data: HealthData) {
    val points = data.spo2.takeLast(24)
    if (points.isEmpty()) return
    SectionTitle("血氧", trailing = "最近 ${points.size} 次")
    FlatPanel {
        Column(Modifier.padding(16.dp)) {
            points.forEach { point ->
                DataRow(
                    Fmt.dayAndTime(point.epochMs),
                    "${point.value}% · ${if (point.automatic) "自动" else "手动"}"
                )
            }
        }
    }
}

@Composable
private fun WorkoutSection(data: HealthData) {
    val workouts = data.workouts.take(10)
    if (workouts.isEmpty()) return
    SectionTitle("运动摘要", trailing = "最近 ${workouts.size} 次，轨迹在运动详情页")
    FlatPanel {
        Column(Modifier.padding(16.dp)) {
            workouts.forEach { workout ->
                DataRow(
                    "${Fmt.dayAndTime(workout.endEpochSec * 1000)} ${workout.name}",
                    "${workout.durationSec} 秒 · ${Fmt.km(workout.distanceM)} km" +
                        " · 配速 ${workout.paceSecPerKm?.let { Fmt.pace(it) } ?: "--"}" +
                        " · 心率 ${workout.avgHr?.toInt() ?: "--"}/${workout.maxHr?.toInt() ?: "--"}" +
                        " · ${Fmt.int(workout.calories)} kcal" +
                        " · 负荷 ${workout.load?.toInt() ?: "--"}" +
                        " · 上升 ${workout.altitudeGainM?.toInt() ?: "--"} m" +
                        " · 步数 ${workout.steps ?: "--"}" +
                        " · 步频 ${workout.avgCadence?.toInt() ?: "--"}" +
                        " · ${workout.city ?: "--"}"
                )
            }
        }
    }
}

@Composable
private fun StrainSection(data: HealthData) {
    val days = data.strain.takeLast(14)
    if (days.isEmpty()) return
    SectionTitle("负荷（本地计算）", trailing = "最近 ${days.size} 天")
    FlatPanel {
        Column(Modifier.padding(16.dp)) {
            days.forEach { day ->
                DataRow(
                    Fmt.date(day.date),
                    "负荷 ${day.strain?.let { String.format("%.2f", it) } ?: "--"}" +
                        " · 心率负荷 ${day.trimp?.let { Fmt.oneDecimal(it) } ?: "--"}" +
                        " · 平均 ${day.averageHr ?: "--"} 最高 ${day.maxHr ?: "--"}" +
                        " · 区间 ${day.zoneMinutes.joinToString("/")}" +
                        " · ${day.sampleMinutes} 分钟采样"
                )
            }
        }
    }
}
