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
 */
class AutoLockController(
    private val session: SessionManager,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate),
    private val idleTimeoutMs: Long = 60_000L,
) : DefaultLifecycleObserver {

    private val playbackActive = AtomicBoolean(false)
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

    fun bumpIdle() {
        if (session.state.value !is SessionManager.SessionState.Unlocked) return
        if (playbackActive.get()) return
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(idleTimeoutMs)
            if (!playbackActive.get() &&
                session.state.value is SessionManager.SessionState.Unlocked
            ) {
                session.lock()
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        // App went to background — lock immediately
        if (session.state.value is SessionManager.SessionState.Unlocked) {
            session.lock()
        }
        idleJob?.cancel()
    }

    override fun onStart(owner: LifecycleOwner) {
        bumpIdle()
    }
}
