package com.genkaim.zeppglass.data

import java.time.LocalDate

/** One calendar day of activity, as the band reports it. */
data class DayStat(
    val date: LocalDate,
    val steps: Int? = null,
    val distanceM: Double? = null,
    val calories: Double? = null,
    /** Walking + running minutes from the band's sleep payload; not 活动时长. */
    val activeMinutes: Int? = null,
    /** Official 活动时长 for the day (`totalBurningDuration`), walking excluded. */
    val activityMinutes: Int? = null,
    /** Official daily 活动时长 goal (`burningDurationGoal`). */
    val activityGoal: Int? = null,
    val restingHr: Int? = null,
    val sleepMinutes: Int? = null,
    val sleepScore: Int? = null,
    val stepGoal: Int? = null,
    /** Watch-estimated VO2 max for the day, when the band reported one. */
    val vo2Max: Int? = null
)

/**
 * Official activity figures from `DailyHealth/summary`, which reports on its own
 * schedule and covers days the band summary may not. Walking is excluded there,
 * which is exactly what 活动时长 means in the Zepp app.
 */
data class DailyActivityStat(
    val date: LocalDate,
    val minutes: Int?,
    val goal: Int?
)

data class SleepStageSlice(
    /** deep / light / rem / awake / unknown */
    val stage: String,
    val startEpochSec: Long,
    val endEpochSec: Long
)

data class SleepNight(
    val date: LocalDate,
    val startEpochSec: Long,
    val endEpochSec: Long,
    val score: Int?,
    val deepMinutes: Int?,
    val lightMinutes: Int?,
    val remMinutes: Int?,
    val awakeMinutes: Int?,
    val wakeCount: Int?,
    val restingHr: Int?,
    val stages: List<SleepStageSlice>,
    /** Daytime nap, from the band's `odd_stage` slices. */
    val napMinutes: Int? = null,
    val napStartEpochSec: Long? = null,
    val napEndEpochSec: Long? = null,
    val napStages: List<SleepStageSlice> = emptyList()
) {
    val totalMinutes: Int
        get() = ((endEpochSec - startEpochSec) / 60).toInt().coerceAtLeast(0)
}

data class ChargeSample(
    val epochMs: Long,
    val total: Int?,
    val physical: Double?,
    val mental: Double?
) {
    /**
     * The watch reports `total` as a rounded integer, so a chart drawn from it
     * is a staircase: 83, 83, … 82, 82, … The two components are unrounded
     * doubles and `total` is `round((physical + mental) / 2)`, so their average
     * is the same quantity without the rounding — the honest smooth curve.
     */
    val curveValue: Double?
        get() = if (physical != null && mental != null) (physical + mental) / 2.0
        else total?.toDouble()
}

data class ChargeDay(
    val date: LocalDate,
    val total: Int?,
    val physical: Double?,
    val mental: Double?,
    val samples: List<ChargeSample>
)

/**
 * Charge at waking: the reading closest to the moment the band recorded waking
 * up. Falls back to the day's last score when there is no sleep record.
 */
fun ChargeDay.morningSample(wakeEpochSec: Long?): ChargeSample? {
    val valid = samples.filter { it.total != null }
    if (valid.isEmpty()) return null
    if (wakeEpochSec == null) return valid.lastOrNull()
    val target = wakeEpochSec * 1000
    return valid.minByOrNull { kotlin.math.abs(it.epochMs - target) }
}

fun ChargeDay.morningScore(wakeEpochSec: Long?): Int? =
    morningSample(wakeEpochSec)?.total ?: total

data class ReadinessDay(
    val date: LocalDate,
    val score: Int?,
    val physicalScore: Int?,
    val mentalScore: Int?,
    val hrvScore: Int?,
    val rhrScore: Int?,
    val sleepHrv: Int?,
    val sleepRhr: Int?,
    val ahiScore: Int?,
    val skinTempScore: Int?,
    val insight: Int?,
    /** Official baselines; null when the watch sent the 255 "no baseline" marker. */
    val rhrBaseline: Int? = null,
    val hrvBaseline: Int? = null
) {
    /**
     * The Zepp app shows 身心准备度 as 无数据 when the physical or mental
     * recovery component is missing, even if `rdnsScore` happens to carry a
     * number. Follow the official behaviour instead of showing a score the
     * official app does not show.
     */
    val officialScore: Int?
        get() = score?.takeIf { physicalScore != null && mentalScore != null }
}

/** Body facts from the Zepp profile, used for a diet energy target. */
data class BodyProfile(
    val heightCm: Int? = null,
    val birthYear: Int? = null,
    val birthMonth: Int? = null,
    val gender: Int? = null,
    val weightKg: Double? = null
) {
    /** 1 = male in the Zepp profile. */
    val isMale: Boolean?
        get() = when (gender) {
            1 -> true
            0, 2 -> false
            else -> null
        }
}

data class PaiDay(
    val date: LocalDate,
    val daily: Double?,
    val total: Double?,
    val lowMinutes: Int?,
    val mediumMinutes: Int?,
    val highMinutes: Int?,
    /** PAI earned in each heart-rate zone, as the watch reports it. */
    val lowPai: Double? = null,
    val mediumPai: Double? = null,
    val highPai: Double? = null,
    /** Lower heart-rate limits of the three zones. */
    val lowHr: Int? = null,
    val mediumHr: Int? = null,
    val highHr: Int? = null
)

data class StressDay(
    val date: LocalDate,
    val average: Int?,
    val minimum: Int?,
    val maximum: Int?,
    val relaxedPct: Double?,
    val normalPct: Double?,
    val mediumPct: Double?,
    val highPct: Double?,
    val curve: List<Pair<Long, Int>>
)

data class Spo2Point(val epochMs: Long, val value: Int, val automatic: Boolean)

data class WeightPoint(
    val epochMs: Long,
    val weightKg: Double?,
    val bmi: Double?,
    val bodyFatPct: Double?
)

data class Workout(
    val id: String,
    val typeCode: Int?,
    val startEpochSec: Long,
    val endEpochSec: Long,
    val durationSec: Int,
    val distanceM: Double,
    val calories: Double?,
    val avgHr: Double?,
    val maxHr: Double?,
    val load: Double?,
    /** Cloud bucket the detail endpoint needs; the history row carries it. */
    val source: String? = null,
    val altitudeGainM: Double? = null,
    val altitudeLossM: Double? = null,
    val steps: Int? = null,
    val avgCadence: Double? = null,
    val maxCadence: Double? = null,
    val location: String? = null,
    val city: String? = null,
    /** Watch-estimated VO2 max attached to some running workouts. */
    val vo2Max: Int? = null
) {
    val name: String get() = SportTypes.label(typeCode)
    val paceSecPerKm: Double?
        get() = if (distanceM >= 100 && durationSec > 0) durationSec / (distanceM / 1000.0) else null
}

/** One GPS fix of a workout track. */
data class GeoPoint(val lat: Double, val lon: Double)

/**
 * A per-second series from the workout detail payload. The payload stores each
 * metric as "count,value;" runs (heart rate as second-to-second deltas), so the
 * parser expands it into one value per second from the workout start.
 */
data class WorkoutSeries(
    val label: String,
    val unit: String?,
    val values: List<Float>
)

/**
 * What `/v1/sport/run/detail.json` adds to a workout summary: the GPS track,
 * the per-second curves it carries and every other raw field.
 */
data class WorkoutDetail(
    val points: List<GeoPoint>,
    val fields: List<Pair<String, String>>,
    val series: List<WorkoutSeries> = emptyList()
)

data class LoadDay(
    val date: LocalDate,
    val load: Double?,
    val weekLoad: Double?,
    val optimalMin: Double?,
    val optimalMax: Double?,
    val overreaching: Double?
)

/** One all-day HRV (rMSSD) sample from the v2 events stream. */
data class HrvSample(val epochMs: Long, val value: Double)

/**
 * A single number attributed to a calendar day. Used by the streams the watch
 * reports once per night (呼吸率) rather than as a point on a clock.
 */
data class DailyValue(val date: LocalDate, val value: Double)

/**
 * The night's breathing summary from the watch's blood-oxygen stream.
 *
 * [apneaIndex] is 低通气/呼吸暂停指数 in events per hour, [apneaEvents] the
 * count behind it. Both stay `null` when the watch did not report them: a zero
 * would read as "measured, nothing found", which is a different claim.
 */
data class NightBreathing(
    val date: LocalDate,
    val apneaIndex: Double? = null,
    val apneaEvents: Int? = null,
    val oxygenScore: Int? = null,
    val measuredMinutes: Int? = null
)

data class HealthData(
    val fetchedAt: Long,
    val nickname: String?,
    val avatarUrl: String?,
    val days: List<DayStat>,
    val nights: List<SleepNight>,
    val chargeDays: List<ChargeDay>,
    val readiness: List<ReadinessDay>,
    val pai: List<PaiDay>,
    val stress: List<StressDay>,
    val spo2: List<Spo2Point>,
    val weights: List<WeightPoint>,
    val workouts: List<Workout>,
    val load: List<LoadDay>,
    val heartRate: List<Pair<Long, Int>>,
    val strain: List<StrainDay> = emptyList(),
    val hrv: List<HrvSample> = emptyList(),
    /** 每夜呼吸率（次/分），由手表的逐分钟呼吸率折算成一天一个值。 */
    val respiration: List<DailyValue> = emptyList(),
    /** 夜间呼吸暂停/低通气汇总，来自血氧流的 `odi` 子类型。 */
    val nightBreathing: List<NightBreathing> = emptyList(),
    /** Watch's latest VO2 max, independent of the 34-day page window. */
    val vo2Max: Int? = null,
    val body: BodyProfile = BodyProfile(),
    val warnings: List<String> = emptyList()
) {
    fun day(date: LocalDate): DayStat? = days.lastOrNull { it.date == date }
    fun latestDay(): DayStat? = days.lastOrNull()
    fun latestNight(): SleepNight? = nights.maxByOrNull { it.date }
    fun latestCharge(): ChargeDay? = chargeDays.maxByOrNull { it.date }
    fun latestReadiness(): ReadinessDay? = readiness.maxByOrNull { it.date }
    fun latestLoad(): LoadDay? = load.maxByOrNull { it.date }
    fun latestWeight(): WeightPoint? = weights.maxByOrNull { it.epochMs }
    fun latestStrain(): StrainDay? = strain.maxByOrNull { it.date }
    fun strainOn(date: LocalDate): StrainDay? = strain.lastOrNull { it.date == date }
    fun chargeOn(date: LocalDate): ChargeDay? = chargeDays.lastOrNull { it.date == date }

    fun nightOn(date: LocalDate): SleepNight? = nights.lastOrNull { it.date == date }

    fun respirationOn(date: LocalDate): Double? = respiration.lastOrNull { it.date == date }?.value

    fun breathingOn(date: LocalDate): NightBreathing? = nightBreathing.lastOrNull { it.date == date }
}

/**
 * Same shape, no readings.
 *
 * When the last sync is more than three hours old the app keeps the layout but
 * shows `--` instead of numbers, so a value on screen is always recent. Dates,
 * labels and the workout history are kept: they are records, not "now".
 */
fun HealthData.withoutValues(): HealthData = copy(
    days = days.map { DayStat(date = it.date) },
    nights = emptyList(),
    chargeDays = chargeDays.map {
        ChargeDay(date = it.date, total = null, physical = null, mental = null, samples = emptyList())
    },
    readiness = readiness.map {
        ReadinessDay(
            date = it.date,
            score = null,
            physicalScore = null,
            mentalScore = null,
            hrvScore = null,
            rhrScore = null,
            sleepHrv = null,
            sleepRhr = null,
            ahiScore = null,
            skinTempScore = null,
            insight = null,
            rhrBaseline = null,
            hrvBaseline = null
        )
    },
    pai = pai.map {
        PaiDay(date = it.date, daily = null, total = null, lowMinutes = null, mediumMinutes = null, highMinutes = null)
    },
    stress = stress.map {
        StressDay(
            date = it.date,
            average = null,
            minimum = null,
            maximum = null,
            relaxedPct = null,
            normalPct = null,
            mediumPct = null,
            highPct = null,
            curve = emptyList()
        )
    },
    spo2 = emptyList(),
    weights = weights.map { WeightPoint(epochMs = it.epochMs, weightKg = null, bmi = null, bodyFatPct = null) },
    load = load.map {
        LoadDay(date = it.date, load = null, weekLoad = null, optimalMin = null, optimalMax = null, overreaching = null)
    },
    heartRate = emptyList(),
    strain = emptyList(),
    hrv = emptyList(),
    respiration = emptyList(),
    nightBreathing = emptyList()
)
