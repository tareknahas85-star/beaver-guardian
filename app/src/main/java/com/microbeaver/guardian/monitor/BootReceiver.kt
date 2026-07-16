package com.microbeaver.guardian.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.microbeaver.guardian.Prefs

/** Restart supervision after reboot on the child device. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (Prefs.getRole(context) == Prefs.ROLE_CHILD && Prefs.getPairCode(context) != null) {
            MonitorService.start(context)
        }
    }
}
