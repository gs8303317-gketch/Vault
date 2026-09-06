package app.vault.workspace.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MediaContainerProbeTest {

    @Test
    fun sniff_moovBeforeMdat_isSeekable() {
        val f = File.createTempFile("vault-probe-seek", ".mp4")
        try {
            FakeMp4.write(f, moovBeforeMdat = true, withMoof = false)
            val r = MediaContainerProbe.sniffFile(f)
            assertEquals(MediaContainerProbe.Kind.PROGRESSIVE_SEEKABLE_MP4, r.kind)
            assertTrue(r.alreadySeekable)
        } finally {
            f.delete()
        }
    }

    @Test
    fun sniff_moovAfterMdat_needsFaststart() {
        val f = File.createTempFile("vault-probe-moovend", ".mp4")
        try {
            FakeMp4.write(f, moovBeforeMdat = false, withMoof = false)
            val r = MediaContainerProbe.sniffFile(f)
            assertEquals(MediaContainerProbe.Kind.MP4_MOOV_AT_END, r.kind)
            assertFalse(r.alreadySeekable)
        } finally {
            f.delete()
        }
    }

    @Test
    fun sniff_fragmentedHasMoof() {
        val f = File.createTempFile("vault-probe-fmp4", ".mp4")
        try {
            FakeMp4.write(f, moovBeforeMdat = true, withMoof = true)
            val r = MediaContainerProbe.sniffFile(f)
            assertEquals(MediaContainerProbe.Kind.FRAGMENTED_MP4, r.kind)
            assertFalse(r.alreadySeekable)
        } finally {
            f.delete()
        }
    }

    @Test
    fun sniff_mpegTs() {
        val f = File.createTempFile("vault-probe-ts", ".ts")
        try {
            val packet = ByteArray(188) { 0 }
            packet[0] = 0x47
            val out = ByteArray(188 * 5)
            for (i in 0 until 5) {
                System.arraycopy(packet, 0, out, i * 188, 188)
            }
            f.writeBytes(out)
            assertTrue(MediaContainerProbe.looksLikeMpegTs(f))
            val r = MediaContainerProbe.sniffFile(f)
            assertEquals(MediaContainerProbe.Kind.MPEG_TS, r.kind)
            assertFalse(r.alreadySeekable)
        } finally {
            f.delete()
        }
    }

    @Test
    fun mp4BoxParser_readsTopLevel() {
        val f = File.createTempFile("vault-boxes", ".mp4")
        try {
            FakeMp4.write(f, moovBeforeMdat = true, withMoof = false)
            val layout = Mp4BoxParser.analyze(f)
            assertNotNull(layout)
            assertTrue(layout!!.moovOffset >= 0)
            assertTrue(layout.mdatOffset > layout.moovOffset)
            assertFalse(layout.hasMoof)
        } finally {
            f.delete()
        }
    }

    @Test
    fun faststart_movesMoovBeforeMdat() {
        val input = File.createTempFile("vault-fs-in", ".mp4")
        val output = File.createTempFile("vault-fs-out", ".mp4")
        try {
            FakeMp4.write(input, moovBeforeMdat = false, withMoof = false)
            val before = Mp4BoxParser.analyze(input)!!
            assertTrue(before.moovOffset > before.mdatOffset)
            assertTrue(Mp4Faststart.moveMoovToStart(input, output))
            val after = Mp4BoxParser.analyze(output)!!
            assertTrue(after.moovOffset < after.mdatOffset)
            assertFalse(after.hasMoof)
            val sniff = MediaContainerProbe.sniffFile(output)
            assertTrue(sniff.alreadySeekable)
        } finally {
            input.delete()
            output.delete()
        }
    }

    @Test
    fun patchChunkOffsets_addsDeltaToStco() {
        // minimal stco payload inside a fake moov-like buffer: box "stco"
        // size(4)=16, type stco, ver+flags(4)=0, count(4)=1, entry(4)=100
        val box = ByteBuffer.allocate(20).order(ByteOrder.BIG_ENDIAN)
        box.putInt(20) // size
        box.put("stco".toByteArray(Charsets.US_ASCII))
        box.putInt(0) // version+flags
        box.putInt(1) // count
        box.putInt(100) // offset
        val moov = box.array()
        // Wrap in moov box for walker: size + "moov" + content
        val wrapped = ByteBuffer.allocate(8 + moov.size).order(ByteOrder.BIG_ENDIAN)
        wrapped.putInt(8 + moov.size)
        wrapped.put("moov".toByteArray(Charsets.US_ASCII))
        wrapped.put(moov)
        val arr = wrapped.array()
        val patched = Mp4Faststart.patchChunkOffsets(arr, delta = 50L)
        assertNotNull(patched)
        // entry is at offset 8 (moov hdr) + 8 (stco hdr) + 4 (ver) + 4 (count) = 24
        val entry = ByteBuffer.wrap(arr, 24, 4).order(ByteOrder.BIG_ENDIAN).int
        assertEquals(150, entry)
    }
}
