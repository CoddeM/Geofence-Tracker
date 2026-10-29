package com.rahul.assignmentrahul.notification

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.rahul.assignmentrahul.MainActivity
import com.rahul.assignmentrahul.R
import com.rahul.assignmentrahul.permission.AppPermissions
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        val alerts = NotificationChannelCompat.Builder(CHANNEL_ALERTS, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(context.getString(R.string.channel_alerts_name))
            .setDescription(context.getString(R.string.channel_alerts_description))
            .build()
        val status = NotificationChannelCompat.Builder(CHANNEL_STATUS, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(context.getString(R.string.channel_status_name))
            .setDescription(context.getString(R.string.channel_status_description))
            .build()
        manager.createNotificationChannelsCompat(listOf(alerts, status))
    }

    /** "<member> left the zone": shown on the other members' devices. */
    fun showMemberExited(title: String, body: String, groupId: String?) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openGroupIntent(groupId))
            .setAutoCancel(true)
            .build()
        notify(uniqueId(), notification)
    }

    /** Quiet confirmation on the device that left, so the user knows the group was told. */
    fun showExitRecorded(groupName: String?) {
        val title = if (groupName != null) {
            context.getString(R.string.notification_exit_recorded_title, groupName)
        } else {
            context.getString(R.string.notification_exit_recorded_title_generic)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notification_exit_recorded_body))
            .setContentIntent(openGroupIntent(null))
            .setAutoCancel(true)
            .build()
        notify(EXIT_RECORDED_NOTIFICATION_ID, notification)
    }

    fun buildUploadNotification(): Notification =
        NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_uploading))
            .setOngoing(true)
            .build()

    @SuppressLint("MissingPermission") // checked just below
    private fun notify(id: Int, notification: Notification) {
        if (AppPermissions.hasNotifications(context)) manager.notify(id, notification)
    }

    private fun openGroupIntent(groupId: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            groupId?.let { putExtra(EXTRA_GROUP_ID, it) }
        }
        return PendingIntent.getActivity(
            context,
            groupId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun uniqueId(): Int = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()

    companion object {
        // Must match default_notification_channel_id in AndroidManifest and channelId in the Cloud Function.
        const val CHANNEL_ALERTS = "geofence_alerts"
        const val CHANNEL_STATUS = "tracking_status"

        // Same key as the FCM data payload, so a tap on a system-shown notification works too.
        const val EXTRA_GROUP_ID = "groupId"

        const val UPLOAD_NOTIFICATION_ID = 1001
        private const val EXIT_RECORDED_NOTIFICATION_ID = 1002
    }
}
