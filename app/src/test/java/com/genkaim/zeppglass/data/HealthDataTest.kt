package com.genkaim.zeppglass.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The three-hour rule hides readings but keeps the shape: dates stay so the
 * layout does not collapse, workouts stay because they are history, and every
 * metric becomes null so the UI prints `--` instead of an old number.
 */
class HealthDataTest {

    private val day = LocalDate.parse("2026-11-17")

    private fun sample(): HealthData = HealthData(
        fetchedAt = 1_000L,
        nickname = "测试",
        avatarUrl = null,
        days = listOf(DayStat(day, steps = 8000, distanceM = 6000.0, calories = 300.0, stepGoal = 7000, vo2Max = 47)),
        nights = listOf(
            SleepNight(day, 0, 28_800, 78, 90, 240, 80, 20, 3, 51, emptyList())
        ),
        chargeDays = listOf(ChargeDay(day, 72, 76.0, 66.0, listOf(ChargeSample(1_000L, 72, 76.0, 66.0)))),
        readiness = listOf(
            ReadinessDay(day, 78, 81, 74, 70, 76, 118, 51, 90, 85, 60)
        ),
        pai = listOf(PaiDay(day, 12.4, 87.2, 30, 12, 4)),
        stress = listOf(StressDay(day, 34, 12, 71, 55.5, 30.2, 10.1, 4.2, listOf(1_000L to 30))),
        spo2 = listOf(Spo2Point(1_000L, 97, true)),
        weights = listOf(WeightPoint(1_000L, 70.4, 22.3, 18.2)),
        workouts = listOf(
            Workout(
                id = "1",
                typeCode = 1,
                startEpochSec = 0,
                endEpochSec = 1800,
                durationSec = 1800,
                distanceM = 5000.0,
                calories = 300.0,
                avgHr = 140.0,
                maxHr = 170.0,
                load = 80.0
            )
        ),
        load = listOf(LoadDay(day, 240.0, 860.0, 700.0, 1200.0, 1500.0)),
        heartRate = listOf(1_000L to 60),
        strain = listOf(StrainDay(day, 12.0, 300.0, listOf(1, 2, 3, 4, 5), 120, 170, 300))
    )

    @Test
    fun readingsBecomeNullButDatesStay() {
        val blank = sample().withoutValues()
        assertEquals(1, blank.days.size)
        assertEquals(day, blank.days.first().date)
        assertNull(blank.days.first().steps)
        assertNull(blank.days.first().distanceM)
        assertNull(blank.days.first().vo2Max)
        assertNull(blank.readiness.first().score)
        assertNull(blank.readiness.first().sleepHrv)
        assertNull(blank.chargeDays.first().total)
        assertNull(blank.chargeDays.first().mental)
        assertNull(blank.load.first().weekLoad)
        assertNull(blank.weights.first().weightKg)
        assertNull(blank.pai.first().total)
        assertNull(blank.stress.first().average)
    }

    @Test
    fun seriesAreEmptiedSoChartsDrawNothing() {
        val blank = sample().withoutValues()
        assertTrue(blank.nights.isEmpty())
        assertTrue(blank.spo2.isEmpty())
        assertTrue(blank.heartRate.isEmpty())
        assertTrue(blank.strain.isEmpty())
        assertTrue(blank.chargeDays.first().samples.isEmpty())
        assertTrue(blank.stress.first().curve.isEmpty())
    }

    @Test
    fun workoutHistoryIsKept() {
        val blank = sample().withoutValues()
        assertEquals(1, blank.workouts.size)
        assertEquals("1", blank.workouts.first().id)
        assertEquals(5000.0, blank.workouts.first().distanceM, 0.01)
        assertEquals("测试", blank.nickname)
        assertEquals(1_000L, blank.fetchedAt)
    }
}
