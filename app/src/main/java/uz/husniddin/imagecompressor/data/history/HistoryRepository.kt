package uz.husniddin.imagecompressor.data.history

import android.content.ContentResolver
import androidx.core.net.toUri
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import uz.husniddin.imagecompressor.data.processing.SavedLocation
import uz.husniddin.imagecompressor.di.IoDispatcher
import uz.husniddin.imagecompressor.domain.model.ProcessedImage
import uz.husniddin.imagecompressor.util.Formatters

@Singleton
class HistoryRepository @Inject constructor(
    private val dao: HistoryDao,
    private val resolver: ContentResolver,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    val entries: Flow<List<HistoryEntity>> = dao.observeAll()

    fun recent(limit: Int): Flow<List<HistoryEntity>> = dao.observeRecent(limit)

    suspend fun record(result: ProcessedImage, location: SavedLocation, now: Long = System.currentTimeMillis()): Long =
        dao.insert(
            HistoryEntity(
                originalName = result.source.displayName,
                outputName = location.displayName,
                outputUri = location.uri.toString(),
                locationLabel = location.locationLabel,
                originalSizeBytes = result.originalSizeBytes,
                outputSizeBytes = result.sizeBytes,
                originalWidth = result.source.width,
                originalHeight = result.source.height,
                width = result.width,
                height = result.height,
                formatLabel = result.format.label,
                mimeType = result.format.mimeType,
                settingsSummary = summarize(result),
                createdAt = now,
            ),
        )

    /** Removes only the history entry; the saved image is left untouched. */
    suspend fun delete(id: Long) = dao.delete(id)

    /** Clears the list; saved images and originals are never deleted. */
    suspend fun clear() = dao.clear()

    /** True if the saved file can still be opened (it may have been deleted or moved by the user). */
    suspend fun exists(uri: String): Boolean = withContext(io) {
        try {
            resolver.openFileDescriptor(uri.toUri(), "r")?.use { true } ?: false
        } catch (e: Exception) {
            // FileNotFound, SecurityException (revoked access) and IllegalArgument all mean "unavailable".
            false
        }
    }

    companion object {
        fun summarize(result: ProcessedImage): String = buildList {
            val target = result.targetSizeBytes
            when {
                target != null -> add("Target ${Formatters.bytes(target)}" + if (result.targetMet == false) " (not reached)" else "")
                result.quality != null -> add("Quality ${result.quality}")
                else -> add("Lossless")
            }
            if (result.width != result.source.width || result.height != result.source.height) {
                add("Resized to ${Formatters.dimensions(result.width, result.height)}")
            }
            add(result.format.label)
        }.joinToString(" · ")
    }
}
