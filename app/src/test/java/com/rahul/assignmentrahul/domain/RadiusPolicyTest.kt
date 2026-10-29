package com.rahul.assignmentrahul.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadiusPolicyTest {

    @Test
    fun `values below minimum are raised to minimum`() {
        assertEquals(RadiusPolicy.MIN_METERS, RadiusPolicy.normalize(3f))
    }

    @Test
    fun `values above maximum are capped`() {
        assertEquals(RadiusPolicy.MAX_METERS, RadiusPolicy.normalize(10_000f))
    }

    @Test
    fun `small values snap to 5 metres and large values to 10 metres`() {
        assertEquals(25f, RadiusPolicy.normalize(23f))
        assertEquals(20f, RadiusPolicy.normalize(22f))
        assertEquals(250f, RadiusPolicy.normalize(247.6f))
        assertEquals(240f, RadiusPolicy.normalize(244.9f))
    }

    @Test
    fun `slider ends map to min and max`() {
        assertEquals(RadiusPolicy.MIN_METERS, RadiusPolicy.fromSliderPosition(0f))
        assertEquals(RadiusPolicy.MAX_METERS, RadiusPolicy.fromSliderPosition(1f))
    }

    @Test
    fun `slider position round-trips`() {
        listOf(10f, 50f, 100f, 500f, 2000f).forEach { meters ->
            val position = RadiusPolicy.toSliderPosition(meters)
            assertEquals(meters, RadiusPolicy.fromSliderPosition(position), meters * 0.05f)
        }
    }

    @Test
    fun `small radius is flagged as below the recommendation`() {
        assertTrue(RadiusPolicy.isBelowRecommended(50f))
        assertFalse(RadiusPolicy.isBelowRecommended(100f))
    }
}
