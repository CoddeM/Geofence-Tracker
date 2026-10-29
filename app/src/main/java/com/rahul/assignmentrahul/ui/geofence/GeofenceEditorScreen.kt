package com.rahul.assignmentrahul.ui.geofence

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rahul.assignmentrahul.R
import com.rahul.assignmentrahul.domain.RadiusPolicy
import com.rahul.assignmentrahul.ui.components.radiusLabel

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
                title = {
                    Text(
                        state.groupName.ifEmpty { stringResource(R.string.group_zone_title) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
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

                if (state.canEdit) {
                    MapHint(
                        text = stringResource(if (state.center == null) R.string.geofence_tap_hint else R.string.geofence_adjust_hint),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(12.dp),
                    )
                    FloatingActionButton(
                        onClick = viewModel::useMyLocation,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                    ) {
                        if (state.isLocating) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.LocationOn, contentDescription = stringResource(R.string.geofence_use_my_location))
                        }
                    }
                }
                if (state.isLoading) CircularProgressIndicator(Modifier.align(Alignment.Center))
            }

            EditorPanel(
                state = state,
                onRadiusChange = viewModel::onRadiusChange,
                onSave = viewModel::save,
            )
        }
    }
}

@Composable
private fun MapHint(text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 4.dp,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Bottom panel: radius presets + slider + save (owner), or a read-only summary (members). */
@Composable
private fun EditorPanel(
    state: GeofenceEditorUiState,
    onRadiusChange: (Float) -> Unit,
    onSave: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    stringResource(R.string.geofence_radius_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    radiusLabel(state.radiusMeters),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (!state.canEdit) {
                Text(
                    stringResource(R.string.geofence_owner_only),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RadiusPolicy.PRESETS.forEach { preset ->
                    FilterChip(
                        selected = state.radiusMeters == preset,
                        onClick = { onRadiusChange(preset) },
                        label = { Text(radiusLabel(preset)) },
                    )
                }
            }

            // Logarithmic: small radii get as much slider travel as large ones.
            Slider(
                value = RadiusPolicy.toSliderPosition(state.radiusMeters),
                onValueChange = { position -> onRadiusChange(RadiusPolicy.fromSliderPosition(position)) },
            )

            AnimatedVisibility(visible = RadiusPolicy.isBelowRecommended(state.radiusMeters)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        stringResource(R.string.geofence_small_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Button(
                onClick = onSave,
                enabled = state.canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(stringResource(R.string.geofence_save))
                }
            }
        }
    }
}
