package com.microbeaver.guardian.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.microbeaver.guardian.Prefs
import com.microbeaver.guardian.alerts.ParentAlertService
import com.microbeaver.guardian.data.ActivityEvent

/**
 * Brings supervision back after a reboot.
 *
 * Both roles need this, not just the child. The parent's alert listener is a
 * foreground service, and after a restart nothing started it again until the
 * parent happened to open the app — one of the reasons alerts arrived late or not
 * at all.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (Prefs.getPairCode(context) == null) return

        when (Prefs.getRole(context)) {
            Prefs.ROLE_CHILD -> {
                MonitorService.start(context)
                EventReporter.record(context, ActivityEvent.BOOT, "Phone restarted")
            }
            Prefs.ROLE_PARENT -> ParentAlertService.start(context)
        }
    }
}
