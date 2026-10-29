package com.rahul.assignmentrahul

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rahul.assignmentrahul.notification.NotificationHelper
import com.rahul.assignmentrahul.ui.AppRoot
import com.rahul.assignmentrahul.ui.theme.AssignmentRahulTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Group to open because the user tapped an alert notification. */
    private var pendingGroupId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // On recreation (e.g. rotation) the deep link was already handled.
        if (savedInstanceState == null) pendingGroupId = intent.groupIdExtra()
        setContent {
            AssignmentRahulTheme {
                AppRoot(
                    pendingGroupId = pendingGroupId,
                    onPendingGroupHandled = { pendingGroupId = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.groupIdExtra()?.let { pendingGroupId = it }
    }

    private fun Intent.groupIdExtra(): String? = getStringExtra(NotificationHelper.EXTRA_GROUP_ID)
}
