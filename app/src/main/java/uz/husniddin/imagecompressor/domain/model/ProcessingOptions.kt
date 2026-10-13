package uz.husniddin.imagecompressor.domain.model

import android.graphics.Color

/** Formats the app can actually encode with platform APIs. */
enum class OutputFormat(
    val label: String,
    val extension: String,
    val mimeType: String,
    val supportsQuality: Boolean,
    val supportsTransparency: Boolean,
) {
    JPEG("JPEG", "jpg", "image/jpeg", supportsQuality = true, supportsTransparency = false),
    PNG("PNG", "png", "image/png", supportsQuality = false, supportsTransparency = true),
    WEBP("WebP", "webp", "image/webp", supportsQuality = true, supportsTransparency = true);

    companion object {
        fun fromMimeType(mimeType: String?): OutputFormat? = when (mimeType?.lowercase()) {
            "image/jpeg", "image/jpg" -> JPEG
            "image/png" -> PNG
            "image/webp" -> WEBP
            else -> null
        }
    }
}

/** User-facing format choice. [ORIGINAL] keeps the input format when it can be encoded. */
enum class FormatOption(val label: String) {
    ORIGINAL("Original"),
    JPEG("JPEG"),
    PNG("PNG"),
    WEBP("WebP");

    /** Inputs we cannot encode (HEIC, GIF, BMP…) fall back to JPEG. */
    fun resolve(inputMimeType: String?): OutputFormat = when (this) {
        ORIGINAL -> OutputFormat.fromMimeType(inputMimeType) ?: OutputFormat.JPEG
        JPEG -> OutputFormat.JPEG
        PNG -> OutputFormat.PNG
        WEBP -> OutputFormat.WEBP
    }
}

enum class CompressionPreset(val label: String, val quality: Int, val description: String) {
    HIGH_QUALITY("High quality", 90, "Nearly indistinguishable from the original"),
    BALANCED("Balanced", 75, "Good quality with a much smaller file"),
    SMALL_SIZE("Small size", 50, "Smallest files, visible softening possible");

    companion object {
        fun forQuality(quality: Int): CompressionPreset? = entries.firstOrNull { it.quality == quality }
    }
}

sealed interface CompressionMode {
    /** Encode with a fixed quality (10–100). */
    data class Quality(val quality: Int) : CompressionMode

    /**
     * Search for the highest quality whose real encoded size is ≤ [targetBytes].
     * If [allowDownscale] is set, dimensions are reduced when quality alone is not enough.
     */
    data class TargetSize(val targetBytes: Long, val allowDownscale: Boolean) : CompressionMode
}

sealed interface ResizeSpec {
    data object None : ResizeSpec

    /** Scale both sides by [percent] (1–100). */
    data class Percentage(val percent: Int) : ResizeSpec

    /**
     * Keep aspect ratio and fit inside [maxWidth] × [maxHeight].
     * Presets disallow upscaling; custom locked dimensions allow it.
     */
    data class FitWithin(val maxWidth: Int, val maxHeight: Int, val allowUpscale: Boolean) : ResizeSpec

    /** Exact dimensions, aspect ratio NOT preserved (aspect lock off). */
    data class Exact(val width: Int, val height: Int) : ResizeSpec
}

data class ProcessingOptions(
    val format: FormatOption = FormatOption.ORIGINAL,
    val mode: CompressionMode = CompressionMode.Quality(CompressionPreset.BALANCED.quality),
    val resize: ResizeSpec = ResizeSpec.None,
    /** Used when transparent pixels are written to a format without alpha (JPEG). */
    val backgroundColor: Int = Color.WHITE,
    /**
     * In quality mode, if the chosen quality would produce a file at least as large as the
     * original, lower the quality just enough to stay below it (when the format allows).
     */
    val preventLarger: Boolean = true,
) {
    companion object {
        const val MIN_QUALITY = 10
        const val MAX_QUALITY = 100
        const val MIN_TARGET_BYTES = 5L * 1024
    }
}
