package app.vault.workspace.media

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Shared tiny fake progressive / fragmented MP4 for JVM unit tests. */
object FakeMp4 {
    /** Build a tiny fake MP4: ftyp + (moov|mdat order) + optional moof. */
    fun write(file: File, moovBeforeMdat: Boolean, withMoof: Boolean = false) {
        fun box(type: String, payload: ByteArray): ByteArray {
            val bb = ByteBuffer.allocate(8 + payload.size).order(ByteOrder.BIG_ENDIAN)
            bb.putInt(8 + payload.size)
            bb.put(type.toByteArray(Charsets.US_ASCII))
            bb.put(payload)
            return bb.array()
        }
        val ftyp = box("ftyp", "isomisom".toByteArray(Charsets.US_ASCII))
        // Include a trivial stco so faststart patch has something to touch
        val stcoPayload = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN)
        stcoPayload.putInt(0)
        stcoPayload.putInt(1)
        stcoPayload.putInt(32)
        val stco = box("stco", stcoPayload.array())
        val stbl = box("stbl", stco)
        val minf = box("minf", stbl)
        val mdia = box("mdia", minf)
        val trak = box("trak", mdia)
        val moov = box("moov", trak)
        val mdat = box("mdat", ByteArray(64) { 0xAB.toByte() })
        val moof = box("moof", ByteArray(8))
        val parts = mutableListOf(ftyp)
        if (moovBeforeMdat) {
            parts += moov
            if (withMoof) parts += moof
            parts += mdat
        } else {
            parts += mdat
            parts += moov
            if (withMoof) parts += moof
        }
        file.outputStream().use { out ->
            for (p in parts) out.write(p)
        }
    }
}
