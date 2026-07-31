package com.microbeaver.guardian.alerts

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.microbeaver.guardian.App
import com.microbeaver.guardian.R
import com.microbeaver.guardian.calls.CallerIdLookup
import com.microbeaver.guardian.data.ActivityEvent
import com.microbeaver.guardian.data.Alert
import kotlin.math.absoluteValue

/**
 * Turns an [Alert] from the child into a heads-up notification on the parent's
 * phone, with the one action that is actually useful for that alert type:
 * a map link for location events, a Truecaller lookup for unknown callers.
 */
object AlertNotifier {

    fun canPost(ctx: Context): Boolean =
        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                ctx, android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    /**
     * @return true when the notification was actually handed to Android. Callers
     *         need to know: this silently returned when the permission was
     *         missing, so a child's SOS was received, marked as seen, and then
     *         thrown away with nothing shown on screen.
     */
    fun show(ctx: Context, alert: Alert): Boolean {
        if (!canPost(ctx)) return false
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return false

        val id = (alert.id.takeIf { it.isNotBlank() } ?: alert.ts.toString())
            .hashCode().absoluteValue

        val b = NotificationCompat.Builder(ctx, App.CH_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(alert.title.ifBlank { "تنبيه / Alert" })
            .setContentText(alert.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.body))
            .setWhen(alert.ts)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        when (alert.type) {
            Alert.SOS -> {
                b.setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setOngoing(false)
                addMapAction(ctx, b, alert, id)
            }
            Alert.ZONE_EXIT, Alert.ZONE_ENTER -> addMapAction(ctx, b, alert, id)
            Alert.UNKNOWN_CALL, Alert.BLOCKED_CALL -> addCallerActions(ctx, b, alert, id)
        }

        return try {
            NotificationManagerCompat.from(ctx).notify(id, b.build())
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun addMapAction(ctx: Context, b: NotificationCompat.Builder, a: Alert, id: Int) {
        if (a.lat == 0.0 && a.lng == 0.0) return
        val map = Intent(Intent.ACTION_VIEW, Uri.parse("geo:${a.lat},${a.lng}?q=${a.lat},${a.lng}"))
        val pi = PendingIntent.getActivity(
            ctx, id, map, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        b.addAction(0, "الخريطة / Map", pi).setContentIntent(pi)
    }

    private fun addCallerActions(ctx: Context, b: NotificationCompat.Builder, a: Alert, id: Int) {
        if (a.number.isBlank()) return

        // Who is this? -> hand the number to Truecaller (or their web search).
        val lookup = CallerIdLookup.truecallerLookupIntent(ctx, a.number)
        val lookupPi = PendingIntent.getActivity(
            ctx, id * 2, lookup,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        b.addAction(0, "من هذا؟ / Who is this?", lookupPi)

        // Call the number back yourself.
        val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${a.number}"))
        val dialPi = PendingIntent.getActivity(
            ctx, id * 2 + 1, dial,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        b.addAction(0, "اتصال / Call", dialPi)
    }

    /**
     * A live-feed event, on the quieter [App.CH_ACTIVITY] channel.
     *
     * Events share one notification id per type so a burst replaces itself rather
     * than stacking twenty rows in the shade — the parent sees the latest app
     * opened, not a scroll of every switch.
     */
    fun showEvent(ctx: Context, e: ActivityEvent): Boolean {
        if (!canPost(ctx)) return false
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return false

        val b = NotificationCompat.Builder(ctx, App.CH_ACTIVITY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(e.title.ifBlank { ActivityEvent.label(e.type) })
            .setContentText(e.detail.ifBlank { ActivityEvent.label(e.type) })
            .setWhen(e.ts)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setGroup(GROUP_ACTIVITY)

        return try {
            NotificationManagerCompat.from(ctx).notify(e.type.hashCode().absoluteValue, b.build())
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private const val GROUP_ACTIVITY = "guardian_activity"
}
