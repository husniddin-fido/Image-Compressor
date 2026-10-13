package uz.husniddin.imagecompressor.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import uz.husniddin.imagecompressor.ui.theme.AppTheme
import uz.husniddin.imagecompressor.ui.theme.Spacing
import uz.husniddin.imagecompressor.util.Formatters

@Composable
fun StatItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clearAndSetSemantics { contentDescription = "$label: $value" }) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Pill showing how the output size compares to the original. */
@Composable
fun SavingsBadge(originalBytes: Long, outputBytes: Long, modifier: Modifier = Modifier) {
    val reduced = outputBytes < originalBytes
    val container = if (reduced) AppTheme.status.successContainer else AppTheme.status.warningContainer
    val content = if (reduced) AppTheme.status.onSuccessContainer else AppTheme.status.onWarningContainer
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.extraSmall, modifier = modifier) {
        Text(
            Formatters.percentChange(originalBytes, outputBytes),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
        )
    }
}
