package com.microbeaver.guardian.fcm

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.microbeaver.guardian.App
import com.microbeaver.guardian.Prefs
import com.microbeaver.guardian.R
import com.microbeaver.guardian.monitor.MonitorService
import com.microbeaver.guardian.ui.MainActivity

/**
 * FCM command channel.
 *
 * • onNewToken   → stores the device FCM token under devices/{pairCode}/fcmToken
 *                  so the parent app / a Cloud Function can target this child device.
 * • onMessageReceived → wakes MonitorService on child; shows a heads-up alert on parent.
 *
 * IMPORTANT: google-services.json contains placeholder values.
 *            Replace it with the real file from your Firebase Console
 *            for FCM to work in production.
 */
class CommandMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Log.d(TAG, "FCM token refreshed")
        // Persist token under this device's pair-code node so the parent / Cloud Function
        // can send targeted pushes to this specific child device.
        val code = Prefs.getPairCode(this) ?: run {
            Log.w(TAG, "onNewToken: pair code not set yet — token not stored")
            return
        }
        FirebaseDatabase.getInstance()
            .getReference("devices/$code/fcmToken")
            .setValue(token)
            .addOnSuccessListener { Log.d(TAG, "FCM token stored for $code") }
            .addOnFailureListener { e -> Log.e(TAG, "Failed to store FCM token: ${e.message}") }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d(TAG, "FCM message from ${message.from} — data=${message.data}")

        val role = Prefs.getRole(this)

        // Child device: wake MonitorService so the pending command is applied
        // even when the phone is in deep-doze mode.
        if (role == Prefs.ROLE_CHILD) {
            MonitorService.start(this)
        }

        // Show a visible heads-up notification on any device (parent or child).
        // For the parent this is the main alert; for the child it acknowledges
        // that a command was received.
        val title = message.notification?.title
            ?: message.data["title"]
            ?: getString(R.string.app_name)
        val body = message.notification?.body
            ?: message.data["body"]
            ?: if (role == Prefs.ROLE_PARENT) "New message" else "Alert"

        showAlertNotification(title, body)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun showAlertNotification(title: String, body: String) {
        val nm = getSystemService(NotificationManager::class.java) ?: return

        // Tap notification → open the parent shell (no-op if it is already open)
        val tapIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, App.CH_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)   // monochrome white vector — no ic_launcher
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .build()

        // Use a stable-ish ID so rapid pushes don't flood the notification drawer
        nm.notify(NOTIF_ID, notification)
    }

    companion object {
        private const val TAG     = "CmdMessagingService"
        private const val NOTIF_ID = 1001
    }
}
