package com.genkaim.zeppglass.ui

import java.time.LocalDate

enum class DetailKind {
    SLEEP, CHARGE, STRESS, READINESS, ACTIVITY, LOAD, PAI, WEIGHT, SPO2, WORKOUT, STRAIN, VO2, RHR, HRV, DIET,

    /** The three sleep-vitals cards on the sleep page, each with a 7-day trend. */
    SLEEP_HR, SLEEP_RR, SLEEP_APNEA,

    /** The watch's 0–100 skin-temperature score, with a 7-day line. */
    SKIN_TEMP,

    /** Morning Charge (energy at wake), with a 7-day line. */
    MORNING_CHARGE,

    /** Nightly average blood oxygen, with a 7-day line. */
    NIGHT_SPO2
}

/** What a card opens. Dates are local days; workouts are addressed by id. */
data class DetailTarget(
    val kind: DetailKind,
    val date: LocalDate? = null,
    val workoutId: String? = null
)

