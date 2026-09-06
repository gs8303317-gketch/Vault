package app.vault.workspace.auth

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Auto-lock on background. Idle timer pauses during media playback and while SAF is open.
 */
class AutoLockController(
    private val session: SessionManager,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate),
    context: Context? = null,
) : DefaultLifecycleObserver {

    private val prefs = context?.applicationContext
        ?.getSharedPreferences("vault_autolock", Context.MODE_PRIVATE)

    private val playbackActive = AtomicBoolean(false)
    private val deferBackgroundLock = AtomicBoolean(false)
    private val idleTimeoutMs = AtomicLong(
        prefs?.getLong(KEY_IDLE_MS, DEFAULT_IDLE_MS) ?: DEFAULT_IDLE_MS,
    )
    private val _idleTimeoutMsFlow = MutableStateFlow(idleTimeoutMs.get())
    val idleTimeoutMsFlow: StateFlow<Long> = _idleTimeoutMsFlow.asStateFlow()

    private var idleJob: Job? = null

    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    fun stop() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        idleJob?.cancel()
    }

    fun setIdleTimeoutMs(ms: Long) {
        val clamped = ms.coerceIn(15_000L, 30 * 60_000L)
        idleTimeoutMs.set(clamped)
        _idleTimeoutMsFlow.value = clamped
        prefs?.edit()?.putLong(KEY_IDLE_MS, clamped)?.apply()
        bumpIdle()
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
            delay(idleTimeoutMs.get())
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
        if (deferBackgroundLock.get()) return
        if (session.state.value is SessionManager.SessionState.Unlocked) {
            session.lock()
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        bumpIdle()
    }

    companion object {
        const val DEFAULT_IDLE_MS = 60_000L
        private const val KEY_IDLE_MS = "idle_timeout_ms"

        val PRESETS = listOf(
            30_000L to "30 seconds",
            60_000L to "1 minute",
            2 * 60_000L to "2 minutes",
            5 * 60_000L to "5 minutes",
            15 * 60_000L to "15 minutes",
        )
    }
}
