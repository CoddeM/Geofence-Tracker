package com.rahul.assignmentrahul.ui.groupdetail

import android.content.Context
import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rahul.assignmentrahul.R
import com.rahul.assignmentrahul.data.model.ExitEvent
import com.rahul.assignmentrahul.data.model.Group

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    onBack: () -> Unit,
    onOpenGeofence: (String) -> Unit,
    viewModel: GroupDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val group = state.group

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group?.name ?: stringResource(R.string.group_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
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
    onOpenGeofence: () -> Unit,
) {
    val context = LocalContext.current
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { InviteCard(group = group, onShare = { shareInvite(context, group) }) }
        item { GeofenceCard(group = group, isOwner = state.isOwner, onOpenGeofence = onOpenGeofence) }
        sectionHeader(R.string.group_members)
        items(group.memberIds, key = { it }) { memberId ->
            MemberRow(
                name = group.memberNames[memberId] ?: stringResource(R.string.member_unknown),
                isOwner = group.isOwner(memberId),
                isYou = memberId == state.currentUserId,
            )
        }
        sectionHeader(R.string.group_recent_exits)
        if (state.recentExits.isEmpty()) {
            item { Text(stringResource(R.string.group_no_exits), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(state.recentExits, key = { it.id }) { ExitRow(it) }
        }
    }
}

@Composable
private fun InviteCard(group: Group, onShare: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.group_invite_code), style = MaterialTheme.typography.labelLarge)
                Text(group.inviteCode, style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Monospace)
                Text(
                    stringResource(R.string.group_invite_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Default.Share, contentDescription = stringResource(R.string.action_share_invite))
            }
        }
    }
}

@Composable
private fun GeofenceCard(group: Group, isOwner: Boolean, onOpenGeofence: () -> Unit) {
    val geofence = group.geofence
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.group_geofence), style = MaterialTheme.typography.labelLarge)
            Text(
                if (geofence != null) {
                    stringResource(
                        R.string.geofence_summary_full,
                        geofence.radiusMeters.toInt(),
                        geofence.latitude,
                        geofence.longitude,
                    )
                } else {
                    stringResource(R.string.geofence_not_set)
                }
            )
            val buttonText = when {
                isOwner && geofence == null -> R.string.geofence_set
                isOwner -> R.string.geofence_edit
                else -> R.string.geofence_view
            }
            // Members can only view, and there's nothing to view until the owner sets one.
            if (isOwner || geofence != null) {
                Button(onClick = onOpenGeofence) { Text(stringResource(buttonText)) }
            }
        }
    }
}

private fun LazyListScope.sectionHeader(titleRes: Int) {
    item(key = "header_$titleRes") {
        Text(
            stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun MemberRow(name: String, isOwner: Boolean, isYou: Boolean) {
    val tags = buildList {
        if (isOwner) add(stringResource(R.string.member_tag_owner))
        if (isYou) add(stringResource(R.string.member_tag_you))
    }
    Text(if (tags.isEmpty()) name else "$name (${tags.joinToString()})", style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun ExitRow(event: ExitEvent) {
    val time = DateUtils.getRelativeTimeSpanString(
        event.timestampMillis,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()
    Column {
        Text(stringResource(R.string.exit_row_title, event.userName), style = MaterialTheme.typography.bodyLarge)
        Text(time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun shareInvite(context: Context, group: Group) {
    val text = context.getString(R.string.invite_share_text, group.name, group.inviteCode)
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null))
}
