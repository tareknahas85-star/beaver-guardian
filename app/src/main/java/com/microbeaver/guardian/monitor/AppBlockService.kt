package com.microbeaver.guardian.monitor

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.microbeaver.guardian.Prefs
import com.microbeaver.guardian.data.FirebaseRepo

/**
 * Enforces app blocking + time limits, AND emits a real-time APP_OPEN event
 * every time the child opens a different app (drives parent notifications).
 */
class AppBlockService : AccessibilityService() {

    private var lastEventPkg = ""

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
            return
        }

        if (!isSystemUi(pkg) && pkg != lastEventPkg) {
            lastEventPkg = pkg
            emitOpen(pkg)
        }
    }

    private fun emitOpen(pkg: String) {
        val code = Prefs.getPairCode(this) ?: return
        val label = try {
            val ai = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(ai).toString()
        } catch (_: Exception) {
            pkg
        }
        FirebaseRepo.pushEvent(code, "APP_OPEN", "فتح: $label")
    }

    private fun isSystemUi(pkg: String): Boolean =
        pkg.contains("launcher") || pkg.contains("systemui")

    override fun onInterrupt() {}

    companion object {
        @Volatile
        var blockedPackages: HashSet<String> = HashSet()
    }
}
