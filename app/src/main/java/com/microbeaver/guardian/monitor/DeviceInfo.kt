package com.microbeaver.guardian.monitor

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager

/**
 * Facts about the child device that the parent screen shows: battery level and
 * the list of apps that can actually be launched.
 */
object DeviceInfo {

    data class Battery(val percent: Int, val charging: Boolean)

    /**
     * Battery level, read from the sticky ACTION_BATTERY_CHANGED broadcast so no
     * receiver has to stay registered.
     *
     * @return percent -1 when it cannot be read.
     */
    fun battery(ctx: Context): Battery {
        return try {
            val i: Intent? = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (i == null) return Battery(-1, false)
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            val pct = if (level >= 0 && scale > 0) level * 100 / scale else -1
            Battery(pct, charging)
        } catch (_: Exception) {
            Battery(-1, false)
        }
    }

    /**
     * Apps with a launcher entry, i.e. the ones a child can actually open.
     * System components without an icon are irrelevant to a parent and would
     * bury the useful entries.
     *
     * @return package name -> app label, excluding ourselves.
     */
    fun launchableApps(ctx: Context): Map<String, String> {
        val pm = ctx.packageManager
        val out = HashMap<String, String>()
        try {
            val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            @Suppress("DEPRECATION")
            val resolved = pm.queryIntentActivities(main, PackageManager.MATCH_ALL)
            for (ri in resolved) {
                val pkg = ri.activityInfo?.packageName ?: continue
                if (pkg == ctx.packageName) continue
                val label = try {
                    ri.loadLabel(pm).toString()
                } catch (_: Exception) {
                    pkg.substringAfterLast('.')
                }
                out[pkg] = label
            }
        } catch (_: Exception) {
        }
        return out
    }
}
