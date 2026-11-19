package com.genkaim.zeppglass.ui

import java.time.LocalDate

enum class DetailKind {
    SLEEP, CHARGE, STRESS, READINESS, ACTIVITY, LOAD, PAI, WEIGHT, SPO2, WORKOUT, STRAIN, VO2, RHR, HRV, DIET,

    /** The three sleep-vitals cards on the sleep page, each with a 7-day trend. */
    SLEEP_HR, SLEEP_RR, SLEEP_APNEA
}

/** What a card opens. Dates are local days; workouts are addressed by id. */
data class DetailTarget(
    val kind: DetailKind,
    val date: LocalDate? = null,
    val workoutId: String? = null
)

