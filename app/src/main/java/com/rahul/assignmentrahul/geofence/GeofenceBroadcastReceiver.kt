package com.rahul.assignmentrahul.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.WorkManager
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import com.rahul.assignmentrahul.domain.TransitionType
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives transitions from Play services even when the app is closed.
 * It only filters duplicates (fast, local) and hands the network upload to WorkManager,
 * because a receiver has ~10 seconds and no network guarantee.
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ReceiverEntryPoint {
        fun geofenceStateStore(): GeofenceStateStore
        fun workManager(): WorkManager
    }

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            // GEOFENCE_NOT_AVAILABLE means location was turned off and the system dropped our
            // geofences; they are registered again on the next app start or reboot.
            Log.w(TAG, "Geofence error: ${GeofenceStatusCodes.getStatusCodeString(event.errorCode)}")
            return
        }
        val transition = when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> TransitionType.ENTER
            Geofence.GEOFENCE_TRANSITION_EXIT -> TransitionType.EXIT
            else -> return
        }
        val groupIds = event.triggeringGeofences.orEmpty().map { it.requestId }
        val nowMillis = System.currentTimeMillis()
        val entryPoint = EntryPointAccessors.fromApplication(context, ReceiverEntryPoint::class.java)

        // goAsync keeps the receiver alive while we do the DataStore write off the main thread.
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                groupIds.forEach { groupId ->
                    val shouldReport = entryPoint.geofenceStateStore()
                        .recordTransition(groupId, transition, nowMillis)
                    Log.d(TAG, "$transition for group $groupId, report=$shouldReport")
                    if (shouldReport) {
                        GeofenceEventWorker.enqueue(entryPoint.workManager(), groupId, nowMillis)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "GeofenceReceiver"
    }
}
