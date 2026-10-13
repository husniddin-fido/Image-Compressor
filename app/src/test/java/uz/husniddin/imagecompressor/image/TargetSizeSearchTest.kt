package uz.husniddin.imagecompressor.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetSizeSearchTest {

    /** Fake encoder: size grows linearly with quality (q * 1000 bytes). */
    private val calls = mutableListOf<Int>()
    private val linear: (Int) -> ByteArray = { q -> calls += q; ByteArray(q * 1000) }

    @Test
    fun findsHighestQualityUnderTarget() {
        val r = TargetSizeSearch.search(targetBytes = 55_500, minQuality = 10, maxQuality = 100, encode = linear)
        assertTrue(r.met)
        assertEquals(55, r.quality)
        assertTrue(r.bytes.size <= 55_500)
    }

    @Test
    fun usesBoundedNumberOfEncodes() {
        TargetSizeSearch.search(targetBytes = 37_000, minQuality = 10, maxQuality = 100, encode = linear)
        assertTrue("used ${calls.size} encodes", calls.size <= 8)
        assertEquals(calls.size, calls.distinct().size)
    }

    @Test
    fun returnsMaxQualityWhenAlreadyUnderTarget() {
        val r = TargetSizeSearch.search(targetBytes = 1_000_000, minQuality = 10, maxQuality = 100, encode = linear)
        assertTrue(r.met)
        assertEquals(100, r.quality)
        assertEquals(1, r.encodeCount)
    }

    @Test
    fun unreachableTarget_reportsNotMet_withSmallestOutput() {
        val r = TargetSizeSearch.search(targetBytes = 5_000, minQuality = 10, maxQuality = 100, encode = linear)
        assertFalse(r.met)
        assertEquals(10, r.quality)
        assertEquals(10_000, r.bytes.size)
    }

    @Test
    fun exactBoundaryCountsAsMet() {
        val r = TargetSizeSearch.search(targetBytes = 40_000, minQuality = 10, maxQuality = 100, encode = linear)
        assertTrue(r.met)
        assertEquals(40, r.quality)
    }

    @Test
    fun nonMonotonicEncoder_neverReturnsOversizedResultAsMet() {
        // Real encoders are not perfectly monotonic; the result must still respect the target.
        val noisy: (Int) -> ByteArray = { q -> ByteArray(q * 1000 + if (q % 2 == 0) 700 else 0) }
        val r = TargetSizeSearch.search(targetBytes = 50_500, minQuality = 10, maxQuality = 100, encode = noisy)
        assertTrue(r.met)
        assertTrue(r.bytes.size <= 50_500)
    }
}
