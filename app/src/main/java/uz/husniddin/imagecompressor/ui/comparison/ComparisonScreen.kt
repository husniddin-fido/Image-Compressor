package uz.husniddin.imagecompressor.ui.comparison

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.husniddin.imagecompressor.data.output.ResultSaver
import uz.husniddin.imagecompressor.data.output.SaveDestination
import uz.husniddin.imagecompressor.data.output.ShareHelper
import uz.husniddin.imagecompressor.data.processing.BatchItem
import uz.husniddin.imagecompressor.domain.model.ProcessedImage
import uz.husniddin.imagecompressor.domain.model.SaveLocation
import uz.husniddin.imagecompressor.ui.components.AppButton
import uz.husniddin.imagecompressor.ui.components.AppCard
import uz.husniddin.imagecompressor.ui.components.AppTopBar
import uz.husniddin.imagecompressor.ui.components.Banner
import uz.husniddin.imagecompressor.ui.components.BannerKind
import uz.husniddin.imagecompressor.ui.components.ButtonStyle
import uz.husniddin.imagecompressor.ui.components.CollectMessages
import uz.husniddin.imagecompressor.ui.components.EmptyState
import uz.husniddin.imagecompressor.ui.components.isCompactHeight
import uz.husniddin.imagecompressor.ui.components.rememberPicturesWriteGate
import uz.husniddin.imagecompressor.ui.theme.AppTheme
import uz.husniddin.imagecompressor.ui.theme.Spacing
import uz.husniddin.imagecompressor.util.Formatters

@Composable
fun ComparisonRouteScreen(
    onBack: () -> Unit,
    viewModel: ComparisonViewModel = hiltViewModel(),
) {
    val item by viewModel.item.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(viewModel.messages, snackbar)

    val current = item
    val result = current?.result
    Scaffold(
        topBar = { AppTopBar("Compare", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (current != null && result != null) {
                ComparisonActions(current, result, saving, viewModel)
            }
        },
    ) { padding ->
        if (current == null || result == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Outlined.BrokenImage,
                    title = "Result unavailable",
                    message = "This result is no longer part of the current session.",
                ) { AppButton("Go back", onBack, fillWidth = false) }
            }
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            CompareSlider(
                original = result.source.uri,
                output = result.outputFile,
                aspectRatio = result.width.toFloat() / result.height,
            )
            Text(
                "Drag the handle to compare. The right side shows the actual output file.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StatsCard(result)
            Notes(result)
            current.saved?.let { saved ->
                Banner("Saved to ${saved.locationLabel} as ${saved.displayName}", BannerKind.Info)
            }
            Spacer(Modifier.height(Spacing.xs))
        }
    }
}

@Composable
private fun StatsCard(result: ProcessedImage) {
    AppCard {
        val saved = result.savedBytes
        Text(
            if (saved > 0) "${Formatters.bytes(saved)} saved" else "${Formatters.bytes(-saved)} larger",
            style = MaterialTheme.typography.headlineSmall,
            color = if (saved > 0) AppTheme.status.success else AppTheme.status.warning,
        )
        Text(
            Formatters.percentChange(result.originalSizeBytes, result.sizeBytes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.md))
        StatRow("", "Original", "Compressed", header = true)
        HorizontalDivider(Modifier.padding(vertical = Spacing.xs), color = MaterialTheme.colorScheme.outlineVariant)
        StatRow("Size", Formatters.bytes(result.originalSizeBytes), Formatters.bytes(result.sizeBytes))
        StatRow(
            "Dimensions",
            Formatters.dimensions(result.source.width, result.source.height),
            Formatters.dimensions(result.width, result.height),
        )
        StatRow("Format", result.source.formatLabel, result.format.label)
        StatRow("Quality", "—", result.quality?.toString() ?: "Lossless")
    }
}

@Composable
private fun StatRow(label: String, original: String, output: String, header: Boolean = false) {
    val style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium
    val color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xxs)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.9f))
        Text(original, style = style, color = color, modifier = Modifier.weight(1f))
        Text(output, style = style, color = color, fontWeight = if (header) null else FontWeight.SemiBold, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Notes(result: ProcessedImage) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        val target = result.targetSizeBytes
        if (target != null && result.targetMet == true) {
            Banner("Target of ${Formatters.bytes(target)} reached (actual size ${Formatters.bytes(result.sizeBytes)}).", BannerKind.Info)
        }
        if (target != null && result.targetMet == false) {
            Banner(
                "Couldn't reach ${Formatters.bytes(target)} — the smallest result was ${Formatters.bytes(result.sizeBytes)}. " +
                    "Turn on “Shrink dimensions if needed” or choose smaller dimensions.",
                BannerKind.Warning,
            )
        }
        if (result.qualityLoweredToAvoidGrowth) {
            Banner(
                "Quality ${result.requestedQuality} would have made this file bigger than the original, so quality ${result.quality} was used instead.",
                BannerKind.Info,
            )
        }
        if (result.downscaledForTarget) {
            Banner("Dimensions were reduced to reach the target size.", BannerKind.Info)
        }
        if (!result.isSmaller) {
            Banner(
                "The output isn't smaller than the original. The original may already be well optimised, or the chosen format/quality is less efficient for this image.",
                BannerKind.Warning,
            )
        }
        if (result.transparencyFlattened) {
            Banner("Transparent areas were filled with a solid background because JPEG doesn't support transparency.", BannerKind.Info)
        }
        if (result.dimensionsCapped) {
            Banner("Dimensions were limited to keep memory use safe on this device.", BannerKind.Info)
        }
        if (result.reducedForMemory) {
            Banner("The image was decoded at reduced resolution because the device ran low on memory.", BannerKind.Warning)
        }
    }
}

@Composable
private fun ComparisonActions(item: BatchItem, result: ProcessedImage, saving: Boolean, viewModel: ComparisonViewModel) {
    val context = LocalContext.current
    var showSaveDialog by rememberSaveable { mutableStateOf(false) }
    val writeGate = rememberPicturesWriteGate { viewModel.showMessage("Storage permission is needed to save to Pictures.") }
    val saveLocation by viewModel.saveLocation.collectAsStateWithLifecycle()
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(result.format.mimeType)) { uri: Uri? ->
        if (uri != null) viewModel.save(SaveDestination.Document(uri), null)
    }

    if (showSaveDialog) {
        SaveDialog(
            initialName = result.outputFile.nameWithoutExtension,
            extension = result.format.extension,
            locationLabel = saveLocation.label,
            onDismiss = { showSaveDialog = false },
            onSaveToDefault = { name ->
                showSaveDialog = false
                if (saveLocation is SaveLocation.Pictures) writeGate { viewModel.saveToDefault(name) } else viewModel.saveToDefault(name)
            },
            onChooseLocation = { name ->
                showSaveDialog = false
                createDocument.launch(ResultSaver.withExtension(name, result.format.extension))
            },
        )
    }

    val saved = item.saved
    val save: @Composable (Modifier) -> Unit = { m ->
        AppButton(
            text = when {
                saving -> "Saving…"
                saved != null -> "Save another copy"
                else -> "Save"
            },
            onClick = { showSaveDialog = true },
            icon = Icons.Outlined.SaveAlt,
            enabled = !saving,
            style = if (saved != null) ButtonStyle.Tonal else ButtonStyle.Primary,
            modifier = m,
        )
    }
    val share: @Composable (Modifier) -> Unit = { m ->
        AppButton(
            "Share",
            onClick = {
                val uri = ShareHelper.contentUri(context, result.outputFile)
                try {
                    context.startActivity(ShareHelper.shareIntent(context, listOf(uri), result.format.mimeType))
                } catch (e: ActivityNotFoundException) {
                    viewModel.showMessage("No app available to share images.")
                }
            },
            icon = Icons.Outlined.Share,
            style = ButtonStyle.Outlined,
            modifier = m,
        )
    }
    val open: @Composable (Modifier) -> Unit = { m ->
        AppButton(
            "Open",
            onClick = {
                val uri = saved?.uri ?: ShareHelper.contentUri(context, result.outputFile)
                try {
                    context.startActivity(ShareHelper.viewIntent(uri, result.format.mimeType))
                } catch (e: ActivityNotFoundException) {
                    viewModel.showMessage("No app available to open images.")
                }
            },
            icon = Icons.AutoMirrored.Outlined.OpenInNew,
            style = ButtonStyle.Outlined,
            modifier = m,
        )
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        val padding = Modifier.navigationBarsPadding().padding(horizontal = Spacing.screen, vertical = Spacing.sm)
        if (isCompactHeight()) {
            Row(padding, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                save(Modifier.weight(1.4f))
                share(Modifier.weight(1f))
                open(Modifier.weight(1f))
            }
        } else {
            Column(padding, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                save(Modifier)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    share(Modifier.weight(1f))
                    open(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SaveDialog(
    initialName: String,
    extension: String,
    locationLabel: String,
    onDismiss: () -> Unit,
    onSaveToDefault: (String) -> Unit,
    onChooseLocation: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    val valid = name.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save image") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(100) },
                    label = { Text("File name") },
                    suffix = { Text(".$extension") },
                    singleLine = true,
                    isError = !valid,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Saves to $locationLabel", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Saving never overwrites existing files — a number is added if the name is taken. The original image is not changed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { onChooseLocation(name) }, enabled = valid) { Text("Choose another location…") }
            }
        },
        confirmButton = { TextButton(onClick = { onSaveToDefault(name) }, enabled = valid) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
