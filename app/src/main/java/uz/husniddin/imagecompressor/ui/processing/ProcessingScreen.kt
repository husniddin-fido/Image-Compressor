package uz.husniddin.imagecompressor.ui.processing

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.husniddin.imagecompressor.data.processing.BatchItem
import uz.husniddin.imagecompressor.data.processing.BatchState
import uz.husniddin.imagecompressor.data.processing.ItemStatus
import uz.husniddin.imagecompressor.ui.components.AppButton
import uz.husniddin.imagecompressor.ui.components.AppCard
import uz.husniddin.imagecompressor.ui.components.AppTopBar
import uz.husniddin.imagecompressor.ui.components.ButtonStyle
import uz.husniddin.imagecompressor.ui.components.ImageThumbnail
import uz.husniddin.imagecompressor.ui.components.EmptyState
import uz.husniddin.imagecompressor.ui.components.SavingsBadge
import uz.husniddin.imagecompressor.ui.components.StatusLabel
import uz.husniddin.imagecompressor.ui.theme.Spacing
import uz.husniddin.imagecompressor.util.Formatters

@Composable
fun ProcessingRoute(
    onFinished: () -> Unit,
    onHome: () -> Unit,
    viewModel: ProcessingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmCancel by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state?.isRunning) {
        if (state?.isRunning == false) onFinished()
    }
    BackHandler(enabled = state?.isRunning == true) { confirmCancel = true }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Stop processing?") },
            text = { Text("Images already finished are kept. The rest will be marked as cancelled and can be retried.") },
            confirmButton = {
                TextButton(onClick = { confirmCancel = false; viewModel.cancel() }) { Text("Stop") }
            },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Keep going") } },
        )
    }

    val current = state
    Scaffold(
        topBar = { AppTopBar("Processing") },
        bottomBar = {
            if (current?.isRunning == true) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppButton(
                        "Cancel",
                        onClick = { confirmCancel = true },
                        style = ButtonStyle.Outlined,
                        modifier = Modifier.navigationBarsPadding().padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                    )
                }
            }
        },
    ) { padding ->
        if (current == null) {
            // Happens after the app process was killed: in-memory sessions don't survive that.
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Outlined.HourglassEmpty,
                    title = "This session has ended",
                    message = "The app was closed while processing. Please select your images again.",
                ) { AppButton("Back to home", onHome, fillWidth = false) }
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            item(key = "progress") { ProgressCard(current) }
            items(current.items, key = { it.id }) { ProcessingRow(it) }
        }
    }
}

@Composable
private fun ProgressCard(state: BatchState) {
    AppCard(Modifier.padding(bottom = Spacing.xs)) {
        Text(
            "${state.finishedCount} of ${state.total} finished",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            state.current?.let { "Working on ${it.source.displayName}" } ?: "Finishing up…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(Spacing.md))
        LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth().height(8.dp))
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "Images are processed one at a time on your device. You can leave this screen — work continues in the app.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProcessingRow(item: BatchItem) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
        ImageThumbnail(item.source.uri, contentDescription = null, size = 48.dp)
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(item.source.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.status == ItemStatus.FAILED && item.error != null) {
                Text(item.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 2)
            } else {
                StatusLabel(item.status)
            }
        }
        val result = item.result
        if (item.status == ItemStatus.DONE && result != null) {
            Column(horizontalAlignment = Alignment.End) {
                Text(Formatters.bytes(result.sizeBytes), style = MaterialTheme.typography.labelLarge)
                SavingsBadge(result.originalSizeBytes, result.sizeBytes)
            }
        }
    }
}
