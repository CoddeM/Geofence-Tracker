package com.rahul.assignmentrahul.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RadiusPolicyTest {

    @Test
    fun `values below minimum are raised to minimum`() {
        assertEquals(RadiusPolicy.MIN_METERS, RadiusPolicy.normalize(20f))
    }

    @Test
    fun `values above maximum are capped`() {
        assertEquals(RadiusPolicy.MAX_METERS, RadiusPolicy.normalize(10_000f))
    }

    @Test
    fun `values snap to the nearest 10 metres`() {
        assertEquals(250f, RadiusPolicy.normalize(247.6f))
        assertEquals(240f, RadiusPolicy.normalize(244.9f))
    }
}
