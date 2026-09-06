package app.vault.workspace

import android.app.Application
import app.vault.workspace.auth.AutoLockController
import app.vault.workspace.auth.SessionManager
import app.vault.workspace.data.VaultRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class VaultApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var session: SessionManager
        private set
    lateinit var repository: VaultRepository
        private set
    lateinit var autoLock: AutoLockController
        private set

    override fun onCreate() {
        super.onCreate()
        session = SessionManager(this)
        // Wipe leftover tmp (.part, pdf cache) on every cold start
        session.wipeTmp()
        repository = VaultRepository(this, session)
        autoLock = AutoLockController(session, appScope, this)
        autoLock.start()
        session.addLockListener {
            // Players stopped by UI listeners; ensure tmp wiped
            session.wipeTmp()
        }
    }
}
