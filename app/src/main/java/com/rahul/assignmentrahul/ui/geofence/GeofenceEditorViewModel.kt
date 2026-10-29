package com.rahul.assignmentrahul.ui.geofence

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.rahul.assignmentrahul.data.model.Coordinates
import com.rahul.assignmentrahul.data.model.GeofenceArea
import com.rahul.assignmentrahul.data.repository.AuthRepository
import com.rahul.assignmentrahul.data.repository.GroupRepository
import com.rahul.assignmentrahul.data.repository.LocationRepository
import com.rahul.assignmentrahul.domain.RadiusPolicy
import com.rahul.assignmentrahul.ui.navigation.GeofenceEditorRoute
import com.rahul.assignmentrahul.util.runSuspendCatching
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GeofenceEditorUiState(
    val groupName: String = "",
    val canEdit: Boolean = false,
    val center: Coordinates? = null,
    val radiusMeters: Float = RadiusPolicy.DEFAULT_METERS,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isLocating: Boolean = false,
) {
    val canSave: Boolean get() = canEdit && center != null && !isSaving
}

sealed interface GeofenceEditorEvent {
    data class MoveCamera(val target: Coordinates) : GeofenceEditorEvent
    data object Saved : GeofenceEditorEvent
    data object LocationUnavailable : GeofenceEditorEvent
    data class ShowError(val message: String?) : GeofenceEditorEvent
}

@HiltViewModel
class GeofenceEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    authRepository: AuthRepository,
    private val groupRepository: GroupRepository,
    private val locationRepository: LocationRepository,
) : ViewModel() {

    private val groupId = savedStateHandle.toRoute<GeofenceEditorRoute>().groupId
    private val currentUserId = authRepository.currentUser?.uid

    private val _uiState = MutableStateFlow(GeofenceEditorUiState())
    val uiState: StateFlow<GeofenceEditorUiState> = _uiState.asStateFlow()

    private val _events = Channel<GeofenceEditorEvent>(Channel.BUFFERED)
    val events: Flow<GeofenceEditorEvent> = _events.receiveAsFlow()

    init {
        loadGroup()
    }

    fun onMapClick(position: Coordinates) {
        if (_uiState.value.canEdit) _uiState.update { it.copy(center = position) }
    }

    fun onRadiusChange(meters: Float) {
        _uiState.update { it.copy(radiusMeters = RadiusPolicy.normalize(meters)) }
    }

    fun useMyLocation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLocating = true) }
            val location = runSuspendCatching { locationRepository.currentLocation() }.getOrNull()
            _uiState.update { it.copy(isLocating = false, center = location ?: it.center) }
            _events.send(location?.let { GeofenceEditorEvent.MoveCamera(it) } ?: GeofenceEditorEvent.LocationUnavailable)
        }
    }

    fun save() {
        val state = _uiState.value
        val center = state.center ?: return
        if (!state.canSave) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val area = GeofenceArea(center.latitude, center.longitude, RadiusPolicy.normalize(state.radiusMeters).toDouble())
            // No need to register the geofence here: SessionViewModel sees the group change
            // in Firestore and re-syncs geofences on every member's device.
            runSuspendCatching { groupRepository.updateGeofence(groupId, area) }
                .onSuccess { _events.send(GeofenceEditorEvent.Saved) }
                .onFailure { _events.send(GeofenceEditorEvent.ShowError(it.localizedMessage)) }
            _uiState.update { it.copy(isSaving = false) }
        }
    }

    private fun loadGroup() {
        viewModelScope.launch {
            val group = runSuspendCatching { groupRepository.getGroup(groupId) }
                .onFailure { _events.send(GeofenceEditorEvent.ShowError(it.localizedMessage)) }
                .getOrNull()
            val existing = group?.geofence?.let { Coordinates(it.latitude, it.longitude) }
            _uiState.update {
                it.copy(
                    groupName = group?.name.orEmpty(),
                    canEdit = group?.isOwner(currentUserId) == true,
                    center = existing,
                    radiusMeters = group?.geofence?.radiusMeters?.toFloat() ?: RadiusPolicy.DEFAULT_METERS,
                    isLoading = false,
                )
            }
            // Start on the saved area; for a new geofence start where the user is.
            val target = existing ?: runSuspendCatching { locationRepository.currentLocation() }.getOrNull()
            target?.let { _events.send(GeofenceEditorEvent.MoveCamera(it)) }
        }
    }
}
