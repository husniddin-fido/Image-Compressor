package uz.husniddin.imagecompressor.image

import uz.husniddin.imagecompressor.domain.model.ResizeSpec
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Pure dimension math: no Android dependencies, fully unit tested. */
object ResizeCalculator {
    const val MAX_SIDE = 16_384
    const val MAX_OUTPUT_PIXELS = 40_000_000L
    /** Upper bound for the intermediate decoded bitmap (≈256 MB at ARGB_8888). */
    const val MAX_DECODE_PIXELS = 64_000_000L

    data class Size(val width: Int, val height: Int) {
        val pixels: Long get() = width.toLong() * height
    }

    data class Target(val width: Int, val height: Int, val capped: Boolean)

    /**
     * Computes output dimensions for an image whose (oriented) size is [srcWidth] × [srcHeight].
     * The result is always ≥ 1 px per side and within [MAX_SIDE] / [MAX_OUTPUT_PIXELS];
     * [Target.capped] reports when the safety limits reduced the requested size.
     */
    fun target(srcWidth: Int, srcHeight: Int, spec: ResizeSpec): Target {
        require(srcWidth > 0 && srcHeight > 0) { "Invalid source dimensions" }
        val requested: Pair<Double, Double> = when (spec) {
            ResizeSpec.None -> srcWidth.toDouble() to srcHeight.toDouble()
            is ResizeSpec.Percentage -> {
                require(spec.percent in 1..100) { "Percentage must be between 1 and 100" }
                srcWidth * spec.percent / 100.0 to srcHeight * spec.percent / 100.0
            }
            is ResizeSpec.FitWithin -> {
                require(spec.maxWidth > 0 && spec.maxHeight > 0) { "Width and height must be positive" }
                var scale = min(spec.maxWidth.toDouble() / srcWidth, spec.maxHeight.toDouble() / srcHeight)
                if (!spec.allowUpscale) scale = min(scale, 1.0)
                srcWidth * scale to srcHeight * scale
            }
            is ResizeSpec.Exact -> {
                require(spec.width > 0 && spec.height > 0) { "Width and height must be positive" }
                spec.width.toDouble() to spec.height.toDouble()
            }
        }
        return clamp(requested.first, requested.second)
    }

    private fun clamp(w: Double, h: Double): Target {
        var scale = 1.0
        val longest = max(w, h)
        if (longest > MAX_SIDE) scale = MAX_SIDE / longest
        val pixels = w * h * scale * scale
        if (pixels > MAX_OUTPUT_PIXELS) scale *= sqrt(MAX_OUTPUT_PIXELS / pixels)
        val width = max(1, (w * scale).roundToInt())
        val height = max(1, (h * scale).roundToInt())
        return Target(width, height, capped = scale < 1.0)
    }

    /**
     * Largest power-of-two sample size such that the decoded image is still at least
     * [reqWidth] × [reqHeight] (so we only ever scale *down* afterwards), while keeping the
     * decoded bitmap under [MAX_DECODE_PIXELS].
     */
    fun sampleSize(rawWidth: Int, rawHeight: Int, reqWidth: Int, reqHeight: Int): Int {
        var sample = 1
        while (rawWidth / (sample * 2) >= reqWidth && rawHeight / (sample * 2) >= reqHeight) {
            sample *= 2
        }
        while ((rawWidth.toLong() / sample) * (rawHeight.toLong() / sample) > MAX_DECODE_PIXELS) {
            sample *= 2
        }
        return sample
    }

    /** Keeps aspect ratio when the user edits one side with the lock enabled. */
    fun heightForWidth(srcWidth: Int, srcHeight: Int, width: Int): Int =
        max(1, (width.toDouble() * srcHeight / srcWidth).roundToInt())

    fun widthForHeight(srcWidth: Int, srcHeight: Int, height: Int): Int =
        max(1, (height.toDouble() * srcWidth / srcHeight).roundToInt())

    /** Scale factor used to shrink an image toward a byte target (sizes roughly track pixel count). */
    fun downscaleFactorForTarget(currentBytes: Long, targetBytes: Long): Double =
        (sqrt(targetBytes.toDouble() / currentBytes) * 0.95).coerceIn(0.1, 0.95)
}
