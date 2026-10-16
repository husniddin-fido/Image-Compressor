package uz.husniddin.imagecompressor.data.processing

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uz.husniddin.imagecompressor.data.output.OutputStorage
import uz.husniddin.imagecompressor.di.ApplicationScope
import uz.husniddin.imagecompressor.domain.model.ProcessedImage
import uz.husniddin.imagecompressor.domain.model.ProcessingOptions
import uz.husniddin.imagecompressor.domain.model.SourceImage
import uz.husniddin.imagecompressor.image.ImageProcessingException
import uz.husniddin.imagecompressor.telemetry.Events
import uz.husniddin.imagecompressor.telemetry.Telemetry
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class ItemStatus { QUEUED, PROCESSING, DONE, FAILED, CANCELLED }

/** Where a processed file was permanently saved. */
data class SavedLocation(val uri: android.net.Uri, val displayName: String, val locationLabel: String)

data class BatchItem(
    val source: SourceImage,
    val status: ItemStatus = ItemStatus.QUEUED,
    val result: ProcessedImage? = null,
    val error: String? = null,
    val saved: SavedLocation? = null,
) {
    val id: String get() = source.id
}

data class BatchState(
    val batchId: Long,
    val items: List<BatchItem>,
    val options: ProcessingOptions,
    val isRunning: Boolean,
) {
    val total: Int get() = items.size
    val finishedCount: Int get() = items.count { it.status == ItemStatus.DONE || it.status == ItemStatus.FAILED || it.status == ItemStatus.CANCELLED }
    val succeeded: List<BatchItem> get() = items.filter { it.status == ItemStatus.DONE && it.result != null }
    val failedCount: Int get() = items.count { it.status == ItemStatus.FAILED }
    val cancelledCount: Int get() = items.count { it.status == ItemStatus.CANCELLED }
    val retryableCount: Int get() = failedCount + cancelledCount
    val progress: Float get() = if (total == 0) 0f else finishedCount.toFloat() / total
    val current: BatchItem? get() = items.firstOrNull { it.status == ItemStatus.PROCESSING }

    val originalBytes: Long get() = succeeded.sumOf { it.result!!.originalSizeBytes }
    val outputBytes: Long get() = succeeded.sumOf { it.result!!.sizeBytes }
    val savedBytes: Long get() = originalBytes - outputBytes
}

/**
 * Runs compression jobs in an application-scoped coroutine so work continues across
 * navigation and configuration changes. Items run one at a time to bound memory use;
 * a failing item never stops the rest.
 */
@Singleton
class BatchProcessor @Inject constructor(
    private val engine: ImageEngine,
    private val storage: OutputStorage,
    @ApplicationScope private val scope: CoroutineScope,
    private val telemetry: Telemetry,
) {
    private val _state = MutableStateFlow<BatchState?>(null)
    val state: StateFlow<BatchState?> = _state.asStateFlow()

    private var job: Job? = null
    private var directory: File? = null
    private var nextBatchId = 0L

    fun start(images: List<SourceImage>, options: ProcessingOptions) {
        job?.cancel()
        storage.cleanUp()
        directory = storage.newBatchDirectory()
        _state.value = BatchState(++nextBatchId, images.map { BatchItem(it) }, options, isRunning = true)
        launchRun(images.map { it.id }.toSet())
    }

    /** Re-processes failed and cancelled items with the same options. */
    fun retryFailed() {
        val current = _state.value ?: return
        if (current.isRunning) return
        val ids = current.items.filter { it.status == ItemStatus.FAILED || it.status == ItemStatus.CANCELLED }.map { it.id }.toSet()
        if (ids.isEmpty()) return
        _state.update { s ->
            s?.copy(
                isRunning = true,
                items = s.items.map { if (it.id in ids) it.copy(status = ItemStatus.QUEUED, error = null) else it },
            )
        }
        launchRun(ids)
    }

    fun cancel() {
        job?.cancel()
    }

    fun markSaved(id: String, location: SavedLocation) {
        _state.update { s -> s?.copy(items = s.items.map { if (it.id == id) it.copy(saved = location) else it }) }
    }

    /** Ends the session and deletes its temporary outputs. */
    fun clear() {
        job?.cancel()
        _state.value = null
        storage.cleanUp()
        directory = null
    }

    private fun launchRun(ids: Set<String>) {
        val batch = _state.value ?: return
        val options = batch.options
        val batchId = batch.batchId
        val dir = directory ?: storage.newBatchDirectory().also { directory = it }
        job = scope.launch {
            try {
                for (item in batch.items.filter { it.id in ids }) {
                    processItem(batchId, item, options, dir)
                }
            } finally {
                // Anything not reached (cancellation) is marked as cancelled, never as done.
                _state.update { s ->
                    if (s == null || s.batchId != batchId) return@update s
                    s.copy(
                        isRunning = false,
                        items = s.items.map {
                            if (it.status == ItemStatus.QUEUED || it.status == ItemStatus.PROCESSING) it.copy(status = ItemStatus.CANCELLED) else it
                        },
                    )
                }
                _state.value?.takeIf { it.batchId == batchId }?.let(::reportFinished)
            }
        }
    }

    /** Anonymous summary only: counts and an overall percentage, no names or sizes. */
    private fun reportFinished(state: BatchState) {
        val savedPercent = if (state.originalBytes > 0) (state.savedBytes * 100 / state.originalBytes).toInt() else 0
        telemetry.logEvent(
            Events.COMPRESSION_FINISHED,
            mapOf(
                Events.P_SUCCEEDED to state.succeeded.size,
                Events.P_FAILED to state.failedCount,
                Events.P_CANCELLED to state.cancelledCount,
                Events.P_SAVED_PERCENT to savedPercent,
            ),
        )
    }

    private suspend fun processItem(batchId: Long, item: BatchItem, options: ProcessingOptions, dir: File) {
        updateItem(batchId, item.id) { it.copy(status = ItemStatus.PROCESSING) }
        // A retried item must not keep a stale output from a previous attempt.
        item.result?.outputFile?.delete()
        val format = options.format.resolve(item.source.mimeType)
        val output = storage.outputFileFor(dir, item.source.displayName, format)
        try {
            val result = engine.process(item.source, options, output)
            updateItem(batchId, item.id) { it.copy(status = ItemStatus.DONE, result = result, error = null) }
        } catch (e: CancellationException) {
            updateItem(batchId, item.id) { it.copy(status = ItemStatus.CANCELLED) }
            throw e
        } catch (e: ImageProcessingException) {
            updateItem(batchId, item.id) { it.copy(status = ItemStatus.FAILED, error = e.message) }
        } catch (e: OutOfMemoryError) {
            updateItem(batchId, item.id) { it.copy(status = ItemStatus.FAILED, error = "Not enough memory to process this image. Try resizing it smaller.") }
        } catch (e: Exception) {
            Log.w(TAG, "Unexpected processing failure", e)
            telemetry.recordNonFatal(e)
            updateItem(batchId, item.id) { it.copy(status = ItemStatus.FAILED, error = "Something went wrong while processing this image.") }
        }
    }

    /** Applies [transform] only if [batchId] is still the current batch (stale runs are ignored). */
    private fun updateItem(batchId: Long, id: String, transform: (BatchItem) -> BatchItem) {
        _state.update { s ->
            if (s == null || s.batchId != batchId) s
            else s.copy(items = s.items.map { if (it.id == id) transform(it) else it })
        }
    }

    private companion object {
        const val TAG = "BatchProcessor"
    }
}

/** Seam over [uz.husniddin.imagecompressor.image.ImageProcessor] so batch logic can be unit tested. */
fun interface ImageEngine {
    suspend fun process(source: SourceImage, options: ProcessingOptions, outputFile: File): ProcessedImage
}
