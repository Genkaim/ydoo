package com.genkaim.zeppglass.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LoadModelTest {

    private fun day(index: Int, load: Double): LoadDay {
        val date = LocalDate.parse("2026-11-01").plusDays(index.toLong())
        return LoadDay(date, load, load * 7, 100.0, 200.0, 300.0)
    }

    @Test
    fun steadyLoadKeepsFitnessAndFatigueTogether() {
        val days = List(45) { day(it, 20.0) }
        val trend = LoadModel.compute(days, today = days.last().date)
        assertEquals(20.0, trend.last().atl, 0.001)
        assertEquals(20.0, trend.last().ctl, 0.001)
        assertEquals(0.0, trend.last().tsb, 0.001)
    }

    @Test
    fun aHardDayLiftsFatigueFasterThanFitness() {
        val days = List(44) { day(it, 20.0) } + day(44, 100.0)
        val last = LoadModel.compute(days, today = days.last().date).last()
        assertTrue("fatigue should rise first", last.atl > last.ctl)
        assertTrue("balance should go negative", last.tsb < 0)
    }

    @Test
    fun aRestDayCountsAsZeroExertion() {
        val days = List(45) { day(it, 20.0) } + day(45, 0.0)
        val last = LoadModel.compute(days, today = days.last().date).last()
        assertEquals((6 * 20.0) / 7.0, last.atl, 0.001)
        assertEquals((41 * 20.0) / 42.0, last.ctl, 0.001)
    }

    @Test
    fun nextHardSessionFollowsTheBalance() {
        val fresh = LoadTrendPoint(LocalDate.now(), 10.0, 10.0, 20.0, 10.0)
        val neutral = fresh.copy(tsb = 2.0)
        val tired = fresh.copy(tsb = -5.0)
        val veryTired = fresh.copy(tsb = -20.0)
        assertEquals("今天", LoadModel.nextHighIntensity(fresh))
        assertEquals("明天", LoadModel.nextHighIntensity(neutral))
        assertEquals("2 天后", LoadModel.nextHighIntensity(tired))
        assertEquals("3 天后", LoadModel.nextHighIntensity(veryTired))
        assertEquals("--", LoadModel.nextHighIntensity(null))
    }
}
