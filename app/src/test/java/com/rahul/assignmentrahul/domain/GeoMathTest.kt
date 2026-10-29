package com.rahul.assignmentrahul.domain

import com.rahul.assignmentrahul.data.model.Coordinates
import org.junit.Assert.assertEquals
import org.junit.Test

class GeoMathTest {

    private val pune = Coordinates(18.5204, 73.8567)

    @Test
    fun `every circle point is radius metres from the centre`() {
        val points = GeoMath.circle(pune, radiusMeters = 250.0, steps = 32)

        points.forEach { assertEquals(250.0, GeoMath.distanceMeters(pune, it), 0.5) }
    }

    @Test
    fun `circle is a closed ring`() {
        val points = GeoMath.circle(pune, radiusMeters = 100.0, steps = 16)

        assertEquals(17, points.size)
        assertEquals(points.first().latitude, points.last().latitude, 1e-9)
        assertEquals(points.first().longitude, points.last().longitude, 1e-9)
    }

    @Test
    fun `distance matches a known value`() {
        // Pune to Mumbai (CST) is about 120 km in a straight line.
        val mumbai = Coordinates(18.9398, 72.8355)

        assertEquals(118_000.0, GeoMath.distanceMeters(pune, mumbai), 3_000.0)
    }
}
