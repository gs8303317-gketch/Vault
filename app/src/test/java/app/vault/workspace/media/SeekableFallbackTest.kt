package app.vault.workspace.media

import androidx.media3.common.C
import androidx.media3.extractor.ConstantBitrateSeekMap
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.SeekPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekableFallbackTest {

    private class FakeSeekMap(
        private val seekable: Boolean,
        private val durationUs: Long,
    ) : SeekMap {
        override fun isSeekable(): Boolean = seekable
        override fun getDurationUs(): Long = durationUs
        override fun getSeekPoints(timeUs: Long): SeekMap.SeekPoints =
            SeekMap.SeekPoints(SeekPoint(0, 0))
    }

    @Test
    fun maybeReplace_unseekableWithDurationAndLength_becomesCbrSeekable() {
        val durationUs = 10_000_000L // 10 seconds
        val contentLength = 1_250_000L // 1.25 MB → 1_000_000 bit/s
        val original = FakeSeekMap(seekable = false, durationUs = durationUs)

        val replaced = SeekableFallback.maybeReplace(original, contentLength)

        assertTrue(replaced.isSeekable)
        assertTrue(replaced is ConstantBitrateSeekMap)
        assertEquals(durationUs, replaced.durationUs)
        // bitrate = contentLength * 8 * 1_000_000 / durationUs = 1_000_000
        val cbr = replaced as ConstantBitrateSeekMap
        // Spot-check: midpoint time should map near half the file
        val mid = cbr.getSeekPoints(durationUs / 2)
        assertTrue(mid.first.position in (contentLength / 4)..(contentLength * 3 / 4))
    }

    @Test
    fun maybeReplace_alreadySeekable_passesThrough() {
        val original = FakeSeekMap(seekable = true, durationUs = 5_000_000L)
        val out = SeekableFallback.maybeReplace(original, 1_000_000L)
        assertSame(original, out)
    }

    @Test
    fun maybeReplace_unseekableWithoutDuration_unchanged() {
        val original = FakeSeekMap(seekable = false, durationUs = C.TIME_UNSET)
        val out = SeekableFallback.maybeReplace(original, 1_000_000L)
        assertSame(original, out)
        assertFalse(out.isSeekable)
    }

    @Test
    fun maybeReplace_unseekableWithoutLength_unchanged() {
        val original = FakeSeekMap(seekable = false, durationUs = 5_000_000L)
        val out = SeekableFallback.maybeReplace(original, 0L)
        assertSame(original, out)
        assertFalse(out.isSeekable)
    }

    @Test
    fun maybeReplace_unseekableBuiltIn_becomesSeekable() {
        val durationUs = 60_000_000L
        val length = 15_000_000L
        val original = SeekMap.Unseekable(durationUs)
        assertFalse(original.isSeekable)

        val replaced = SeekableFallback.maybeReplace(original, length)
        assertTrue(replaced.isSeekable)
        assertTrue(replaced is ConstantBitrateSeekMap)
        assertEquals(durationUs, replaced.durationUs)
    }

    @Test
    fun maybeReplace_bitrateAtLeastOne() {
        // Tiny file / long duration still yields bitrate >= 1
        val replaced = SeekableFallback.maybeReplace(
            FakeSeekMap(false, durationUs = 1_000_000_000_000L),
            contentLength = 1L,
        )
        assertTrue(replaced.isSeekable)
        assertTrue(replaced is ConstantBitrateSeekMap)
    }
}
