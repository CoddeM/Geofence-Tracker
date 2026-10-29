package com.rahul.assignmentrahul.ui.groupdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.rahul.assignmentrahul.data.model.ExitEvent
import com.rahul.assignmentrahul.data.model.Group
import com.rahul.assignmentrahul.data.repository.AuthRepository
import com.rahul.assignmentrahul.data.repository.GroupRepository
import com.rahul.assignmentrahul.ui.navigation.GroupDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class GroupDetailUiState(
    val currentUserId: String?,
    val group: Group? = null,
    val recentExits: List<ExitEvent> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
) {
    val isOwner: Boolean get() = group?.isOwner(currentUserId) == true
}

@HiltViewModel
class GroupDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    authRepository: AuthRepository,
    groupRepository: GroupRepository,
) : ViewModel() {

    private val groupId = savedStateHandle.toRoute<GroupDetailRoute>().groupId
    private val currentUserId = authRepository.currentUser?.uid

    // Both are live Firestore listeners: new members and new exits appear without refreshing.
    val uiState: StateFlow<GroupDetailUiState> = combine(
        groupRepository.observeGroup(groupId),
        groupRepository.observeExitEvents(groupId),
    ) { group, exits ->
        GroupDetailUiState(currentUserId = currentUserId, group = group, recentExits = exits, isLoading = false)
    }
        .catch { emit(GroupDetailUiState(currentUserId, isLoading = false, errorMessage = it.localizedMessage)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupDetailUiState(currentUserId))
}
