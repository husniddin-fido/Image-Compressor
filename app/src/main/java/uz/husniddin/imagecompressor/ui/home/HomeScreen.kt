package uz.husniddin.imagecompressor.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import uz.husniddin.imagecompressor.ui.components.AppButton
import uz.husniddin.imagecompressor.ui.components.AppCard
import uz.husniddin.imagecompressor.ui.components.ButtonStyle
import uz.husniddin.imagecompressor.ui.components.SectionHeader
import uz.husniddin.imagecompressor.ui.theme.Spacing

@Composable
fun HomeScreen(
    onSelectImages: () -> Unit,
    onSelectSingleImage: () -> Unit,
    recentContent: @Composable () -> Unit = {},
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = Spacing.screen, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            HomeHeader()
            HeroCard(onSelectImages, onSelectSingleImage)
            Capabilities()
            recentContent()
            Spacer(Modifier.height(Spacing.xs))
        }
    }
}

@Composable
private fun HomeHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Compress, null, tint = MaterialTheme.colorScheme.onPrimary)
        }
        Spacer(Modifier.width(Spacing.sm))
        Text(
            "Image Compressor",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
    }
}

@Composable
private fun HeroCard(onSelectImages: () -> Unit, onSelectSingleImage: () -> Unit) {
    AppCard(contentPadding = PaddingValues(Spacing.xl)) {
        Text("Make your images lighter", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(Spacing.xs))
        Text(
            "Compress, resize and convert photos in seconds. Pick one image or a whole batch.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xl))
        AppButton("Select images", onSelectImages, icon = Icons.Outlined.AddPhotoAlternate)
        Spacer(Modifier.height(Spacing.sm))
        AppButton("Compress a single photo", onSelectSingleImage, icon = Icons.Outlined.Image, style = ButtonStyle.Tonal)
        Spacer(Modifier.height(Spacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Lock,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(Spacing.xs))
            Text(
                "Processed on your device. Your images are never uploaded.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Capabilities() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader("What you can do")
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            CapabilityTile(Icons.Outlined.Compress, "Compress", "Quality or target size", Modifier.weight(1f))
            CapabilityTile(Icons.Outlined.AspectRatio, "Resize", "Pixels or percent", Modifier.weight(1f))
            CapabilityTile(Icons.Outlined.SwapHoriz, "Convert", "JPEG · PNG · WebP", Modifier.weight(1f))
        }
    }
}

@Composable
private fun CapabilityTile(icon: ImageVector, title: String, subtitle: String, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(Spacing.sm)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(Spacing.xs))
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
