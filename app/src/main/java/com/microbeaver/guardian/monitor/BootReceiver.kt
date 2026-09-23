package com.microbeaver.guardian.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.microbeaver.guardian.Prefs
import com.microbeaver.guardian.alerts.ParentAlertService
import com.microbeaver.guardian.data.ActivityEvent

/**
 * Brings supervision back after a reboot — or after this app updates itself.
 *
 * Both triggers matter equally, and both roles need this, not just the child.
 * MonitorService and ParentAlertService are foreground services; installing a
 * new APK version kills them like any other running service, and nothing
 * restarted them until whoever owned that phone happened to reopen the app.
 * Since this project ships updates often, that meant the app going silent —
 * no notifications, no command execution — after every single release.
 * MY_PACKAGE_REPLACED closes that gap the same way BOOT_COMPLETED does.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (Prefs.getPairCode(context) == null) return

        val isUpdate = intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED
        when (Prefs.getRole(context)) {
            Prefs.ROLE_CHILD -> {
                MonitorService.start(context)
                EventReporter.record(
                    context, ActivityEvent.BOOT,
                    if (isUpdate) "App updated" else "Phone restarted"
                )
            }
            Prefs.ROLE_PARENT -> ParentAlertService.start(context)
        }
    }
}
