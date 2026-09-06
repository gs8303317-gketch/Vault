package app.vault.workspace.media

import android.content.Context
import java.io.File
import java.io.RandomAccessFile

/**
 * Session cache wipe helpers under `cacheDir/playcache` and legacy `cacheDir/seekprep`.
 *
 * Playback no longer writes play-cache or seek-prepare files; [wipeAll] still purges
 * leftovers from older builds on lock / cold start
 * ([app.vault.workspace.auth.SessionManager.wipeTmp]).
 */
object PlaybackPlaintextCache {
    private const val DIR_NAME = "playcache"
    private const val SEEKPREP_DIR = "seekprep"
    /** Overwrite at most this many leading bytes before delete (same idea as PDF tmp wipe). */
    private const val WIPE_CAP_BYTES = 8L * 1024L * 1024L

    fun dir(context: Context): File =
        File(context.cacheDir, DIR_NAME)

    fun wipeAll(context: Context) {
        wipeAll(dir(context))
        wipeAll(File(context.cacheDir, SEEKPREP_DIR))
    }

    fun wipeAll(cacheDir: File) {
        if (!cacheDir.exists()) return
        cacheDir.listFiles()?.forEach { f ->
            try {
                if (f.isDirectory) {
                    f.deleteRecursively()
                } else {
                    wipeAndDelete(f)
                }
            } catch (_: Exception) {
            }
        }
        try {
            cacheDir.delete()
        } catch (_: Exception) {
        }
    }

    private fun wipeAndDelete(file: File) {
        try {
            if (file.exists() && file.isFile) {
                val len = file.length().coerceAtMost(WIPE_CAP_BYTES)
                if (len > 0) {
                    RandomAccessFile(file, "rw").use { raf ->
                        val buf = ByteArray(minOf(len, 64L * 1024L).toInt())
                        var left = len
                        raf.seek(0)
                        while (left > 0) {
                            val n = minOf(buf.size.toLong(), left).toInt()
                            raf.write(buf, 0, n)
                            left -= n
                        }
                        raf.fd.sync()
                    }
                }
            }
        } catch (_: Exception) {
        }
        try {
            file.delete()
        } catch (_: Exception) {
        }
    }
}
