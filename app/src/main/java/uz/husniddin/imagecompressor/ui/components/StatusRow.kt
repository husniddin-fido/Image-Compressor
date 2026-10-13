package uz.husniddin.imagecompressor.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uz.husniddin.imagecompressor.data.processing.ItemStatus
import uz.husniddin.imagecompressor.ui.theme.AppTheme
import uz.husniddin.imagecompressor.ui.theme.Spacing

@Composable
fun StatusLabel(status: ItemStatus, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val color = when (status) {
            ItemStatus.DONE -> AppTheme.status.success
            ItemStatus.FAILED -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        if (status == ItemStatus.PROCESSING) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
        } else {
            val icon = when (status) {
                ItemStatus.QUEUED -> Icons.Outlined.Schedule
                ItemStatus.DONE -> Icons.Outlined.CheckCircle
                ItemStatus.FAILED -> Icons.Outlined.ErrorOutline
                else -> Icons.Outlined.Block
            }
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(Spacing.xxs))
        Text(
            when (status) {
                ItemStatus.QUEUED -> "Waiting"
                ItemStatus.PROCESSING -> "Processing…"
                ItemStatus.DONE -> "Done"
                ItemStatus.FAILED -> "Failed"
                ItemStatus.CANCELLED -> "Cancelled"
            },
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}
