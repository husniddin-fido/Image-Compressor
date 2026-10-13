package uz.husniddin.imagecompressor.ui.configure

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uz.husniddin.imagecompressor.domain.model.CompressionMode
import uz.husniddin.imagecompressor.domain.model.FormatOption
import uz.husniddin.imagecompressor.domain.model.ResizeSpec
import uz.husniddin.imagecompressor.domain.model.SourceImage

@RunWith(RobolectricTestRunner::class)
class ConfigureUiStateTest {

    private fun image(w: Int, h: Int, mime: String = "image/jpeg", name: String = "a") =
        SourceImage(Uri.parse("content://t/$name"), "$name.jpg", 1000, w, h, mime, 1)

    private val photo = ConfigureUiState(images = listOf(image(4000, 3000)), widthText = "4000", heightText = "3000")

    @Test
    fun lockedAspect_widthDrivesHeight_andViceVersa() {
        val s = photo.withWidthText("2000")
        assertEquals("1500", s.heightText)
        val t = s.withHeightText("600")
        assertEquals("800", t.widthText)
    }

    @Test
    fun unlockedAspect_sidesAreIndependent() {
        val s = photo.withAspectLocked(false).withWidthText("500")
        assertEquals("500", s.widthText)
        assertEquals("3000", s.heightText)
        assertEquals(ResizeSpec.Exact(500, 3000), s.copy(resizeMode = ResizeMode.CUSTOM).resizeSpec)
    }

    @Test
    fun relocking_snapsHeightBackToRatio() {
        val s = photo.withAspectLocked(false).withWidthText("1000").withHeightText("999").withAspectLocked(true)
        assertEquals("750", s.heightText)
    }

    @Test
    fun dimensionInput_acceptsDigitsOnly_andRejectsInvalidValues() {
        val custom = photo.copy(tab = CompressionTab.MANUAL, resizeMode = ResizeMode.CUSTOM)
        assertEquals("12", custom.withWidthText("1a2-").widthText)
        assertNotNull(custom.withAspectLocked(false).withWidthText("0").dimensionError)
        assertNotNull(custom.withAspectLocked(false).withWidthText("").dimensionError)
        assertNotNull(custom.withAspectLocked(false).withWidthText("20000").dimensionError)
        assertFalse(custom.withAspectLocked(false).withWidthText("0").canStart)
        assertNull(custom.withWidthText("1920").dimensionError)
    }

    @Test
    fun targetSize_validationAndUnits() {
        val t = photo.copy(tab = CompressionTab.FILE_SIZE, targetText = "1.5", targetUnit = SizeUnit.MB)
        assertEquals((1.5 * 1024 * 1024).toLong(), t.targetBytes)
        assertNull(t.targetError)
        assertNotNull(t.copy(targetText = "").targetError)
        assertNotNull(t.copy(targetText = "4", targetUnit = SizeUnit.KB).targetError)
        assertFalse(t.copy(targetText = "0").canStart)
        // Target errors don't block quality mode.
        assertTrue(t.copy(tab = CompressionTab.FAST, targetText = "").canStart)
    }

    @Test
    fun toOptions_mapsUiChoicesToEngineOptions() {
        val quality = photo.copy(quality = 65, format = FormatOption.WEBP).toOptions()
        assertEquals(CompressionMode.Quality(65), quality.mode)
        assertEquals(FormatOption.WEBP, quality.format)
        assertEquals(ResizeSpec.None, quality.resize)
        assertTrue("files must not grow by default", quality.preventLarger)
        assertFalse(photo.copy(preventLarger = false).toOptions().preventLarger)

        val target = photo.copy(tab = CompressionTab.FILE_SIZE, targetText = "200", allowDownscale = false).toOptions()
        assertEquals(CompressionMode.TargetSize(200 * 1024, false), target.mode)

        assertEquals(ResizeSpec.Percentage(40), photo.copy(resizeMode = ResizeMode.PERCENT, percent = 40).resizeSpec)
        assertEquals(
            ResizeSpec.FitWithin(1280, 1280, allowUpscale = false),
            photo.copy(resizeMode = ResizeMode.PRESET, preset = DimensionPreset.HD).resizeSpec,
        )
        assertEquals(
            ResizeSpec.FitWithin(2000, 1500, allowUpscale = true),
            photo.copy(resizeMode = ResizeMode.CUSTOM).withWidthText("2000").resizeSpec,
        )
    }

    @Test
    fun resize_appliesOnlyInManualTab() {
        val preset = photo.copy(resizeMode = ResizeMode.PRESET, preset = DimensionPreset.HD)
        assertEquals(ResizeSpec.None, preset.copy(tab = CompressionTab.FAST).toOptions().resize)
        assertEquals(ResizeSpec.None, preset.copy(tab = CompressionTab.FILE_SIZE).toOptions().resize)
        assertEquals(ResizeSpec.FitWithin(1280, 1280, false), preset.copy(tab = CompressionTab.MANUAL).toOptions().resize)
        // An invalid custom size in Manual must not block the other tabs.
        val badCustom = photo.copy(resizeMode = ResizeMode.CUSTOM, widthText = "0").withAspectLocked(false)
        assertTrue(badCustom.copy(tab = CompressionTab.FAST).canStart)
        assertFalse(badCustom.copy(tab = CompressionTab.MANUAL).canStart)
    }

    @Test
    fun manualTab_usesQualityMode() {
        assertEquals(CompressionMode.Quality(80), photo.copy(tab = CompressionTab.MANUAL, quality = 80).toOptions().mode)
    }

    @Test
    fun previewDimensions_useEngineMath() {
        assertEquals(1920 to 1440, photo.copy(tab = CompressionTab.MANUAL, resizeMode = ResizeMode.PRESET, preset = DimensionPreset.FHD).previewDimensions)
        assertNull(photo.copy(tab = CompressionTab.MANUAL, resizeMode = ResizeMode.CUSTOM, widthText = "", heightText = "").previewDimensions)
    }

    @Test
    fun backgroundChoice_onlyWhenATransparentCapableImageBecomesJpeg() {
        val mixed = ConfigureUiState(images = listOf(image(10, 10, "image/jpeg", "a"), image(10, 10, "image/png", "b")))
        // Original format: the PNG stays PNG, so nothing gets flattened.
        assertFalse(mixed.copy(format = FormatOption.ORIGINAL).showBackgroundChoice)
        assertTrue(mixed.copy(format = FormatOption.JPEG).showBackgroundChoice)
        assertFalse(mixed.copy(format = FormatOption.WEBP).showBackgroundChoice)
        // HEIC input with Original format is written as JPEG and may carry alpha.
        val heic = ConfigureUiState(images = listOf(image(10, 10, "image/heic")))
        assertTrue(heic.copy(format = FormatOption.ORIGINAL).showBackgroundChoice)
    }

    @Test
    fun pngWarning_reflectsResolvedFormats() {
        val png = ConfigureUiState(images = listOf(image(10, 10, "image/png")))
        assertTrue(png.copy(format = FormatOption.ORIGINAL).pngSelected)
        assertFalse(png.copy(format = FormatOption.JPEG).pngSelected)
    }
}
