package com.genkaim.zeppglass.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Day strain on the 0–21 column.
 *
 * The heart-rate term is a real training-impulse model rather than a hand-tuned
 * zone sum: Banister's TRIMP weights each minute by the fraction of heart-rate
 * reserve it used, with an exponential that makes high intensity count far more
 * than time spent at low intensity.
 *
 *   x     = (HR − HRrest) / (HRmax − HRrest)
 *   TRIMP = minutes × x × a × e^(b·x),  a/b = 0.64/1.92 (male), 0.86/1.67 (female)
 *
 * Only minutes above 30% HRR count, so sitting and slow walking stay at zero;
 * HRmax comes from Tanaka (208 − 0.7 × age) when the profile has a birthday,
 * otherwise from the 95th percentile of the user's own readings. Workout records
 * fill in sessions the sparse band samples missed (the larger of the two is
 * used, never the sum), and steps add a small non-exercise term for days the
 * band was not worn during movement.
 *
 * The resulting impulse is mapped onto 0–21 with a saturation curve
 * `21 × (1 − e^(−TRIMP/90))`, calibrated so a 30-minute moderate session lands
 * around 8, a hard 90-minute session around 18, and a rest day near 1–3.
 */
data class StrainDay(
    val date: LocalDate,
    val strain: Double?,
    val trimp: Double?,
    val zoneMinutes: List<Int>,
    val averageHr: Int?,
    val maxHr: Int?,
    val sampleMinutes: Int,
    /** 0-100 cognitive load, folded into the score as an equivalent impulse. */
    val mental: Double? = null,
    /** Multiplier applied to today's impulse from HRV and resting HR vs baseline. */
    val recovery: Double? = null
)

/** Wording for the 0–21 score. */
val StrainDay.band: String
    get() = when {
        strain == null -> "还没算出"
        strain < 10 -> "轻度"
        strain < 14 -> "适度"
        strain < 18 -> "偏高"
        else -> "很高"
    }

/** Wording for the separate 0–100 cognitive load. */
val StrainDay.mentalBand: String
    get() = when (val value = mental) {
        null -> "没有数据"
        else -> when {
            value < 30 -> "轻度"
            value < 55 -> "中度"
            value < 75 -> "偏高"
            else -> "很高"
        }
    }

/** The two inputs behind [StrainDay.mental], for the card's second line. */
data class MentalInputs(
    val stress: Int?,
    val mentalCharge: Double?
)

object StrainCalculator {

    /** Banister coefficients. */
    private const val MALE_A = 0.64
    private const val MALE_B = 1.92
    private const val FEMALE_A = 0.86
    private const val FEMALE_B = 1.67

    /** Below this share of heart-rate reserve nothing is counted. */
    private const val FLOOR = 0.30

    /** Impulse that maps to 63% of the 21-point column. */
    private const val SCALE = 90.0

    /** A full 0-100 cognitive load is worth this many TRIMP. */
    private const val MENTAL_TO_TRIMP = 0.25

    // Zones used for the minutes-per-zone card, not for the score itself.
    private val zoneStarts = doubleArrayOf(0.30, 0.50, 0.60, 0.70, 0.80, 0.90)

    fun compute(
        days: List<DayStat>,
        heartRate: List<Pair<Long, Int>>,
        readiness: List<ReadinessDay>,
        workouts: List<Workout>,
        body: BodyProfile = BodyProfile(),
        stress: List<StressDay> = emptyList(),
        charge: List<ChargeDay> = emptyList(),
        nights: List<SleepNight> = emptyList(),
        zone: ZoneId
    ): List<StrainDay> {
        // A load day ends when the night does, not at midnight: heart rate taken
        // after 00:00 but before waking still belongs to the day that is being
        // closed, otherwise the score reset while the user was still up and the
        // new day started with the tail of the old one. Without a sleep record
        // the 05:00 boundary stands in for the wake time.
        val wakeByDate = nights.associate { it.date to it.endEpochSec }
        fun loadDay(epochSec: Long): LocalDate {
            val at = Instant.ofEpochSecond(epochSec).atZone(zone)
            val date = at.toLocalDate()
            val wake = wakeByDate[date]
            return when {
                wake != null && epochSec < wake -> date.minusDays(1)
                wake == null && at.hour < 5 -> date.minusDays(1)
                else -> date
            }
        }
        val byDate = heartRate.groupBy { instant -> loadDay(instant.first) }
        val restingByDate = days.associate { it.date to it.restingHr }
        val readinessRest = readiness.associate { it.date to it.sleepRhr }
        val stressByDate = stress.associate { it.date to it.average }
        val mentalChargeByDate = charge.associate { it.date to it.mental }
        val readinessByDate = readiness.associateBy { it.date }

        val age = body.birthYear?.let { year ->
            LocalDate.now().year - year
        }?.takeIf { it in 10..100 }
        val male = body.isMale
        val a = if (male == false) FEMALE_A else if (male == true) MALE_A else (MALE_A + FEMALE_A) / 2
        val b = if (male == false) FEMALE_B else if (male == true) MALE_B else (MALE_B + FEMALE_B) / 2

        // Tanaka fits real populations better than 220 − age; without a birthday
        // fall back to the user's own high readings.
        val highReadings = heartRate.map { it.second }.filter { it >= 140 }.sorted()
        val observedMax = if (highReadings.size >= 5) {
            highReadings[(highReadings.size * 0.95).toInt().coerceAtMost(highReadings.lastIndex)]
        } else {
            185
        }
        val formulaMax = age?.let { (208.0 - 0.7 * it).roundToInt() }

        return days.map { day ->
            val samples = byDate[day.date].orEmpty().sortedBy { it.first }
            val mental = mentalLoad(
                stressByDate[day.date],
                mentalChargeByDate[day.date]
            )
            if (samples.size < 10) {
                return@map StrainDay(
                    day.date, null, null, emptyList(), null, null, samples.size, mental
                )
            }
            val rest = (readinessRest[day.date] ?: restingByDate[day.date] ?: 60).coerceIn(35, 100)
            val max = (formulaMax ?: observedMax).coerceIn(rest + 40, 215)
            val reserve = (max - rest).coerceAtLeast(1).toDouble()
            val minutesPerSample = medianSpacingMinutes(samples)

            fun impulseX(bpm: Int): Double = ((bpm - rest) / reserve).coerceIn(0.0, 1.0)

            fun impulse(minutes: Double, x: Double): Double {
                val effective = if (x < FLOOR) 0.0 else x
                return minutes * effective * a * exp(b * effective)
            }

            val zoneMinutes = DoubleArray(zoneStarts.size)
            var hrImpulse = 0.0
            samples.forEach { (_, bpm) ->
                val x = impulseX(bpm)
                hrImpulse += impulse(minutesPerSample, x)
                val index = zoneStarts.indexOfLast { x >= it }
                if (index >= 0) zoneMinutes[index] += minutesPerSample
            }

            // The band samples HR sparsely, so a recorded session contributes at
            // least what its average HR implies. max() keeps one session from
            // being counted twice.
            val workoutImpulse = workouts
                .filter {
                    Instant.ofEpochSecond(it.endEpochSec).atZone(zone).toLocalDate() == day.date
                }
                .sumOf { workout ->
                    val hr = workout.avgHr ?: return@sumOf 0.0
                    impulse(workout.durationSec / 60.0, impulseX(hr.roundToInt()))
                }

            val cardio = maxOf(hrImpulse, workoutImpulse)
            // Non-exercise movement, a small and deliberately modest term.
            val movement = (day.steps ?: 0) / 1000.0 * 2.0
            // Cognitive work is converted to an equivalent impulse: the 0-100
            // mental load is worth at most 25 TRIMP, i.e. about a third of a
            // moderate session. It is a weighted estimate, so it never dominates.
            val mentalImpulse = (mental ?: 0.0) * MENTAL_TO_TRIMP
            val recovery = recoveryFactor(
                hrv = readinessByDate[day.date]?.sleepHrv,
                hrvBaseline = baselineFor(readiness, day.date, { it.hrvBaseline?.toDouble() }, { it.sleepHrv?.toDouble() }),
                rhr = readinessByDate[day.date]?.sleepRhr ?: restingByDate[day.date],
                rhrBaseline = baselineFor(readiness, day.date, { it.rhrBaseline?.toDouble() }, { it.sleepRhr?.toDouble() })
            )
            val load = (cardio + movement + mentalImpulse) * recovery
            val strain = (21.0 * (1.0 - exp(-load / SCALE))).coerceIn(0.0, 21.0)

            StrainDay(
                date = day.date,
                strain = strain,
                trimp = cardio,
                zoneMinutes = zoneMinutes.map { it.roundToInt() },
                averageHr = samples.map { it.second }.average().toInt(),
                maxHr = samples.maxOf { it.second },
                sampleMinutes = (samples.size * minutesPerSample).roundToInt(),
                mental = mental,
                recovery = recovery
            )
        }
    }

    /** Cognitive load (0-100) from all-day stress and the mental Charge part. */
    private fun mentalLoad(stress: Int?, mentalCharge: Double?): Double? {
        val fromStress = stress?.toDouble()
        val fromCharge = mentalCharge?.let { (100.0 - it).coerceIn(0.0, 100.0) }
        return when {
            fromStress != null && fromCharge != null -> 0.65 * fromStress + 0.35 * fromCharge
            fromStress != null -> fromStress
            fromCharge != null -> fromCharge
            else -> null
        }?.coerceIn(0.0, 100.0)
    }

    /**
     * Day-to-day recovery multiplier. Lower-than-usual HRV and a raised resting
     * heart rate are the two established markers of incomplete recovery
     * (HRV-guided training, Plews/Kiviniemi), so the same impulse is scored
     * harder on such a day. Both terms are bounded, and with no HRV or RHR the
     * factor is exactly 1.0 — nothing is invented.
     */
    private fun recoveryFactor(
        hrv: Int?,
        hrvBaseline: Double?,
        rhr: Int?,
        rhrBaseline: Double?
    ): Double {
        val hrvTerm = if (hrv != null && hrvBaseline != null && hrvBaseline > 0) {
            (1.0 + 0.30 * (1.0 - hrv / hrvBaseline)).coerceIn(0.85, 1.30)
        } else 1.0
        val rhrTerm = if (rhr != null && rhrBaseline != null && rhrBaseline > 0) {
            (1.0 + 0.02 * (rhr - rhrBaseline)).coerceIn(0.90, 1.25)
        } else 1.0
        return (hrvTerm * rhrTerm).coerceIn(0.80, 1.35)
    }

    /** Official baseline when the watch sent one, else the last seven days' median. */
    private fun baselineFor(
        readiness: List<ReadinessDay>,
        date: LocalDate,
        official: (ReadinessDay) -> Double?,
        daily: (ReadinessDay) -> Double?
    ): Double? {
        readiness.lastOrNull { it.date == date }?.let { official(it) }?.let { return it }
        val window = readiness
            .filter { it.date < date && it.date >= date.minusDays(7) }
            .mapNotNull(daily)
            .sorted()
        if (window.isEmpty()) return null
        return window[window.size / 2]
    }

    /**
     * Median gap between heart-rate samples, in minutes, kept inside 1–7 so a
     * sparse or gappy day cannot inflate the load.
     */
    private fun medianSpacingMinutes(samples: List<Pair<Long, Int>>): Double {
        val gaps = samples.map { it.first }
            .zipWithNext { a, b -> b - a }
            .filter { it in 1..3600 }
            .sorted()
        if (gaps.isEmpty()) return 1.0
        return (gaps[gaps.size / 2] / 60.0).coerceIn(1.0, 7.0)
    }
}


