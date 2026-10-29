package com.rahul.assignmentrahul.ui.groups

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.rahul.assignmentrahul.R
import com.rahul.assignmentrahul.permission.AppPermissions

/**
 * Walks the user through what geofencing needs, one step at a time.
 * Android requires background location to be requested *after* foreground location,
 * and on Android 11+ it can only be granted from the system settings page.
 * Hides itself once everything is granted.
 */
@Composable
fun TrackingPermissionsCard(
    onGeofencingAllowed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var status by remember { mutableStateOf(AppPermissions.status(context)) }
    // Once the system refuses to show a dialog again, the only way forward is app settings.
    var backgroundDenied by rememberSaveable { mutableStateOf(false) }
    var notificationsDenied by rememberSaveable { mutableStateOf(false) }
    val refresh = { status = AppPermissions.status(context) }

    // The user may change permissions in Settings and come back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }

    val foregroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refresh() }
    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> backgroundDenied = !granted; refresh() }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notificationsDenied = !granted; refresh() }

    LaunchedEffect(status.canMonitorGeofences) {
        if (status.canMonitorGeofences) onGeofencingAllowed()
    }

    if (status.allReady) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.permissions_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.permissions_body), style = MaterialTheme.typography.bodyMedium)

            PermissionStep(
                label = stringResource(R.string.permission_precise_location),
                granted = status.preciseLocation,
                enabled = true,
                onRequest = {
                    foregroundLauncher.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    )
                },
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                PermissionStep(
                    label = stringResource(R.string.permission_background_location),
                    hint = stringResource(R.string.permission_background_location_hint),
                    granted = status.backgroundLocation,
                    enabled = status.preciseLocation,
                    onRequest = {
                        if (backgroundDenied) openAppSettings(context)
                        else backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    },
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionStep(
                    label = stringResource(R.string.permission_notifications),
                    granted = status.notifications,
                    enabled = true,
                    onRequest = {
                        if (notificationsDenied) openAppSettings(context)
                        else notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                )
            }
            PermissionStep(
                label = stringResource(R.string.permission_location_services),
                granted = status.locationServicesOn,
                enabled = true,
                onRequest = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
            )
        }
    }
}

@Composable
private fun PermissionStep(
    label: String,
    granted: Boolean,
    enabled: Boolean,
    onRequest: () -> Unit,
    hint: String? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (hint != null && !granted) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (granted) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = stringResource(R.string.permission_granted),
                tint = MaterialTheme.colorScheme.primary,
            )
        } else {
            FilledTonalButton(onClick = onRequest, enabled = enabled) {
                Text(stringResource(R.string.action_allow))
            }
        }
    }
}

private fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    )
}
