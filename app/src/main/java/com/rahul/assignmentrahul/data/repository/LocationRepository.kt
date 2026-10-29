package com.rahul.assignmentrahul.data.repository

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.rahul.assignmentrahul.data.model.Coordinates
import com.rahul.assignmentrahul.permission.AppPermissions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: FusedLocationProviderClient,
) {
    /** One-off location fix for centring the map. Returns null without permission or a fix. */
    @SuppressLint("MissingPermission") // checked just below
    suspend fun currentLocation(): Coordinates? {
        if (!AppPermissions.hasPreciseLocation(context)) return null
        val location = client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
            ?: client.lastLocation.await()
        return location?.let { Coordinates(it.latitude, it.longitude) }
    }
}
