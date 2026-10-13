package uz.husniddin.imagecompressor.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uz.husniddin.imagecompressor.domain.model.CompressionMode
import uz.husniddin.imagecompressor.domain.model.FormatOption
import uz.husniddin.imagecompressor.domain.model.OutputFormat
import uz.husniddin.imagecompressor.domain.model.ProcessingOptions
import uz.husniddin.imagecompressor.domain.model.ResizeSpec
import uz.husniddin.imagecompressor.domain.model.SourceImage
import java.io.File

/** Runs the real platform encoders/decoders on a device or emulator. */
@RunWith(AndroidJUnit4::class)
class ImageProcessorTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver = context.contentResolver
    private val inspector = ImageInspector(resolver, Dispatchers.IO)
    private val processor = ImageProcessor(resolver, Dispatchers.Default)
    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = File(context.cacheDir, "processor-test").apply { deleteRecursively(); mkdirs() }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun source(file: File): SourceImage = runBlocking { inspector.inspect(Uri.fromFile(file)) }

    private fun photoJpeg(name: String = "photo.jpg", w: Int = 1600, h: Int = 1200): SourceImage {
        val bmp = TestImages.photo(w, h)
        val file = TestImages.write(bmp, File(dir, name), Bitmap.CompressFormat.JPEG, 98)
        bmp.recycle()
        return source(file)
    }

    private fun out(name: String) = File(dir, name)

    private fun bounds(file: File) = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
        BitmapFactory.decodeFile(file.absolutePath, this)
    }

    private fun process(src: SourceImage, options: ProcessingOptions, name: String) =
        runBlocking { processor.process(src, options, out(name)) }

    // ---- Compression ----

    @Test
    fun jpeg_outputIsReadable_andSizeIsMeasured() {
        val src = photoJpeg()
        val result = process(src, ProcessingOptions(FormatOption.JPEG, CompressionMode.Quality(70)), "a.jpg")
        assertTrue(result.outputFile.exists())
        assertEquals(result.outputFile.length(), result.sizeBytes)
        assertEquals(src.sizeBytes, result.originalSizeBytes)
        val b = bounds(result.outputFile)
        assertEquals("image/jpeg", b.outMimeType)
        assertEquals(1600, b.outWidth)
        assertEquals(1200, b.outHeight)
        assertTrue("q70 output should be smaller than the q98 original", result.isSmaller)
        val decoded = BitmapFactory.decodeFile(result.outputFile.absolutePath)
        assertNotNull(decoded)
    }

    @Test
    fun jpeg_lowerQuality_producesSmallerFile() {
        val src = photoJpeg()
        val high = process(src, ProcessingOptions(FormatOption.JPEG, CompressionMode.Quality(90)), "high.jpg")
        val low = process(src, ProcessingOptions(FormatOption.JPEG, CompressionMode.Quality(30)), "low.jpg")
        assertTrue("q30 (${low.sizeBytes}) < q90 (${high.sizeBytes})", low.sizeBytes < high.sizeBytes)
        assertEquals(90, high.quality)
        assertEquals(30, low.quality)
    }

    @Test
    fun webp_lowerQuality_producesSmallerFile_andCorrectMime() {
        val src = photoJpeg()
        val high = process(src, ProcessingOptions(FormatOption.WEBP, CompressionMode.Quality(90)), "high.webp")
        val low = process(src, ProcessingOptions(FormatOption.WEBP, CompressionMode.Quality(30)), "low.webp")
        assertTrue(low.sizeBytes < high.sizeBytes)
        assertEquals("image/webp", bounds(low.outputFile).outMimeType)
        assertEquals(OutputFormat.WEBP, low.format)
    }

    @Test
    fun png_ignoresQuality() {
        val src = photoJpeg(w = 400, h = 300)
        val a = process(src, ProcessingOptions(FormatOption.PNG, CompressionMode.Quality(90)), "a.png")
        val b = process(src, ProcessingOptions(FormatOption.PNG, CompressionMode.Quality(20)), "b.png")
        assertEquals("image/png", bounds(a.outputFile).outMimeType)
        assertEquals(a.sizeBytes, b.sizeBytes)
        assertNull(a.quality)
    }

    @Test
    fun originalFormat_keepsInputFormat() {
        val src = photoJpeg()
        val r = process(src, ProcessingOptions(FormatOption.ORIGINAL, CompressionMode.Quality(60)), "o.jpg")
        assertEquals(OutputFormat.JPEG, r.format)
    }

    @Test
    fun originalIsNeverModified() {
        val src = photoJpeg()
        val file = File(requireNotNull(src.uri.path))
        val before = file.readBytes()
        process(src, ProcessingOptions(FormatOption.JPEG, CompressionMode.Quality(40)), "x.jpg")
        assertTrue(before.contentEquals(file.readBytes()))
    }

    // ---- Maximum quality / never larger ----

    /** A typical phone photo: already JPEG-compressed at quality 85. */
    private fun compressedPhoto(): SourceImage {
        val bmp = TestImages.photo(1600, 1200)
        val file = TestImages.write(bmp, File(dir, "phone.jpg"), Bitmap.CompressFormat.JPEG, 85)
        bmp.recycle()
        return source(file)
    }

    @Test
    fun quality100_reencodingACompressedJpeg_growsWhenGuardIsOff() {
        val src = compressedPhoto()
        val r = process(src, ProcessingOptions(FormatOption.JPEG, CompressionMode.Quality(100), preventLarger = false), "q100.jpg")
        Log.i("SizeCheck", "JPEG q85 source ${src.sizeBytes} B -> q100 ${r.sizeBytes} B (x%.2f)".format(r.sizeBytes.toDouble() / src.sizeBytes))
        assertTrue("q100 re-encode is expected to grow", r.sizeBytes > src.sizeBytes)
        assertEquals(100, r.quality)
        assertFalse(r.qualityLoweredToAvoidGrowth)
    }

    @Test
    fun quality100_withGuard_isNeverLargerThanOriginal() {
        val src = compressedPhoto()
        val r = process(src, ProcessingOptions(FormatOption.JPEG, CompressionMode.Quality(100)), "guard.jpg")
        Log.i("SizeCheck", "JPEG q85 source ${src.sizeBytes} B -> guarded ${r.sizeBytes} B at q${r.quality}")
        assertTrue("${r.sizeBytes} < ${src.sizeBytes}", r.sizeBytes < src.sizeBytes)
        assertEquals(r.outputFile.length(), r.sizeBytes)
        assertEquals(100, r.requestedQuality)
        assertTrue(r.quality!! < 100)
        assertTrue(r.qualityLoweredToAvoidGrowth)
        assertEquals("image/jpeg", bounds(r.outputFile).outMimeType)
    }

    @Test
    fun guard_keepsChosenQuality_whenOutputIsAlreadySmaller() {
        val src = compressedPhoto()
        val r = process(src, ProcessingOptions(FormatOption.JPEG, CompressionMode.Quality(50)), "q50.jpg")
        assertEquals(50, r.quality)
        assertFalse(r.qualityLoweredToAvoidGrowth)
    }

    @Test
    fun webpQuality100_staysLossy_andFarSmallerThanLossless() {
        val src = compressedPhoto()
        val r = process(src, ProcessingOptions(FormatOption.WEBP, CompressionMode.Quality(100), preventLarger = false), "q100.webp")
        val decoded = BitmapFactory.decodeFile(src.uri.path)
        val lossless = java.io.ByteArrayOutputStream().also { decoded.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, it) }.size()
        Log.i("SizeCheck", "WebP q100 lossy ${r.sizeBytes} B vs lossless $lossless B (source ${src.sizeBytes} B)")
        assertTrue("lossy ${r.sizeBytes} should be < half of lossless $lossless", r.sizeBytes < lossless / 2)
    }

    // ---- Target size ----

    @Test
    fun targetSize_reachedByQuality() {
        val src = photoJpeg()
        val target = 120L * 1024
        val r = process(src, ProcessingOptions(FormatOption.JPEG, CompressionMode.TargetSize(target, false)), "t.jpg")
        assertEquals(true, r.targetMet)
        assertTrue("${r.sizeBytes} <= $target", r.sizeBytes <= target)
        assertEquals(r.outputFile.length(), r.sizeBytes)
        assertNotNull(r.quality)
        assertEquals(1600, r.width) // no downscale needed
    }

    @Test
    fun targetSize_unreachable_withoutDownscale_isReportedHonestly() {
        val src = photoJpeg()
        val target = 8L * 1024
        val r = process(src, ProcessingOptions(FormatOption.JPEG, CompressionMode.TargetSize(target, false)), "t.jpg")
        assertEquals(false, r.targetMet)
        assertTrue(r.sizeBytes > target)
        assertEquals(10, r.quality)
        assertFalse(r.downscaledForTarget)
    }

    @Test
    fun targetSize_unreachable_withDownscale_shrinksDimensions() {
        val src = photoJpeg()
        val target = 20L * 1024
        val r = process(src, ProcessingOptions(FormatOption.JPEG, CompressionMode.TargetSize(target, true)), "t.jpg")
        assertEquals(true, r.targetMet)
        assertTrue(r.sizeBytes <= target)
        assertTrue(r.downscaledForTarget)
        assertTrue(r.width < 1600)
        val b = bounds(r.outputFile)
        assertEquals(r.width, b.outWidth)
        assertEquals(r.height, b.outHeight)
        assertEquals(1600.0 / 1200, r.width.toDouble() / r.height, 0.02)
    }

    @Test
    fun targetSize_png_usesDownscaling() {
        val src = photoJpeg(w = 800, h = 600)
        val target = 150L * 1024
        val r = process(src, ProcessingOptions(FormatOption.PNG, CompressionMode.TargetSize(target, true)), "t.png")
        assertEquals(true, r.targetMet)
        assertTrue(r.sizeBytes <= target)
        assertTrue(r.downscaledForTarget)
    }

    // ---- Resize ----

    @Test
    fun resize_percentage() {
        val src = photoJpeg()
        val r = process(src, ProcessingOptions(FormatOption.JPEG, resize = ResizeSpec.Percentage(50)), "r.jpg")
        assertEquals(800, r.width)
        assertEquals(600, r.height)
        assertEquals(800, bounds(r.outputFile).outWidth)
    }

    @Test
    fun resize_fitWithin_preservesAspect() {
        val src = photoJpeg()
        val r = process(src, ProcessingOptions(FormatOption.JPEG, resize = ResizeSpec.FitWithin(1000, 1000, false)), "r.jpg")
        assertEquals(1000, r.width)
        assertEquals(750, r.height)
    }

    @Test
    fun resize_exact_unlockedAspect() {
        val src = photoJpeg()
        val r = process(src, ProcessingOptions(FormatOption.JPEG, resize = ResizeSpec.Exact(500, 500)), "r.jpg")
        val b = bounds(r.outputFile)
        assertEquals(500, b.outWidth)
        assertEquals(500, b.outHeight)
    }

    @Test
    fun resize_invalid_isRejectedWithoutCrash() {
        val src = photoJpeg()
        try {
            process(src, ProcessingOptions(FormatOption.JPEG, resize = ResizeSpec.Exact(0, 100)), "r.jpg")
            fail("expected exception")
        } catch (e: ImageProcessingException) {
            assertFalse(out("r.jpg").exists())
        }
    }

    // ---- EXIF orientation ----

    @Test
    fun exifRotate90_isBakedIntoPixels() {
        val file = TestImages.jpegWithOrientation(File(dir, "rot.jpg"), 400, 200, ExifInterface.ORIENTATION_ROTATE_90)
        val src = source(file)
        assertEquals(200, src.width)
        assertEquals(400, src.height)
        val r = process(src, ProcessingOptions(FormatOption.PNG), "rot.png")
        val bmp = BitmapFactory.decodeFile(r.outputFile.absolutePath)
        assertEquals(200, bmp.width)
        assertEquals(400, bmp.height)
        // Raw top-left red marker must end up at the top-RIGHT after a 90° clockwise rotation.
        assertTrue(isRed(bmp.getPixel(bmp.width - 10, 10)))
        assertFalse(isRed(bmp.getPixel(10, 10)))
        // Output carries no EXIF orientation (pixels are already upright).
        val outExif = ExifInterface(r.outputFile.absolutePath)
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
        assertTrue(outExif == ExifInterface.ORIENTATION_UNDEFINED || outExif == ExifInterface.ORIENTATION_NORMAL)
    }

    @Test
    fun exifRotate270_andFlip() {
        val f270 = TestImages.jpegWithOrientation(File(dir, "r270.jpg"), 400, 200, ExifInterface.ORIENTATION_ROTATE_270)
        val r270 = BitmapFactory.decodeFile(process(source(f270), ProcessingOptions(FormatOption.PNG), "r270.png").outputFile.absolutePath)
        assertTrue("marker bottom-left", isRed(r270.getPixel(10, r270.height - 10)))

        val flip = TestImages.jpegWithOrientation(File(dir, "flip.jpg"), 400, 200, ExifInterface.ORIENTATION_FLIP_HORIZONTAL)
        val rf = BitmapFactory.decodeFile(process(source(flip), ProcessingOptions(FormatOption.PNG), "flip.png").outputFile.absolutePath)
        assertEquals(400, rf.width)
        assertTrue("marker top-right", isRed(rf.getPixel(rf.width - 10, 10)))

        val r180 = TestImages.jpegWithOrientation(File(dir, "r180.jpg"), 400, 200, ExifInterface.ORIENTATION_ROTATE_180)
        val b180 = BitmapFactory.decodeFile(process(source(r180), ProcessingOptions(FormatOption.PNG), "r180.png").outputFile.absolutePath)
        assertTrue("marker bottom-right", isRed(b180.getPixel(b180.width - 10, b180.height - 10)))
    }

    @Test
    fun exifRotation_withResize_usesOrientedDimensions() {
        val file = TestImages.jpegWithOrientation(File(dir, "rot.jpg"), 800, 400, ExifInterface.ORIENTATION_ROTATE_90)
        val r = process(source(file), ProcessingOptions(FormatOption.JPEG, resize = ResizeSpec.Percentage(50)), "rot.jpg")
        assertEquals(200, r.width)
        assertEquals(400, r.height)
    }

    // ---- Transparency ----

    @Test
    fun transparentToJpeg_isFlattenedOnChosenBackground() {
        val file = TestImages.write(TestImages.transparent(200), File(dir, "t.png"), Bitmap.CompressFormat.PNG)
        val r = process(source(file), ProcessingOptions(FormatOption.JPEG, backgroundColor = Color.BLACK), "t.jpg")
        assertTrue(r.transparencyFlattened)
        val bmp = BitmapFactory.decodeFile(r.outputFile.absolutePath)
        val corner = bmp.getPixel(5, 5)
        assertTrue("corner should be black", Color.red(corner) < 20 && Color.green(corner) < 20 && Color.blue(corner) < 20)
        val centre = bmp.getPixel(100, 100)
        assertTrue("centre stays blue", Color.blue(centre) > 200 && Color.red(centre) < 60)
    }

    @Test
    fun transparentToPngAndWebp_keepsAlpha() {
        val file = TestImages.write(TestImages.transparent(200), File(dir, "t.png"), Bitmap.CompressFormat.PNG)
        val src = source(file)
        for ((opt, name) in listOf(FormatOption.PNG to "o.png", FormatOption.WEBP to "o.webp")) {
            val r = process(src, ProcessingOptions(opt, CompressionMode.Quality(80)), name)
            assertFalse(r.transparencyFlattened)
            val bmp = BitmapFactory.decodeFile(r.outputFile.absolutePath)
            assertEquals("$name corner alpha", 0, Color.alpha(bmp.getPixel(5, 5)))
            assertEquals("$name centre alpha", 255, Color.alpha(bmp.getPixel(100, 100)))
        }
    }

    // ---- Errors & cancellation ----

    @Test
    fun corruptFile_reportsUnsupported() {
        val file = File(dir, "bad.jpg").apply { writeText("not an image".repeat(50)) }
        try {
            source(file)
            fail("expected exception")
        } catch (e: UnsupportedImageException) {
            // expected
        }
    }

    @Test
    fun missingOriginal_reportsReadError_andLeavesNoOutput() {
        val src = photoJpeg()
        File(requireNotNull(src.uri.path)).delete()
        try {
            process(src, ProcessingOptions(), "m.jpg")
            fail("expected exception")
        } catch (e: ImageReadException) {
            assertFalse(out("m.jpg").exists())
        }
    }

    @Test
    fun unwritableOutput_reportsWriteError() {
        val src = photoJpeg()
        val blocker = File(dir, "blocker").apply { writeText("x") } // a file, so it can't be a directory
        try {
            runBlocking { processor.process(src, ProcessingOptions(), File(blocker, "o.jpg")) }
            fail("expected exception")
        } catch (e: ImageWriteException) {
            // expected
        }
    }

    @Test
    fun invalidQuality_isRejected() {
        val src = photoJpeg()
        try {
            process(src, ProcessingOptions(mode = CompressionMode.Quality(5)), "q.jpg")
            fail("expected exception")
        } catch (e: ImageProcessingException) {
            // expected
        }
    }

    @Test
    fun cancellation_stopsWork_andRemovesPartialOutput() = runBlocking {
        val src = photoJpeg(w = 3000, h = 2000)
        val job = async(Dispatchers.Default) {
            processor.process(src, ProcessingOptions(FormatOption.JPEG, CompressionMode.TargetSize(10L * 1024, true)), out("c.jpg"))
        }
        yield()
        job.cancel()
        try {
            job.await()
        } catch (e: CancellationException) {
            // expected
        }
        assertTrue(job.isCancelled)
        assertFalse(out("c.jpg").exists())
    }

    private fun isRed(c: Int) = Color.red(c) > 200 && Color.green(c) < 60 && Color.blue(c) < 60
}
