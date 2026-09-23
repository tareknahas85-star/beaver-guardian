package com.microbeaver.guardian.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.microbeaver.guardian.data.ActivityEvent

/**
 * Turns system broadcasts into feed events: screen on/off, unlock, charger, and
 * apps being installed or removed.
 *
 * Screen and power broadcasts cannot be declared in the manifest on modern
 * Android — they are registered at runtime only, which is why [register] is
 * called from [MonitorService] rather than listed as a `<receiver>`.
 *
 * Package install/remove *can* be a manifest receiver, but registering it here
 * too keeps all of it in one place and means it stops when the service stops.
 */
class DeviceEventReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context?, intent: Intent?) {
        val c = ctx ?: return
        when (intent?.action) {
            Intent.ACTION_SCREEN_ON ->
                EventReporter.record(c, ActivityEvent.SCREEN_ON, "Screen on")

            Intent.ACTION_SCREEN_OFF ->
                EventReporter.record(c, ActivityEvent.SCREEN_OFF, "Screen off")

            // Fires after the keyguard is actually dismissed, so this is a real
            // unlock rather than just the screen waking.
            Intent.ACTION_USER_PRESENT ->
                EventReporter.record(c, ActivityEvent.UNLOCK, "Phone unlocked")

            Intent.ACTION_POWER_CONNECTED ->
                EventReporter.record(c, ActivityEvent.POWER, "Charger plugged in")

            Intent.ACTION_POWER_DISCONNECTED ->
                EventReporter.record(c, ActivityEvent.POWER, "Charger unplugged")

            Intent.ACTION_PACKAGE_ADDED -> {
                val pkg = intent.data?.schemeSpecificPart ?: return
                // A replace is an update, not a new install.
                if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
                EventReporter.recordApp(c, ActivityEvent.APP_INSTALLED, pkg, "New app installed")
            }

            Intent.ACTION_PACKAGE_FULLY_REMOVED, Intent.ACTION_PACKAGE_REMOVED -> {
                val pkg = intent.data?.schemeSpecificPart ?: return
                if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
                EventReporter.recordApp(c, ActivityEvent.APP_UNINSTALLED, pkg, "App removed")
            }
        }
    }

    companion object {
        fun register(ctx: Context): DeviceEventReceiver {
            val r = DeviceEventReceiver()

            ctx.registerReceiver(r, IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            })

            // Package events carry a data scheme, so they need their own filter.
            ctx.registerReceiver(r, IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
                addDataScheme("package")
            })

            return r
        }

        fun unregister(ctx: Context, r: DeviceEventReceiver?) {
            if (r == null) return
            try { ctx.unregisterReceiver(r) } catch (_: Exception) {}
        }
    }
}
