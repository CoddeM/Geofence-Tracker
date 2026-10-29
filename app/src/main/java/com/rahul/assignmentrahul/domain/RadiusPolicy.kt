package com.rahul.assignmentrahul.domain

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Allowed geofence sizes and how the radius slider maps to metres.
 *
 * Google recommends at least 100 m: below that, normal GPS error (often 10–50 m) can cause
 * missed or false transitions. We still allow down to 10 m (useful for demos and small sites)
 * but the UI warns about it.
 */
object RadiusPolicy {
    const val MIN_METERS = 10f
    const val MAX_METERS = 2000f
    const val DEFAULT_METERS = 100f
    const val RECOMMENDED_MIN_METERS = 100f

    /** Quick picks shown as chips under the slider. */
    val PRESETS = listOf(10f, 25f, 50f, 100f, 250f, 500f, 1000f)

    /** Clamps to the allowed range and snaps to 5 m (below 100 m) or 10 m steps. */
    fun normalize(meters: Float): Float {
        val step = if (meters < 100f) 5 else 10
        val snapped = (meters / step).roundToInt() * step
        return snapped.toFloat().coerceIn(MIN_METERS, MAX_METERS)
    }

    fun isBelowRecommended(meters: Float): Boolean = meters < RECOMMENDED_MIN_METERS

    /*
     * The slider is logarithmic: 10 m → 2 km is a 200x range, so with a linear slider everything
     * under 100 m would be squeezed into the first few millimetres. Position 0..1 maps to
     * MIN * (MAX / MIN)^position.
     */
    fun toSliderPosition(meters: Float): Float =
        (ln(meters.coerceIn(MIN_METERS, MAX_METERS) / MIN_METERS) / ln(MAX_METERS / MIN_METERS))

    fun fromSliderPosition(position: Float): Float =
        normalize(MIN_METERS * (MAX_METERS / MIN_METERS).pow(position.coerceIn(0f, 1f)))
}
