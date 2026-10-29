package com.rahul.assignmentrahul.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rahul.assignmentrahul.R
import com.rahul.assignmentrahul.ui.components.MAX_CONTENT_WIDTH
import com.rahul.assignmentrahul.data.model.Group
import com.rahul.assignmentrahul.domain.InviteCodeGenerator
import com.rahul.assignmentrahul.ui.components.EmptyState
import com.rahul.assignmentrahul.ui.components.InitialAvatar
import com.rahul.assignmentrahul.ui.components.StatusPill
import com.rahul.assignmentrahul.ui.components.TextInputDialog
import com.rahul.assignmentrahul.ui.components.radiusLabel

private enum class GroupsDialog { None, Create, Join, ConfirmSignOut }

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
    var menuExpanded by remember { mutableStateOf(false) }

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
                actions = {
                    TextButton(onClick = { dialog = GroupsDialog.Join }, enabled = !state.isSubmitting) {
                        Text(stringResource(R.string.action_join))
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_more))
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_sign_out)) },
                                onClick = {
                                    menuExpanded = false
                                    dialog = GroupsDialog.ConfirmSignOut
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { if (!state.isSubmitting) dialog = GroupsDialog.Create },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.groups_new)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = MAX_CONTENT_WIDTH),
            // Extra bottom space so the FAB never covers the last card.
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { TrackingPermissionsCard(onGeofencingAllowed = viewModel::onTrackingPermissionsGranted) }
            when {
                state.isLoading -> item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.loadError != null -> item {
                    Text(state.loadError.orEmpty(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                }
                state.groups.isEmpty() -> item {
                    EmptyState(
                        icon = Icons.Default.LocationOn,
                        title = stringResource(R.string.groups_empty_title),
                        message = stringResource(R.string.groups_empty_message),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { dialog = GroupsDialog.Create }) { Text(stringResource(R.string.groups_new)) }
                            OutlinedButton(onClick = { dialog = GroupsDialog.Join }) { Text(stringResource(R.string.action_join)) }
                        }
                    }
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
        GroupsDialog.ConfirmSignOut -> AlertDialog(
            onDismissRequest = { dialog = GroupsDialog.None },
            title = { Text(stringResource(R.string.sign_out_confirm_title)) },
            text = { Text(stringResource(R.string.sign_out_confirm_message)) },
            confirmButton = {
                TextButton(onClick = { dialog = GroupsDialog.None; onSignOut() }) {
                    Text(stringResource(R.string.action_sign_out))
                }
            },
            dismissButton = {
                TextButton(onClick = { dialog = GroupsDialog.None }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
        GroupsDialog.None -> Unit
    }
}

@Composable
private fun GroupCard(group: Group, onClick: () -> Unit) {
    val geofence = group.geofence
    val members = pluralStringResource(R.plurals.member_count, group.memberIds.size, group.memberIds.size)
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            InitialAvatar(name = group.name, size = 48.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    group.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (geofence != null) {
                        stringResource(R.string.group_card_summary, members, radiusLabel(geofence.radiusMeters))
                    } else {
                        members
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (geofence != null) {
                    StatusPill(
                        text = stringResource(R.string.pill_zone_set),
                        icon = Icons.Default.CheckCircle,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else {
                    StatusPill(text = stringResource(R.string.pill_no_zone))
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
