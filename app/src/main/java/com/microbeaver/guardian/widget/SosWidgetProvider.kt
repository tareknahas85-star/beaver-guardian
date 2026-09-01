package com.microbeaver.guardian.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.microbeaver.guardian.R
import com.microbeaver.guardian.monitor.SosReporter

/**
 * SOS Widget — one-tap emergency alert from home screen.
 * Sends alert + current GPS position to parent via Firebase.
 */
class SosWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        ctx: Context,
        manager: AppWidgetManager,
        ids: IntArray
    ) {
        val intent = Intent(ctx, SosWidgetProvider::class.java).apply {
            action = ACTION_SOS_TAP
        }
        val pi = PendingIntent.getBroadcast(
            ctx, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        for (id in ids) {
            manager.updateAppWidget(id, android.widget.RemoteViews(ctx.packageName, R.layout.widget_sos).apply {
                setOnClickPendingIntent(R.id.btnSosWidget, pi)
            })
        }
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == ACTION_SOS_TAP) {
            Toast.makeText(ctx, "SOS sent — parent alerted", Toast.LENGTH_LONG).show()
            SosReporter.trigger(ctx)
        }
        super.onReceive(ctx, intent)
    }

    companion object {
        const val ACTION_SOS_TAP = "com.microbeaver.guardian.SOS_TAP"
        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(
                ComponentName(ctx, SosWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) {
                val provider = SosWidgetProvider()
                provider.onUpdate(ctx, mgr, ids)
            }
        }
    }
}
