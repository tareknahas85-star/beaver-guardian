package com.microbeaver.guardian.fcm

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.microbeaver.guardian.Prefs
import com.microbeaver.guardian.monitor.MonitorService

/**
 * Optional wake channel. A push from the parent guarantees the child's
 * MonitorService is running so a command is applied even in deep doze.
 */
class CommandMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        if (Prefs.getRole(this) == Prefs.ROLE_CHILD) {
            MonitorService.start(this)
        }
    }

    override fun onNewToken(token: String) {
        // A production build stores this token under the device node so the
        // parent app / a Cloud Function can target this specific child device.
    }
}
