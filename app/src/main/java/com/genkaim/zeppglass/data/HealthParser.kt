package com.genkaim.zeppglass.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Base64
import java.util.zip.GZIPInputStream
import kotlin.math.roundToInt

/**
 * Turns the Zepp JSON payloads into the app's own model.
 *
 * Field names come from the live payloads, not from guesses. A value that is
 * missing stays `null`: the app never prints a zero for "not measured".
 */
object HealthParser {

    // ---------- helpers ----------

    private fun JSONObject.anyNumber(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return when (val value = opt(key)) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        }
    }

    private fun JSONObject.anyInt(key: String): Int? = anyNumber(key)?.roundToInt()

    private fun JSONObject.anyString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val value = optString(key, "")
        return value.ifBlank { null }
    }

    private fun JSONObject.obj(key: String): JSONObject? = optJSONObject(key)

    private fun JSONObject.arr(key: String): JSONArray? = optJSONArray(key)

    private fun dateOf(epochMs: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()

    // ---------- band ----------

    class BandParsed(
        val days: List<DayStat>,
        val nights: List<SleepNight>,
        val heartRate: List<Pair<Long, Int>>
    )

    fun parseBand(payload: JSONObject, zone: ZoneId): BandParsed {
        val days = mutableMapOf<LocalDate, DayStat>()
        val nights = mutableListOf<SleepNight>()
        val heartRate = mutableListOf<Pair<Long, Int>>()

        val items = payload.optJSONArray("data") ?: return BandParsed(emptyList(), emptyList(), emptyList())
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val dateText = item.anyString("date_time") ?: continue
            val date = runCatching { LocalDate.parse(dateText) }.getOrNull() ?: continue
            val summary = decodeSummary(item.anyString("summary")) ?: continue

            val activity = summary.obj("stp")
            val sleep = summary.obj("slp")

            val steps = activity?.anyInt("ttl")
            val distance = activity?.anyNumber("dis")
            val calories = activity?.anyNumber("cal")
            val active = (activity?.anyInt("wk") ?: 0) + (activity?.anyInt("rn") ?: 0)

            val night = sleep?.let { parseNight(date, it, zone) }
            if (night != null) nights.add(night)

            days[date] = DayStat(
                date = date,
                steps = steps,
                distanceM = distance,
                calories = calories,
                activeMinutes = active.takeIf { it > 0 },
                restingHr = sleep?.anyInt("rhr"),
                sleepMinutes = night?.totalMinutes,
                sleepScore = night?.score,
                stepGoal = summary.anyInt("goal"),
                vo2Max = (
                    summary.anyNumber("vo2max")
                        ?: summary.obj("vo2max")?.anyNumber("value")
                        ?: summary.obj("vo2")?.anyNumber("vo2max")
                        ?: summary.anyNumber("vo2")
                    )?.takeIf { it in 10.0..90.0 }?.roundToInt()
            )

            item.anyString("data_hr")?.let { encoded ->
                decodeHeartRate(encoded, date, zone).let(heartRate::addAll)
            }
        }
        return BandParsed(days.values.sortedBy { it.date }, nights.sortedBy { it.date }, heartRate)
    }

    /**
     * `DailyHealth/summary` carries the official 活动时长 (`totalBurningDuration`,
     * walking excluded) and its daily goal (`burningDurationGoal`). Values are
     * keyed by `dateString` because each item holds one day's samples.
     */
    fun parseDailyHealth(payload: JSONObject, zone: ZoneId): Map<LocalDate, DailyActivityStat> {
        val out = mutableMapOf<LocalDate, DailyActivityStat>()
        val items = payload.optJSONArray("items") ?: return out
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val samples = item.optJSONObject("value")?.optJSONArray("samples") ?: continue
            for (j in 0 until samples.length()) {
                val sample = samples.optJSONObject(j) ?: continue
                val date = sample.anyString("dateString")
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?: continue
                val minutes = sample.anyNumber("totalBurningDuration")?.roundToInt()
                val goal = sample.anyNumber("burningDurationGoal")?.roundToInt()
                out[date] = DailyActivityStat(
                    date = date,
                    minutes = minutes?.takeIf { it >= 0 },
                    goal = goal?.takeIf { it > 0 }
                )
            }
        }
        return out
    }

    private fun decodeSummary(encoded: String?): JSONObject? {        if (encoded.isNullOrBlank()) return null
        return runCatching {
            var bytes = Base64.getDecoder().decode(encoded)
            if (bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
                bytes = GZIPInputStream(ByteArrayInputStream(bytes)).readBytes()
            }
            JSONObject(String(bytes, Charsets.UTF_8))
        }.getOrNull()
    }

    /**
     * 呼吸率: `RespiratoryRate/real_data` carries one item per day whose
     * `value.measurements` is a base64 blob of 1440 bytes — one byte of
     * breaths-per-minute per minute of the local day, 0 meaning "not measured".
     * The night's figure is the mean of the plausible readings; bytes outside
     * 4–60 brpm are sensor noise, not physiology, and are dropped rather than
     * dragged into an average.
     */
    fun parseRespiration(payload: JSONObject, zone: ZoneId): List<DailyValue> {
        val items = payload.optJSONArray("items") ?: return emptyList()
        val out = mutableListOf<DailyValue>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val encoded = item.optJSONObject("value")?.anyString("measurements")
                ?: item.anyString("measurements")
                ?: continue
            val bytes = runCatching {
                var raw = Base64.getDecoder().decode(encoded)
                if (raw.size > 2 && raw[0] == 0x1f.toByte() && raw[1] == 0x8b.toByte()) {
                    raw = GZIPInputStream(ByteArrayInputStream(raw)).readBytes()
                }
                raw
            }.getOrNull() ?: continue
            val date = itemDate(item, zone) ?: continue
            val readings = bytes.asSequence()
                .map { (it.toInt() and 0xFF).toDouble() }
                .filter { it in 4.0..60.0 }
                .toList()
            if (readings.isEmpty()) continue
            out.add(DailyValue(date, readings.average()))
        }
        return out.distinctBy { it.date }.sortedBy { it.date }
    }

    /**
     * 夜间呼吸暂停/低通气: the `odi` subtype of the blood-oxygen stream. The
     * fields sit at the item's top level (not under `value`).
     *
     * `ahi`/`ahiNum` are the hypopnea figures and are preferred when the
     * account reports them; `odi`/`odiNum` are the oxygen-desaturation
     * fallback, so the card is never blank on firmware that ships only ODI.
     */
    fun parseNightBreathing(payload: JSONObject, zone: ZoneId): List<NightBreathing> {
        val items = payload.optJSONArray("items") ?: return emptyList()
        val out = mutableListOf<NightBreathing>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val date = itemDate(item, zone) ?: continue
            val index = item.anyNumber("ahi") ?: item.anyNumber("odi")
            val events = item.anyInt("ahiNum") ?: item.anyInt("odiNum")
            val score = item.anyInt("score")?.takeIf { it in 0..100 }
            val seconds = item.anyNumber("cost")
            val minutes = seconds?.let { (it / 60.0).roundToInt() }?.takeIf { it > 0 && it < 1440 }
            if (index == null && events == null && score == null && minutes == null) continue
            out.add(
                NightBreathing(
                    date = date,
                    apneaIndex = index?.takeIf { it >= 0.0 },
                    apneaEvents = events?.takeIf { it >= 0 },
                    oxygenScore = score,
                    measuredMinutes = minutes
                )
            )
        }
        return out.distinctBy { it.date }.sortedBy { it.date }
    }

    /** The calendar day an event item belongs to, however the payload spells it. */
    private fun itemDate(item: JSONObject, zone: ZoneId): LocalDate? {
        for (key in listOf("dateString", "date", "day", "dayId")) {
            val text = item.anyString(key) ?: continue
            runCatching { LocalDate.parse(text.take(10)) }.getOrNull()?.let { return it }
        }
        val stamp = item.anyNumber("timestamp") ?: item.anyNumber("time") ?: item.anyNumber("startTime")
        return stamp?.let { dateOf(it.toLong(), zone) }
    }

    private fun parseNight(date: LocalDate, sleep: JSONObject, zone: ZoneId): SleepNight? {
        val start = sleep.anyNumber("st")?.toLong() ?: return null
        val end = sleep.anyNumber("ed")?.toLong() ?: return null
        if (end <= start) return null

        // Stage offsets count minutes from the midnight before the reported day.
        val anchor = date.atStartOfDay(zone).minusDays(1).toEpochSecond()
        val slices = mutableListOf<SleepStageSlice>()
        val stages = sleep.arr("stage")
        if (stages != null) {
            for (i in 0 until stages.length()) {
                val stage = stages.optJSONObject(i) ?: continue
                val mode = stage.anyNumber("mode")?.roundToInt() ?: continue
                val from = stage.anyNumber("start")?.toLong() ?: continue
                val to = stage.anyNumber("stop")?.toLong() ?: continue
                if (to < from) continue
                val name = when (mode) {
                    5 -> "deep"
                    4 -> "light"
                    8, 11 -> "rem"
                    7 -> "awake"
                    else -> "unknown"
                }
                slices.add(
                    SleepStageSlice(
                        stage = name,
                        startEpochSec = anchor + from * 60,
                        endEpochSec = anchor + (to + 1) * 60
                    )
                )
            }
        }

        // Daytime naps ride in `odd_stage`; parse their stages like the night.
        val napSlices = mutableListOf<SleepStageSlice>()
        sleep.arr("odd_stage")?.let { odd ->
            for (i in 0 until odd.length()) {
                val stage = odd.optJSONObject(i) ?: continue
                val mode = stage.anyNumber("mode")?.roundToInt() ?: continue
                val from = stage.anyNumber("start")?.toLong() ?: continue
                val to = stage.anyNumber("stop")?.toLong() ?: continue
                if (to < from) continue
                val name = when (mode) {
                    5 -> "deep"
                    4 -> "light"
                    8, 11 -> "rem"
                    7 -> "awake"
                    else -> "unknown"
                }
                napSlices.add(
                    SleepStageSlice(
                        stage = name,
                        startEpochSec = anchor + from * 60,
                        endEpochSec = anchor + (to + 1) * 60
                    )
                )
            }
        }
        val napStart = napSlices.minOfOrNull { it.startEpochSec }
        val napEnd = napSlices.maxOfOrNull { it.endEpochSec }

        // The band does not always write `dp/lt/rm/wk`; when a field is absent the
        // stage list still carries the same information. Summing the recognised
        // stages keeps REM from showing as "--" on firmware that only ships stages.
        fun stageMinutes(name: String): Int? {
            val minutes = slices
                .filter { it.stage == name }
                .sumOf { ((it.endEpochSec - it.startEpochSec) / 60).toInt() }
            return minutes.takeIf { it > 0 }
        }

        return SleepNight(
            date = date,
            startEpochSec = start,
            endEpochSec = end,
            score = sleep.anyInt("ss")?.takeIf { it in 1..100 },
            deepMinutes = sleep.anyInt("dp") ?: stageMinutes("deep"),
            lightMinutes = sleep.anyInt("lt") ?: stageMinutes("light"),
            remMinutes = sleep.anyInt("rm") ?: stageMinutes("rem"),
            awakeMinutes = sleep.anyInt("wk") ?: stageMinutes("awake"),
            wakeCount = sleep.anyInt("wc"),
            restingHr = sleep.anyInt("rhr")?.takeIf { it in 20..200 },
            stages = slices.sortedBy { it.startEpochSec },
            napMinutes = if (napStart != null && napEnd != null) {
                ((napEnd - napStart) / 60).toInt().takeIf { it > 0 }
            } else null,
            napStartEpochSec = napStart,
            napEndEpochSec = napEnd,
            napStages = napSlices.sortedBy { it.startEpochSec }
        )
    }

    private fun decodeHeartRate(encoded: String, date: LocalDate, zone: ZoneId): List<Pair<Long, Int>> {
        val bytes = runCatching { Base64.getDecoder().decode(encoded) }.getOrNull() ?: return emptyList()
        val midnight = date.atStartOfDay(zone).toEpochSecond()
        val out = ArrayList<Pair<Long, Int>>(bytes.size)
        bytes.take(1440).forEachIndexed { minute, raw ->
            val bpm = raw.toInt() and 0xFF
            if (bpm in 20..240) out.add(midnight + minute * 60L to bpm)
        }
        return out
    }

    // ---------- profile ----------

    fun parseProfile(json: JSONObject): Pair<String?, String?> =
        json.anyString("nickName") to json.anyString("iconUrl")

    /** Height, birthday, gender and weight from the same profile payload. */
    fun parseBodyProfile(json: JSONObject): BodyProfile {
        val birthday = json.anyString("birthday").orEmpty()
        val year = birthday.substringBefore("-").toIntOrNull()
        val month = birthday.substringAfter("-", "").take(2).toIntOrNull()
        return BodyProfile(
            heightCm = json.anyNumber("height")?.roundToInt()?.takeIf { it in 100..250 },
            birthYear = year?.takeIf { it in 1900..2020 },
            birthMonth = month?.takeIf { it in 1..12 },
            gender = json.anyNumber("gender")?.roundToInt()?.takeIf { it in 0..2 },
            weightKg = json.anyNumber("weight")?.takeIf { it in 20.0..300.0 }
        )
    }

    // ---------- readiness ----------

    fun parseReadiness(json: JSONObject, zone: ZoneId): List<ReadinessDay> {
        val items = json.optJSONArray("items") ?: return emptyList()
        val out = mutableListOf<ReadinessDay>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val ts = item.anyNumber("timestamp")?.toLong() ?: continue
            fun score(key: String): Int? = item.anyNumber(key)?.roundToInt()?.takeIf { it in 1..100 }
            out.add(
                ReadinessDay(
                    date = dateOf(ts, zone),
                    score = score("rdnsScore"),
                    physicalScore = score("phyScore"),
                    mentalScore = score("mentScore"),
                    hrvScore = score("hrvScore"),
                    rhrScore = score("rhrScore"),
                    sleepHrv = item.anyNumber("sleepHRV")?.roundToInt(),
                    sleepRhr = item.anyNumber("sleepRHR")?.roundToInt(),
                    ahiScore = score("ahiScore"),
                    skinTempScore = score("skinTempScore"),
                    insight = item.anyNumber("rdnsInsight")?.roundToInt()?.takeIf { it in 0..100 },
                    // 255 is the watch's "baseline not ready" marker: keep it null.
                    rhrBaseline = item.anyNumber("rhrBaseline")?.roundToInt()?.takeIf { it in 20..200 },
                    hrvBaseline = item.anyNumber("hrvBaseline")?.roundToInt()?.takeIf { it in 5..254 }
                )
            )
        }
        return out.distinctBy { it.date }
    }

    // ---------- PAI ----------

    fun parsePai(json: JSONObject, zone: ZoneId): List<PaiDay> {
        val items = json.optJSONArray("items") ?: return emptyList()
        val out = mutableListOf<PaiDay>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val ts = item.anyNumber("timestamp")?.toLong() ?: continue
            out.add(
                PaiDay(
                    date = dateOf(ts, zone),
                    daily = item.anyNumber("dailyPai"),
                    total = item.anyNumber("totalPai"),
                    lowMinutes = item.anyNumber("lowZoneMinutes")?.roundToInt(),
                    mediumMinutes = item.anyNumber("mediumZoneMinutes")?.roundToInt(),
                    highMinutes = item.anyNumber("highZoneMinutes")?.roundToInt(),
                    lowPai = item.anyNumber("lowZonePai"),
                    mediumPai = item.anyNumber("mediumZonePai"),
                    highPai = item.anyNumber("highZonePai"),
                    lowHr = item.anyNumber("lowZoneLowerLimit")?.roundToInt(),
                    mediumHr = item.anyNumber("mediumZoneLowerLimit")?.roundToInt(),
                    highHr = item.anyNumber("highZoneLowerLimit")?.roundToInt()
                )
            )
        }
        return out.distinctBy { it.date }
    }

    // ---------- charge ----------

    fun parseCharge(json: JSONObject, zone: ZoneId): List<ChargeDay> {
        val items = json.optJSONArray("items") ?: return emptyList()
        val grouped = linkedMapOf<LocalDate, MutableList<ChargeSample>>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val value = item.obj("value") ?: continue
            val start = value.anyNumber("startTime")?.toLong()
                ?: item.anyNumber("timestamp")?.toLong()
                ?: continue
            // Zepp stamps Charge at UTC midnight; `timeZone` carries the zone the
            // watch used ("801,Asia/Shanghai"), which is what decides the local day.
            val itemZone = value.anyString("timeZone")
                ?.substringAfterLast(',')
                ?.trim()
                ?.takeIf { it.contains("/") }
                ?.let { runCatching { ZoneId.of(it) }.getOrNull() }
                ?: zone
            val samples = value.arr("samples") ?: continue
            for (j in 0 until samples.length()) {
                val sample = samples.optJSONObject(j) ?: continue
                val offset = sample.anyNumber("s")?.toLong() ?: 0L
                val total = sample.anyNumber("total")?.roundToInt()?.takeIf { it in 0..100 }
                val physical = sample.anyNumber("physical")?.takeIf { it in 0.0..100.0 }
                val mental = sample.anyNumber("mental")?.takeIf { it in 0.0..100.0 }
                if (total == null && physical == null && mental == null) continue
                // A Charge item starts at 08:00 and runs to 07:55 next day, so the
                // item's start date is wrong for the early-morning half. Group each
                // sample by its own date or 00:00–08:00 ends up empty.
                val sampleTime = start + offset
                val sampleDate = dateOf(sampleTime, itemZone)
                grouped.getOrPut(sampleDate) { mutableListOf() }
                    .add(ChargeSample(sampleTime, total, physical, mental))
            }
        }
        return grouped.map { (date, samples) ->
            val sorted = samples.sortedBy { it.epochMs }
            // The last sample that still carries a real score. Zepp uses 255 as
            // "watch did not compute a value", which is already filtered out.
            val latest = sorted.lastOrNull { it.total != null }
            ChargeDay(
                date = date,
                total = latest?.total,
                physical = latest?.physical,
                mental = latest?.mental,
                samples = sorted
            )
        }.sortedBy { it.date }
    }

    // ---------- stress ----------

    fun parseStress(json: JSONObject, zone: ZoneId): List<StressDay> {
        val items = json.optJSONArray("items") ?: return emptyList()
        // One payload item spans 08:00 -> next 07:55, so its points belong to two
        // calendar dates. Group by each point's own date; otherwise the morning
        // half is filed under yesterday and the day view disagrees with Zepp.
        val pointsByDate = mutableMapOf<LocalDate, MutableList<Pair<Long, Int>>>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val raw = item.anyString("data") ?: continue
            runCatching {
                val arr = JSONArray(raw)
                for (j in 0 until arr.length()) {
                    val point = arr.optJSONObject(j) ?: continue
                    val time = point.anyNumber("time")?.toLong() ?: continue
                    val value = point.anyNumber("value")?.roundToInt() ?: continue
                    if (value !in 1..100) continue
                    val date = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
                    pointsByDate.getOrPut(date) { mutableListOf() }.add(time to value)
                }
            }
        }
        return pointsByDate.map { (date, points) ->
            val curve = points.distinctBy { it.first }.sortedBy { it.first }
            val values = curve.map { it.second }
            val total = values.size.coerceAtLeast(1)
            fun pct(predicate: (Int) -> Boolean) =
                values.count(predicate) * 100.0 / total
            StressDay(
                date = date,
                average = (values.sum().toDouble() / total).roundToInt(),
                minimum = values.minOrNull(),
                maximum = values.maxOrNull(),
                // Zepp's four bands: 放松 0–39, 正常 40–59, 中等 60–79, 偏高 80+.
                relaxedPct = pct { it <= 39 },
                normalPct = pct { it in 40..59 },
                mediumPct = pct { it in 60..79 },
                highPct = pct { it >= 80 },
                curve = curve
            )
        }.sortedBy { it.date }
    }

    // ---------- SpO2 ----------

    fun parseSpo2(json: JSONObject): List<Spo2Point> {
        val items = json.optJSONArray("items") ?: return emptyList()
        val out = mutableListOf<Spo2Point>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val extra = item.anyString("extra") ?: continue
            val parsed = runCatching { JSONObject(extra) }.getOrNull() ?: continue
            val value = parsed.anyNumber("spo2")?.roundToInt()?.takeIf { it in 50..100 } ?: continue
            val ts = parsed.anyNumber("timestamp")?.toLong()
                ?: item.anyNumber("timestamp")?.toLong()
                ?: continue
            out.add(Spo2Point(ts, value, parsed.optBoolean("isAuto", false)))
        }
        return out.distinctBy { it.epochMs }
    }

    // ---------- all-day HRV ----------

    /**
     * The HRVRMSSD event is bucketed per day: `timestamp` is the day start and
     * `value.samples[]` holds `{hrv, s}` where `s` is the offset in the day.
     * Flat `{timestamp, rmssd}` items are accepted too, for other firmware.
     */
    fun parseHrv(json: JSONObject): List<HrvSample> {
        val items = json.optJSONArray("items") ?: return emptyList()
        val out = mutableListOf<HrvSample>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val dayStart = item.anyNumber("timestamp")?.toLong() ?: continue
            val samples = item.optJSONObject("value")?.optJSONArray("samples")
            if (samples != null) {
                for (j in 0 until samples.length()) {
                    val sample = samples.optJSONObject(j) ?: continue
                    val offset = sample.anyNumber("s") ?: 0.0
                    val value = sample.anyNumber("hrv") ?: sample.anyNumber("rmssd") ?: continue
                    if (value in 5.0..300.0) {
                        out.add(HrvSample(dayStart + offset.toLong(), value))
                    }
                }
                continue
            }
            // Flat shape fallback.
            val rawTs = item.anyNumber("time") ?: dayStart.toDouble()
            val epochMs = if (rawTs < 10_000_000_000.0) (rawTs * 1000).toLong() else rawTs.toLong()
            val value = item.anyNumber("rmssd")
                ?: item.anyNumber("hrv")
                ?: item.anyNumber("value")
                ?: continue
            if (value in 5.0..300.0) out.add(HrvSample(epochMs, value))
        }
        return out.distinctBy { it.epochMs }.sortedBy { it.epochMs }
    }

    // ---------- weight ----------

    fun parseWeights(json: JSONObject): List<WeightPoint> {
        val items = json.optJSONArray("items") ?: return emptyList()
        val out = mutableListOf<WeightPoint>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val summary = item.obj("summary") ?: continue
            val seconds = item.anyNumber("generatedTime")?.toLong() ?: continue
            val weight = summary.anyNumber("weight")?.takeIf { it in 20.0..400.0 } ?: continue
            out.add(
                WeightPoint(
                    epochMs = seconds * 1000,
                    weightKg = weight,
                    bmi = summary.anyNumber("bmi"),
                    bodyFatPct = summary.anyNumber("bodyFatRate")
                        ?: summary.anyNumber("fatRate")
                        ?: summary.anyNumber("bodyFat")
                )
            )
        }
        return out.sortedBy { it.epochMs }
    }

    // ---------- workouts ----------

    fun parseWorkouts(json: JSONObject): List<Workout> {
        val summaries = json.optJSONObject("data")?.optJSONArray("summary") ?: return emptyList()
        val out = mutableListOf<Workout>()
        for (i in 0 until summaries.length()) {
            val item = summaries.optJSONObject(i) ?: continue
            val end = item.anyNumber("end_time")?.toLong() ?: continue
            val duration = (item.anyNumber("run_time") ?: item.anyNumber("total_time") ?: 0.0).toInt()
            val distance = item.anyNumber("dis") ?: 0.0
            out.add(
                Workout(
                    id = item.anyString("trackid") ?: "$end-$i",
                    typeCode = item.anyNumber("type")?.roundToInt(),
                    startEpochSec = end - duration.coerceAtLeast(0),
                    endEpochSec = end,
                    durationSec = duration,
                    distanceM = distance,
                    calories = item.anyNumber("calorie")?.takeIf { it > 0 },
                    avgHr = item.anyNumber("avg_heart_rate")?.takeIf { it > 0 },
                    maxHr = item.anyNumber("max_heart_rate")?.takeIf { it > 0 },
                    load = item.anyNumber("exercise_load")?.takeIf { it > 0 },
                    source = item.anyString("source"),
                    altitudeGainM = item.anyNumber("altitude_ascend")?.takeIf { it > 0 },
                    altitudeLossM = item.anyNumber("altitude_descend")?.takeIf { it > 0 },
                    steps = item.anyNumber("total_step")?.roundToInt()?.takeIf { it > 0 },
                    avgCadence = item.anyNumber("avg_cadence")?.takeIf { it > 0 },
                    maxCadence = item.anyNumber("max_cadence")?.takeIf { it > 0 },
                    location = item.anyString("location"),
                    city = item.anyString("city"),
                    vo2Max = item.anyNumber("VO2_max")?.takeIf { it in 10.0..90.0 }?.roundToInt()
                )
            )
        }
        return out.sortedByDescending { it.endEpochSec }
    }

    // ---------- workout detail (GPS + every raw field) ----------

    fun parseWorkoutDetail(json: JSONObject): WorkoutDetail {
        val data = json.optJSONObject("data") ?: JSONObject()
        val fields = mutableListOf<Pair<String, String>>()
        val keys = data.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key == "longitude_latitude") continue
            val value = data.opt(key) ?: continue
            if (value == JSONObject.NULL) continue
            val text = when (value) {
                is JSONArray -> "${value.length()} 项"
                is JSONObject -> "${value.length()} 个字段"
                else -> value.toString()
            }
            if (text.isBlank()) continue
            fields.add(key to text.take(200))
        }
        return WorkoutDetail(
            points = parseTrack(data.optString("longitude_latitude")),
            fields = fields,
            series = parseWorkoutSeries(data)
        )
    }

    /**
     * The detail payload packs each curve as "count,value;" runs: a bare
     * "count,value" repeats that value `count` seconds, and a bare number is a
     * single second. `heart_rate` is delta encoded (the first run is the absolute
     * value, every later run is the change), which was checked against a day of
     * real samples. Curves with no shape left after expansion are dropped, so an
     * indoor session simply shows no speed chart.
     */
    private fun parseWorkoutSeries(data: JSONObject): List<WorkoutSeries> {
        val out = mutableListOf<WorkoutSeries>()

        fun add(key: String, label: String, unit: String?, scale: Float, delta: Boolean) {
            val values = expandSeries(data.optString(key), delta)?.map { it * scale } ?: return
            if (values.size < 3) return
            if (values.max() == values.min()) return
            out.add(WorkoutSeries(label, unit, downsample(values, 160)))
        }

        add("heart_rate", "心率", "bpm", 1f, delta = true)
        add("speed", "速度", "km/h", 3.6f, delta = false)
        add("gait", "步频", "spm", 1f, delta = false)
        add("altitude", "海拔", "m", 0.01f, delta = false)
        return out
    }

    private fun expandSeries(raw: String, delta: Boolean): List<Float>? {
        if (raw.isBlank()) return null
        val out = ArrayList<Float>()
        var current = 0f
        var first = true
        for (chunk in raw.split(';')) {
            if (chunk.isEmpty()) continue
            val parts = chunk.split(',')
            val count = if (parts.size == 1) 1 else parts[0].trim().toIntOrNull() ?: 1
            val value = parts.last().trim().toFloatOrNull() ?: continue
            if (delta) {
                if (first) {
                    current = value
                    first = false
                    repeat(count.coerceAtLeast(1)) { out.add(current) }
                } else {
                    current += value
                    repeat(count.coerceAtLeast(0)) { out.add(current) }
                }
            } else {
                repeat(count.coerceAtLeast(0)) { out.add(value) }
            }
            if (out.size > 100_000) break
        }
        return out.takeIf { it.isNotEmpty() }
    }

    private fun downsample(values: List<Float>, max: Int): List<Float> {
        if (values.size <= max) return values
        val step = values.size.toFloat() / max
        return (0 until max).map { values[(it * step).toInt().coerceIn(0, values.lastIndex)] }
    }

    /**
     * `longitude_latitude` is "lon,lat;lon,lat;…": the first fix is absolute and
     * every later pair is the difference to the previous one. Some firmware
     * writes absolute fixes for all points, so both readings are built and the
     * one that forms a sane, local track wins.
     */
    private fun parseTrack(text: String): List<GeoPoint> {
        if (text.isBlank()) return emptyList()
        val pairs = text.split(';').mapNotNull { chunk ->
            val parts = chunk.trim().split(',')
            if (parts.size < 2) return@mapNotNull null
            val lon = parts[0].trim().toDoubleOrNull() ?: return@mapNotNull null
            val lat = parts[1].trim().toDoubleOrNull() ?: return@mapNotNull null
            lon to lat
        }
        if (pairs.isEmpty()) return emptyList()

        fun valid(lat: Double, lon: Double) =
            lat in -90.0..90.0 && lon in -180.0..180.0 && !(lat == 0.0 && lon == 0.0)

        val absolute = pairs.mapNotNull { (lon, lat) ->
            if (valid(lat, lon)) GeoPoint(lat, lon) else null
        }

        var lon = 0.0
        var lat = 0.0
        val cumulative = mutableListOf<GeoPoint>()
        pairs.forEachIndexed { index, (dLon, dLat) ->
            if (index == 0) {
                lon = dLon
                lat = dLat
            } else {
                lon += dLon
                lat += dLat
            }
            if (valid(lat, lon)) cumulative.add(GeoPoint(lat, lon))
        }

        fun span(points: List<GeoPoint>): Double {
            if (points.size < 2) return Double.MAX_VALUE
            val latSpan = points.maxOf { it.lat } - points.minOf { it.lat }
            val lonSpan = points.maxOf { it.lon } - points.minOf { it.lon }
            return latSpan + lonSpan
        }

        val useCumulative = cumulative.size > absolute.size ||
            (cumulative.size == absolute.size && span(cumulative) < span(absolute))
        val picked = if (useCumulative) cumulative else absolute
        return if (picked.size < 2) emptyList() else picked
    }

    // ---------- VO2 max ----------

    /**
     * `WatchSportStatistics/VO2_MAX` only returns days on which the watch
     * re-estimated the value; most days carry 0. The official app keeps showing
     * the last real estimate, so take the newest item with a plausible value.
     */
    fun parseVo2Max(json: JSONObject): Int? {
        val items = json.optJSONArray("items") ?: return null
        var bestDate: LocalDate? = null
        var best: Double? = null
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val day = item.anyString("dayId")
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: continue
            val value = maxOf(
                item.anyNumber("vo2_max_run") ?: 0.0,
                item.anyNumber("vo2_max_walking") ?: 0.0
            )
            if (value !in 10.0..90.0) continue
            if (bestDate == null || day.isAfter(bestDate)) {
                bestDate = day
                best = value
            }
        }
        return best?.roundToInt()
    }

    // ---------- training load ----------

    fun parseLoad(json: JSONObject, zone: ZoneId): List<LoadDay> {
        val items = json.optJSONArray("items") ?: return emptyList()
        val out = mutableListOf<LoadDay>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val day = item.anyString("dayId") ?: continue
            val date = runCatching { LocalDate.parse(day) }.getOrNull() ?: continue
            out.add(
                LoadDay(
                    date = date,
                    load = item.anyNumber("currnetDayTrainLoad"),
                    weekLoad = item.anyNumber("wtlSum"),
                    optimalMin = item.anyNumber("wtlSumOptimalMin"),
                    optimalMax = item.anyNumber("wtlSumOptimalMax"),
                    overreaching = item.anyNumber("wtlSumOverreaching")
                )
            )
        }
        return out.sortedBy { it.date }
    }
}

