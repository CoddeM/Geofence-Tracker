package com.rahul.assignmentrahul.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.rahul.assignmentrahul.data.repository.AuthRepository
import com.rahul.assignmentrahul.data.repository.GroupRepository
import com.rahul.assignmentrahul.data.repository.UserRepository
import com.rahul.assignmentrahul.geofence.GeofenceManager
import com.rahul.assignmentrahul.geofence.GeofenceStateStore
import com.rahul.assignmentrahul.geofence.GeofenceSyncWorker
import com.rahul.assignmentrahul.util.runSuspendCatching
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Activity-scoped: owns "who is signed in" and everything tied to the session
 * (push token, keeping device geofences in sync, sign-out cleanup).
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val groupRepository: GroupRepository,
    private val geofenceManager: GeofenceManager,
    private val stateStore: GeofenceStateStore,
    private val workManager: WorkManager,
) : ViewModel() {

    val isSignedIn: StateFlow<Boolean> = authRepository.authState
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, authRepository.currentUser != null)

    init {
        viewModelScope.launch {
            authRepository.authState
                .map { it?.uid }
                .distinctUntilChanged()
                .collectLatest { uid -> if (uid != null) runSession(uid) }
        }
    }

    private suspend fun runSession(uid: String) = coroutineScope {
        launch {
            runSuspendCatching { userRepository.refreshPushToken(uid) }
                .onFailure { Log.w(TAG, "Could not refresh push token", it) }
        }
        // Re-register device geofences whenever a group's area is added/changed/removed,
        // including edits made by the group owner on another device.
        groupRepository.observeMyGroups(uid)
            .map { groups -> groups.associate { it.id to it.geofence } }
            .distinctUntilChanged()
            .catch { Log.w(TAG, "Stopped watching groups", it) }
            .collect { GeofenceSyncWorker.enqueue(workManager) }
    }

    fun signOut() {
        viewModelScope.launch {
            val uid = authRepository.currentUser?.uid
            // Best effort, bounded so a bad connection can't block sign-out.
            if (uid != null) {
                withTimeoutOrNull(SIGN_OUT_CLEANUP_TIMEOUT_MILLIS) {
                    runSuspendCatching { userRepository.removePushToken(uid) }
                        .onFailure { Log.w(TAG, "Could not remove push token", it) }
                }
            }
            runSuspendCatching { geofenceManager.removeAll() }
            stateStore.clearAll()
            authRepository.signOut()
        }
    }

    private companion object {
        const val TAG = "SessionViewModel"
        const val SIGN_OUT_CLEANUP_TIMEOUT_MILLIS = 5_000L
    }
}
