package com.rahul.assignmentrahul.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager

/** Geofences don't survive a reboot or an app update, so register them again. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            GeofenceSyncWorker.enqueue(WorkManager.getInstance(context))
        }
    }
}
