package com.rahul.assignmentrahul.domain

import com.rahul.assignmentrahul.data.model.Coordinates
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object GeoMath {
    private const val EARTH_RADIUS_METERS = 6_371_008.8

    /**
     * Points of a circle on the Earth's surface, to draw a geofence as a polygon
     * (maps have no "circle in metres" shape). First and last point are equal (closed ring).
     */
    fun circle(center: Coordinates, radiusMeters: Double, steps: Int = 64): List<Coordinates> {
        val lat = Math.toRadians(center.latitude)
        val lng = Math.toRadians(center.longitude)
        val angularDistance = radiusMeters / EARTH_RADIUS_METERS
        return (0..steps).map { i ->
            val bearing = 2 * Math.PI * i / steps
            // Standard "destination point given distance and bearing" formula.
            val pointLat = asin(sin(lat) * cos(angularDistance) + cos(lat) * sin(angularDistance) * cos(bearing))
            val pointLng = lng + atan2(
                sin(bearing) * sin(angularDistance) * cos(lat),
                cos(angularDistance) - sin(lat) * sin(pointLat),
            )
            Coordinates(Math.toDegrees(pointLat), Math.toDegrees(pointLng))
        }
    }

    /** Haversine distance between two points, in metres. */
    fun distanceMeters(a: Coordinates, b: Coordinates): Double {
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLng = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * EARTH_RADIUS_METERS * atan2(sqrt(h), sqrt(1 - h))
    }
}
