package com.rahul.assignmentrahul.ui.geofence

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rahul.assignmentrahul.R
import com.rahul.assignmentrahul.domain.RadiusPolicy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeofenceEditorScreen(
    onBack: () -> Unit,
    viewModel: GeofenceEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val mapController = remember { GeofenceMapController() }

    val genericError = stringResource(R.string.error_generic)
    val locationUnavailable = stringResource(R.string.error_location_unavailable)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is GeofenceEditorEvent.MoveCamera -> mapController.moveTo(event.target)
                GeofenceEditorEvent.Saved -> onBack()
                GeofenceEditorEvent.LocationUnavailable -> snackbarHostState.showSnackbar(locationUnavailable)
                is GeofenceEditorEvent.ShowError -> snackbarHostState.showSnackbar(event.message ?: genericError)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.groupName.ifEmpty { stringResource(R.string.group_geofence) }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
        ) {
            Box(Modifier.weight(1f)) {
                GeofenceMap(
                    center = state.center,
                    radiusMeters = state.radiusMeters,
                    onMapClick = viewModel::onMapClick,
                    controller = mapController,
                    modifier = Modifier.fillMaxSize(),
                )
                if (state.isLoading) CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            EditorControls(
                state = state,
                onRadiusChange = viewModel::onRadiusChange,
                onUseMyLocation = viewModel::useMyLocation,
                onSave = viewModel::save,
            )
        }
    }
}

@Composable
private fun EditorControls(
    state: GeofenceEditorUiState,
    onRadiusChange: (Float) -> Unit,
    onUseMyLocation: () -> Unit,
    onSave: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!state.canEdit) {
            Text(stringResource(R.string.geofence_owner_only), style = MaterialTheme.typography.bodyMedium)
            if (state.center != null) {
                Text(stringResource(R.string.geofence_summary_radius, state.radiusMeters.toInt()))
            }
            return@Column
        }

        Text(
            stringResource(if (state.center == null) R.string.geofence_tap_hint else R.string.geofence_adjust_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(stringResource(R.string.geofence_radius_value, state.radiusMeters.toInt()), style = MaterialTheme.typography.titleMedium)
        Slider(
            value = state.radiusMeters,
            onValueChange = onRadiusChange,
            valueRange = RadiusPolicy.MIN_METERS..RadiusPolicy.MAX_METERS,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onUseMyLocation, enabled = !state.isLocating, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.geofence_use_my_location))
            }
            Button(onClick = onSave, enabled = state.canSave, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}
