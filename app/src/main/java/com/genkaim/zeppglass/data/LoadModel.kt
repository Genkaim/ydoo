package com.genkaim.zeppglass.data

import java.time.LocalDate

/** One day of the fitness/fatigue model. */
data class LoadTrendPoint(
    val date: LocalDate,
    val load: Double,
    /** Acute load: the 7-day average exertion, the app's 疲劳水平 (ATL). */
    val atl: Double,
    /** Chronic load: the 42-day average exertion, the app's 体能水平 (CTL). */
    val ctl: Double,
    /** Training status: fitness minus fatigue (TSB). */
    val tsb: Double
)

/**
 * The Zepp / Amazfit training-status model, using the same definition the
 * official app documents: 疲劳水平 is the average daily exertion of the last
 * 7 days (ATL), 体能水平 the average over the last 42 days (CTL), and 训练状态
 * their difference (CTL − ATL).
 *
 * The daily exertion is the value the watch reports as `currnetDayTrainLoad`
 * (a typo in the cloud field), which equals the sum of the workouts'
 * `exercise_load` for that day. Days without a record count as zero exertion —
 * a rest day is real data, not a missing value — so fitness decays while the
 * athlete rests, exactly as the model is meant to behave.
 */
object LoadModel {

    private const val FATIGUE_DAYS = 7
    private const val FITNESS_DAYS = 42

    fun compute(load: List<LoadDay>, today: LocalDate = LocalDate.now()): List<LoadTrendPoint> {
        val byDate = load.associateBy { it.date }
        if (byDate.isEmpty()) return emptyList()
        val first = byDate.keys.min()
        val last = maxOf(today, byDate.keys.max())
        if (last.isBefore(first)) return emptyList()

        val out = mutableListOf<LoadTrendPoint>()
        var date = first
        while (!date.isAfter(last)) {
            val daily = byDate[date]?.load?.coerceAtLeast(0.0) ?: 0.0
            val atl = mean(byDate, date, FATIGUE_DAYS)
            val ctl = mean(byDate, date, FITNESS_DAYS)
            out.add(LoadTrendPoint(date, daily, atl, ctl, ctl - atl))
            date = date.plusDays(1)
        }
        return out
    }

    /** Average daily exertion over the [days] calendar days ending at [end]. */
    private fun mean(byDate: Map<LocalDate, LoadDay>, end: LocalDate, days: Int): Double {
        var sum = 0.0
        for (offset in 0 until days) {
            val day = end.minusDays(offset.toLong())
            sum += byDate[day]?.load?.coerceAtLeast(0.0) ?: 0.0
        }
        return sum / days
    }

    /**
     * Next hard session from the training status: a positive balance means
     * there is room for intensity, a negative one means the body is still
     * carrying fatigue.
     */
    fun nextHighIntensity(latest: LoadTrendPoint?): String {
        val tsb = latest?.tsb ?: return "--"
        return when {
            tsb >= 5 -> "今天"
            tsb >= 0 -> "明天"
            tsb >= -10 -> "2 天后"
            else -> "3 天后"
        }
    }
}
