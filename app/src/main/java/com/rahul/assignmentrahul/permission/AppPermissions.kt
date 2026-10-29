package com.rahul.assignmentrahul.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

data class TrackingPermissionStatus(
    val preciseLocation: Boolean,
    val backgroundLocation: Boolean,
    val notifications: Boolean,
    val locationServicesOn: Boolean,
) {
    val canMonitorGeofences: Boolean get() = preciseLocation && backgroundLocation
    val allReady: Boolean get() = canMonitorGeofences && notifications && locationServicesOn
}

object AppPermissions {

    fun hasPreciseLocation(context: Context): Boolean =
        isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION)

    /** Background location is a separate runtime permission only from Android 10 (Q). */
    fun hasBackgroundLocation(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            isGranted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    /** POST_NOTIFICATIONS is a runtime permission only from Android 13 (Tiramisu). */
    fun hasNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            isGranted(context, Manifest.permission.POST_NOTIFICATIONS)

    fun canMonitorGeofences(context: Context): Boolean =
        hasPreciseLocation(context) && hasBackgroundLocation(context)

    fun status(context: Context): TrackingPermissionStatus {
        val locationManager = context.getSystemService(LocationManager::class.java)
        return TrackingPermissionStatus(
            preciseLocation = hasPreciseLocation(context),
            backgroundLocation = hasBackgroundLocation(context),
            notifications = hasNotifications(context),
            locationServicesOn = LocationManagerCompat.isLocationEnabled(locationManager),
        )
    }

    private fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
