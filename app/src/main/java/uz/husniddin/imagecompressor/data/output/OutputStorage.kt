package uz.husniddin.imagecompressor.data.output

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import uz.husniddin.imagecompressor.domain.model.OutputFormat
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Temporary, app-private output files (cache/outputs/<batch>/). These back the comparison
 * screen and sharing until the user saves them somewhere permanent.
 */
@Singleton
class OutputStorage @Inject constructor(
    @ApplicationContext context: Context,
) {
    val root: File = File(context.cacheDir, "outputs")

    /** Live previews shown while the user adjusts settings (separate from batch outputs). */
    val previewRoot: File = File(context.cacheDir, "previews")

    fun newPreviewFile(format: OutputFormat): File =
        File(previewRoot.apply { mkdirs() }, "preview_${System.nanoTime()}.${format.extension}")

    fun cleanPreviews() {
        previewRoot.deleteRecursively()
    }

    fun newBatchDirectory(): File =
        File(root, "batch_${System.currentTimeMillis()}").apply { mkdirs() }

    fun outputFileFor(directory: File, sourceName: String, format: OutputFormat): File =
        uniqueFile(directory, "${baseName(sourceName)}_compressed", format.extension)

    /** Deletes every temporary batch. Only touches files this app created in its cache. */
    fun cleanUp() {
        root.listFiles()?.forEach { it.deleteRecursively() }
    }

    /** Deletes batches last modified before [timestamp] (used at startup for orphaned sessions). */
    fun cleanUpOlderThan(timestamp: Long) {
        root.listFiles()?.filter { it.lastModified() < timestamp }?.forEach { it.deleteRecursively() }
    }

    companion object {
        private val ILLEGAL = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")

        fun baseName(fileName: String): String {
            val base = fileName.substringBeforeLast('.', fileName)
            return sanitize(base).ifBlank { "image" }
        }

        fun sanitize(name: String): String = name.replace(ILLEGAL, "_").trim().trim('.').take(100)

        fun uniqueFile(directory: File, base: String, extension: String): File {
            var candidate = File(directory, "$base.$extension")
            var index = 1
            while (candidate.exists()) {
                candidate = File(directory, "$base ($index).$extension")
                index++
            }
            return candidate
        }
    }
}
