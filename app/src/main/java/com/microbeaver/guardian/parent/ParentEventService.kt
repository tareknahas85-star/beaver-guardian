package com.microbeaver.guardian.parent

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.microbeaver.guardian.App
import com.microbeaver.guardian.Prefs
import com.microbeaver.guardian.R
import com.microbeaver.guardian.data.FirebaseRepo

/**
 * Runs on the PARENT device. Keeps a live connection to the child's event feed
 * and raises a high-priority local notification for each new activity — no server
 * or paid plan needed.
 */
class ParentEventService : Service() {

    private val startTs = System.currentTimeMillis()
    private var nextId = 2000

    override fun onCreate() {
        super.onCreate()
        startForeground(3, baseNotification())
        val code = Prefs.getPairCode(this) ?: return
        FirebaseRepo.listenEvents(code) { type, text, ts ->
            if (ts >= startTs - 3000) notifyAlert(type, text)
        }
    }

    private fun notifyAlert(type: String, text: String) {
        val title = when (type) {
            "GEOFENCE_EXIT" -> "⚠️ تنبيه موقع"
            "APP_OPEN" -> "نشاط على جهاز الطفل"
            else -> "Beaver Guardian"
        }
        val n = NotificationCompat.Builder(this, App.CH_ALERT)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(nextId++, n)
        if (nextId > 2900) nextId = 2000
    }

    private fun baseNotification(): Notification =
        NotificationCompat.Builder(this, App.CH_MONITOR)
            .setContentTitle("Beaver Guardian")
            .setContentText("مراقبة نشاط الطفل فعّالة / Live activity monitoring")
            .setSmallIcon(R.drawable.ic_launcher)
            .setOngoing(true)
            .build()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(ctx: Context) {
            val i = Intent(ctx, ParentEventService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }
    }
}
