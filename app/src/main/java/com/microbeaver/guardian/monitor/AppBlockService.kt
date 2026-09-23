package com.microbeaver.guardian.monitor

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.microbeaver.guardian.admin.PolicyManager
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
 * again within one poll interval. This does not touch MonitorService or the
 * Firebase listeners; it does now also call into [PolicyManager] (see below).
 *
 * ## Whole-device lock ("*") also forces a real screen lock
 * Bouncing to Home is a request, not a guarantee — some device/launcher
 * combinations can silently ignore [performGlobalAction]. When the whole
 * device is meant to be locked, that is not good enough, so this also calls
 * [PolicyManager.lockNow] every time it fires, on the same ~700ms cadence.
 */
class AppBlockService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastSeenPkg: String? = null
    private val policyMgr by lazy { PolicyManager(this) }

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
            // Whole-device lock ("*", from the parent's "Lock" button or a
            // schedule) used to rely on the Home bounce above landing, same as
            // any single blocked app. That is a best-effort action — nothing
            // guarantees Android actually honours it — whereas Device Admin's
            // lockNow() is the one enforcement already proven reliable end to
            // end (confirmed working since v8.7). Firing it here too, every
            // ~700ms for as long as the phone is unlocked with something in the
            // foreground, means the screen re-locks almost immediately even on
            // a device/launcher combination where the Home action alone quietly
            // does nothing.
            if (blocked.contains("*")) policyMgr.lockNow()
            if (changed) {
                Toast.makeText(this, "Blocked by parent", Toast.LENGTH_SHORT).show()
                EventReporter.recordApp(
                    this, ActivityEvent.APP_BLOCKED, pkg, "Tried to open a blocked app"
                )
            }
            lastSeenPkg = pkg
            lastForegroundPkg = pkg
            return
        }

        // The launcher and system UI are not interesting to a parent, and the
        // launcher reappears between every app switch, so it would dominate the feed.
        if (changed && !isSystemUi(pkg)) {
            EventReporter.recordApp(this, ActivityEvent.APP_OPENED, pkg)
        }
        lastSeenPkg = pkg
        lastForegroundPkg = pkg
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

        /**
         * Whatever was foreground last, event-driven or polled. Read by
         * MonitorService.checkScreenPinning() so a pin-attempt alert can name
         * the app, instead of just saying "something" got pinned.
         */
        @Volatile
        var lastForegroundPkg: String? = null

        /** Frequent enough to close the recents-switch gap without being wasteful. */
        private const val POLL_MS = 700L

        /**
         * Whether the user has actually turned this specific accessibility
         * service on in system Settings — the one thing app blocking, the
         * whole-device lock ("*"), daily/per-app time limits and schedule
         * blocking all depend on, independently of Device Admin or the VPN.
         *
         * Checked directly against Settings.Secure rather than trusting an
         * in-memory "connected" flag, so it stays correct even if it was
         * switched off from outside this app entirely — some OEM security
         * centres (Huawei's included) are known to silently re-disable
         * third-party accessibility services on their own, with nothing in
         * this process ever getting a callback to notice.
         */
        fun isEnabled(ctx: Context): Boolean {
            val expected = ComponentName(ctx, AppBlockService::class.java).flattenToString()
            val enabled = try {
                Settings.Secure.getString(
                    ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
            } catch (_: Exception) {
                null
            } ?: return false
            return enabled.splitToSequence(':').any { it.equals(expected, ignoreCase = true) }
        }
    }
}
