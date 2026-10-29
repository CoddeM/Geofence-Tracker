package com.rahul.assignmentrahul.geofence

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.firebase.firestore.FirebaseFirestoreException
import com.rahul.assignmentrahul.data.remote.ExitAlertClient
import com.rahul.assignmentrahul.data.remote.ExitAlertException
import com.rahul.assignmentrahul.data.repository.AuthRepository
import com.rahul.assignmentrahul.data.repository.GroupRepository
import com.rahul.assignmentrahul.data.repository.displayNameOrEmail
import com.rahul.assignmentrahul.notification.NotificationHelper
import com.rahul.assignmentrahul.util.runSuspendCatching
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Reports one EXIT:
 *  1. saves the event in Firestore (shows up in the group's "Recent exits"),
 *  2. asks the exit-alert service to push a notification to the other members.
 * Both steps are idempotent, so WorkManager can safely retry the whole thing until it works,
 * including after the phone was offline when the user left.
 */
@HiltWorker
class GeofenceEventWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val authRepository: AuthRepository,
    private val groupRepository: GroupRepository,
    private val exitAlertClient: ExitAlertClient,
    private val notificationHelper: NotificationHelper,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val groupId = inputData.getString(KEY_GROUP_ID) ?: return Result.failure()
        val timestamp = inputData.getLong(KEY_TIMESTAMP, System.currentTimeMillis())
        val user = authRepository.currentUser ?: return Result.failure() // signed out meanwhile

        // The work id never changes between retries, so it doubles as an idempotent event id.
        val eventId = id.toString()

        val outcome = runSuspendCatching {
            withTimeoutOrNull(SAVE_TIMEOUT_MILLIS) {
                groupRepository.addExitEvent(groupId, eventId, user.uid, user.displayNameOrEmail(), timestamp)
            } ?: throw IOException("Timed out saving the exit event")
            exitAlertClient.notifyGroup(groupId, eventId)
        }

        return outcome.fold(
            onSuccess = {
                val groupName = runSuspendCatching { groupRepository.getGroup(groupId)?.name }.getOrNull()
                notificationHelper.showExitRecorded(groupName)
                Result.success()
            },
            onFailure = { error ->
                when {
                    error.isPermanent() -> {
                        Log.w(TAG, "Giving up on exit for group $groupId", error)
                        Result.failure()
                    }
                    runAttemptCount < MAX_ATTEMPTS -> {
                        Log.w(TAG, "Exit report failed (attempt $runAttemptCount), will retry", error)
                        Result.retry()
                    }
                    else -> Result.failure()
                }
            },
        )
    }

    /** Errors that will fail the same way on every retry (not a member, misconfigured URL...). */
    private fun Throwable.isPermanent(): Boolean =
        (this is FirebaseFirestoreException && code == FirebaseFirestoreException.Code.PERMISSION_DENIED) ||
            (this is ExitAlertException && !isRetryable)

    // Only used below Android 12, where expedited work runs as a foreground service.
    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(NotificationHelper.UPLOAD_NOTIFICATION_ID, notificationHelper.buildUploadNotification())

    companion object {
        private const val TAG = "GeofenceEventWorker"
        private const val KEY_GROUP_ID = "groupId"
        private const val KEY_TIMESTAMP = "timestamp"
        private const val SAVE_TIMEOUT_MILLIS = 30_000L
        private const val MAX_ATTEMPTS = 8

        fun enqueue(workManager: WorkManager, groupId: String, timestampMillis: Long) {
            val request = OneTimeWorkRequestBuilder<GeofenceEventWorker>()
                .setInputData(workDataOf(KEY_GROUP_ID to groupId, KEY_TIMESTAMP to timestampMillis))
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                // Ask to run as soon as possible; falls back to normal work if quota is used up.
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
            workManager.enqueue(request)
        }
    }
}
