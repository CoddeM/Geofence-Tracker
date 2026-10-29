package com.rahul.assignmentrahul.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rahul.assignmentrahul.ui.auth.AuthScreen
import com.rahul.assignmentrahul.ui.navigation.AppNavHost

/**
 * Signed-out users only ever see the auth screen, so no screen in the nav graph
 * has to handle "not signed in".
 */
@Composable
fun AppRoot(
    pendingGroupId: String?,
    onPendingGroupHandled: () -> Unit,
    sessionViewModel: SessionViewModel = hiltViewModel(),
) {
    val isSignedIn by sessionViewModel.isSignedIn.collectAsStateWithLifecycle()

    if (isSignedIn) {
        AppNavHost(
            pendingGroupId = pendingGroupId,
            onPendingGroupHandled = onPendingGroupHandled,
            onSignOut = sessionViewModel::signOut,
        )
    } else {
        AuthScreen()
    }
}
