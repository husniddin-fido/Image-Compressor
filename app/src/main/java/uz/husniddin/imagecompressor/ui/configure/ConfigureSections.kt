package uz.husniddin.imagecompressor.ui.configure

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import uz.husniddin.imagecompressor.domain.model.CompressionPreset
import uz.husniddin.imagecompressor.domain.model.FormatOption
import uz.husniddin.imagecompressor.ui.components.Banner
import uz.husniddin.imagecompressor.ui.components.BannerKind
import uz.husniddin.imagecompressor.ui.components.SectionHeader
import uz.husniddin.imagecompressor.ui.components.SegmentedChoice
import uz.husniddin.imagecompressor.ui.theme.Spacing
import uz.husniddin.imagecompressor.util.Formatters

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Hint(subtitle)
        }
        Spacer(Modifier.width(Spacing.sm))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** "Compression amount": Low keeps the most quality, High makes the smallest files. */
private enum class CompressionAmount(val label: String, val preset: CompressionPreset) {
    LOW("Low", CompressionPreset.HIGH_QUALITY),
    MEDIUM("Medium", CompressionPreset.BALANCED),
    HIGH("High", CompressionPreset.SMALL_SIZE),
}

@Composable
private fun QualitySlider(quality: Int, onQuality: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Quality", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        Text("$quality", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
    Slider(
        value = quality.toFloat(),
        onValueChange = { onQuality(it.toInt()) },
        valueRange = 10f..100f,
        steps = 17, // multiples of 5
        modifier = Modifier.semantics {
            contentDescription = "Quality"
            stateDescription = "$quality"
        },
    )
    Row {
        Hint("Smaller file")
        Spacer(Modifier.weight(1f))
        Hint("Better quality")
    }
    if (quality > 95) {
        Spacer(Modifier.height(Spacing.xs))
        Banner(
            "Above 95 files usually get much bigger with no visible difference. With “Never make files larger” on, the quality is lowered automatically when that happens.",
            BannerKind.Info,
        )
    }
}

@Composable
private fun PngQualityNote(state: ConfigureUiState) {
    if (state.pngSelected) {
        Spacer(Modifier.height(Spacing.sm))
        Banner(
            "PNG is lossless, so quality has no effect on PNG output. Choose JPEG or WebP in output options, or resize in Manual.",
            BannerKind.Info,
        )
    }
}

@Composable
fun FastTab(state: ConfigureUiState, onPreset: (CompressionPreset) -> Unit, onQuality: (Int) -> Unit) {
    Column {
        SectionHeader("Compression amount")
        Spacer(Modifier.height(Spacing.sm))
        val selected = CompressionAmount.entries.firstOrNull { it.preset.quality == state.quality }
        SegmentedChoice(
            options = CompressionAmount.entries,
            selected = selected ?: CompressionAmount.MEDIUM,
            onSelect = { onPreset(it.preset) },
            label = { it.label },
            hasSelection = selected != null,
        )
        Spacer(Modifier.height(Spacing.md))
        QualitySlider(state.quality, onQuality)
        PngQualityNote(state)
    }
}

@Composable
fun FileSizeTab(
    state: ConfigureUiState,
    onTargetText: (String) -> Unit,
    onTargetUnit: (SizeUnit) -> Unit,
    onAllowDownscale: (Boolean) -> Unit,
) {
    Column {
        SectionHeader("Maximum file size", subtitle = "The highest quality that fits is chosen for you")
        Spacer(Modifier.height(Spacing.sm))
        Row(verticalAlignment = Alignment.Top) {
            OutlinedTextField(
                value = state.targetText,
                onValueChange = onTargetText,
                label = { Text("Size") },
                singleLine = true,
                isError = state.targetError != null,
                supportingText = state.targetError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Spacing.sm))
            SegmentedChoice(
                options = SizeUnit.entries,
                selected = state.targetUnit,
                onSelect = onTargetUnit,
                label = { it.label },
                modifier = Modifier.width(128.dp).padding(top = Spacing.xs),
            )
        }
        SwitchRow(
            "Shrink dimensions if needed",
            "Reduces width and height when quality alone can't reach the size",
            state.allowDownscale,
            onAllowDownscale,
        )
        if (state.pngSelected) {
            Spacer(Modifier.height(Spacing.sm))
            Banner(
                if (state.allowDownscale) {
                    "PNG ignores quality, so the size can only be reached by shrinking dimensions."
                } else {
                    "PNG ignores quality. Turn on “Shrink dimensions if needed” or choose JPEG/WebP in output options."
                },
                if (state.allowDownscale) BannerKind.Info else BannerKind.Warning,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManualTab(
    state: ConfigureUiState,
    onQuality: (Int) -> Unit,
    onMode: (ResizeMode) -> Unit,
    onPercent: (Int) -> Unit,
    onPreset: (DimensionPreset) -> Unit,
    onWidth: (String) -> Unit,
    onHeight: (String) -> Unit,
    onAspectLocked: (Boolean) -> Unit,
) {
    Column {
        SectionHeader("Resolution")
        Spacer(Modifier.height(Spacing.sm))
        SegmentedChoice(ResizeMode.entries, state.resizeMode, onMode, { it.label })
        Spacer(Modifier.height(Spacing.sm))
        when (state.resizeMode) {
            ResizeMode.NONE -> Hint("Original resolution will be preserved.")
            ResizeMode.PERCENT -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Scale", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Text("${state.percent}%", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = state.percent.toFloat(),
                    onValueChange = { onPercent(it.toInt()) },
                    valueRange = 10f..100f,
                    steps = 17,
                    modifier = Modifier.semantics {
                        contentDescription = "Scale percentage"
                        stateDescription = "${state.percent} percent"
                    },
                )
            }
            ResizeMode.PRESET -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    DimensionPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = preset == state.preset,
                            onClick = { onPreset(preset) },
                            label = { Text("${preset.label} · ${preset.longEdge}px") },
                        )
                    }
                }
                Hint("Fits the longest side within ${state.preset.longEdge}px. Smaller images are never enlarged.")
            }
            ResizeMode.CUSTOM -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = state.widthText,
                        onValueChange = onWidth,
                        label = { Text("Width") },
                        suffix = { Text("px") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = state.dimensionError != null,
                        modifier = Modifier.weight(1f),
                    )
                    IconToggleButton(checked = state.aspectLocked, onCheckedChange = onAspectLocked) {
                        Icon(
                            if (state.aspectLocked) Icons.Outlined.Link else Icons.Outlined.LinkOff,
                            contentDescription = if (state.aspectLocked) "Aspect ratio locked" else "Aspect ratio unlocked",
                            tint = if (state.aspectLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedTextField(
                        value = state.heightText,
                        onValueChange = onHeight,
                        label = { Text("Height") },
                        suffix = { Text("px") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = state.dimensionError != null,
                        modifier = Modifier.weight(1f),
                    )
                }
                state.dimensionError?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Hint(
                    when {
                        !state.aspectLocked -> "Aspect ratio unlocked: images are stretched to exactly these dimensions."
                        state.isSingle -> "Aspect ratio locked: editing one side updates the other."
                        else -> "Aspect ratio locked: each image is fitted inside these dimensions without stretching."
                    },
                )
            }
        }
        val ref = state.reference
        val preview = state.previewDimensions
        if (ref != null && preview != null && state.resizeMode != ResizeMode.NONE) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                (if (state.isSingle) "" else "First image: ") +
                    "${Formatters.dimensions(ref.width, ref.height)}  →  ${Formatters.dimensions(preview.first, preview.second)}",
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Spacer(Modifier.height(Spacing.lg))
        QualitySlider(state.quality, onQuality)
        PngQualityNote(state)
    }
}

/** Content of the "Output options" bottom sheet: format, transparency background, growth guard. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OutputOptions(
    state: ConfigureUiState,
    onFormat: (FormatOption) -> Unit,
    onBackground: (BackgroundOption) -> Unit,
    onPreventLarger: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader("Output format")
        SegmentedChoice(FormatOption.entries, state.format, onFormat, { it.label })
        Hint(
            when (state.format) {
                FormatOption.ORIGINAL -> "Keeps each image's format. Formats that can't be written (such as HEIC or GIF) are saved as JPEG."
                FormatOption.JPEG -> "Best for photos: small files with adjustable quality. Doesn't support transparency."
                FormatOption.PNG -> "Lossless and supports transparency. Ideal for screenshots and graphics, but photos stay large."
                FormatOption.WEBP -> "Modern format with efficient compression and transparency support. Usually smaller than JPEG at the same quality."
            },
        )
        if (state.format == FormatOption.ORIGINAL && state.resolvedFormats.isNotEmpty()) {
            Hint("Output: " + state.resolvedFormats.joinToString { it.label })
        }
        if (state.showBackgroundChoice) {
            Banner("JPEG doesn't store transparency. Transparent areas will be filled with the colour below.", BannerKind.Info)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                BackgroundOption.entries.forEach { bg ->
                    FilterChip(selected = bg == state.background, onClick = { onBackground(bg) }, label = { Text("${bg.label} background") })
                }
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        SwitchRow(
            "Never make files larger",
            "If the chosen quality would produce a bigger file than the original, the quality is lowered just enough to stay smaller",
            state.preventLarger,
            onPreventLarger,
        )
    }
}
