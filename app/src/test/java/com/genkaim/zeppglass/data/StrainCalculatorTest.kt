package com.genkaim.zeppglass.data

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrainCalculatorTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val date: LocalDate = LocalDate.of(2026, 11, 16)

    private fun day(steps: Int = 8000) = DayStat(date = date, steps = steps, restingHr = 60)

    /** Heart-rate samples starting at midnight. */
    private fun samples(count: Int, gapSec: Long, bpm: Int): List<Pair<Long, Int>> {
        val start = date.atStartOfDay(zone).toEpochSecond()
        return (0 until count).map { start + it * gapSec to bpm }
    }

    @Test
    fun oneMinuteSamplesCountOneMinuteEach() {
        val result = StrainCalculator.compute(
            days = listOf(day()),
            heartRate = samples(60, 60, 150),
            readiness = emptyList(),
            workouts = emptyList(),
            zone = zone
        ).single()

        assertNotNull(result.strain)
        assertEquals(60, result.zoneMinutes.sum())
        assertEquals(60, result.sampleMinutes)
        assertTrue(result.strain!! in 0.0..21.0)
    }

    @Test
    fun fiveMinuteSamplesScaleTheZoneMinutes() {
        val result = StrainCalculator.compute(
            days = listOf(day()),
            heartRate = samples(60, 300, 150),
            readiness = emptyList(),
            workouts = emptyList(),
            zone = zone
        ).single()

        assertEquals(300, result.zoneMinutes.sum())
        assertEquals(300, result.sampleMinutes)
    }

    @Test
    fun zeroWhenOnlyRestingSamples() {
        val result = StrainCalculator.compute(
            days = listOf(day(steps = 0)),
            heartRate = samples(30, 60, 58),
            readiness = emptyList(),
            workouts = emptyList(),
            zone = zone
        ).single()

        assertNotNull(result.strain)
        assertEquals(0.0, result.strain!!, 0.001)
    }

    @Test
    fun fewerThanTenSamplesIsMissing() {
        val result = StrainCalculator.compute(
            days = listOf(day()),
            heartRate = samples(5, 60, 150),
            readiness = emptyList(),
            workouts = emptyList(),
            zone = zone
        ).single()

        assertNull(result.strain)
        assertNull(result.trimp)
    }
}

