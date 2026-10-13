package uz.husniddin.imagecompressor.util

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

object Formatters {
    /** Human readable size using 1024-based units, e.g. "1.4 MB". */
    fun bytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, if (kb < 10) "%.1f KB" else "%.0f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, if (mb < 100) "%.2f MB" else "%.1f MB", mb)
        return String.format(Locale.US, "%.2f GB", mb / 1024.0)
    }

    fun dimensions(width: Int, height: Int): String = "$width × $height"

    /** Formats a ratio change as a whole percentage, e.g. "-63%" or "+12%". */
    fun percentChange(original: Long, output: Long): String {
        if (original <= 0) return "—"
        val change = (output - original) * 100.0 / original
        val rounded = change.roundToInt()
        return when {
            rounded == 0 && change != 0.0 -> if (change < 0) "<1% smaller" else "<1% larger"
            rounded < 0 -> "${abs(rounded)}% smaller"
            rounded > 0 -> "$rounded% larger"
            else -> "Same size"
        }
    }

    fun megapixels(width: Int, height: Int): String =
        String.format(Locale.US, "%.1f MP", width.toLong() * height / 1_000_000.0)
}
