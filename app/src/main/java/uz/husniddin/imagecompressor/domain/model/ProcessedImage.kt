package uz.husniddin.imagecompressor.domain.model

import java.io.File

/** A verified output file produced from a [SourceImage]. All sizes are measured, not estimated. */
data class ProcessedImage(
    val source: SourceImage,
    val outputFile: File,
    val format: OutputFormat,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    /** Quality passed to the encoder, or null for formats that ignore it (PNG). */
    val quality: Int?,
    /** Quality the user asked for; differs from [quality] when it was lowered to avoid growth. */
    val requestedQuality: Int? = null,
    val targetSizeBytes: Long? = null,
    val targetMet: Boolean? = null,
    val downscaledForTarget: Boolean = false,
    val dimensionsCapped: Boolean = false,
    val transparencyFlattened: Boolean = false,
    val reducedForMemory: Boolean = false,
) {
    val originalSizeBytes: Long get() = source.sizeBytes
    val savedBytes: Long get() = originalSizeBytes - sizeBytes
    val isSmaller: Boolean get() = sizeBytes < originalSizeBytes
    val qualityLoweredToAvoidGrowth: Boolean
        get() = quality != null && requestedQuality != null && quality < requestedQuality

    /** Fraction of the original size saved; negative if the output is larger. */
    val savingsFraction: Double
        get() = if (originalSizeBytes <= 0) 0.0 else savedBytes.toDouble() / originalSizeBytes
}
