package uz.husniddin.imagecompressor.data.history

import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uz.husniddin.imagecompressor.data.processing.SavedLocation
import uz.husniddin.imagecompressor.domain.model.OutputFormat
import uz.husniddin.imagecompressor.domain.model.ProcessedImage
import uz.husniddin.imagecompressor.domain.model.SourceImage
import java.io.File

@RunWith(RobolectricTestRunner::class)
class HistoryRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()
    private lateinit var db: AppDatabase
    private lateinit var repo: HistoryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = HistoryRepository(db.historyDao(), context.contentResolver, Dispatchers.IO)
    }

    @After
    fun tearDown() = db.close()

    private fun result(output: File, width: Int = 1920, quality: Int? = 75, target: Long? = null, met: Boolean? = null) = ProcessedImage(
        source = SourceImage(Uri.parse("content://src/1"), "photo.jpg", 3_000_000, 4000, 3000, "image/jpeg", 1),
        outputFile = output,
        format = OutputFormat.JPEG,
        width = width,
        height = width * 3 / 4,
        sizeBytes = 500_000,
        quality = quality,
        targetSizeBytes = target,
        targetMet = met,
    )

    @Test
    fun record_storesActualMetadata_newestFirst() = runBlocking {
        val out = tmp.newFile("o.jpg")
        repo.record(result(out), SavedLocation(Uri.fromFile(out), "first.jpg", "Pictures/Image Compressor"), now = 1_000)
        repo.record(result(out), SavedLocation(Uri.fromFile(out), "second.jpg", "Pictures/Image Compressor"), now = 2_000)
        val entries = repo.entries.first()
        assertEquals(listOf("second.jpg", "first.jpg"), entries.map { it.outputName })
        val e = entries.first()
        assertEquals("photo.jpg", e.originalName)
        assertEquals(3_000_000, e.originalSizeBytes)
        assertEquals(500_000, e.outputSizeBytes)
        assertEquals(1920, e.width)
        assertEquals("JPEG", e.formatLabel)
        assertEquals("Quality 75 · Resized to 1920 × 1440 · JPEG", e.settingsSummary)
        assertEquals(1, repo.recent(1).first().size)
    }

    @Test
    fun delete_and_clear_onlyTouchMetadata() = runBlocking {
        val out = tmp.newFile("keep.jpg").apply { writeBytes(ByteArray(10)) }
        val id = repo.record(result(out), SavedLocation(Uri.fromFile(out), "keep.jpg", "Pictures"))
        repo.record(result(out), SavedLocation(Uri.fromFile(out), "keep2.jpg", "Pictures"))
        repo.delete(id)
        assertEquals(1, repo.entries.first().size)
        repo.clear()
        assertTrue(repo.entries.first().isEmpty())
        assertTrue("the saved image must never be deleted", out.exists())
    }

    @Test
    fun exists_detectsMissingFiles() = runBlocking {
        val present = tmp.newFile("present.jpg").apply { writeBytes(ByteArray(10)) }
        val missing = File(tmp.root, "missing.jpg")
        assertTrue(repo.exists(Uri.fromFile(present).toString()))
        assertFalse(repo.exists(Uri.fromFile(missing).toString()))
        assertFalse(repo.exists("content://nonexistent.provider/1"))
    }

    @Test
    fun summary_describesTargetSize() {
        val s = HistoryRepository.summarize(result(File("x"), width = 4000, quality = 40, target = 200 * 1024L, met = false))
        assertEquals("Target 200 KB (not reached) · JPEG", s)
    }
}
