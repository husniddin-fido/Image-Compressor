package uz.husniddin.imagecompressor.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {
    @Test
    fun bytes() {
        assertEquals("512 B", Formatters.bytes(512))
        assertEquals("1.5 KB", Formatters.bytes(1536))
        assertEquals("250 KB", Formatters.bytes(250 * 1024))
        assertEquals("3.32 MB", Formatters.bytes(3_478_812))
        assertEquals("1.00 GB", Formatters.bytes(1024L * 1024 * 1024))
    }

    @Test
    fun percentChange() {
        assertEquals("75% smaller", Formatters.percentChange(1000, 250))
        assertEquals("20% larger", Formatters.percentChange(1000, 1200))
        assertEquals("Same size", Formatters.percentChange(1000, 1000))
        assertEquals("<1% smaller", Formatters.percentChange(100_000, 99_900))
        assertEquals("—", Formatters.percentChange(0, 10))
    }
}
