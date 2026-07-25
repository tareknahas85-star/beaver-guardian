package com.microbeaver.guardian.monitor

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

/**
 * Enforces app blocking + time limits. When a blocked app comes to the
 * foreground, we bounce the user back Home. "*" means the whole device is locked.
 */
class AppBlockService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        val blocked = blockedPackages
        val isBlocked = blocked.contains("*") || blocked.contains(pkg)
        if (isBlocked && !isSystemUi(pkg)) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            Toast.makeText(
                this,
                "التطبيق محظور من وليّ الأمر / Blocked by parent",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun isSystemUi(pkg: String): Boolean =
        pkg.contains("launcher") || pkg == "com.android.systemui" || pkg.contains("systemui")

    override fun onInterrupt() {}

    companion object {
        /** Updated live by MonitorService from the current Policy. */
        @Volatile
        var blockedPackages: HashSet<String> = HashSet()
    }
}
