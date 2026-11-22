package com.genkaim.zeppglass.data

import kotlin.math.abs
import kotlin.math.roundToInt

/** How far a value sits outside a range, in its own units. */
fun outsideRange(value: Double, low: Double, high: Double): Double = when {
    value < low -> low - value
    value > high -> value - high
    else -> 0.0
}

/**
 * 身体得分: starts at 100 and subtracts for every figure outside the WHO/ACSM
 * reference ranges. Calculated on the phone, not a Zepp number.
 */
fun bodyCompositionScore(
    bmi: Double?,
    fat: Double?,
    muscleKg: Double?,
    water: Double?,
    visceral: Double?,
    protein: Double?,
    bone: Double?,
    bmr: Double?,
    bmrEstimate: Double?,
    heightM: Double?,
    male: Boolean
): Int? {
    var penalty = 0.0
    var any = false
    bmi?.let { any = true; penalty += outsideRange(it, 18.5, 24.0) * 2.0 }
    fat?.let {
        any = true
        penalty += outsideRange(it, if (male) 11.0 else 21.0, if (male) 17.0 else 24.0) * 1.5
    }
    if (muscleKg != null && heightM != null) {
        any = true
        penalty += maxOf(0.0, 16.5 * heightM * heightM - muscleKg) * 2.0
    }
    water?.let { any = true; penalty += maxOf(0.0, 55.0 - it) * 0.8 }
    protein?.let { any = true; penalty += maxOf(0.0, 16.0 - it) * 1.5 }
    visceral?.let { any = true; penalty += maxOf(0.0, it - 10.0) * 2.0 }
    bone?.let { any = true; penalty += maxOf(0.0, (if (male) 2.0 else 1.5) - it) * 3.0 }
    if (bmr != null && bmrEstimate != null && bmrEstimate > 0.0) {
        any = true
        penalty += maxOf(0.0, abs(bmr - bmrEstimate) / bmrEstimate - 0.05) * 50.0
    }
    if (!any) return null
    return (100.0 - penalty).roundToInt().coerceIn(0, 100)
}

/**
 * 身体年龄: Deurenberg's body-fat ↔ age relation (Br J Nutr, 1991), solved for
 * age: age = (fat% − 1.2×BMI + 5.4 + 10.8×male) / 0.23.
 */
fun deurenbergAge(bmi: Double?, fat: Double?, male: Boolean): Int? {
    if (bmi == null || fat == null) return null
    val age = (fat - 1.2 * bmi + 5.4 + if (male) 10.8 else 0.0) / 0.23
    // No lower floor: a lean, young body can compute below 18, and the UI shows
    // that as "<18" instead of pretending it is 18.
    return age.roundToInt().coerceIn(0, 80)
}

/** Mifflin-St Jeor basal rate from the Zepp profile; null when incomplete. */
fun mifflinBmr(body: BodyProfile?, weightKg: Double?, todayYear: Int = java.time.LocalDate.now().year): Double? {
    val height = body?.heightCm ?: return null
    val birthYear = body?.birthYear ?: return null
    val male = body?.isMale ?: return null
    val weight = weightKg ?: body?.weightKg ?: return null
    val age = todayYear - birthYear
    if (age !in 5..110) return null
    val base = 10.0 * weight + 6.25 * height - 5.0 * age
    return base + if (male) 5.0 else -161.0
}
