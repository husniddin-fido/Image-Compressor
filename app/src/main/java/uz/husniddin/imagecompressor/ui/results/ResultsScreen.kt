package uz.husniddin.imagecompressor.ui.results

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.DriveFolderUpload
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.husniddin.imagecompressor.domain.model.SaveLocation
import uz.husniddin.imagecompressor.data.output.ShareHelper
import uz.husniddin.imagecompressor.data.processing.BatchItem
import uz.husniddin.imagecompressor.data.processing.BatchState
import uz.husniddin.imagecompressor.data.processing.ItemStatus
import uz.husniddin.imagecompressor.ui.components.AppButton
import uz.husniddin.imagecompressor.ui.components.AppCard
import uz.husniddin.imagecompressor.ui.components.AppTopBar
import uz.husniddin.imagecompressor.ui.components.Banner
import uz.husniddin.imagecompressor.ui.components.BannerKind
import uz.husniddin.imagecompressor.ui.components.ButtonStyle
import uz.husniddin.imagecompressor.ui.components.CollectMessages
import uz.husniddin.imagecompressor.ui.components.EmptyState
import uz.husniddin.imagecompressor.ui.components.ImageThumbnail
import uz.husniddin.imagecompressor.ui.components.isCompactHeight
import uz.husniddin.imagecompressor.ui.components.SavingsBadge
import uz.husniddin.imagecompressor.ui.components.StatItem
import uz.husniddin.imagecompressor.ui.components.StatusLabel
import uz.husniddin.imagecompressor.ui.components.rememberPicturesWriteGate
import uz.husniddin.imagecompressor.ui.theme.AppTheme
import uz.husniddin.imagecompressor.ui.theme.Spacing
import uz.husniddin.imagecompressor.util.Formatters

@Composable
fun ResultsRoute(
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit,
    onDone: () -> Unit,
    viewModel: ResultsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val saveLocation by viewModel.saveLocation.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var confirmFinish by rememberSaveable { mutableStateOf(false) }
    val finish = { viewModel.finishSession(); onDone() }
    if (confirmFinish) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text("Discard unsaved images?") },
            text = { Text("Some results haven't been saved. Finishing deletes these temporary files. Your originals are not affected.") },
            confirmButton = { TextButton(onClick = { confirmFinish = false; finish() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text("Keep") } },
        )
    }
    CollectMessages(viewModel.messages, snackbar)

    val writeGate = rememberPicturesWriteGate { viewModel.showMessage("Storage permission is needed to save to Pictures.") }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) viewModel.saveAllToFolder(uri)
    }

    val share: () -> Unit = share@{
        val items = state?.succeeded.orEmpty()
        if (items.isEmpty()) return@share
        val uris = items.map { ShareHelper.contentUri(context, it.result!!.outputFile) }
        val mime = ShareHelper.commonMimeType(items.map { it.result!!.format.mimeType })
        try {
            context.startActivity(ShareHelper.shareIntent(context, uris, mime))
            viewModel.onShared(uris.size)
        } catch (e: ActivityNotFoundException) {
            viewModel.showMessage("No app available to share images.")
        }
    }

    ResultsScreen(
        state = state,
        saving = saving,
        snackbar = snackbar,
        onBack = onBack,
        onDone = {
            val hasUnsaved = state?.succeeded?.any { it.saved == null } == true
            if (hasUnsaved) confirmFinish = true else finish()
        },
        onOpenItem = onOpenItem,
        onRetry = viewModel::retryFailed,
        saveLocation = saveLocation,
        onSaveAll = {
            if (saveLocation is SaveLocation.Pictures) writeGate { viewModel.saveAllToDefault() } else viewModel.saveAllToDefault()
        },
        onSaveAllToFolder = { folderPicker.launch(null) },
        onShareAll = share,
    )
}

@Composable
fun ResultsScreen(
    state: BatchState?,
    saving: Boolean,
    saveLocation: SaveLocation,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onOpenItem: (String) -> Unit,
    onRetry: () -> Unit,
    onSaveAll: () -> Unit,
    onSaveAllToFolder: () -> Unit,
    onShareAll: () -> Unit,
) {
    Scaffold(
        topBar = {
            AppTopBar("Results", onBack = onBack) {
                IconButton(onClick = onDone) { Icon(Icons.Outlined.Done, contentDescription = "Finish and start over") }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            val s = state
            if (s != null && s.succeeded.isNotEmpty()) BottomActions(s, saving, saveLocation, onSaveAll, onSaveAllToFolder, onShareAll)
        },
    ) { padding ->
        if (state == null) {
            EmptyState(
                icon = Icons.Outlined.CheckCircle,
                title = "No results",
                message = "This session has ended. Start a new one from the home screen.",
                modifier = Modifier.padding(padding),
            ) { AppButton("Back to home", onDone, fillWidth = false) }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            item(key = "summary") { SummaryCard(state) }
            if (state.retryableCount > 0 && !state.isRunning) {
                item(key = "retry") {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Banner(
                            buildString {
                                if (state.failedCount > 0) append("${state.failedCount} image(s) failed. ")
                                if (state.cancelledCount > 0) append("${state.cancelledCount} cancelled. ")
                                append("Other results are unaffected.")
                            },
                            BannerKind.Warning,
                        )
                        AppButton("Retry ${state.retryableCount}", onRetry, icon = Icons.Outlined.Refresh, style = ButtonStyle.Tonal)
                    }
                }
            }
            if (state.isRunning) {
                item(key = "running") { Banner("Retrying… results update as each image finishes.", BannerKind.Info) }
            }
            items(state.items, key = { it.id }) { item ->
                ResultRow(item, onClick = { onOpenItem(item.id) })
            }
        }
    }
}

@Composable
private fun SummaryCard(state: BatchState) {
    AppCard(Modifier.padding(bottom = Spacing.xs)) {
        val done = state.succeeded.size
        if (done == 0) {
            Text("No images were processed", style = MaterialTheme.typography.titleLarge)
            return@AppCard
        }
        val saved = state.savedBytes
        val percent = if (state.originalBytes > 0) saved * 100.0 / state.originalBytes else 0.0
        if (saved > 0) {
            Text("Saved ${Formatters.bytes(saved)}", style = MaterialTheme.typography.headlineMedium, color = AppTheme.status.success)
            Text(
                "${"%.0f".format(percent)}% smaller in total",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text("No space saved", style = MaterialTheme.typography.headlineMedium, color = AppTheme.status.warning)
            Text(
                "The outputs are ${Formatters.bytes(-saved)} larger than the originals. Try a lower quality, a smaller size, or JPEG/WebP.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            StatItem("Before", Formatters.bytes(state.originalBytes), Modifier.weight(1f))
            StatItem("After", Formatters.bytes(state.outputBytes), Modifier.weight(1f))
            StatItem("Images", if (done == state.total) "$done" else "$done of ${state.total}", Modifier.weight(1f))
        }
    }
}

@Composable
private fun ResultRow(item: BatchItem, onClick: () -> Unit) {
    val result = item.result
    Surface(
        onClick = onClick,
        enabled = result != null,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            ImageThumbnail(result?.outputFile ?: item.source.uri, contentDescription = null, size = 64.dp)
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    result?.outputFile?.name ?: item.source.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (result == null) {
                    if (item.error != null) {
                        Text(item.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 2)
                    } else {
                        StatusLabel(item.status)
                    }
                } else {
                    Text(
                        "${Formatters.bytes(result.originalSizeBytes)} → ${Formatters.bytes(result.sizeBytes)} · ${result.format.label} · ${Formatters.dimensions(result.width, result.height)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Spacing.xxs))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        SavingsBadge(result.originalSizeBytes, result.sizeBytes)
                        when {
                            result.targetMet == false -> NoteText("Target not reached")
                            result.qualityLoweredToAvoidGrowth -> NoteText("Quality ${result.quality}")
                            item.saved != null -> NoteText("Saved")
                        }
                    }
                }
            }
            if (result != null) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (item.status != ItemStatus.FAILED) {
                Spacer(Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun NoteText(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun BottomActions(
    state: BatchState,
    saving: Boolean,
    saveLocation: SaveLocation,
    onSaveAll: () -> Unit,
    onSaveAllToFolder: () -> Unit,
    onShareAll: () -> Unit,
) {
    val unsaved = state.succeeded.count { it.saved == null }
    val canSave = unsaved > 0 && !saving && !state.isRunning
    val saveAll: @Composable (Modifier) -> Unit = { m ->
        AppButton(
            text = when {
                saving -> "Saving…"
                unsaved == 0 -> "All saved"
                else -> "Save" + (if (unsaved > 1) " $unsaved" else "") + " to " + shortLabel(saveLocation)
            },
            onClick = onSaveAll,
            icon = Icons.Outlined.SaveAlt,
            enabled = canSave,
            modifier = m,
        )
    }
    val saveTo: @Composable (Modifier) -> Unit = { m ->
        AppButton("Save to…", onSaveAllToFolder, icon = Icons.Outlined.DriveFolderUpload, style = ButtonStyle.Outlined, enabled = canSave, modifier = m)
    }
    val share: @Composable (Modifier) -> Unit = { m ->
        AppButton("Share", onShareAll, icon = Icons.Outlined.Share, style = ButtonStyle.Outlined, enabled = !state.isRunning, modifier = m)
    }
    Surface(color = MaterialTheme.colorScheme.background) {
        val padding = Modifier.navigationBarsPadding().padding(horizontal = Spacing.screen, vertical = Spacing.sm)
        if (isCompactHeight()) {
            Row(padding, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                saveAll(Modifier.weight(1.6f))
                saveTo(Modifier.weight(1f))
                share(Modifier.weight(1f))
            }
        } else {
            Column(padding, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                saveAll(Modifier)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    saveTo(Modifier.weight(1f))
                    share(Modifier.weight(1f))
                }
            }
        }
    }
}

private fun shortLabel(location: SaveLocation): String = when (location) {
    SaveLocation.Pictures -> "Pictures"
    is SaveLocation.Folder -> location.label
}
