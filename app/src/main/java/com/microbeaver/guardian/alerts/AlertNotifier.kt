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

    fun show(ctx: Context, alert: Alert) {
        if (!canPost(ctx)) return

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

        try {
            NotificationManagerCompat.from(ctx).notify(id, b.build())
        } catch (_: SecurityException) {
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
}
