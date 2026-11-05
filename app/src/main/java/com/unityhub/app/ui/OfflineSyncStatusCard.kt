package com.unityhub.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import com.unityhub.app.sync.OfflineSyncDisplayState
import com.unityhub.app.sync.OfflineSyncScheduler
import com.unityhub.app.sync.OfflineSyncStatusRules
import com.unityhub.app.sync.OfflineSyncStatusStore
import com.unityhub.app.sync.OfflineSyncWorker
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun OfflineSyncStatusCard(
    userId: String,
    organizationId: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val workFlow = remember(context, userId) {
        OfflineSyncScheduler.observeWork(context, userId)
    }
    val workInfos by workFlow.collectAsState(initial = emptyList())
    val snapshot = remember(context, userId, organizationId, workInfos) {
        OfflineSyncStatusStore.read(context, userId, organizationId)
    }

    val runningWork = workInfos.firstOrNull { it.state == WorkInfo.State.RUNNING }
    val immediateWorkQueued = workInfos.any { info ->
        info.state == WorkInfo.State.ENQUEUED &&
            OfflineSyncScheduler.immediateTagFor(userId) in info.tags
    }
    val displayState = OfflineSyncStatusRules.resolve(
        isRunning = runningWork != null,
        isImmediateWorkQueued = immediateWorkQueued,
        hasError = !snapshot.lastError.isNullOrBlank(),
        hasSuccessfulSync = snapshot.lastSuccessfulSyncAtMillis != null
    )
    val isBusy = displayState == OfflineSyncDisplayState.RUNNING ||
        displayState == OfflineSyncDisplayState.QUEUED

    val statusLabel = when (displayState) {
        OfflineSyncDisplayState.RUNNING -> OfflineSyncStatusRules.progressLabel(
            runningWork?.progress?.getString(OfflineSyncWorker.PROGRESS_STAGE_KEY)
        )
        OfflineSyncDisplayState.QUEUED -> "Sync queued · waiting for network or retry"
        OfflineSyncDisplayState.RETRYING -> "Sync needs attention"
        OfflineSyncDisplayState.SYNCED -> "Offline data is up to date"
        OfflineSyncDisplayState.NOT_SYNCED -> "Not synced yet"
    }

    val lastSyncLabel = snapshot.lastSuccessfulSyncAtMillis?.let { timestamp ->
        "Last successful sync · " + DateTimeFormatter
            .ofPattern("MMM d, yyyy · h:mm a", Locale.getDefault())
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(timestamp))
    } ?: "No successful sync recorded on this device yet."

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Icon(
                    imageVector = if (displayState == OfflineSyncDisplayState.SYNCED) {
                        Icons.Default.CloudDone
                    } else {
                        Icons.Default.Sync
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Offline sync", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        statusLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (displayState == OfflineSyncDisplayState.RETRYING) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                if (displayState == OfflineSyncDisplayState.RUNNING) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }

            Text(
                lastSyncLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!snapshot.lastError.isNullOrBlank() &&
                displayState != OfflineSyncDisplayState.RUNNING &&
                displayState != OfflineSyncDisplayState.QUEUED
            ) {
                Text(
                    snapshot.lastError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Text(
                "Songs, service plans, setlists, and assignment snapshots refresh in the background.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = {
                    OfflineSyncScheduler.syncNow(context, userId, organizationId)
                },
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Text(
                    when {
                        displayState == OfflineSyncDisplayState.RUNNING -> "Syncing…"
                        displayState == OfflineSyncDisplayState.QUEUED -> "Sync queued"
                        else -> "Sync now"
                    },
                    modifier = Modifier.padding(start = 7.dp)
                )
            }
        }
    }
}
