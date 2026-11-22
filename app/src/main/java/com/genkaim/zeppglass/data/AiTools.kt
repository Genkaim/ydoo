package com.genkaim.zeppglass.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The local "database" the AI can query by itself.
 *
 * The model only gets a catalog and the query protocol; it asks for the slices
 * it needs, which keeps the prompt small and the answers grounded in rows the
 * app actually holds.
 */
object AiTools {

    private val zone: ZoneId = ZoneId.systemDefault()

    fun catalog(data: HealthData?): String {
        if (data == null) return "本机还没有健康数据。"
        val days = data.days
        val range = if (days.isEmpty()) "无" else "${days.first().date} 至 ${days.last().date}"
        return buildString {
            appendLine("本机数据表（日期范围 $range，共 ${days.size} 天）：")
            appendLine("- daily：日期、步数、目标、公里、千卡、活动时长、静息心率、睡眠分钟、睡眠分、最大摄氧量")
            appendLine("- sleep：日期、入睡、醒来、总分钟、评分、深睡、浅睡、REM、清醒、夜醒、睡眠心率")
            appendLine("- charge：日期、总分、体力、精神（0-100）")
            appendLine("- readiness：日期、准备度、HRV 分、静息心率分、睡眠 HRV、睡眠静息心率、体力分、精神分、呼吸、皮温")
            appendLine("- stress：日期、平均、最低、最高（0-100）")
            appendLine("- pai：日期、当日、累计、低/中/高强度分钟")
            appendLine("- load：日期、当天负荷、7 天负荷、合适下限、合适上限")
            appendLine("- weight：时间、来源 scale/watch、体重 kg、BMI、体脂 %、肌肉 kg、骨骼肌 kg、体水分 %、骨量 kg、内脏脂肪、基础代谢 kcal、蛋白质 %")
            appendLine("- spo2：时间、血氧 %")
            appendLine("- respiration：日期、夜间呼吸率 次/分（brpm）")
            appendLine("- breathing：日期、低通气指数 次/时、呼吸事件次数、血氧评分、监测分钟（夜间 ODI/呼吸暂停）")
            appendLine("- workouts：时间、类型、分钟、公里、配速、平均心率、最高心率、负荷")
            appendLine("- strain：日期、每日负荷(0-21)、心率负荷、平均心率、最高心率、五个心率区间分钟")
            appendLine("- diet：日期、摄入千卡、目标千卡、剩余千卡、蛋白/碳水/脂肪克数、每餐时间与食物条目（本机饮食记录，热量为识别估算）")
            appendLine("可用的行数：睡眠 ${data.nights.size}、Charge ${data.chargeDays.size}、准备度 ${data.readiness.size}、压力 ${data.stress.size}、PAI ${data.pai.size}、负荷 ${data.load.size}、体重 ${data.weights.size}、血氧 ${data.spo2.size}、运动 ${data.workouts.size}、每日负荷 ${data.strain.size}、饮食 ${DietStore.meals.groupBy { it.date() }.size} 天。")
        }
    }

    /** Executes one `{"table":..., "from":..., "to":...}` query. */
    fun execute(query: String, data: HealthData?, goal: DietGoal = DietGoal.CUT): String {
        if (data == null) return "{\"error\":\"本机没有数据\"}"
        val json = runCatching { JSONObject(query) }.getOrNull()
            ?: return "{\"error\":\"查询不是合法 JSON\"}"
        val table = json.optString("table").lowercase()
        val from = json.optString("from").takeIf { it.isNotBlank() }?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        }
        val to = json.optString("to").takeIf { it.isNotBlank() }?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        }
        val days = json.optInt("days", 7).coerceIn(1, 60)
        val limit = json.optInt("limit", 30).coerceIn(1, 60)
        val start = from ?: (to ?: LocalDate.now(zone)).minusDays(days.toLong() - 1)
        val end = to ?: LocalDate.now(zone)

        fun inRange(date: LocalDate) = !date.isBefore(start) && !date.isAfter(end)

        val rows = JSONArray()
        when (table) {
            "daily" -> data.days.filter { inRange(it.date) }.takeLast(limit).forEach { d ->
                rows.put(
                    JSONObject().apply {
                        put("date", d.date.toString())
                        d.steps?.let { put("steps", it) }
                        d.stepGoal?.let { put("goal", it) }
                        d.distanceM?.let { put("km", "%.2f".format(it / 1000)) }
                        d.calories?.let { put("kcal", it.toInt()) }
                        d.activityMinutes?.let { put("activity_min", it) }
                        d.activityGoal?.let { put("activity_goal", it) }
                        d.restingHr?.let { put("resting_hr", it) }
                        d.sleepMinutes?.let { put("sleep_min", it) }
                        d.sleepScore?.let { put("sleep_score", it) }
                        d.vo2Max?.let { put("vo2max", it) }
                    }
                )
            }
            "sleep" -> data.nights.filter { inRange(it.date) }.takeLast(limit).forEach { n ->
                rows.put(
                    JSONObject().apply {
                        put("date", n.date.toString())
                        put("total_min", n.totalMinutes)
                        n.score?.let { put("score", it) }
                        n.deepMinutes?.let { put("deep_min", it) }
                        n.lightMinutes?.let { put("light_min", it) }
                        n.remMinutes?.let { put("rem_min", it) }
                        n.awakeMinutes?.let { put("awake_min", it) }
                        n.wakeCount?.let { put("wake_count", it) }
                        n.restingHr?.let { put("sleep_hr", it) }
                    }
                )
            }
            "charge" -> data.chargeDays.filter { inRange(it.date) }.takeLast(limit).forEach { c ->
                rows.put(
                    JSONObject().apply {
                        put("date", c.date.toString())
                        // 晨起 is the value at waking; total is the value now. They
                        // are different readings and must not be swapped.
                        c.morningScore(data.nightOn(c.date)?.endEpochSec)?.let { put("morning", it) }
                            ?: put("morning", JSONObject.NULL)
                        c.total?.let { put("now_total", it) }
                        c.physical?.let { put("now_physical", "%.1f".format(it)) }
                        c.mental?.let { put("now_mental", "%.1f".format(it)) }
                    }
                )
            }
            "readiness" -> data.readiness.filter { inRange(it.date) }.takeLast(limit).forEach { r ->
                rows.put(
                    JSONObject().apply {
                        put("date", r.date.toString())
                        // Only a day the watch actually scored has a 准备度;
                        // HRV/RHR rows without one are not a score.
                        r.officialScore?.let { put("score", it) }
                        r.hrvScore?.let { put("hrv_score", it) }
                        r.rhrScore?.let { put("rhr_score", it) }
                        r.sleepHrv?.let { put("sleep_hrv", it) }
                        r.sleepRhr?.let { put("sleep_rhr", it) }
                        r.hrvBaseline?.let { put("hrv_baseline", it) }
                        r.rhrBaseline?.let { put("rhr_baseline", it) }
                        r.physicalScore?.let { put("physical", it) }
                        r.mentalScore?.let { put("mental", it) }
                        r.ahiScore?.let { put("breathing", it) }
                        r.skinTempScore?.let { put("skin_temp", it) }
                        r.skinTempDeltaC?.let { put("skin_temp_delta_c", "%.1f".format(it)) }
                        if (r.officialScore == null) put("score_given", false)
                    }
                )
            }
            "stress" -> data.stress.filter { inRange(it.date) }.takeLast(limit).forEach { s ->
                rows.put(
                    JSONObject().apply {
                        put("date", s.date.toString())
                        s.average?.let { put("avg", it) }
                        s.minimum?.let { put("min", it) }
                        s.maximum?.let { put("max", it) }
                    }
                )
            }
            "pai" -> data.pai.filter { inRange(it.date) }.takeLast(limit).forEach { p ->
                rows.put(
                    JSONObject().apply {
                        put("date", p.date.toString())
                        p.daily?.let { put("daily", "%.2f".format(it)) }
                        p.total?.let { put("total", "%.1f".format(it)) }
                        p.lowMinutes?.let { put("low_min", it) }
                        p.mediumMinutes?.let { put("medium_min", it) }
                        p.highMinutes?.let { put("high_min", it) }
                    }
                )
            }
            "load" -> data.load.filter { inRange(it.date) }.takeLast(limit).forEach { l ->
                rows.put(
                    JSONObject().apply {
                        put("date", l.date.toString())
                        l.load?.let { put("day", it.toInt()) }
                        l.weekLoad?.let { put("week", it.toInt()) }
                        l.optimalMin?.let { put("optimal_min", it.toInt()) }
                        l.optimalMax?.let { put("optimal_max", it.toInt()) }
                    }
                )
            }
            "weight" -> data.weights.filter {
                val d = Instant.ofEpochMilli(it.epochMs).atZone(zone).toLocalDate()
                inRange(d)
            }.takeLast(limit).forEach { w ->
                rows.put(
                    JSONObject().apply {
                        put("time", FmtIso(w.epochMs))
                        put("source", if (w.weightType == 0) "scale" else "watch")
                        w.weightKg?.let { put("kg", it) }
                        w.bmi?.let { put("bmi", "%.1f".format(it)) }
                        w.bodyFatPct?.let { put("fat", "%.1f".format(it)) }
                        w.muscleKg?.let { put("muscle_kg", "%.1f".format(it)) }
                        w.skeletalMuscleKg?.let { put("skeletal_muscle_kg", "%.1f".format(it)) }
                        w.bodyWaterPct?.let { put("water", "%.1f".format(it)) }
                        w.boneMassKg?.let { put("bone_kg", "%.2f".format(it)) }
                        w.visceralFat?.let { put("visceral_fat", it.toInt()) }
                        w.metabolismKcal?.let { put("bmr_kcal", it.toInt()) }
                        w.proteinPct?.let { put("protein", "%.1f".format(it)) }
                    }
                )
            }
            "spo2" -> data.spo2.filter {
                val d = Instant.ofEpochMilli(it.epochMs).atZone(zone).toLocalDate()
                inRange(d)
            }.takeLast(limit).forEach { p ->
                rows.put(JSONObject().put("time", FmtIso(p.epochMs)).put("spo2", p.value))
            }
            "respiration" -> data.respiration.filter { inRange(it.date) }.takeLast(limit)
                .forEach { r ->
                    rows.put(
                        JSONObject()
                            .put("date", r.date.toString())
                            .put("brpm", "%.1f".format(r.value))
                    )
                }
            "breathing" -> data.nightBreathing.filter { inRange(it.date) }.takeLast(limit)
                .forEach { b ->
                    rows.put(
                        JSONObject().apply {
                            put("date", b.date.toString())
                            b.apneaIndex?.let { put("apnea_index_per_hour", "%.1f".format(it)) }
                            b.apneaEvents?.let { put("apnea_events", it) }
                            b.oxygenScore?.let { put("oxygen_score", it) }
                            b.measuredMinutes?.let { put("measured_minutes", it) }
                        }
                    )
                }
            "workouts" -> data.workouts.filter {
                val d = Instant.ofEpochSecond(it.endEpochSec).atZone(zone).toLocalDate()
                inRange(d)
            }.take(limit).forEach { w ->
                val date = Instant.ofEpochSecond(w.endEpochSec).atZone(zone).toLocalDate()
                rows.put(
                    JSONObject().apply {
                        put("date", date.toString())
                        put("type", w.name)
                        put("minutes", w.durationSec / 60)
                        if (w.distanceM > 100) put("km", "%.2f".format(w.distanceM / 1000))
                        w.paceSecPerKm?.let { put("pace", com.genkaim.zeppglass.ui.Fmt.pace(it)) }
                        w.avgHr?.let { put("avg_hr", it.toInt()) }
                        w.maxHr?.let { put("max_hr", it) }
                        w.load?.let { put("load", it.toInt()) }
                    }
                )
            }
            "strain" -> data.strain.filter { inRange(it.date) }.takeLast(limit).forEach { s ->
                rows.put(
                    JSONObject().apply {
                        put("date", s.date.toString())
                        s.strain?.let { put("strain", "%.1f".format(it)) }
                        s.trimp?.let { put("trimp", it.toInt()) }
                        s.averageHr?.let { put("avg_hr", it) }
                        s.maxHr?.let { put("max_hr", it) }
                        if (s.zoneMinutes.isNotEmpty()) put("zones_min", s.zoneMinutes.joinToString("/"))
                    }
                )
            }
            "diet" -> DietStore.meals
                .groupBy { it.date() }
                .toList()
                .filter { inRange(it.first) }
                .sortedBy { it.first }
                .takeLast(limit)
                .forEach { (date, meals) ->
                    val intake = meals.sumOf { it.calories }
                    rows.put(
                        JSONObject().apply {
                            put("date", date.toString())
                            put("intake_kcal", intake)
                            DietStore.calorieTarget(data, date, goal)?.let { target ->
                                put("target_kcal", target)
                                put("remaining_kcal", target - intake)
                            }
                            meals.mapNotNull { it.proteinG }.takeIf { it.isNotEmpty() }?.let {
                                put("protein_g", "%.0f".format(it.sum()))
                            }
                            meals.mapNotNull { it.carbsG }.takeIf { it.isNotEmpty() }?.let {
                                put("carbs_g", "%.0f".format(it.sum()))
                            }
                            meals.mapNotNull { it.fatG }.takeIf { it.isNotEmpty() }?.let {
                                put("fat_g", "%.0f".format(it.sum()))
                            }
                            put(
                                "meals",
                                JSONArray().apply {
                                    meals.sortedBy { it.epochMs }.forEach { meal ->
                                        put(
                                            JSONObject().apply {
                                                put("time", FmtTime(meal.epochMs))
                                                put("kcal", meal.calories)
                                                if (meal.items.isNotEmpty()) {
                                                    put(
                                                        "items",
                                                        JSONArray().apply {
                                                            meal.items.forEach { item ->
                                                                put(
                                                                    JSONObject().apply {
                                                                        put("name", item.name)
                                                                        item.amount?.let { put("amount", it) }
                                                                        item.kcal?.let { put("kcal", it) }
                                                                    }
                                                                )
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                            )
                        }
                    )
                }
            else -> return "{\"error\":\"未知数据表：$table\"}"
        }
        return JSONObject().apply {
            put("table", table)
            put("from", start.toString())
            put("to", end.toString())
            put("rows", rows)
        }.toString()
    }

    private fun FmtIso(epochMs: Long): String =
        Instant.ofEpochMilli(epochMs).atZone(zone)
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

    private fun FmtTime(epochMs: Long): String =
        Instant.ofEpochMilli(epochMs).atZone(zone)
            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
}
