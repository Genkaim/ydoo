package com.genkaim.zeppglass.ui

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

object Fmt {
    private val zone: ZoneId = ZoneId.systemDefault()
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)

    fun minutes(total: Int?): String {
        if (total == null) return "--"
        val h = total / 60
        val m = total % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    fun hoursMinutes(total: Int?): String {
        if (total == null) return "--"
        return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
    }

    fun time(epochMs: Long): String =
        Instant.ofEpochMilli(epochMs).atZone(zone).toLocalTime().format(timeFormatter)

    fun timeSec(epochSec: Long): String =
        Instant.ofEpochSecond(epochSec).atZone(zone).toLocalTime().format(timeFormatter)

    /** A clock time given as minutes since midnight, wrapping past 24:00. */
    fun clock(minuteOfDay: Int): String {
        val minute = ((minuteOfDay % 1440) + 1440) % 1440
        return "%02d:%02d".format(minute / 60, minute % 60)
    }

    fun date(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("M 月 d 日", Locale.CHINA))

    fun weekday(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("EEE", Locale.CHINA))

    fun weekdayShort(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("EEEEE", Locale.CHINA))

    fun dayAndTime(timeMillis: Long): String {
        val dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(timeMillis), zone)
        return dt.format(DateTimeFormatter.ofPattern("M/d HH:mm", Locale.CHINA))
    }

    fun greeting(): String {
        val hour = LocalDateTime.now().hour
        return when (hour) {
            in 5..10 -> "早上好"
            in 11..13 -> "中午好"
            in 14..18 -> "下午好"
            else -> "晚上好"
        }
    }

    fun int(value: Double?): String = value?.roundToInt()?.toString() ?: "--"

    fun oneDecimal(value: Double?): String =
        value?.let { String.format(Locale.CHINA, "%.1f", it) } ?: "--"

    /**
     * A signed one-decimal reading such as the skin-temperature deviation:
     * "+0.2", "-0.3". A night that rounds to zero prints "0.0" without a sign.
     */
    fun signedOneDecimal(value: Double?): String = value?.let {
        val text = String.format(Locale.CHINA, "%+.1f", it)
        when (text) {
            // A night right on the baseline is "±0" in the Zepp app, not "+0.0".
            "+0.0", "-0.0" -> "±0"
            // Whole degrees drop the ".0": "+1" rather than "+1.0".
            else -> text.removeSuffix(".0")
        }
    } ?: "--"

    fun km(meters: Double?): String =
        meters?.let { String.format(Locale.CHINA, "%.1f", it / 1000.0) } ?: "--"

    fun pace(secondsPerKm: Double?): String {
        if (secondsPerKm == null || !secondsPerKm.isFinite()) return "--"
        val total = secondsPerKm.roundToInt()
        val m = total / 60
        val s = total % 60
        return "$m:${s.toString().padStart(2, '0')}"
    }

    fun duration(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        return if (h > 0) "${h}:${m.toString().padStart(2, '0')}" else "${m} 分钟"
    }
}
