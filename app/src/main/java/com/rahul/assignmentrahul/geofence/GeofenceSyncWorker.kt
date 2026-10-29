package com.rahul.assignmentrahul.geofence

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.rahul.assignmentrahul.data.repository.AuthRepository
import com.rahul.assignmentrahul.data.repository.GroupRepository
import com.rahul.assignmentrahul.util.runSuspendCatching
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Makes the geofences on this device match the user's groups in Firestore.
 * Scheduled on sign-in, when a group's geofence changes, after permissions are granted,
 * and after reboot/app update (the system clears geofences in those cases).
 */
@HiltWorker
class GeofenceSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val authRepository: AuthRepository,
    private val groupRepository: GroupRepository,
    private val geofenceManager: GeofenceManager,
    private val stateStore: GeofenceStateStore,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val user = authRepository.currentUser
        if (user == null) {
            runSuspendCatching { geofenceManager.removeAll() }
            return Result.success()
        }
        return runSuspendCatching {
            val groups = groupRepository.getMyGroups(user.uid)
            stateStore.resetTransitions()
            geofenceManager.registerFor(groups)
        }.fold(
            onSuccess = { count ->
                Log.d(TAG, "Registered $count geofence(s)")
                Result.success()
            },
            onFailure = { error ->
                Log.w(TAG, "Geofence sync failed", error)
                if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
            },
        )
    }

    companion object {
        private const val TAG = "GeofenceSyncWorker"
        private const val UNIQUE_WORK_NAME = "geofence-sync"
        private const val MAX_ATTEMPTS = 3

        /** REPLACE: if several triggers fire at once, only the latest sync runs. */
        fun enqueue(workManager: WorkManager) {
            workManager.enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<GeofenceSyncWorker>().build(),
            )
        }
    }
}
