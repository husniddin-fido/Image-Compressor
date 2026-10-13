package uz.husniddin.imagecompressor.data.output

import android.util.Log
import kotlinx.coroutines.CancellationException
import uz.husniddin.imagecompressor.data.history.HistoryRepository
import uz.husniddin.imagecompressor.data.processing.BatchItem
import uz.husniddin.imagecompressor.data.processing.BatchProcessor
import uz.husniddin.imagecompressor.data.processing.SavedLocation
import uz.husniddin.imagecompressor.image.ImageProcessingException
import javax.inject.Inject
import javax.inject.Singleton

data class SaveSummary(val saved: List<SavedLocation>, val failures: List<String>)

/** Saves processed results and records the outcome on the batch. */
@Singleton
class ResultSaver @Inject constructor(
    private val saver: ImageSaver,
    private val batchProcessor: BatchProcessor,
    private val history: HistoryRepository,
) {
    suspend fun save(item: BatchItem, destination: SaveDestination, fileName: String? = null): SavedLocation {
        val result = requireNotNull(item.result) { "Item has no output" }
        val name = fileName?.let { withExtension(it, result.format.extension) } ?: result.outputFile.name
        val location = saver.save(result.outputFile, name, result.format.mimeType, destination)
        batchProcessor.markSaved(item.id, location)
        // History is best-effort metadata: a database problem must not turn a real save into a failure.
        try {
            history.record(result, location)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("ResultSaver", "Couldn't record history entry", e)
        }
        return location
    }

    /** Saves each item independently; one failure doesn't stop the rest. */
    suspend fun saveAll(items: List<BatchItem>, destination: SaveDestination): SaveSummary {
        val saved = mutableListOf<SavedLocation>()
        val failures = mutableListOf<String>()
        for (item in items) {
            try {
                saved += save(item, destination)
            } catch (e: ImageProcessingException) {
                failures += "${item.source.displayName}: ${e.message}"
            }
        }
        return SaveSummary(saved, failures)
    }

    companion object {
        fun withExtension(name: String, extension: String): String {
            val clean = OutputStorage.sanitize(name).removeSuffix(".$extension").ifBlank { "image" }
            return "$clean.$extension"
        }
    }
}
