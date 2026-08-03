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

    /**
     * Every package that can actually open the phone's camera — the default
     * camera app, and any OEM alternative that also registers for it.
     *
     * Exists because DevicePolicyManager.setCameraDisabled() (see
     * PolicyManager.setCameraDisabled) is standard Android and is called
     * correctly, but some manufacturers' own camera app is known not to honour
     * it for a plain Device Admin on every build. This gives
     * [com.microbeaver.guardian.monitor.MonitorService] a second, independent
     * way to enforce "camera off" — through the same accessibility-based
     * app-blocking already used for ordinary restrictions — rather than
     * relying solely on a manufacturer respecting the DPM flag.
     */
    fun cameraPackages(ctx: Context): Set<String> {
        val pm = ctx.packageManager
        val out = HashSet<String>()
        try {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(
                Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE), PackageManager.MATCH_ALL
            ).forEach { ri -> ri.activityInfo?.packageName?.let { out.add(it) } }
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(
                Intent(android.provider.MediaStore.ACTION_VIDEO_CAPTURE), PackageManager.MATCH_ALL
            ).forEach { ri -> ri.activityInfo?.packageName?.let { out.add(it) } }
        } catch (_: Exception) {
        }
        return out
    }
}
