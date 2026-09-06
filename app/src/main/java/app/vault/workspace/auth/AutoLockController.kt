package app.vault.workspace.auth

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Auto-lock on background. Idle timer (default 60s) pauses during media playback.
 * Background lock is deferred while a system SAF picker / create-document UI is open,
 * otherwise import/export always fails (ProcessLifecycle onStop → lock → no VMK).
 */
class AutoLockController(
    private val session: SessionManager,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate),
    private val idleTimeoutMs: Long = 60_000L,
) : DefaultLifecycleObserver {

    private val playbackActive = AtomicBoolean(false)
    private val deferBackgroundLock = AtomicBoolean(false)
    private var idleJob: Job? = null

    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    fun stop() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        idleJob?.cancel()
    }

    fun setPlaybackActive(active: Boolean) {
        playbackActive.set(active)
        if (active) {
            idleJob?.cancel()
            idleJob = null
        } else {
            bumpIdle()
        }
    }

    /** Call true before launching SAF; false in the ActivityResult callback (success or cancel). */
    fun setDeferBackgroundLock(defer: Boolean) {
        deferBackgroundLock.set(defer)
        if (!defer) {
            bumpIdle()
        } else {
            idleJob?.cancel()
            idleJob = null
        }
    }

    fun bumpIdle() {
        if (session.state.value !is SessionManager.SessionState.Unlocked) return
        if (playbackActive.get()) return
        if (deferBackgroundLock.get()) return
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(idleTimeoutMs)
            if (!playbackActive.get() &&
                !deferBackgroundLock.get() &&
                session.state.value is SessionManager.SessionState.Unlocked
            ) {
                session.lock()
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        idleJob?.cancel()
        if (deferBackgroundLock.get()) {
            // System document UI is in front — do not lock mid-import/export.
            return
        }
        if (session.state.value is SessionManager.SessionState.Unlocked) {
            session.lock()
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        bumpIdle()
    }
}
