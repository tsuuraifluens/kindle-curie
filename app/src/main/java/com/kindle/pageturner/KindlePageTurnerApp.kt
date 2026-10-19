package com.kindle.pageturner

import android.app.Application
import com.kindle.pageturner.diagnostics.DiagnosticLogger
import com.kindle.pageturner.diagnostics.LogCategory
import com.kindle.pageturner.root.RootHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class KindlePageTurnerApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        DiagnosticLogger.i(LogCategory.SYSTEM, "KindlePageTurnerApp initialized")
        RootHelper.ensureInitialized(this)
        appScope.launch {
            try {
                val report = RootHelper.runStartupRecoveryCheck(this@KindlePageTurnerApp)
                if (report.hasPendingRecovery) {
                    DiagnosticLogger.w(
                        LogCategory.SYSTEM,
                        "Startup recovery check detected ${report.pendingItems.size} pending transaction(s)",
                        report.toSummaryText()
                    )
                }
            } catch (t: Throwable) {
                DiagnosticLogger.e(LogCategory.SYSTEM, "Startup recovery check failed", t)
            }
        }
    }
}
