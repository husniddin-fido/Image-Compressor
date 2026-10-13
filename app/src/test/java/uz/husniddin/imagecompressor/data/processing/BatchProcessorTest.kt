package uz.husniddin.imagecompressor.data.processing

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uz.husniddin.imagecompressor.data.output.OutputStorage
import uz.husniddin.imagecompressor.domain.model.OutputFormat
import uz.husniddin.imagecompressor.domain.model.ProcessedImage
import uz.husniddin.imagecompressor.domain.model.ProcessingOptions
import uz.husniddin.imagecompressor.domain.model.SourceImage
import uz.husniddin.imagecompressor.image.ImageReadException
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BatchProcessorTest {

    private val dispatcher = StandardTestDispatcher()
    private val testScope = TestScope(dispatcher)
    private val appScope = CoroutineScope(SupervisorJob() + dispatcher)
    private lateinit var storage: OutputStorage

    @Before
    fun setUp() {
        storage = OutputStorage(ApplicationProvider.getApplicationContext())
    }

    private fun image(name: String, size: Long = 1000) = SourceImage(
        uri = Uri.parse("content://test/$name"),
        displayName = "$name.jpg",
        sizeBytes = size,
        width = 100,
        height = 100,
        mimeType = "image/jpeg",
        exifOrientation = 1,
    )

    /** Writes a real file of [outputSize] bytes, like the actual engine would. */
    private fun fakeResult(source: SourceImage, file: File, outputSize: Int): ProcessedImage {
        file.writeBytes(ByteArray(outputSize))
        return ProcessedImage(source, file, OutputFormat.JPEG, 100, 100, file.length(), 75)
    }

    @Test
    fun oneFailure_doesNotStopOtherItems() = testScope.runTest {
        val engine = ImageEngine { src, _, out ->
            if (src.displayName == "b.jpg") throw ImageReadException("The image is no longer available.")
            fakeResult(src, out, 400)
        }
        val processor = BatchProcessor(engine, storage, appScope)
        processor.start(listOf(image("a"), image("b"), image("c")), ProcessingOptions())
        advanceUntilIdle()

        val state = processor.state.value!!
        assertFalse(state.isRunning)
        assertEquals(listOf(ItemStatus.DONE, ItemStatus.FAILED, ItemStatus.DONE), state.items.map { it.status })
        assertEquals("The image is no longer available.", state.items[1].error)
        assertNull(state.items[1].result)
        assertEquals(1f, state.progress)
        assertEquals(1, state.failedCount)
    }

    @Test
    fun unexpectedException_isReportedAsFailure() = testScope.runTest {
        val engine = ImageEngine { _, _, _ -> throw IllegalStateException("boom") }
        val processor = BatchProcessor(engine, storage, appScope)
        processor.start(listOf(image("a")), ProcessingOptions())
        advanceUntilIdle()
        val item = processor.state.value!!.items.single()
        assertEquals(ItemStatus.FAILED, item.status)
        assertTrue(item.error!!.isNotBlank())
    }

    @Test
    fun savings_areComputedFromMeasuredSizes() = testScope.runTest {
        val engine = ImageEngine { src, _, out -> fakeResult(src, out, if (src.displayName == "a.jpg") 250 else 1500) }
        val processor = BatchProcessor(engine, storage, appScope)
        processor.start(listOf(image("a", 1000), image("b", 1000)), ProcessingOptions())
        advanceUntilIdle()
        val state = processor.state.value!!
        assertEquals(2000, state.originalBytes)
        assertEquals(1750, state.outputBytes)
        assertEquals(250, state.savedBytes)
        assertEquals(0.75, state.items[0].result!!.savingsFraction, 1e-9)
        assertEquals(-0.5, state.items[1].result!!.savingsFraction, 1e-9)
        assertFalse(state.items[1].result!!.isSmaller)
    }

    @Test
    fun progress_reflectsFinishedItems() = testScope.runTest {
        val gate = CompletableDeferred<Unit>()
        val engine = ImageEngine { src, _, out ->
            if (src.displayName == "b.jpg") gate.await()
            fakeResult(src, out, 10)
        }
        val processor = BatchProcessor(engine, storage, appScope)
        processor.start(listOf(image("a"), image("b"), image("c"), image("d")), ProcessingOptions())
        advanceUntilIdle()
        val mid = processor.state.value!!
        assertTrue(mid.isRunning)
        assertEquals(1, mid.finishedCount)
        assertEquals(0.25f, mid.progress)
        assertEquals(ItemStatus.PROCESSING, mid.items[1].status)
        assertEquals(ItemStatus.QUEUED, mid.items[2].status)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(1f, processor.state.value!!.progress)
    }

    @Test
    fun cancel_marksRemainingItemsCancelled_neverDone() = testScope.runTest {
        val engine = ImageEngine { src, _, out ->
            if (src.displayName == "b.jpg") awaitCancellation()
            fakeResult(src, out, 10)
        }
        val processor = BatchProcessor(engine, storage, appScope)
        processor.start(listOf(image("a"), image("b"), image("c")), ProcessingOptions())
        advanceUntilIdle()
        processor.cancel()
        advanceUntilIdle()
        val state = processor.state.value!!
        assertFalse(state.isRunning)
        assertEquals(listOf(ItemStatus.DONE, ItemStatus.CANCELLED, ItemStatus.CANCELLED), state.items.map { it.status })
        assertEquals(2, state.retryableCount)
        assertEquals(1, state.succeeded.size)
    }

    @Test
    fun retryFailed_reprocessesOnlyFailedItems() = testScope.runTest {
        val calls = mutableListOf<String>()
        var failB = true
        val engine = ImageEngine { src, _, out ->
            calls += src.displayName
            if (src.displayName == "b.jpg" && failB) throw ImageReadException("temporary")
            fakeResult(src, out, 10)
        }
        val processor = BatchProcessor(engine, storage, appScope)
        processor.start(listOf(image("a"), image("b")), ProcessingOptions())
        advanceUntilIdle()
        failB = false
        processor.retryFailed()
        advanceUntilIdle()
        val state = processor.state.value!!
        assertEquals(listOf(ItemStatus.DONE, ItemStatus.DONE), state.items.map { it.status })
        assertEquals(listOf("a.jpg", "b.jpg", "b.jpg"), calls)
        assertNull(state.items[1].error)
    }

    @Test
    fun restartingBatch_ignoresUpdatesFromPreviousRun() = testScope.runTest {
        val engine = ImageEngine { src, _, out ->
            if (src.displayName == "old.jpg") awaitCancellation()
            fakeResult(src, out, 10)
        }
        val processor = BatchProcessor(engine, storage, appScope)
        processor.start(listOf(image("old")), ProcessingOptions())
        advanceUntilIdle()
        processor.start(listOf(image("new")), ProcessingOptions())
        advanceUntilIdle()
        val state = processor.state.value!!
        assertEquals(listOf("new.jpg"), state.items.map { it.source.displayName })
        assertEquals(ItemStatus.DONE, state.items.single().status)
    }

    @Test
    fun clear_removesSessionAndTemporaryFiles() = testScope.runTest {
        val engine = ImageEngine { src, _, out -> fakeResult(src, out, 10) }
        val processor = BatchProcessor(engine, storage, appScope)
        processor.start(listOf(image("a")), ProcessingOptions())
        advanceUntilIdle()
        val file = processor.state.value!!.items.single().result!!.outputFile
        assertTrue(file.exists())
        processor.clear()
        assertNull(processor.state.value)
        assertFalse(file.exists())
    }
}
