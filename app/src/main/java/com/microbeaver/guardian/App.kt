package com.microbeaver.guardian

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.google.firebase.auth.FirebaseAuth

/**
 * Application class — runs once at process start.
 *
 * IMPORTANT: google-services.json contains placeholder values.
 *            Replace it with the real file from your Firebase Console
 *            before building a production APK.
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()

        // ── Notification channels ─────────────────────────────────────────────
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)

            // Background monitoring (low priority — persistent foreground notification)
            nm.createNotificationChannel(
                NotificationChannel(CH_MONITOR, "Supervision", NotificationManager.IMPORTANCE_LOW)
            )
            // VPN / internet filter indicator (min priority)
            nm.createNotificationChannel(
                NotificationChannel(CH_VPN, "Internet filter", NotificationManager.IMPORTANCE_MIN)
            )
            // Parent alerts — high priority, heads-up style
            nm.createNotificationChannel(
                NotificationChannel(CH_ALERTS, "Alerts / تنبيهات", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "تنبيهات فورية لوليّ الأمر / Instant alerts for the parent"
                }
            )
        }

        // ── Anonymous Firebase Auth ───────────────────────────────────────────
        // Ensures every install has a Firebase UID even before the user logs in.
        // This is required for Realtime Database security rules that check auth != null.
        // NOTE: Replace google-services.json with the real file from Firebase Console
        //       for authentication to work in production.
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            auth.signInAnonymously()
                .addOnSuccessListener { result ->
                    Log.d(TAG, "Anonymous auth OK — uid=${result.user?.uid}")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Auth failed: ${e.message}")
                }
        }
    }

    companion object {
        private const val TAG = "BeaverGuardian"

        const val CH_MONITOR = "monitor"
        const val CH_VPN     = "vpn_filter"
        const val CH_ALERTS  = "alerts"          // high-priority parent notifications
    }
}
