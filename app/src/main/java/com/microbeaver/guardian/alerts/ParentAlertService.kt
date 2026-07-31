package com.microbeaver.guardian.alerts

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.microbeaver.guardian.App
import com.microbeaver.guardian.Prefs
import com.microbeaver.guardian.R
import com.microbeaver.guardian.data.ActivityEvent
import com.microbeaver.guardian.data.Alert
import com.microbeaver.guardian.data.FirebaseRepo
import com.microbeaver.guardian.ui.RoleSelectActivity

/**
 * Keeps a database listener alive on the **parent's** phone so alerts arrive when
 * the app is closed.
 *
 * ## Why a service and not push
 * Alerts used to be watched from inside `ParentActivity`. That listener died the
 * moment the parent left the screen, so an SOS or an unknown caller at 3am
 * reached nobody. The obvious fix is a push message from child to parent, but
 * sending one needs a server key — Firebase does not allow device-to-device push,
 * and Cloud Functions needs a paid plan. A foreground service holding a Realtime
 * Database listener does the same job on the free tier.
 *
 * The service shows a quiet, low-priority notification, which Android requires
 * for a foreground service and which also tells the parent that watching is on.
 */
class ParentAlertService : Service() {

    private var code: String = ""
    private var attached = false

    /** Alerts older than this at startup are shown in-app but not popped up. */
    private var startedAt = 0L

    override fun onCreate() {
        super.onCreate()
        startedAt = System.currentTimeMillis()
        startForeground(NOTIF_ID, statusNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (code.isEmpty()) code = Prefs.getPairCode(this) ?: ""
        if (code.isNotEmpty() && !attached) {
            attached = true
            FirebaseRepo.claimDevice(code) { ok ->
                if (!ok) Log.w(TAG, "claimDevice failed — listening anyway")
                FirebaseRepo.listenAlerts(code) { alert -> onAlert(alert) }

                // Which event types buzz is the parent's choice, so keep the
                // policy in view while listening to the feed.
                FirebaseRepo.listenPolicy(code) { p -> notifyTypes = p.notifyOnEvents.toSet() }
                FirebaseRepo.listenEvents(code) { e -> onEvent(e) }
            }
        }
        return START_STICKY
    }

    /** Types the parent asked to be notified about. Everything is still logged. */
    @Volatile private var notifyTypes: Set<String> = emptySet()

    private fun onEvent(e: ActivityEvent) {
        // Skip the backlog that predates this service starting.
        if (e.ts < startedAt - GRACE_MS) return
        if (e.type !in notifyTypes) return
        AlertNotifier.showEvent(this, e)
    }

    private fun onAlert(alert: Alert) {
        // Skip the backlog that already existed when we attached, and anything
        // the parent has already acknowledged, so opening the app does not
        // replay a week of alerts.
        if (alert.seen) return
        if (alert.ts < startedAt - GRACE_MS) return

        AlertNotifier.show(this, alert)
        FirebaseRepo.markAlertSeen(code, alert.id)
    }

    private fun statusNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, RoleSelectActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, App.CH_MONITOR)
            .setContentTitle("Beaver Guardian")
            .setContentText("Watching for alerts")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(pi)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "ParentAlertService"
        private const val NOTIF_ID = 3

        /** Alerts from the last minute still pop up, to cover a restart. */
        private const val GRACE_MS = 60_000L

        fun start(ctx: Context) {
            val code = Prefs.getPairCode(ctx)
            if (code.isNullOrBlank()) return
            val i = Intent(ctx, ParentAlertService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
                else ctx.startService(i)
            } catch (e: Exception) {
                Log.e(TAG, "start failed: ${e.message}")
            }
        }

        fun stop(ctx: Context) {
            try { ctx.stopService(Intent(ctx, ParentAlertService::class.java)) } catch (_: Exception) {}
        }
    }
}
