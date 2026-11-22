package com.genkaim.zeppglass.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Parses real (trimmed) responses captured from the Zepp app API. These are
 * the shapes the app actually receives; the assertions pin the field mapping so
 * a future refactor cannot silently turn a reading into a null.
 */
class HealthParserTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun fixture(name: String): JSONObject {
        val stream = javaClass.classLoader!!.getResourceAsStream("$name.json")
        assertNotNull("fixture $name.json", stream)
        return JSONObject(stream!!.readBytes().toString(Charsets.UTF_8))
    }

    @Test
    fun bandSummaryYieldsStepsSleepAndHeartRate() {
        val parsed = HealthParser.parseBand(fixture("band"), zone)

        val day = parsed.days.first { it.date == LocalDate.parse("2026-11-14") }
        assertEquals(5425, day.steps)
        assertEquals(3800.0, day.distanceM!!, 0.1)
        assertEquals(497.0, day.calories!!, 0.1)

        val night = parsed.nights.first { it.date == LocalDate.parse("2026-11-14") }
        assertEquals(69, night.score)
        assertEquals(70, night.deepMinutes)
        assertEquals(242, night.lightMinutes)
        assertEquals(14, night.awakeMinutes)
        assertEquals(62, night.restingHr)
        assertTrue(night.stages.isNotEmpty())
        // REM is not a field on this payload; it must be summed from modes 8/11.
        assertTrue("REM should come from stages", (night.remMinutes ?: 0) > 0)
        assertEquals(
            night.remMinutes,
            night.stages.filter { it.stage == "rem" }
                .sumOf { ((it.endEpochSec - it.startEpochSec) / 60).toInt() }
        )
        assertTrue(night.totalMinutes > 300)

        assertTrue(parsed.heartRate.isNotEmpty())
        assertTrue(parsed.heartRate.all { it.second in 20..240 })
    }

    @Test
    fun chargeKeepsTheLastValidSampleAndSkipsTheSentinel() {
        val days = HealthParser.parseCharge(fixture("charge"), zone)
        val day = days.first { it.date == LocalDate.parse("2026-11-16") }
        assertEquals(89, day.total)
        assertEquals(91.33, day.physical!!, 0.05)
        assertEquals(87.36, day.mental!!, 0.05)
        assertTrue(day.samples.size > 10)
        assertTrue(days.all { it.total == null || it.total in 0..100 })
    }

    @Test
    fun readinessMapsScoresAndSleepHrv() {
        val days = HealthParser.parseReadiness(fixture("readiness"), zone)
        val day = days.first { it.date == LocalDate.parse("2026-11-17") }
        assertEquals(94, day.score)
        assertEquals(122, day.sleepHrv)
        assertEquals(51, day.sleepRhr)
        assertEquals(91, day.hrvScore)
        // Sentinel 255 in a score field must not become a reading.
        assertTrue(day.physicalScore == null || day.physicalScore!! in 1..100)
    }

    @Test
    fun paiAndStressKeepTheirUnitFields() {
        val pai = HealthParser.parsePai(fixture("pai"), zone).first()
        assertEquals(0.7416, pai.daily!!, 0.001)
        assertEquals(18.9947, pai.total!!, 0.001)

        val stress = HealthParser.parseStress(fixture("stress"), zone).first()
        assertTrue(stress.average!! in 0..100)
        assertTrue(stress.curve.isNotEmpty())
    }

    @Test
    fun spo2KeepsOnlyMeasuredReadings() {
        val points = HealthParser.parseSpo2(fixture("spo2"))
        assertTrue(points.isNotEmpty())
        assertTrue(points.all { it.value in 50..100 })
        assertTrue(points.first().epochMs > 0)
    }

    @Test
    fun weightKeepsKilogramsAndBmi() {
        val points = HealthParser.parseWeights(fixture("weight"))
        val latest = points.maxByOrNull { it.epochMs }!!
        assertEquals(104.0, latest.weightKg!!, 0.01)
        assertEquals(34.748, latest.bmi!!, 0.01)
    }

    @Test
    fun workoutsAndTrainingLoadUseTheSameNumbersAsTheApi() {
        val workouts = HealthParser.parseWorkouts(fixture("sports"))
        assertTrue(workouts.isNotEmpty())
        val first = workouts.first()
        assertTrue(first.name.isNotBlank())
        assertEquals(333, first.durationSec)
        assertTrue(first.endEpochSec > first.startEpochSec)
        // The cloud bucket is needed to ask for the GPS track later.
        assertEquals("run.10289411.huami.com", first.source)
        // Sentinels (-1) must not become readings.
        assertEquals(null, first.altitudeGainM)
        assertEquals(null, first.steps)
        assertEquals(null, first.vo2Max)

        val load = HealthParser.parseLoad(fixture("load"), zone)
        val day = load.first { it.date == LocalDate.parse("2026-11-17") }
        assertEquals(38.0, day.weekLoad!!, 0.01)
        assertEquals(54.0, day.optimalMin!!, 0.01)
    }

    @Test
    fun gpsTrackIsRebuiltFromRelativeFixes() {
        // Trimmed from a real Hangzhou run: latitude first, 1e-8 degrees, the
        // first pair absolute and every later pair a step from the one before.
        val payload = JSONObject(
            """
            {"data":{
              "longitude_latitude":"3026091166,12012749833;-133,0;-66,0;100,33;-66,-233",
              "heart_rate":"0,0;1,2",
              "distance":"0,0;1,1",
              "kilo_pace":"0",
              "lap":""
            }}
            """.trimIndent()
        )
        val detail = HealthParser.parseWorkoutDetail(payload)
        assertEquals(5, detail.points.size)
        assertEquals(30.26091166, detail.points[0].lat, 1e-8)
        assertEquals(120.12749833, detail.points[0].lon, 1e-8)
        // The step is added to the previous fix, not read as a coordinate.
        assertEquals(30.26091033, detail.points[1].lat, 1e-8)
        assertEquals(120.12749833, detail.points[1].lon, 1e-8)
        assertEquals(30.26091067, detail.points[3].lat, 1e-8)
        assertEquals(120.12749866, detail.points[3].lon, 1e-8)
        // The track stays a few metres wide instead of spanning the globe.
        assertTrue(detail.points.maxOf { it.lat } - detail.points.minOf { it.lat } < 0.001)
        assertTrue(detail.fields.any { it.first == "heart_rate" })
    }

    @Test
    fun bloodPressureReadsBothNamingStyles() {
        val payload = JSONObject(
            """
            {"items":[
              {"timestamp":1795000000000,"systolic":118,"diastolic":76},
              {"time":1795003600,"sbp":142,"dbp":91},
              {"timestamp":1795007200000,"value":"121/79"}
            ]}
            """.trimIndent()
        )
        val readings = HealthParser.parseBloodPressure(payload)
        assertEquals(3, readings.size)
        assertEquals(118, readings[0].systolic)
        assertEquals(76, readings[0].diastolic)
        // Seconds are widened to milliseconds.
        assertEquals(1795003600000L, readings[1].epochMs)
        assertEquals(BloodPressureBand.HIGH, readings[1].band)
        assertEquals(121, readings[2].systolic)
        assertEquals(BloodPressureBand.NORMAL, readings[0].band)
    }

    @Test
    fun bloodPressureDropsImpossibleReadings() {
        val payload = JSONObject(
            """
            {"items":[
              {"timestamp":1795000000000,"systolic":0,"diastolic":0},
              {"timestamp":1795000600000,"systolic":70,"diastolic":95}
            ]}
            """.trimIndent()
        )
        assertTrue(HealthParser.parseBloodPressure(payload).isEmpty())
    }

    @Test
    fun altitudeSentinelsStayEmpty() {
        // -2000000 is the watch's "no barometer" marker; it must not become a
        // -20000 m cliff or be carried forward as a flat fake reading.
        val payload = JSONObject(
            """{"data":{"altitude":"-2000000;-2000000;3500;3500;3400;3600;3500;3500"}}"""
        )
        val altitude = HealthParser.parseWorkoutDetail(payload).series.first { it.label == "海拔" }
        assertEquals(null, altitude.values[0])
        assertEquals(null, altitude.values[1])
        assertEquals(35f, altitude.values[2])
        assertEquals(36f, altitude.values[5])
    }

    @Test
    fun workoutWithoutGpsHasNoPointsButKeepsFields() {
        val detail = HealthParser.parseWorkoutDetail(
            JSONObject("""{"data":{"longitude_latitude":"","heart_rate":"","te":3}}""")
        )
        assertTrue(detail.points.isEmpty())
        assertTrue(detail.fields.any { it.first == "te" })
    }
}
