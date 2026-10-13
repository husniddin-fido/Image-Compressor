package uz.husniddin.imagecompressor.ui.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import uz.husniddin.imagecompressor.data.history.HistoryEntity
import uz.husniddin.imagecompressor.ui.components.ImageThumbnail
import uz.husniddin.imagecompressor.ui.components.SavingsBadge
import uz.husniddin.imagecompressor.ui.theme.Spacing
import uz.husniddin.imagecompressor.util.Formatters
import java.text.DateFormat
import java.util.Date

/** One history entry. Shared by the History screen and the Home "Recent" section. */
@Composable
fun HistoryRow(
    entry: HistoryEntity,
    fileAvailable: Boolean,
    onOpen: () -> Unit,
    onShare: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    var menu by remember { mutableStateOf(false) }
    Surface(
        onClick = onOpen,
        enabled = fileAvailable,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            ImageThumbnail(if (fileAvailable) entry.outputUri else null, contentDescription = null, size = 56.dp)
            Spacer(Modifier.width(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(entry.outputName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${Formatters.bytes(entry.originalSizeBytes)} → ${Formatters.bytes(entry.outputSizeBytes)} · ${entry.formatLabel} · ${Formatters.dimensions(entry.width, entry.height)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SavingsBadge(entry.originalSizeBytes, entry.outputSizeBytes)
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        if (fileAvailable) DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(entry.createdAt))
                        else "File missing — it may have been moved or deleted",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (fileAvailable) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (onShare != null || onRemove != null) {
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "More options for ${entry.outputName}")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (fileAvailable) {
                            DropdownMenuItem(
                                text = { Text("Open") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null) },
                                onClick = { menu = false; onOpen() },
                            )
                            if (onShare != null) {
                                DropdownMenuItem(
                                    text = { Text("Share") },
                                    leadingIcon = { Icon(Icons.Outlined.Share, null) },
                                    onClick = { menu = false; onShare() },
                                )
                            }
                        }
                        if (onRemove != null) {
                            DropdownMenuItem(
                                text = { Text("Remove from history") },
                                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null) },
                                onClick = { menu = false; onRemove() },
                            )
                        }
                    }
                }
            }
        }
    }
}
