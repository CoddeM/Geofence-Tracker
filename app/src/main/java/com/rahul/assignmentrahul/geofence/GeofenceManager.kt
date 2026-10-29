package com.rahul.assignmentrahul.geofence

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.rahul.assignmentrahul.data.model.Group
import com.rahul.assignmentrahul.permission.AppPermissions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Registers one Play-services geofence per group; the group id is the geofence request id. */
@Singleton
class GeofenceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: GeofencingClient,
) {
    private val pendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        // Must be mutable: Play services fills in the transition details on this intent.
        val mutableFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or mutableFlag)
    }

    /** Replaces everything currently registered. Returns how many geofences are now active. */
    @SuppressLint("MissingPermission") // checked via AppPermissions before adding
    suspend fun registerFor(groups: List<Group>): Int {
        client.removeGeofences(pendingIntent).await()

        val geofences = groups.mapNotNull { it.toGeofence() }.take(MAX_GEOFENCES_PER_APP)
        if (geofences.isEmpty()) return 0
        if (!AppPermissions.canMonitorGeofences(context)) {
            Log.w(TAG, "Skipping geofence registration: location permissions missing")
            return 0
        }

        val request = GeofencingRequest.Builder()
            // If the user is already inside, we get ENTER right away and know their state.
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(geofences)
            .build()
        client.addGeofences(request, pendingIntent).await()
        return geofences.size
    }

    suspend fun removeAll() {
        client.removeGeofences(pendingIntent).await()
    }

    private fun Group.toGeofence(): Geofence? {
        val area = geofence ?: return null
        return Geofence.Builder()
            .setRequestId(id)
            .setCircularRegion(area.latitude, area.longitude, area.radiusMeters.toFloat())
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()
    }

    private companion object {
        const val TAG = "GeofenceManager"
        const val MAX_GEOFENCES_PER_APP = 100 // Play services limit per app per device
    }
}
