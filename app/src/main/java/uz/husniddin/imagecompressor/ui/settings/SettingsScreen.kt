package uz.husniddin.imagecompressor.ui.settings

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.husniddin.imagecompressor.BuildConfig
import uz.husniddin.imagecompressor.domain.model.AppSettings
import uz.husniddin.imagecompressor.domain.model.CompressionPreset
import uz.husniddin.imagecompressor.domain.model.FormatOption
import uz.husniddin.imagecompressor.domain.model.SaveLocation
import uz.husniddin.imagecompressor.domain.model.ThemeMode
import uz.husniddin.imagecompressor.ui.components.AppCard
import uz.husniddin.imagecompressor.ui.components.AppTopBar
import uz.husniddin.imagecompressor.ui.components.CollectMessages
import uz.husniddin.imagecompressor.ui.components.SectionHeader
import uz.husniddin.imagecompressor.ui.components.SegmentedChoice
import uz.husniddin.imagecompressor.ui.theme.Spacing

@Composable
fun SettingsRoute(onBack: (() -> Unit)? = null, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(viewModel.messages, snackbar)
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var showPrivacy by rememberSaveable { mutableStateOf(false) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) viewModel.useFolder(uri)
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear history?") },
            text = { Text("This removes the list of past results. Your saved images and original photos are not deleted.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; viewModel.clearHistory() }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
    if (showPrivacy) PrivacyDialog { showPrivacy = false }

    Scaffold(
        topBar = { AppTopBar("Settings", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val s = settings
        // Settings are preloaded at app start, so this is at most a blank frame right after launch.
        if (s == null) return@Scaffold
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            AppearanceCard(s, viewModel::setTheme)
            DefaultsCard(s, viewModel::setDefaultQuality, viewModel::setDefaultFormat)
            SaveLocationCard(s, onPictures = viewModel::usePictures, onChooseFolder = { folderPicker.launch(null) })
            AppCard(contentPadding = PaddingValues(vertical = Spacing.xs)) {
                ActionRow(Icons.Outlined.DeleteSweep, "Clear history", "Removes the list only — files stay", onClick = { confirmClear = true })
                ActionRow(Icons.Outlined.PrivacyTip, "Privacy", "All processing happens on this device", onClick = { showPrivacy = true })
                ActionRow(Icons.Outlined.Info, "Version", BuildConfig.VERSION_NAME, onClick = null)
            }
            Spacer(Modifier.height(Spacing.xs))
        }
    }
}

@Composable
private fun AppearanceCard(settings: AppSettings, onTheme: (ThemeMode) -> Unit) {
    AppCard {
        SectionHeader("Appearance")
        Spacer(Modifier.height(Spacing.sm))
        SegmentedChoice(
            options = ThemeMode.entries,
            selected = settings.themeMode,
            onSelect = onTheme,
            label = {
                when (it) {
                    ThemeMode.SYSTEM -> "System"
                    ThemeMode.LIGHT -> "Light"
                    ThemeMode.DARK -> "Dark"
                }
            },
        )
    }
}

@Composable
private fun DefaultsCard(settings: AppSettings, onQuality: (Int) -> Unit, onFormat: (FormatOption) -> Unit) {
    // Local slider state so dragging is smooth; persisted when the drag ends.
    var quality by remember(settings.defaultQuality) { mutableFloatStateOf(settings.defaultQuality.toFloat()) }
    AppCard {
        SectionHeader("Defaults", subtitle = "Used to pre-fill the compression settings")
        Spacer(Modifier.height(Spacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Default quality", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            val preset = CompressionPreset.forQuality(quality.toInt())
            Text(
                "${quality.toInt()}" + (preset?.let { " · ${it.label}" } ?: ""),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = quality,
            onValueChange = { quality = it },
            onValueChangeFinished = { onQuality(quality.toInt()) },
            valueRange = 10f..100f,
            steps = 17,
            modifier = Modifier.semantics {
                contentDescription = "Default quality"
                stateDescription = "${quality.toInt()}"
            },
        )
        Spacer(Modifier.height(Spacing.xs))
        Text("Default format", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(Spacing.xs))
        SegmentedChoice(FormatOption.entries, settings.defaultFormat, onFormat, { it.label })
    }
}

@Composable
private fun SaveLocationCard(settings: AppSettings, onPictures: () -> Unit, onChooseFolder: () -> Unit) {
    AppCard {
        SectionHeader("Save location", subtitle = "Where “Save” puts your images")
        Spacer(Modifier.height(Spacing.xs))
        Column(Modifier.selectableGroup()) {
            RadioRow(
                selected = settings.saveLocation is SaveLocation.Pictures,
                title = SaveLocation.Pictures.label,
                subtitle = "Visible in your gallery apps",
                onClick = onPictures,
            )
            val folder = settings.saveLocation as? SaveLocation.Folder
            RadioRow(
                selected = folder != null,
                title = folder?.label ?: "A folder you choose",
                subtitle = if (folder != null) "Tap to choose a different folder" else "Pick any folder, e.g. on an SD card",
                onClick = onChooseFolder,
            )
        }
    }
}

@Composable
private fun RadioRow(selected: Boolean, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.minTouchTarget)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(Spacing.sm))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, subtitle: String, onClick: (() -> Unit)?) {
    val content: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Spacing.buttonHeight).padding(horizontal = Spacing.md, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onClick != null) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (onClick != null) {
        Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainerLow) { content() }
    } else {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) { content() }
    }
}

@Composable
private fun PrivacyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Privacy") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text("• Images are compressed, resized and converted entirely on your device. Nothing is uploaded and the app has no internet permission.")
                Text("• The app only reads the photos you pick with the system photo picker, and only writes where you save.")
                Text("• Your original images are never modified or deleted.")
                Text("• Outputs don't contain the original EXIF metadata (such as GPS location or camera details).")
                Text("• Temporary results are stored in the app's private cache and deleted automatically.")
                Text("• History stores only file names, sizes, dimensions and settings — clearing it never deletes images.")
                Text("• No account, analytics or ads.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}
