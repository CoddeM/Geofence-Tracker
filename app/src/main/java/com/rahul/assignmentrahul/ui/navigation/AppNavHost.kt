package com.rahul.assignmentrahul.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rahul.assignmentrahul.ui.geofence.GeofenceEditorScreen
import com.rahul.assignmentrahul.ui.groupdetail.GroupDetailScreen
import com.rahul.assignmentrahul.ui.groups.GroupsScreen
import kotlinx.serialization.Serializable

@Serializable
data object GroupsRoute

@Serializable
data class GroupDetailRoute(val groupId: String)

@Serializable
data class GeofenceEditorRoute(val groupId: String)

@Composable
fun AppNavHost(
    pendingGroupId: String?,
    onPendingGroupHandled: () -> Unit,
    onSignOut: () -> Unit,
) {
    val navController = rememberNavController()

    LaunchedEffect(pendingGroupId) {
        if (pendingGroupId != null) {
            navController.navigate(GroupDetailRoute(pendingGroupId)) { launchSingleTop = true }
            onPendingGroupHandled()
        }
    }

    NavHost(navController = navController, startDestination = GroupsRoute) {
        composable<GroupsRoute> {
            GroupsScreen(
                onGroupClick = { groupId -> navController.navigate(GroupDetailRoute(groupId)) },
                onSignOut = onSignOut,
            )
        }
        composable<GroupDetailRoute> {
            GroupDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenGeofence = { groupId -> navController.navigate(GeofenceEditorRoute(groupId)) },
            )
        }
        composable<GeofenceEditorRoute> {
            GeofenceEditorScreen(onBack = { navController.popBackStack() })
        }
    }
}
