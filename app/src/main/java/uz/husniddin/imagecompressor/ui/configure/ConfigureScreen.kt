package uz.husniddin.imagecompressor.ui.configure

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.husniddin.imagecompressor.ui.components.AppButton
import uz.husniddin.imagecompressor.ui.components.AppTopBar
import uz.husniddin.imagecompressor.ui.components.EmptyState
import uz.husniddin.imagecompressor.ui.theme.Spacing
import uz.husniddin.imagecompressor.util.Formatters

@Composable
fun ConfigureRoute(
    onBack: () -> Unit,
    onStarted: () -> Unit,
    viewModel: ConfigureViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val previews by viewModel.previews.collectAsStateWithLifecycle()
    var showOptions by rememberSaveable { mutableStateOf(false) }

    // Coming back from Results: re-render previews that were interrupted by processing.
    LaunchedEffect(Unit) { viewModel.refreshPreviewsIfNeeded() }

    if (showOptions) {
        ModalBottomSheet(
            onDismissRequest = { showOptions = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screen)
                    .padding(bottom = Spacing.xl),
            ) {
                Text("Output options", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(Spacing.md))
                OutputOptions(
                    state = state,
                    onFormat = viewModel::setFormat,
                    onBackground = viewModel::setBackground,
                    onPreventLarger = viewModel::setPreventLarger,
                )
            }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(if (state.images.size > 1) "Compress ${state.images.size} images" else "Compress image", onBack = onBack)
        },
        bottomBar = {
            if (state.images.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Row(
                        Modifier
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        AppButton(
                            text = if (state.isSingle) "Compress" else "Compress ${state.images.size}",
                            onClick = { if (viewModel.start()) onStarted() },
                            icon = Icons.Outlined.Compress,
                            enabled = state.canStart,
                            modifier = Modifier.weight(1f),
                        )
                        FilledTonalIconButton(onClick = { showOptions = true }, modifier = Modifier.size(Spacing.buttonHeight)) {
                            Icon(Icons.Outlined.Tune, contentDescription = "Output options")
                        }
                    }
                }
            }
        },
    ) { padding ->
        if (state.images.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Outlined.PhotoLibrary,
                    title = "No images to compress",
                    message = "Go back and select at least one readable image.",
                )
            }
            return@Scaffold
        }
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            // The preview takes a fixed share of the height so it stays visible while adjusting settings.
            val previewHeight = maxHeight * if (state.isSingle) 0.45f else 0.42f
            Column(Modifier.fillMaxSize()) {
                LivePreviewPane(
                    images = state.images,
                    previews = previews,
                    modifier = Modifier.fillMaxWidth().height(previewHeight),
                )
                PreviewCaption(state, previews)
                PrimaryTabRow(selectedTabIndex = state.tab.ordinal, containerColor = MaterialTheme.colorScheme.background) {
                    CompressionTab.entries.forEach { tab ->
                        Tab(
                            selected = tab == state.tab,
                            onClick = { viewModel.setTab(tab) },
                            text = { Text(tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            selectedContentColor = MaterialTheme.colorScheme.primary,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.screen, vertical = Spacing.md),
                ) {
                    when (state.tab) {
                        CompressionTab.FAST -> FastTab(state, viewModel::selectPreset, viewModel::setQuality)
                        CompressionTab.FILE_SIZE -> FileSizeTab(
                            state = state,
                            onTargetText = viewModel::setTargetText,
                            onTargetUnit = viewModel::setTargetUnit,
                            onAllowDownscale = viewModel::setAllowDownscale,
                        )
                        CompressionTab.MANUAL -> ManualTab(
                            state = state,
                            onQuality = viewModel::setQuality,
                            onMode = viewModel::setResizeMode,
                            onPercent = viewModel::setPercent,
                            onPreset = viewModel::setDimensionPreset,
                            onWidth = viewModel::setWidth,
                            onHeight = viewModel::setHeight,
                            onAspectLocked = viewModel::setAspectLocked,
                        )
                    }
                }
            }
        }
    }
}

/** One line under the preview with the real output details (or the batch total). */
@Composable
private fun PreviewCaption(state: ConfigureUiState, previews: Map<String, PreviewState>) {
    val ready = state.images.mapNotNull { (previews[it.id] as? PreviewState.Ready)?.result }
    val rendering = previews.values.any { it is PreviewState.Rendering }
    val text = when {
        state.isSingle && ready.isNotEmpty() -> {
            val r = ready.first()
            buildString {
                append("${Formatters.dimensions(r.width, r.height)} · ${r.format.label}")
                r.quality?.let { append(" · quality $it") }
                append(" · ${Formatters.percentChange(r.originalSizeBytes, r.sizeBytes)}")
            }
        }
        !state.isSingle && ready.isNotEmpty() -> {
            val before = ready.sumOf { it.originalSizeBytes }
            val after = ready.sumOf { it.sizeBytes }
            "${ready.size} of ${state.images.size} previewed · ${Formatters.bytes(before)} → ${Formatters.bytes(after)}"
        }
        rendering -> "Rendering preview…"
        else -> " "
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.xxs),
    )
}
