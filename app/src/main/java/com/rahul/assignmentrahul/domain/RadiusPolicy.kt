package com.rahul.assignmentrahul.domain

import kotlin.math.roundToInt

/**
 * Allowed geofence sizes. Google recommends at least 100–150 m: smaller circles are below
 * typical location accuracy and produce missed or false transitions.
 */
object RadiusPolicy {
    const val MIN_METERS = 100f
    const val MAX_METERS = 2000f
    const val DEFAULT_METERS = 200f
    private const val STEP_METERS = 10

    /** Clamps to the allowed range and snaps to 10 m so values stay readable. */
    fun normalize(meters: Float): Float {
        val snapped = (meters / STEP_METERS).roundToInt() * STEP_METERS
        return snapped.toFloat().coerceIn(MIN_METERS, MAX_METERS)
    }
}
