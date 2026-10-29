package com.rahul.assignmentrahul.notification

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.rahul.assignmentrahul.data.repository.AuthRepository
import com.rahul.assignmentrahul.data.repository.UserRepository
import com.rahul.assignmentrahul.util.runSuspendCatching
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TrackerMessagingService : FirebaseMessagingService() {

    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var userRepository: UserRepository
    @Inject lateinit var notificationHelper: NotificationHelper

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** FCM rotates tokens occasionally; keep Firestore up to date so alerts still reach us. */
    override fun onNewToken(token: String) {
        val uid = authRepository.currentUser?.uid ?: return
        scope.launch {
            runSuspendCatching { userRepository.saveFcmToken(uid, token) }
                .onFailure { Log.w(TAG, "Could not save new FCM token", it) }
        }
    }

    /**
     * Called while the app is in the foreground. In the background the system tray shows the
     * `notification` payload itself and a tap opens MainActivity with the `data` as extras.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: return
        val body = message.notification?.body.orEmpty()
        notificationHelper.showMemberExited(title, body, message.data[NotificationHelper.EXTRA_GROUP_ID])
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "TrackerMessaging"
    }
}
