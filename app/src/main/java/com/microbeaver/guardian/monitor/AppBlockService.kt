package com.microbeaver.guardian.monitor

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.microbeaver.guardian.data.ActivityEvent

/**
 * Enforces app blocking + time limits. When a blocked app comes to the
 * foreground, we bounce the user back Home. "*" means the whole device is locked.
 *
 * ## The Recent Apps bypass
 * Event-driven detection alone (TYPE_WINDOW_STATE_CHANGED) has a known gap: a
 * child opens a blocked app, gets bounced Home, then opens the Recent Apps /
 * Overview screen and switches back to that app's card directly — on some
 * OEM launchers (the "lock this app" pin some skins offer on a recents card)
 * this can resume the task without a fresh window-state-changed event ever
 * reaching this service, so the block never re-fires and the app keeps
 * running.
 *
 * The fix is a second, independent detection path: poll the actually-focused
 * window directly every [POLL_MS] via [getRootInActiveWindow], regardless of
 * whether or which accessibility event fired. Whatever got the blocked app to
 * the front — a normal launch, a task switch, a recents "lock" — it is caught
 * again within one poll interval. This does not touch MonitorService, the
 * Firebase listeners, or PolicyManager at all; it is fully contained to this
 * accessibility service.
 */
class AppBlockService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastSeenPkg: String? = null

    private val poll = object : Runnable {
        override fun run() {
            try {
                pollForeground()
            } catch (_: Exception) {
                // getRootInActiveWindow() can throw/return stale data mid window
                // transition — never let that kill the polling loop itself.
            }
            handler.postDelayed(this, POLL_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        handler.removeCallbacks(poll)
        handler.post(poll)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // This callback runs for every window-state change on the device;
        // an uncaught exception here would take the whole accessibility
        // service down, silently, for the rest of its life — same lesson as
        // today's MonitorService fix, applied here too.
        try {
            handleForegroundPackage(pkg)
        } catch (_: Exception) {
        }
    }

    /** Backup path — see class doc. */
    private fun pollForeground() {
        val pkg = rootInActiveWindow?.packageName?.toString() ?: return
        handleForegroundPackage(pkg)
    }

    /**
     * Shared by both detection paths. Only reports/toasts once per actual
     * switch (tracked via [lastSeenPkg]), but re-issues the Home bounce on
     * every call for as long as a blocked package stays in front — so a
     * recents card that keeps resuming the same task gets evicted every time,
     * not just once.
     */
    private fun handleForegroundPackage(pkg: String) {
        if (pkg == packageName) return
        val changed = pkg != lastSeenPkg

        val blocked = blockedPackages
        val isBlocked = blocked.contains("*") || blocked.contains(pkg)

        if (isBlocked && !isSystemUi(pkg)) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            if (changed) {
                Toast.makeText(this, "Blocked by parent", Toast.LENGTH_SHORT).show()
                EventReporter.recordApp(
                    this, ActivityEvent.APP_BLOCKED, pkg, "Tried to open a blocked app"
                )
            }
            lastSeenPkg = pkg
            return
        }

        // The launcher and system UI are not interesting to a parent, and the
        // launcher reappears between every app switch, so it would dominate the feed.
        if (changed && !isSystemUi(pkg)) {
            EventReporter.recordApp(this, ActivityEvent.APP_OPENED, pkg)
        }
        lastSeenPkg = pkg
    }

    private fun isSystemUi(pkg: String): Boolean =
        pkg.contains("launcher") || pkg == "com.android.systemui" || pkg.contains("systemui")

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(poll)
        super.onDestroy()
    }

    companion object {
        /** Updated live by MonitorService from the current Policy. */
        @Volatile
        var blockedPackages: HashSet<String> = HashSet()

        /** Frequent enough to close the recents-switch gap without being wasteful. */
        private const val POLL_MS = 700L
    }
}
