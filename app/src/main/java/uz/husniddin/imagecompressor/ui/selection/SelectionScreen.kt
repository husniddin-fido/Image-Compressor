package uz.husniddin.imagecompressor.ui.selection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.husniddin.imagecompressor.data.session.SelectionItem
import uz.husniddin.imagecompressor.ui.components.AppButton
import uz.husniddin.imagecompressor.ui.components.AppTopBar
import uz.husniddin.imagecompressor.ui.components.Banner
import uz.husniddin.imagecompressor.ui.components.BannerKind
import uz.husniddin.imagecompressor.ui.components.ButtonStyle
import uz.husniddin.imagecompressor.ui.components.EmptyState
import uz.husniddin.imagecompressor.ui.components.ImageThumbnail
import uz.husniddin.imagecompressor.ui.components.rememberImagePicker
import uz.husniddin.imagecompressor.ui.theme.Spacing
import uz.husniddin.imagecompressor.util.Formatters

@Composable
fun SelectionRoute(
    onBack: () -> Unit,
    onContinue: () -> Unit,
    viewModel: SelectionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberImagePicker(viewModel::add)
    SelectionScreen(
        state = state,
        onBack = onBack,
        onAddMore = picker.pickMultiple,
        onRemove = viewModel::remove,
        onRemoveFailed = viewModel::removeFailed,
        onContinue = onContinue,
    )
}

@Composable
fun SelectionScreen(
    state: SelectionUiState,
    onBack: () -> Unit,
    onAddMore: () -> Unit,
    onRemove: (String) -> Unit,
    onRemoveFailed: () -> Unit,
    onContinue: () -> Unit,
) {
    Scaffold(
        topBar = { AppTopBar("Selected images", onBack = onBack) },
        bottomBar = {
            if (state.items.isNotEmpty()) {
                BottomActions(state, onAddMore, onContinue)
            }
        },
    ) { padding ->
        if (state.items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Outlined.PhotoLibrary,
                    title = "No images selected",
                    message = "Choose one or more photos to compress, resize or convert.",
                ) {
                    AppButton("Select images", onAddMore, icon = Icons.Outlined.AddPhotoAlternate, fillWidth = false)
                }
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            item(key = "summary") {
                val summary = buildString {
                    append("${state.readyCount} ready")
                    if (state.totalBytes > 0) append(" · ${Formatters.bytes(state.totalBytes)} total")
                }
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.xxs),
                )
            }
            if (state.failedCount > 0) {
                item(key = "failed-banner") {
                    Column {
                        Banner(
                            "${state.failedCount} file(s) couldn't be read and will be skipped.",
                            BannerKind.Warning,
                        )
                        TextButton(onClick = onRemoveFailed) { Text("Remove unreadable files") }
                    }
                }
            }
            items(state.items, key = { it.id }) { item ->
                SelectionRow(item, onRemove = { onRemove(item.id) }, modifier = Modifier.animateItem())
            }
        }
    }
}

@Composable
private fun SelectionRow(item: SelectionItem, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val name = when (item) {
        is SelectionItem.Ready -> item.image.displayName
        else -> item.uri.lastPathSegment ?: "Image"
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(Modifier.padding(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            ImageThumbnail(model = item.uri, contentDescription = null, size = 64.dp)
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                when (item) {
                    is SelectionItem.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(Spacing.xs))
                        MetaText("Reading details…")
                    }
                    is SelectionItem.Ready -> {
                        val img = item.image
                        MetaText("${img.formatLabel} · ${Formatters.dimensions(img.width, img.height)}")
                        MetaText(Formatters.bytes(img.sizeBytes))
                    }
                    is SelectionItem.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.ErrorOutline,
                            null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(Spacing.xxs))
                        Text(item.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.Close, contentDescription = "Remove $name")
            }
        }
    }
}

@Composable
private fun MetaText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun BottomActions(state: SelectionUiState, onAddMore: () -> Unit, onContinue: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            AppButton(
                "Add more",
                onAddMore,
                icon = Icons.Outlined.Add,
                style = ButtonStyle.Outlined,
                modifier = Modifier.weight(1f),
            )
            AppButton(
                if (state.readyCount > 0) "Continue (${state.readyCount})" else "Continue",
                onContinue,
                icon = Icons.AutoMirrored.Outlined.ArrowForward,
                enabled = state.canContinue,
                modifier = Modifier.weight(1.4f),
            )
        }
    }
}
