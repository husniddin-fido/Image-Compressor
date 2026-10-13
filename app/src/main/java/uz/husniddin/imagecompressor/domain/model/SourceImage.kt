package uz.husniddin.imagecompressor.domain.model

import android.net.Uri

/**
 * A user-selected input image. [width]/[height] are the *displayed* dimensions,
 * i.e. after applying the EXIF orientation.
 */
data class SourceImage(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val mimeType: String?,
    val exifOrientation: Int,
) {
    val id: String get() = uri.toString()
    val formatLabel: String get() = formatLabelFor(mimeType, displayName)
}

fun formatLabelFor(mimeType: String?, fileName: String? = null): String = when (mimeType?.lowercase()) {
    "image/jpeg", "image/jpg" -> "JPEG"
    "image/png" -> "PNG"
    "image/webp" -> "WebP"
    "image/heic", "image/heif" -> "HEIC"
    "image/avif" -> "AVIF"
    "image/gif" -> "GIF"
    "image/bmp", "image/x-ms-bmp" -> "BMP"
    else -> fileName?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() }?.uppercase() ?: "Image"
}
