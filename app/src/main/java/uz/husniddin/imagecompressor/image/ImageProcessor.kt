package uz.husniddin.imagecompressor.image

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import uz.husniddin.imagecompressor.domain.model.CompressionMode
import uz.husniddin.imagecompressor.domain.model.OutputFormat
import uz.husniddin.imagecompressor.domain.model.ProcessedImage
import uz.husniddin.imagecompressor.domain.model.ProcessingOptions
import uz.husniddin.imagecompressor.domain.model.SourceImage
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Converts one [SourceImage] into a verified output file.
 *
 * Pipeline: bounds → target size → sampled decode → one Canvas pass (EXIF orientation,
 * exact scaling, optional background flattening) → encode → re-read to verify.
 * At most two full bitmaps are alive at a time; both are recycled before returning.
 */
class ImageProcessor(
    private val resolver: ContentResolver,
    private val dispatcher: CoroutineDispatcher,
) {

    suspend fun process(source: SourceImage, options: ProcessingOptions, outputFile: File): ProcessedImage =
        withContext(dispatcher) {
            val job = coroutineContext[Job]
            val format = options.format.resolve(source.mimeType)
            validate(options)

            val target = try {
                ResizeCalculator.target(source.width, source.height, options.resize)
            } catch (e: IllegalArgumentException) {
                throw ImageProcessingException(e.message ?: "Invalid resize settings.")
            }

            val rendered = render(source, target.width, target.height, format, options.backgroundColor)
            var bitmap = rendered.bitmap
            try {
                job?.ensureActive()
                var quality: Int? = (options.mode as? CompressionMode.Quality)?.quality
                var targetMet: Boolean? = null
                var downscaled = false

                when (val mode = options.mode) {
                    is CompressionMode.Quality -> {
                        val encode = encoder(bitmap, format) { job?.ensureActive() }
                        var bytes = encode(mode.quality)
                        // Re-encoding an already compressed image at a high quality can make it
                        // bigger than the original. Lower the quality just enough to stay below it.
                        val tooBig = bytes.size >= source.sizeBytes
                        if (options.preventLarger && tooBig && format.supportsQuality && mode.quality > ProcessingOptions.MIN_QUALITY) {
                            val search = TargetSizeSearch.search(
                                targetBytes = source.sizeBytes - 1,
                                minQuality = ProcessingOptions.MIN_QUALITY,
                                maxQuality = mode.quality - 1,
                                encode = encode,
                            )
                            if (search.met) {
                                bytes = search.bytes
                                quality = search.quality
                            }
                        }
                        writeFile(outputFile) { it.write(bytes) }
                    }
                    is CompressionMode.TargetSize -> {
                        var attempt = 0
                        while (true) {
                            job?.ensureActive()
                            val result = encodeForTarget(bitmap, format, mode.targetBytes) { job?.ensureActive() }
                            val done = result.met || !mode.allowDownscale || attempt >= MAX_DOWNSCALE_ROUNDS ||
                                max(bitmap.width, bitmap.height) <= MIN_DOWNSCALE_SIDE
                            if (done) {
                                quality = if (format.supportsQuality) result.quality else null
                                targetMet = result.met
                                writeFile(outputFile) { it.write(result.bytes) }
                                break
                            }
                            val factor = ResizeCalculator.downscaleFactorForTarget(result.bytes.size.toLong(), mode.targetBytes)
                            val w = max(1, (bitmap.width * factor).roundToInt())
                            val h = max(1, (bitmap.height * factor).roundToInt())
                            val smaller = Bitmap.createScaledBitmap(bitmap, w, h, true)
                            if (smaller !== bitmap) bitmap.recycle()
                            bitmap = smaller
                            downscaled = true
                            attempt++
                        }
                    }
                }

                val verified = verify(outputFile, format, bitmap.width, bitmap.height)
                ProcessedImage(
                    source = source,
                    outputFile = outputFile,
                    format = format,
                    width = verified.width,
                    height = verified.height,
                    sizeBytes = outputFile.length(),
                    quality = if (format.supportsQuality) quality else null,
                    requestedQuality = (options.mode as? CompressionMode.Quality)?.quality?.takeIf { format.supportsQuality },
                    targetSizeBytes = (options.mode as? CompressionMode.TargetSize)?.targetBytes,
                    targetMet = targetMet,
                    downscaledForTarget = downscaled,
                    dimensionsCapped = target.capped,
                    transparencyFlattened = rendered.flattened,
                    reducedForMemory = rendered.reducedForMemory,
                )
            } catch (e: Throwable) {
                outputFile.delete()
                throw e
            } finally {
                bitmap.recycle()
            }
        }

    private fun validate(options: ProcessingOptions) {
        when (val mode = options.mode) {
            is CompressionMode.Quality -> if (mode.quality !in ProcessingOptions.MIN_QUALITY..ProcessingOptions.MAX_QUALITY) {
                throw ImageProcessingException("Quality must be between ${ProcessingOptions.MIN_QUALITY} and ${ProcessingOptions.MAX_QUALITY}.")
            }
            is CompressionMode.TargetSize -> if (mode.targetBytes < ProcessingOptions.MIN_TARGET_BYTES) {
                throw ImageProcessingException("Target size must be at least ${ProcessingOptions.MIN_TARGET_BYTES / 1024} KB.")
            }
        }
    }

    private class Rendered(val bitmap: Bitmap, val flattened: Boolean, val reducedForMemory: Boolean)

    private fun render(source: SourceImage, targetW: Int, targetH: Int, format: OutputFormat, background: Int): Rendered {
        val orientation = source.exifOrientation
        val transposed = ImageInspector.isTransposed(orientation)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream(source) { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw UnsupportedImageException("This image format can't be decoded on this device.")
        }
        val rawTargetW = if (transposed) targetH else targetW
        val rawTargetH = if (transposed) targetW else targetH
        val sample = ResizeCalculator.sampleSize(bounds.outWidth, bounds.outHeight, rawTargetW, rawTargetH)

        var reducedForMemory = false
        val decoded = try {
            decode(source, sample)
        } catch (oom: OutOfMemoryError) {
            reducedForMemory = true
            try {
                decode(source, sample * 2)
            } catch (oom2: OutOfMemoryError) {
                throw ImageProcessingException("This image is too large to process on this device. Try resizing it to smaller dimensions.")
            }
        }

        val sourceHasAlpha = decoded.hasAlpha()
        val needsFlatten = !format.supportsTransparency && sourceHasAlpha
        val identity = orientation == ExifInterface.ORIENTATION_NORMAL || orientation == ExifInterface.ORIENTATION_UNDEFINED
        if (identity && !needsFlatten && decoded.width == targetW && decoded.height == targetH) {
            return Rendered(decoded, flattened = false, reducedForMemory = reducedForMemory)
        }

        val output = try {
            Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        } catch (oom: OutOfMemoryError) {
            decoded.recycle()
            throw ImageProcessingException("Not enough memory for an output of $targetW × $targetH. Try smaller dimensions.")
        }
        val canvas = Canvas(output)
        if (needsFlatten) canvas.drawColor(background)
        val matrix = orientationMatrix(orientation, decoded.width, decoded.height)
        val orientedW = if (transposed) decoded.height else decoded.width
        val orientedH = if (transposed) decoded.width else decoded.height
        matrix.postScale(targetW.toFloat() / orientedW, targetH.toFloat() / orientedH)
        canvas.drawBitmap(decoded, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG))
        decoded.recycle()
        if (!needsFlatten) output.setHasAlpha(sourceHasAlpha)
        return Rendered(output, flattened = needsFlatten, reducedForMemory = reducedForMemory)
    }

    private fun decode(source: SourceImage, sample: Int): Bitmap {
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return openStream(source) { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw UnsupportedImageException("This image couldn't be decoded. It may be damaged or in an unsupported format.")
    }

    private inline fun <T> openStream(source: SourceImage, block: (java.io.InputStream) -> T): T = try {
        val stream = resolver.openInputStream(source.uri)
            ?: throw ImageReadException("The original image couldn't be opened.")
        stream.use(block)
    } catch (e: SecurityException) {
        throw ImageReadException("Permission to read this image was lost. Please select it again.", e)
    } catch (e: FileNotFoundException) {
        throw ImageReadException("The original image is no longer available.", e)
    } catch (e: IOException) {
        throw ImageReadException("The original image couldn't be read.", e)
    }

    private fun encodeForTarget(
        bitmap: Bitmap,
        format: OutputFormat,
        targetBytes: Long,
        checkCancelled: () -> Unit,
    ): TargetSizeSearch.Result {
        val encode = encoder(bitmap, format, checkCancelled)
        return if (format.supportsQuality) {
            TargetSizeSearch.search(targetBytes, ProcessingOptions.MIN_QUALITY, ProcessingOptions.MAX_QUALITY, encode)
        } else {
            val bytes = encode(ProcessingOptions.MAX_QUALITY)
            TargetSizeSearch.Result(ProcessingOptions.MAX_QUALITY, bytes, bytes.size <= targetBytes, 1)
        }
    }

    /** Encodes [bitmap] in memory so the real size is known before anything is written. */
    private fun encoder(bitmap: Bitmap, format: OutputFormat, checkCancelled: () -> Unit): (Int) -> ByteArray = { q ->
        checkCancelled()
        val out = ByteArrayOutputStream()
        if (!bitmap.compress(compressFormat(format), encoderQuality(format, q), out)) {
            throw ImageWriteException("The ${format.label} encoder failed.")
        }
        out.toByteArray()
    }

    private inline fun writeFile(file: File, block: (java.io.OutputStream) -> Unit) {
        try {
            file.parentFile?.mkdirs()
            BufferedOutputStream(FileOutputStream(file)).use(block)
        } catch (e: IOException) {
            val noSpace = e.message?.contains("ENOSPC") == true || e.message?.contains("No space", ignoreCase = true) == true
            throw ImageWriteException(
                if (noSpace) "Not enough storage space to write the output." else "The output file couldn't be written.",
                e,
            )
        }
    }

    private data class Verified(val width: Int, val height: Int)

    /** Re-reads the written file to prove it decodes with the expected dimensions and format. */
    private fun verify(file: File, format: OutputFormat, expectedW: Int, expectedH: Int): Verified {
        if (!file.exists() || file.length() == 0L) throw ImageWriteException("No output file was created.")
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        val mimeOk = opts.outMimeType == null || opts.outMimeType.equals(format.mimeType, ignoreCase = true)
        if (opts.outWidth != expectedW || opts.outHeight != expectedH || !mimeOk) {
            throw ImageWriteException("The output file failed verification.")
        }
        return Verified(opts.outWidth, opts.outHeight)
    }

    companion object {
        private const val MAX_DOWNSCALE_ROUNDS = 4
        private const val MIN_DOWNSCALE_SIDE = 64

        /** The legacy WebP encoder (API 28–29) switches to lossless at 100, so cap it at 99. */
        fun encoderQuality(format: OutputFormat, quality: Int): Int =
            if (format == OutputFormat.WEBP && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) quality.coerceAtMost(99) else quality

        @Suppress("DEPRECATION")
        fun compressFormat(format: OutputFormat): Bitmap.CompressFormat = when (format) {
            OutputFormat.JPEG -> Bitmap.CompressFormat.JPEG
            OutputFormat.PNG -> Bitmap.CompressFormat.PNG
            // Always lossy: lossless WebP makes photos several times larger, which is never what a
            // compressor should produce. The legacy encoder is kept lossy by encoderQuality().
            OutputFormat.WEBP ->
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP else Bitmap.CompressFormat.WEBP_LOSSY
        }

        /** Maps EXIF orientation to a matrix that draws the raw bitmap upright at the origin. */
        fun orientationMatrix(orientation: Int, w: Int, h: Int): Matrix = Matrix().apply {
            val fw = w.toFloat()
            val fh = h.toFloat()
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> { postScale(-1f, 1f); postTranslate(fw, 0f) }
                ExifInterface.ORIENTATION_ROTATE_180 -> { postRotate(180f); postTranslate(fw, fh) }
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> { postScale(1f, -1f); postTranslate(0f, fh) }
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    postRotate(90f); postTranslate(fh, 0f); postScale(-1f, 1f); postTranslate(fh, 0f)
                }
                ExifInterface.ORIENTATION_ROTATE_90 -> { postRotate(90f); postTranslate(fh, 0f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    postRotate(270f); postTranslate(0f, fw); postScale(-1f, 1f); postTranslate(fh, 0f)
                }
                ExifInterface.ORIENTATION_ROTATE_270 -> { postRotate(270f); postTranslate(0f, fw) }
            }
        }
    }
}
