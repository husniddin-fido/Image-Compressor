package uz.husniddin.imagecompressor.image

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import uz.husniddin.imagecompressor.domain.model.SourceImage
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

/** Reads image metadata without decoding pixels. */
class ImageInspector(
    private val resolver: ContentResolver,
    private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun inspect(uri: Uri): SourceImage = withContext(ioDispatcher) {
        try {
            val (queriedName, queriedSize) = queryNameAndSize(uri)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            open(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                throw UnsupportedImageException("This file isn't an image format your device can read.")
            }
            val orientation = readExifOrientation(resolver, uri)
            val transposed = isTransposed(orientation)
            SourceImage(
                uri = uri,
                displayName = queriedName ?: uri.lastPathSegment ?: "image",
                sizeBytes = queriedSize?.takeIf { it > 0 } ?: countBytes(uri),
                width = if (transposed) bounds.outHeight else bounds.outWidth,
                height = if (transposed) bounds.outWidth else bounds.outHeight,
                mimeType = bounds.outMimeType ?: resolver.getType(uri),
                exifOrientation = orientation,
            )
        } catch (e: ImageProcessingException) {
            throw e
        } catch (e: SecurityException) {
            throw ImageReadException("Permission to read this image was lost. Please select it again.", e)
        } catch (e: FileNotFoundException) {
            throw ImageReadException("The image is no longer available.", e)
        } catch (e: IOException) {
            throw ImageReadException("The image couldn't be read.", e)
        }
    }

    private fun open(uri: Uri): InputStream =
        resolver.openInputStream(uri) ?: throw ImageReadException("The image couldn't be opened.")

    private fun queryNameAndSize(uri: Uri): Pair<String?, Long?> {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            val file = java.io.File(requireNotNull(uri.path))
            return file.name to file.length()
        }
        return try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { c ->
                    if (!c.moveToFirst()) return null to null
                    val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                    val name = if (nameIdx >= 0 && !c.isNull(nameIdx)) c.getString(nameIdx) else null
                    val size = if (sizeIdx >= 0 && !c.isNull(sizeIdx)) c.getLong(sizeIdx) else null
                    name to size
                } ?: (null to null)
        } catch (e: IllegalArgumentException) {
            null to null
        }
    }

    private fun countBytes(uri: Uri): Long = open(uri).use { input ->
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
        }
        total
    }

    companion object {
        fun readExifOrientation(resolver: ContentResolver, uri: Uri): Int = try {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: IOException) {
            // Missing or malformed EXIF is common and not an error: treat as upright.
            ExifInterface.ORIENTATION_NORMAL
        }

        fun isTransposed(orientation: Int): Boolean = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_ROTATE_270,
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_TRANSVERSE -> true
            else -> false
        }
    }
}
