package uz.husniddin.imagecompressor.ui.history

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.husniddin.imagecompressor.data.history.HistoryEntity
import uz.husniddin.imagecompressor.data.output.ShareHelper
import uz.husniddin.imagecompressor.ui.components.AppTopBar
import uz.husniddin.imagecompressor.ui.components.CollectMessages
import uz.husniddin.imagecompressor.ui.components.EmptyState
import uz.husniddin.imagecompressor.ui.components.LoadingState
import uz.husniddin.imagecompressor.ui.theme.Spacing

/** Opens a saved history file in a viewer app; returns false if no app can handle it. */
fun openHistoryEntry(context: Context, entry: HistoryEntity): Boolean = try {
    context.startActivity(ShareHelper.viewIntent(entry.outputUri.toUri(), entry.mimeType))
    true
} catch (e: ActivityNotFoundException) {
    false
} catch (e: SecurityException) {
    false
}

@Composable
fun HistoryRoute(onBack: (() -> Unit)? = null, viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(viewModel.messages, snackbar)
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear history?") },
            text = { Text("This removes the list of past results. Your saved images are not deleted.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; viewModel.clear() }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }

    val loaded = state as? HistoryUiState.Loaded
    Scaffold(
        topBar = {
            AppTopBar("History", onBack = onBack) {
                if (!loaded?.items.isNullOrEmpty()) {
                    IconButton(onClick = { confirmClear = true }) {
                        Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear history")
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            loaded == null -> LoadingState("Loading history…", Modifier.padding(padding))
            loaded.items.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Outlined.History,
                    title = "No history yet",
                    message = "Images you save will appear here so you can open or share them again.",
                )
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                item(key = "hint") {
                    Text(
                        "Only details are stored here. Removing an entry never deletes the image.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(loaded.items, key = { it.entry.id }) { item ->
                    val entry = item.entry
                    HistoryRow(
                        entry = entry,
                        fileAvailable = item.fileAvailable,
                        onOpen = {
                            if (!openHistoryEntry(context, entry)) viewModel.showMessage("No app available to open this image.")
                        },
                        onShare = {
                            try {
                                context.startActivity(ShareHelper.shareIntent(context, listOf(entry.outputUri.toUri()), entry.mimeType))
                            } catch (e: ActivityNotFoundException) {
                                viewModel.showMessage("No app available to share images.")
                            }
                        },
                        onRemove = { viewModel.remove(entry) },
                    )
                }
            }
        }
    }
}
