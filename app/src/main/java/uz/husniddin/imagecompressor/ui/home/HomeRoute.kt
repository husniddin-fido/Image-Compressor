package uz.husniddin.imagecompressor.ui.home

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.husniddin.imagecompressor.ui.components.AppCard
import uz.husniddin.imagecompressor.ui.components.SectionHeader
import uz.husniddin.imagecompressor.ui.components.rememberImagePicker
import uz.husniddin.imagecompressor.ui.history.HistoryRow
import uz.husniddin.imagecompressor.ui.history.openHistoryEntry
import uz.husniddin.imagecompressor.ui.theme.Spacing

@Composable
fun HomeRoute(
    onImagesPicked: () -> Unit,
    onOpenHistory: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val picker = rememberImagePicker { uris ->
        viewModel.startSession(uris)
        onImagesPicked()
    }
    HomeScreen(
        onSelectImages = picker.pickMultiple,
        onSelectSingleImage = picker.pickSingle,
        recentContent = {
            val items = recent ?: return@HomeScreen
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader("Recent results", Modifier.weight(1f))
                    if (items.isNotEmpty()) TextButton(onClick = onOpenHistory) { Text("See all") }
                }
                if (items.isEmpty()) {
                    AppCard {
                        Text("Nothing here yet", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Images you compress and save will show up here for quick access.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items.forEach { item ->
                        HistoryRow(
                            entry = item.entry,
                            fileAvailable = item.fileAvailable,
                            onOpen = {
                                if (!openHistoryEntry(context, item.entry)) {
                                    Toast.makeText(context, "No app available to open this image.", Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                    }
                }
            }
        },
    )
}
