package com.rahul.assignmentrahul.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rahul.assignmentrahul.R
import com.rahul.assignmentrahul.data.model.Group
import com.rahul.assignmentrahul.domain.InviteCodeGenerator
import com.rahul.assignmentrahul.ui.components.TextInputDialog

private enum class GroupsDialog { None, Create, Join }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
    onGroupClick: (String) -> Unit,
    onSignOut: () -> Unit,
    viewModel: GroupsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var dialog by rememberSaveable { mutableStateOf(GroupsDialog.None) }

    val genericError = stringResource(R.string.error_generic)
    val invalidCode = stringResource(R.string.error_invalid_invite_code)
    val codeNotFound = stringResource(R.string.error_invite_code_not_found)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is GroupsEvent.OpenGroup -> onGroupClick(event.groupId)
                is GroupsEvent.ShowError -> snackbarHostState.showSnackbar(event.message ?: genericError)
                GroupsEvent.InvalidInviteCode -> snackbarHostState.showSnackbar(invalidCode)
                GroupsEvent.InviteCodeNotFound -> snackbarHostState.showSnackbar(codeNotFound)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.groups_title)) },
                actions = { TextButton(onClick = onSignOut) { Text(stringResource(R.string.action_sign_out)) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { TrackingPermissionsCard(onGeofencingAllowed = viewModel::onTrackingPermissionsGranted) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { dialog = GroupsDialog.Create },
                        enabled = !state.isSubmitting,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.groups_create)) }
                    OutlinedButton(
                        onClick = { dialog = GroupsDialog.Join },
                        enabled = !state.isSubmitting,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.groups_join)) }
                }
            }
            when {
                state.isLoading -> item { CenteredContent { CircularProgressIndicator() } }
                state.loadError != null -> item {
                    CenteredContent { Text(state.loadError.orEmpty(), color = MaterialTheme.colorScheme.error) }
                }
                state.groups.isEmpty() -> item {
                    CenteredContent { Text(stringResource(R.string.groups_empty), style = MaterialTheme.typography.bodyLarge) }
                }
                else -> items(state.groups, key = { it.id }) { group ->
                    GroupCard(group = group, onClick = { onGroupClick(group.id) })
                }
            }
        }
    }

    when (dialog) {
        GroupsDialog.Create -> TextInputDialog(
            title = stringResource(R.string.groups_create),
            label = stringResource(R.string.groups_name_label),
            confirmText = stringResource(R.string.action_create),
            onConfirm = { name -> dialog = GroupsDialog.None; viewModel.createGroup(name) },
            onDismiss = { dialog = GroupsDialog.None },
        )
        GroupsDialog.Join -> TextInputDialog(
            title = stringResource(R.string.groups_join),
            label = stringResource(R.string.groups_invite_code_label),
            confirmText = stringResource(R.string.action_join),
            onConfirm = { code -> dialog = GroupsDialog.None; viewModel.joinGroup(code) },
            onDismiss = { dialog = GroupsDialog.None },
            maxLength = InviteCodeGenerator.LENGTH,
            capitalization = KeyboardCapitalization.Characters,
        )
        GroupsDialog.None -> Unit
    }
}

@Composable
private fun GroupCard(group: Group, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(group.name, style = MaterialTheme.typography.titleMedium)
            Text(
                pluralStringResource(R.plurals.member_count, group.memberIds.size, group.memberIds.size),
                style = MaterialTheme.typography.bodyMedium,
            )
            val geofence = group.geofence
            Text(
                text = if (geofence != null) {
                    stringResource(R.string.geofence_summary_radius, geofence.radiusMeters.toInt())
                } else {
                    stringResource(R.string.geofence_not_set)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CenteredContent(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}
