package com.rahul.assignmentrahul.ui.groupdetail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.format.DateUtils
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rahul.assignmentrahul.R
import com.rahul.assignmentrahul.ui.components.MAX_CONTENT_WIDTH
import com.rahul.assignmentrahul.data.model.ExitEvent
import com.rahul.assignmentrahul.data.model.Group
import com.rahul.assignmentrahul.ui.components.EmptyState
import com.rahul.assignmentrahul.ui.components.InitialAvatar
import com.rahul.assignmentrahul.ui.components.SectionHeader
import com.rahul.assignmentrahul.ui.components.StatusPill
import com.rahul.assignmentrahul.ui.components.radiusLabel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    onBack: () -> Unit,
    onOpenGeofence: (String) -> Unit,
    viewModel: GroupDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val group = state.group
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        group?.name ?: stringResource(R.string.group_detail_title),
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                group == null -> Text(
                    text = state.errorMessage ?: stringResource(R.string.group_not_found),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                )
                else -> GroupDetailContent(
                    group = group,
                    state = state,
                    snackbarHostState = snackbarHostState,
                    onOpenGeofence = { onOpenGeofence(group.id) },
                )
            }
        }
    }
}

@Composable
private fun GroupDetailContent(
    group: Group,
    state: GroupDetailUiState,
    snackbarHostState: SnackbarHostState,
    onOpenGeofence: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.invite_copied)
    val clipboardLabel = stringResource(R.string.invite_clipboard_label)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = MAX_CONTENT_WIDTH),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ZoneCard(group = group, isOwner = state.isOwner, onOpenGeofence = onOpenGeofence) }
        item {
            InviteCard(
                group = group,
                onCopy = {
                    copyToClipboard(context, clipboardLabel, group.inviteCode)
                    // Android 13+ shows its own "copied" confirmation.
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
                    }
                },
                onShare = { shareInvite(context, group) },
            )
        }

        item { SectionHeader(stringResource(R.string.group_members), trailing = group.memberIds.size.toString()) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    group.memberIds.forEach { memberId ->
                        MemberRow(
                            name = group.memberNames[memberId] ?: stringResource(R.string.member_unknown),
                            isOwner = group.isOwner(memberId),
                            isYou = memberId == state.currentUserId,
                        )
                    }
                }
            }
        }

        item { SectionHeader(stringResource(R.string.group_recent_exits)) }
        if (state.recentExits.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.Notifications,
                    title = stringResource(R.string.group_no_exits),
                    message = stringResource(R.string.group_zone_explainer),
                )
            }
        } else {
            items(state.recentExits, key = { it.id }) { ExitRow(it) }
        }
    }
}

@Composable
private fun ZoneCard(group: Group, isOwner: Boolean, onOpenGeofence: () -> Unit) {
    val geofence = group.geofence
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.LocationOn, contentDescription = null)
                Text(stringResource(R.string.group_zone_title), style = MaterialTheme.typography.titleMedium)
            }
            if (geofence != null) {
                Text(
                    stringResource(
                        R.string.geofence_summary_full,
                        radiusLabel(geofence.radiusMeters),
                        geofence.latitude,
                        geofence.longitude,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(stringResource(R.string.group_zone_explainer), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(stringResource(R.string.group_zone_missing), style = MaterialTheme.typography.bodyMedium)
            }
            // Members can only view, and there's nothing to view until the owner sets a zone.
            if (isOwner || geofence != null) {
                val label = when {
                    isOwner && geofence == null -> R.string.geofence_set
                    isOwner -> R.string.geofence_edit
                    else -> R.string.geofence_view
                }
                Button(onClick = onOpenGeofence) { Text(stringResource(label)) }
            }
        }
    }
}

@Composable
private fun InviteCard(group: Group, onCopy: () -> Unit, onShare: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.group_invite_code), style = MaterialTheme.typography.labelLarge)
            Text(
                group.inviteCode,
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 6.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.group_invite_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCopy) { Text(stringResource(R.string.action_copy)) }
                FilledTonalButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.action_share), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun MemberRow(name: String, isOwner: Boolean, isYou: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InitialAvatar(name = name)
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (isYou) StatusPill(stringResource(R.string.member_tag_you))
        if (isOwner) {
            StatusPill(
                text = stringResource(R.string.member_tag_owner),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun ExitRow(event: ExitEvent) {
    val time = DateUtils.getRelativeTimeSpanString(
        event.timestampMillis,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Amber = "left the zone", matching the dot on the app icon.
            InitialAvatar(
                name = event.userName,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.exit_row_title, event.userName), style = MaterialTheme.typography.bodyLarge)
                Text(time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    context.getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun shareInvite(context: Context, group: Group) {
    val text = context.getString(R.string.invite_share_text, group.name, group.inviteCode)
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null))
}
