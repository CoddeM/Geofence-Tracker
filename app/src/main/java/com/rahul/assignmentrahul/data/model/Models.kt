package com.rahul.assignmentrahul.data.model

data class GeofenceArea(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
)

data class Group(
    val id: String,
    val name: String,
    val ownerId: String,
    val memberIds: List<String>,
    val memberNames: Map<String, String>,
    val inviteCode: String,
    val geofence: GeofenceArea?,
) {
    fun isOwner(userId: String?): Boolean = userId == ownerId
}

data class ExitEvent(
    val id: String,
    val userId: String,
    val userName: String,
    val timestampMillis: Long,
)

/** Map-SDK-independent point, so ViewModels don't depend on a specific map library. */
data class Coordinates(
    val latitude: Double,
    val longitude: Double,
)
