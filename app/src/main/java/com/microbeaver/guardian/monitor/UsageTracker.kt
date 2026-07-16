package com.microbeaver.guardian.monitor

import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.Calendar

object UsageTracker {
    /** Foreground time per package in MINUTES since midnight today. */
    fun todayUsageMinutes(ctx: Context): Map<String, Int> {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyMap()
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        val end = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            ?: return emptyMap()
        val map = HashMap<String, Int>()
        for (s in stats) {
            val min = (s.totalTimeInForeground / 60000L).toInt()
            if (min > 0) map[s.packageName] = (map[s.packageName] ?: 0) + min
        }
        return map
    }
}
