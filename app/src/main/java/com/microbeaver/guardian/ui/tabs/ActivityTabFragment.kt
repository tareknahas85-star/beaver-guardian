package com.microbeaver.guardian.ui.tabs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.microbeaver.guardian.R
import com.microbeaver.guardian.databinding.FragmentActivityTabBinding
import com.microbeaver.guardian.monitor.ScheduleEvaluator
import com.microbeaver.guardian.ui.GuardianState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Tab 3 — how the time was actually spent, plus what happened. */
class ActivityTabFragment : TabBase() {

    private var _b: FragmentActivityTabBinding? = null
    private val b get() = _b!!

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentActivityTabBinding.inflate(i, c, false)
        return b.root
    }

    override fun render(s: GuardianState.Snapshot) {
        val total = s.usage.values.sum()
        b.tvTotal.text = if (s.usage.isEmpty()) "—" else fmt(total)

        b.tvBreakdown.text = if (s.usage.isEmpty()) {
            "No usage data yet. Grant Usage Access on the child phone (button 1)."
        } else {
            "${s.usage.size} apps used today"
        }

        renderSchedules(s)
        renderBars(s)

        b.tvTimeline.text = if (s.alerts.isEmpty()) "Nothing yet" else {
            val f = SimpleDateFormat("MMM d, HH:mm", Locale.US)
            s.alerts.take(12).joinToString("\n\n") { a ->
                "${f.format(Date(a.ts))}\n${a.title}\n${a.body}"
            }
        }
    }

    /** Bedtime and study windows, with whichever one is running right now marked. */
    private fun renderSchedules(s: GuardianState.Snapshot) {
        if (s.policy.schedules.isEmpty()) {
            b.tvSchedules.text = "No time rules set. Add bedtime or study time from Settings."
            return
        }
        val active = ScheduleEvaluator.evaluate(s.policy.schedules)
        b.tvSchedules.text = s.policy.schedules.joinToString("\n") { r ->
            val running = r.name in active.activeRuleNames
            val window = "${ScheduleEvaluator.formatMinute(r.startMinute)}" +
                "–${ScheduleEvaluator.formatMinute(r.endMinute)}"
            val effects = listOfNotNull(
                if (r.lockDevice) "locks phone" else null,
                if (r.blockInternet) "no internet" else null
            ).joinToString(", ").ifBlank { "apps only" }
            "${if (running) "● ACTIVE  " else "○ "}${r.name}  ·  $window  ·  $effects"
        }
    }

    /**
     * A bar per app, widths relative to the heaviest one. Done with plain views
     * rather than a chart library — this is five rows of data, not a dashboard.
     */
    private fun renderBars(s: GuardianState.Snapshot) {
        val host = b.barsHost
        host.removeAllViews()
        if (s.usage.isEmpty()) return

        val top = s.usage.entries.sortedByDescending { it.value }.take(8)
        val max = top.first().value.coerceAtLeast(1)
        val inflater = LayoutInflater.from(context)

        for ((pkg, min) in top) {
            val row = inflater.inflate(R.layout.item_usage_bar, host, false)
            row.findViewById<TextView>(R.id.tvName).text = appLabel(pkg)
            row.findViewById<TextView>(R.id.tvValue).text = fmt(min)

            val bar = row.findViewById<View>(R.id.bar)
            // Width is a fraction of the track, applied once the track is measured.
            val track = bar.parent as View
            track.post {
                val w = track.width
                if (w > 0) {
                    bar.layoutParams = (bar.layoutParams as LinearLayout.LayoutParams?
                        ?: LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT))
                        .also { lp ->
                            lp.width = (w * min.toFloat() / max).toInt().coerceAtLeast(6)
                            lp.height = LinearLayout.LayoutParams.MATCH_PARENT
                        }
                    bar.requestLayout()
                }
            }
            host.addView(row)
        }
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }
}
