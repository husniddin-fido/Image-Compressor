package uz.husniddin.imagecompressor.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import uz.husniddin.imagecompressor.data.output.OutputStorage
import uz.husniddin.imagecompressor.data.output.ResultSaver
import uz.husniddin.imagecompressor.domain.model.FormatOption
import uz.husniddin.imagecompressor.domain.model.OutputFormat
import uz.husniddin.imagecompressor.domain.model.formatLabelFor
import java.nio.file.Files

class FormatOptionTest {

    @Test
    fun original_keepsEncodableFormats_andFallsBackToJpeg() {
        assertEquals(OutputFormat.JPEG, FormatOption.ORIGINAL.resolve("image/jpeg"))
        assertEquals(OutputFormat.PNG, FormatOption.ORIGINAL.resolve("image/png"))
        assertEquals(OutputFormat.WEBP, FormatOption.ORIGINAL.resolve("image/webp"))
        assertEquals(OutputFormat.JPEG, FormatOption.ORIGINAL.resolve("image/heic"))
        assertEquals(OutputFormat.JPEG, FormatOption.ORIGINAL.resolve("image/gif"))
        assertEquals(OutputFormat.JPEG, FormatOption.ORIGINAL.resolve(null))
    }

    @Test
    fun explicitFormats_ignoreInput() {
        assertEquals(OutputFormat.PNG, FormatOption.PNG.resolve("image/jpeg"))
        assertEquals(OutputFormat.WEBP, FormatOption.WEBP.resolve("image/png"))
    }

    @Test
    fun formatLabels() {
        assertEquals("HEIC", formatLabelFor("image/heif"))
        assertEquals("TIFF", formatLabelFor(null, "scan.tiff"))
        assertEquals("Image", formatLabelFor(null, "noext"))
    }

    @Test
    fun fileNames_areSanitised_andNeverOverwrite() {
        assertEquals("my_photo_1", OutputStorage.baseName("my/photo:1.jpg"))
        assertEquals("image", OutputStorage.baseName("...."))
        assertEquals("holiday.jpg", ResultSaver.withExtension("holiday", "jpg"))
        assertEquals("holiday.jpg", ResultSaver.withExtension("holiday.jpg", "jpg"))
        assertEquals("a_b.webp", ResultSaver.withExtension("a?b", "webp"))

        val dir = Files.createTempDirectory("names").toFile()
        val first = OutputStorage.uniqueFile(dir, "x", "jpg").apply { writeText("1") }
        val second = OutputStorage.uniqueFile(dir, "x", "jpg")
        assertEquals("x.jpg", first.name)
        assertEquals("x (1).jpg", second.name)
        dir.deleteRecursively()
    }
}
