package app.vault.workspace.ui.library

import android.graphics.Bitmap
import java.util.LinkedHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * In-memory LRU cache for decrypted library thumbnails.
 * Cleared on session lock so bitmaps do not outlive the unlocked vault.
 *
 * Concurrent [get] calls for the same id share one in-flight decrypt (no double-decrypt).
 * Parallel decrypts are capped so fast fling does not stampede the CPU/IO.
 */
object ThumbCache {
    private const val MAX_ENTRIES = 96
    /** Cap simultaneous thumb decrypt/decodes during scroll. */
    private const val MAX_PARALLEL_LOADS = 4

    private val lock = Any()
    private val cache = object : LinkedHashMap<String, Bitmap>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean =
            size > MAX_ENTRIES
    }
    private val inflight = HashMap<String, CompletableDeferred<Bitmap?>>()
    private val loadSlots = Semaphore(MAX_PARALLEL_LOADS)
    /** Independent of Compose cancellation so a scrolled-off cell still warms the cache. */
    private val loadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Returns a cached bitmap without loading. */
    fun peek(id: String): Bitmap? = synchronized(lock) { cache[id] }

    /**
     * Returns a cached thumbnail or loads via [loader] and stores the result.
     * Duplicate requests for the same [id] await the same in-flight load.
     */
    suspend fun get(id: String, loader: suspend () -> Bitmap?): Bitmap? {
        synchronized(lock) {
            cache[id]?.let { return it }
        }
        val deferred: CompletableDeferred<Bitmap?>
        var startLoad = false
        synchronized(lock) {
            cache[id]?.let { return it }
            val existing = inflight[id]
            if (existing != null) {
                deferred = existing
            } else {
                deferred = CompletableDeferred()
                inflight[id] = deferred
                startLoad = true
            }
        }
        if (startLoad) {
            loadScope.launch {
                val result = try {
                    loadSlots.withPermit { loader() }
                } catch (_: Exception) {
                    null
                }
                synchronized(lock) {
                    if (inflight[id] === deferred) {
                        inflight.remove(id)
                        if (result != null) {
                            cache[id] = result
                        }
                        deferred.complete(result)
                    } else {
                        // Cleared / removed while loading — drop orphan bitmap.
                        if (result != null && !result.isRecycled) {
                            result.recycle()
                        }
                        if (!deferred.isCompleted) {
                            deferred.complete(null)
                        }
                    }
                }
            }
        }
        return try {
            deferred.await()
        } catch (_: Exception) {
            synchronized(lock) { cache[id] }
        }
    }

    fun remove(id: String) {
        synchronized(lock) {
            cache.remove(id)
            inflight.remove(id)?.let { pending ->
                if (!pending.isCompleted) pending.complete(null)
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            cache.clear()
            val pending = inflight.values.toList()
            inflight.clear()
            pending.forEach { d ->
                if (!d.isCompleted) d.complete(null)
            }
        }
    }
}
