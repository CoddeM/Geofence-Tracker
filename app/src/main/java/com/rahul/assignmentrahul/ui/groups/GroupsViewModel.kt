package com.rahul.assignmentrahul.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.rahul.assignmentrahul.data.model.Group
import com.rahul.assignmentrahul.data.repository.AuthRepository
import com.rahul.assignmentrahul.data.repository.GroupRepository
import com.rahul.assignmentrahul.data.repository.InviteCodeNotFoundException
import com.rahul.assignmentrahul.data.repository.displayNameOrEmail
import com.rahul.assignmentrahul.domain.InviteCodeGenerator
import com.rahul.assignmentrahul.geofence.GeofenceSyncWorker
import com.rahul.assignmentrahul.util.runSuspendCatching
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupsUiState(
    val groups: List<Group> = emptyList(),
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val loadError: String? = null,
)

sealed interface GroupsEvent {
    data class OpenGroup(val groupId: String) : GroupsEvent
    /** [message] is null when we have nothing better than a generic error. */
    data class ShowError(val message: String?) : GroupsEvent
    data object InvalidInviteCode : GroupsEvent
    data object InviteCodeNotFound : GroupsEvent
}

@HiltViewModel
class GroupsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val groupRepository: GroupRepository,
    private val workManager: WorkManager,
) : ViewModel() {

    private val isSubmitting = MutableStateFlow(false)

    private val groupsState: Flow<GroupsUiState> = observeGroups()

    val uiState: StateFlow<GroupsUiState> = combine(groupsState, isSubmitting) { state, submitting ->
        state.copy(isSubmitting = submitting)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupsUiState())

    // Channel, not StateFlow: one-off events must be handled once, not replayed on rotation.
    private val _events = Channel<GroupsEvent>(Channel.BUFFERED)
    val events: Flow<GroupsEvent> = _events.receiveAsFlow()

    fun createGroup(name: String) {
        val user = authRepository.currentUser ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        launchSubmitting {
            groupRepository.createGroup(trimmed, user.uid, user.displayNameOrEmail())
        }
    }

    fun joinGroup(code: String) {
        val user = authRepository.currentUser ?: return
        val normalized = InviteCodeGenerator.normalize(code)
        if (!InviteCodeGenerator.isValid(normalized)) {
            viewModelScope.launch { _events.send(GroupsEvent.InvalidInviteCode) }
            return
        }
        launchSubmitting { groupRepository.joinGroup(normalized, user.uid, user.displayNameOrEmail()) }
    }

    fun onTrackingPermissionsGranted() {
        GeofenceSyncWorker.enqueue(workManager)
    }

    private fun observeGroups(): Flow<GroupsUiState> {
        val uid = authRepository.currentUser?.uid ?: return MutableStateFlow(GroupsUiState(isLoading = false))
        return groupRepository.observeMyGroups(uid)
            .map { GroupsUiState(groups = it, isLoading = false) }
            .catch { emit(GroupsUiState(isLoading = false, loadError = it.localizedMessage)) }
    }

    /** Runs [block] (which returns the group to open) with a busy flag and error reporting. */
    private fun launchSubmitting(block: suspend () -> String) {
        if (isSubmitting.value) return
        viewModelScope.launch {
            isSubmitting.value = true
            runSuspendCatching { block() }
                .onSuccess { groupId -> _events.send(GroupsEvent.OpenGroup(groupId)) }
                .onFailure { error ->
                    val event = if (error is InviteCodeNotFoundException) {
                        GroupsEvent.InviteCodeNotFound
                    } else {
                        GroupsEvent.ShowError(error.localizedMessage)
                    }
                    _events.send(event)
                }
            isSubmitting.value = false
        }
    }
}
