package uz.husniddin.imagecompressor.ui.configure

import android.graphics.Color
import uz.husniddin.imagecompressor.domain.model.CompressionMode
import uz.husniddin.imagecompressor.domain.model.FormatOption
import uz.husniddin.imagecompressor.domain.model.OutputFormat
import uz.husniddin.imagecompressor.domain.model.ProcessingOptions
import uz.husniddin.imagecompressor.domain.model.ResizeSpec
import uz.husniddin.imagecompressor.domain.model.SourceImage
import uz.husniddin.imagecompressor.image.ResizeCalculator

/**
 * The active tab decides how the image is compressed:
 * FAST = quality preset, FILE_SIZE = target size, MANUAL = quality + resize.
 */
enum class CompressionTab(val label: String) { FAST("Fast"), FILE_SIZE("File size"), MANUAL("Manual") }
enum class SizeUnit(val label: String, val bytes: Long) { KB("KB", 1024), MB("MB", 1024 * 1024) }
enum class ResizeMode(val label: String) { NONE("None"), PERCENT("Percent"), PRESET("Preset"), CUSTOM("Custom") }

enum class DimensionPreset(val label: String, val longEdge: Int) {
    UHD("4K", 3840),
    FHD("Full HD", 1920),
    HD("HD", 1280),
    WEB("Web", 1024),
    SMALL("Small", 640),
}

enum class BackgroundOption(val label: String, val color: Int) {
    WHITE("White", Color.WHITE),
    BLACK("Black", Color.BLACK),
}

data class ConfigureUiState(
    val images: List<SourceImage> = emptyList(),
    val tab: CompressionTab = CompressionTab.FAST,
    val quality: Int = 75,
    val preventLarger: Boolean = true,
    val targetText: String = "500",
    val targetUnit: SizeUnit = SizeUnit.KB,
    val allowDownscale: Boolean = true,
    val resizeMode: ResizeMode = ResizeMode.NONE,
    val percent: Int = 50,
    val preset: DimensionPreset = DimensionPreset.FHD,
    val widthText: String = "",
    val heightText: String = "",
    val aspectLocked: Boolean = true,
    val format: FormatOption = FormatOption.ORIGINAL,
    val background: BackgroundOption = BackgroundOption.WHITE,
) {
    val isSingle: Boolean get() = images.size == 1
    val reference: SourceImage? get() = images.firstOrNull()

    val targetBytes: Long?
        get() = targetText.replace(',', '.').toDoubleOrNull()
            ?.takeIf { it > 0 }
            ?.let { (it * targetUnit.bytes).toLong() }

    val targetError: String?
        get() {
            if (tab != CompressionTab.FILE_SIZE) return null
            val bytes = targetBytes ?: return "Enter a size greater than zero"
            return if (bytes < ProcessingOptions.MIN_TARGET_BYTES) "Minimum is ${ProcessingOptions.MIN_TARGET_BYTES / 1024} KB" else null
        }

    private val customWidth: Int? get() = widthText.toIntOrNull()
    private val customHeight: Int? get() = heightText.toIntOrNull()

    val dimensionError: String?
        get() {
            if (tab != CompressionTab.MANUAL || resizeMode != ResizeMode.CUSTOM) return null
            val w = customWidth
            val h = customHeight
            if (w == null || h == null || w <= 0 || h <= 0) return "Enter a width and height"
            if (w > ResizeCalculator.MAX_SIDE || h > ResizeCalculator.MAX_SIDE) {
                return "Maximum is ${ResizeCalculator.MAX_SIDE} px per side"
            }
            return null
        }

    val resizeSpec: ResizeSpec
        get() = when (resizeMode) {
            ResizeMode.NONE -> ResizeSpec.None
            ResizeMode.PERCENT -> ResizeSpec.Percentage(percent)
            ResizeMode.PRESET -> ResizeSpec.FitWithin(preset.longEdge, preset.longEdge, allowUpscale = false)
            ResizeMode.CUSTOM -> {
                val w = customWidth ?: 1
                val h = customHeight ?: 1
                if (aspectLocked) ResizeSpec.FitWithin(w, h, allowUpscale = true) else ResizeSpec.Exact(w, h)
            }
        }

    /** Resizing applies only in the Manual tab. */
    val effectiveResize: ResizeSpec get() = if (tab == CompressionTab.MANUAL) resizeSpec else ResizeSpec.None

    /** Output dimensions for the first image, computed with the same math the engine uses. */
    val previewDimensions: Pair<Int, Int>?
        get() {
            val ref = reference ?: return null
            if (dimensionError != null) return null
            return runCatching { ResizeCalculator.target(ref.width, ref.height, effectiveResize) }
                .getOrNull()?.let { it.width to it.height }
        }

    /** Formats that will be produced for the current selection. */
    val resolvedFormats: Set<OutputFormat> get() = images.map { format.resolve(it.mimeType) }.toSet()

    /** True if any image that may contain transparency will be written as JPEG (and thus flattened). */
    val showBackgroundChoice: Boolean
        get() = images.any { format.resolve(it.mimeType) == OutputFormat.JPEG && mayHaveTransparency(it.mimeType) }

    val pngSelected: Boolean get() = OutputFormat.PNG in resolvedFormats

    val canStart: Boolean get() = images.isNotEmpty() && targetError == null && dimensionError == null

    /** Edits the width; with the aspect lock on, the height follows the first image's ratio. */
    fun withWidthText(text: String): ConfigureUiState {
        val digits = text.filter(Char::isDigit).take(5)
        val ref = reference
        val w = digits.toIntOrNull()
        return if (aspectLocked && ref != null && w != null && w > 0) {
            copy(widthText = digits, heightText = ResizeCalculator.heightForWidth(ref.width, ref.height, w).toString())
        } else {
            copy(widthText = digits)
        }
    }

    fun withHeightText(text: String): ConfigureUiState {
        val digits = text.filter(Char::isDigit).take(5)
        val ref = reference
        val h = digits.toIntOrNull()
        return if (aspectLocked && ref != null && h != null && h > 0) {
            copy(heightText = digits, widthText = ResizeCalculator.widthForHeight(ref.width, ref.height, h).toString())
        } else {
            copy(heightText = digits)
        }
    }

    /** Re-locking snaps the height back to the original ratio for the current width. */
    fun withAspectLocked(locked: Boolean): ConfigureUiState {
        val ref = reference
        val w = widthText.toIntOrNull()
        return if (locked && ref != null && w != null && w > 0) {
            copy(aspectLocked = true, heightText = ResizeCalculator.heightForWidth(ref.width, ref.height, w).toString())
        } else {
            copy(aspectLocked = locked)
        }
    }

    fun toOptions(): ProcessingOptions = ProcessingOptions(
        format = format,
        mode = when (tab) {
            CompressionTab.FAST, CompressionTab.MANUAL -> CompressionMode.Quality(quality)
            CompressionTab.FILE_SIZE -> CompressionMode.TargetSize(targetBytes ?: 0, allowDownscale)
        },
        resize = effectiveResize,
        backgroundColor = background.color,
        preventLarger = preventLarger,
    )
}

/** Input formats that can carry an alpha channel. */
private val ALPHA_CAPABLE = setOf("image/png", "image/webp", "image/gif", "image/heic", "image/heif", "image/avif")

internal fun mayHaveTransparency(mimeType: String?): Boolean = mimeType?.lowercase() in ALPHA_CAPABLE
