package app.vault.workspace.media

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Move `moov` before `mdat` (qt-faststart style) without re-encode.
 * Patches `stco` / `co64` chunk offsets by +moovSize.
 *
 * Only for progressive MP4 with moov after mdat and no `moof`.
 */
object Mp4Faststart {
    /**
     * @return true if [outFile] was written as a valid faststart MP4.
     */
    fun moveMoovToStart(input: File, outFile: File): Boolean {
        val layout = Mp4BoxParser.analyze(input) ?: return false
        if (layout.hasMoof) return false
        if (layout.moovOffset < 0L || layout.mdatOffset < 0L) return false
        if (layout.moovOffset < layout.mdatOffset) {
            // Already moov-before-mdat — copy as-is.
            input.copyTo(outFile, overwrite = true)
            return true
        }
        val moovSize = layout.moovSize
        if (moovSize <= 8L || moovSize > Int.MAX_VALUE.toLong()) return false

        RandomAccessFile(input, "r").use { raf ->
            raf.seek(layout.moovOffset)
            val moov = ByteArray(moovSize.toInt())
            raf.readFully(moov)
            patchChunkOffsets(moov, moovSize) ?: return false

            outFile.parentFile?.mkdirs()
            val part = File(outFile.parentFile, "${outFile.name}.part")
            try {
                if (part.exists()) part.delete()
                RandomAccessFile(part, "rw").use { out ->
                    out.setLength(0)
                    // Copy everything before original mdat (typically ftyp + free),
                    // then moov, then from mdat through end — but skip the original moov.
                    copyRange(raf, out, 0L, layout.mdatOffset)
                    out.write(moov)
                    // Copy mdat..moov (exclusive of moov) then anything after moov
                    val afterMdatBeforeMoov = layout.moovOffset - layout.mdatOffset
                    if (afterMdatBeforeMoov > 0) {
                        copyRange(raf, out, layout.mdatOffset, layout.moovOffset)
                    }
                    val afterMoov = raf.length() - (layout.moovOffset + layout.moovSize)
                    if (afterMoov > 0) {
                        copyRange(
                            raf,
                            out,
                            layout.moovOffset + layout.moovSize,
                            raf.length(),
                        )
                    }
                    out.fd.sync()
                }
                if (outFile.exists()) outFile.delete()
                if (!part.renameTo(outFile)) {
                    part.copyTo(outFile, overwrite = true)
                    part.delete()
                }
                return outFile.exists() && outFile.length() > 0L
            } catch (_: Exception) {
                part.delete()
                return false
            }
        }
    }

    private fun copyRange(src: RandomAccessFile, dst: RandomAccessFile, from: Long, to: Long) {
        val buf = ByteArray(256 * 1024)
        var pos = from
        src.seek(from)
        while (pos < to) {
            val n = minOf(buf.size.toLong(), to - pos).toInt()
            val read = src.read(buf, 0, n)
            if (read <= 0) break
            dst.write(buf, 0, read)
            pos += read
        }
    }

    /**
     * Walk moov tree; add [delta] to every stco/co64 entry.
     * @return null on malformed box tree.
     */
    internal fun patchChunkOffsets(moov: ByteArray, delta: Long): ByteArray? {
        return try {
            patchBoxes(moov, 0, moov.size, delta)
            moov
        } catch (_: Exception) {
            null
        }
    }

    private fun patchBoxes(data: ByteArray, start: Int, end: Int, delta: Long) {
        var pos = start
        while (pos + 8 <= end) {
            val size32 = readU32(data, pos)
            val type = String(data, pos + 4, 4, Charsets.US_ASCII)
            var header = 8
            var size = size32.toLong() and 0xFFFFFFFFL
            if (size == 1L) {
                if (pos + 16 > end) break
                size = readU64(data, pos + 8)
                header = 16
            } else if (size == 0L) {
                size = (end - pos).toLong()
            }
            if (size < header || pos + size > end) break
            val contentStart = pos + header
            val contentEnd = pos + size.toInt()
            when (type) {
                "moov", "trak", "mdia", "minf", "stbl", "edts" ->
                    patchBoxes(data, contentStart, contentEnd, delta)
                "stco" -> patchStco(data, contentStart, contentEnd, delta)
                "co64" -> patchCo64(data, contentStart, contentEnd, delta)
            }
            pos += size.toInt()
        }
    }

    private fun patchStco(data: ByteArray, start: Int, end: Int, delta: Long) {
        // version(1)+flags(3)+entry_count(4)+entries u32*
        if (start + 8 > end) return
        val count = readU32(data, start + 4)
        var off = start + 8
        repeat(count) {
            if (off + 4 > end) return
            val v = (readU32(data, off).toLong() and 0xFFFFFFFFL) + delta
            writeU32(data, off, v.toInt())
            off += 4
        }
    }

    private fun patchCo64(data: ByteArray, start: Int, end: Int, delta: Long) {
        if (start + 8 > end) return
        val count = readU32(data, start + 4)
        var off = start + 8
        repeat(count) {
            if (off + 8 > end) return
            val v = readU64(data, off) + delta
            writeU64(data, off, v)
            off += 8
        }
    }

    private fun readU32(data: ByteArray, off: Int): Int =
        ByteBuffer.wrap(data, off, 4).order(ByteOrder.BIG_ENDIAN).int

    private fun readU64(data: ByteArray, off: Int): Long =
        ByteBuffer.wrap(data, off, 8).order(ByteOrder.BIG_ENDIAN).long

    private fun writeU32(data: ByteArray, off: Int, v: Int) {
        ByteBuffer.wrap(data, off, 4).order(ByteOrder.BIG_ENDIAN).putInt(v)
    }

    private fun writeU64(data: ByteArray, off: Int, v: Long) {
        ByteBuffer.wrap(data, off, 8).order(ByteOrder.BIG_ENDIAN).putLong(v)
    }
}
