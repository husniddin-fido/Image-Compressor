package uz.husniddin.imagecompressor.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.husniddin.imagecompressor.domain.model.ResizeSpec

class ResizeCalculatorTest {

    @Test
    fun none_keepsOriginalSize() {
        assertEquals(ResizeCalculator.Target(4000, 3000, false), ResizeCalculator.target(4000, 3000, ResizeSpec.None))
    }

    @Test
    fun percentage_scalesBothSides() {
        val t = ResizeCalculator.target(4000, 3000, ResizeSpec.Percentage(25))
        assertEquals(1000, t.width)
        assertEquals(750, t.height)
    }

    @Test
    fun percentage_neverProducesZero() {
        val t = ResizeCalculator.target(3, 2, ResizeSpec.Percentage(1))
        assertEquals(1, t.width)
        assertEquals(1, t.height)
    }

    @Test(expected = IllegalArgumentException::class)
    fun percentage_outOfRange_rejected() {
        ResizeCalculator.target(100, 100, ResizeSpec.Percentage(0))
    }

    @Test
    fun fitWithin_preservesAspectRatio_landscape() {
        val t = ResizeCalculator.target(4000, 3000, ResizeSpec.FitWithin(1920, 1920, allowUpscale = false))
        assertEquals(1920, t.width)
        assertEquals(1440, t.height)
    }

    @Test
    fun fitWithin_preservesAspectRatio_portrait() {
        val t = ResizeCalculator.target(3000, 4000, ResizeSpec.FitWithin(1920, 1920, allowUpscale = false))
        assertEquals(1440, t.width)
        assertEquals(1920, t.height)
    }

    @Test
    fun fitWithin_withoutUpscale_keepsSmallImages() {
        val t = ResizeCalculator.target(800, 600, ResizeSpec.FitWithin(1920, 1920, allowUpscale = false))
        assertEquals(800, t.width)
        assertEquals(600, t.height)
    }

    @Test
    fun fitWithin_withUpscale_enlarges() {
        val t = ResizeCalculator.target(800, 600, ResizeSpec.FitWithin(1600, 1600, allowUpscale = true))
        assertEquals(1600, t.width)
        assertEquals(1200, t.height)
    }

    @Test
    fun exact_ignoresAspectRatio() {
        val t = ResizeCalculator.target(4000, 3000, ResizeSpec.Exact(500, 500))
        assertEquals(500, t.width)
        assertEquals(500, t.height)
    }

    @Test(expected = IllegalArgumentException::class)
    fun exact_negative_rejected() {
        ResizeCalculator.target(100, 100, ResizeSpec.Exact(-1, 10))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidSource_rejected() {
        ResizeCalculator.target(0, 100, ResizeSpec.None)
    }

    @Test
    fun hugeRequest_isCappedBySideAndPixelLimits() {
        val t = ResizeCalculator.target(1000, 1000, ResizeSpec.Exact(100_000, 100_000))
        assertTrue(t.capped)
        assertTrue(t.width <= ResizeCalculator.MAX_SIDE)
        assertTrue(t.width.toLong() * t.height <= ResizeCalculator.MAX_OUTPUT_PIXELS + t.width + t.height)
        assertEquals(t.width, t.height) // aspect preserved while capping
    }

    @Test
    fun largeSource_withoutResize_isCappedToPixelLimit() {
        // 200 MP sensor image
        val t = ResizeCalculator.target(16_320, 12_240, ResizeSpec.None)
        assertTrue(t.capped)
        assertTrue(t.width.toLong() * t.height <= ResizeCalculator.MAX_OUTPUT_PIXELS + t.width + t.height)
        assertEquals(16_320.0 / 12_240, t.width.toDouble() / t.height, 0.01)
    }

    @Test
    fun normalSource_isNotCapped() {
        assertFalse(ResizeCalculator.target(4000, 3000, ResizeSpec.None).capped)
    }

    @Test
    fun sampleSize_keepsDecodedAtLeastTarget() {
        assertEquals(1, ResizeCalculator.sampleSize(4000, 3000, 4000, 3000))
        assertEquals(2, ResizeCalculator.sampleSize(4000, 3000, 1920, 1440))
        assertEquals(4, ResizeCalculator.sampleSize(4000, 3000, 1000, 750))
        assertEquals(2, ResizeCalculator.sampleSize(4000, 3000, 1001, 751))
    }

    @Test
    fun sampleSize_respectsDecodeBudget() {
        val sample = ResizeCalculator.sampleSize(20_000, 15_000, 20_000, 15_000)
        assertTrue((20_000L / sample) * (15_000L / sample) <= ResizeCalculator.MAX_DECODE_PIXELS)
    }

    @Test
    fun lockedAspect_helpers() {
        assertEquals(1500, ResizeCalculator.heightForWidth(4000, 3000, 2000))
        assertEquals(2000, ResizeCalculator.widthForHeight(4000, 3000, 1500))
        assertEquals(1, ResizeCalculator.heightForWidth(4000, 1, 10))
    }

    @Test
    fun downscaleFactor_isBounded() {
        val f = ResizeCalculator.downscaleFactorForTarget(400_000, 100_000)
        assertEquals(0.475, f, 0.001)
        assertEquals(0.1, ResizeCalculator.downscaleFactorForTarget(100_000_000, 10), 0.0)
    }
}
