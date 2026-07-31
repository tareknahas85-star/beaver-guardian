package com.microbeaver.guardian.ui.tabs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.microbeaver.guardian.alerts.AlertNotifier
import com.microbeaver.guardian.data.Alert
import com.microbeaver.guardian.databinding.FragmentDashboardBinding
import com.microbeaver.guardian.ui.GuardianState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Tab 1 — the at-a-glance view: where, how long, and what just happened. */
class DashboardFragment : TabBase() {

    private var _b: FragmentDashboardBinding? = null
    private val b get() = _b!!

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentDashboardBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        b.btnLock.setOnClickListener   { host?.sendCommand("LOCK_NOW", "Lock sent") }
        b.btnUnlock.setOnClickListener { host?.sendCommand("UNLOCK", "Unlock sent") }
        b.btnNetOff.setOnClickListener { host?.sendCommand("BLOCK_INTERNET", "Internet paused") }
        b.btnNetOn.setOnClickListener  { host?.sendCommand("ALLOW_INTERNET", "Internet resumed") }
        b.btnFixNotif.setOnClickListener { host?.openNotificationSettings() }

        b.btnTestNotif.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            val ok = AlertNotifier.show(
                ctx,
                Alert(
                    id = "test-${System.currentTimeMillis()}",
                    type = Alert.SOS,
                    title = "Test alert",
                    body = "If you can see this, alerts are working.",
                    ts = System.currentTimeMillis()
                )
            )
            Toast.makeText(
                ctx,
                if (ok) "Sent — check your notifications"
                else "Blocked. Notifications are off for this app.",
                Toast.LENGTH_LONG
            ).show()
            refreshNotifWarning()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshNotifWarning()
    }

    private fun refreshNotifWarning() {
        val ctx = context ?: return
        b.boxNotifWarning.visibility =
            if (AlertNotifier.canPost(ctx)) View.GONE else View.VISIBLE
    }

    override fun render(s: GuardianState.Snapshot) {
        // ── Location + connection ──
        b.tvLocation.text = when {
            s.error != null   -> "Cannot connect"
            s.hasLocation     -> "%.4f, %.4f".format(s.lat, s.lng)
            s.lastSeen == 0L  -> "Not paired yet"
            else              -> "No location yet"
        }
        b.tvZoneState.text = s.error
            ?: if (s.hasLocation) "Updated ${GuardianState.relativeTime(s.locationTs)}" else ""
        b.tvConnection.text = if (s.isOnline) "Online" else "Offline"
        b.tvBattery.text = when {
            s.battery < 0   -> "—"
            s.charging      -> "${s.battery}% ⚡"
            else            -> "${s.battery}%"
        }

        // ── Screen time ring ──
        val total = s.usage.values.sum()
        val limit = dailyLimitMinutes(s)
        b.tvUsedTotal.text = fmt(total)
        b.tvLimit.text = if (limit > 0) "Limit ${fmt(limit)}" else "No daily limit set"
        if (limit > 0) {
            b.ring.progress = total.toFloat() / limit
            val left = limit - total
            b.tvRemaining.text = if (left >= 0) "${fmt(left)} left" else "over by ${fmt(-left)}"
        } else {
            b.ring.progress = 0f
            b.tvRemaining.text = ""
        }

        b.tvTopApps.text = if (s.usage.isEmpty()) {
            "No usage data yet.\n\nIf the phone has been in use, Usage Access is probably not " +
                "granted. On the child phone, open the app and use button 1."
        } else {
            s.usage.entries.sortedByDescending { it.value }.take(4)
                .joinToString("\n") { (pkg, min) -> "${appLabel(pkg)}  ·  ${fmt(min)}" }
        }

        // ── Feed ──
        b.tvFeed.text = if (s.alerts.isEmpty()) "Nothing yet" else {
            val f = SimpleDateFormat("MMM d, HH:mm", Locale.US)
            s.alerts.take(5).joinToString("\n\n") { a ->
                "${f.format(Date(a.ts))}\n${a.title}"
            }
        }
    }

    /**
     * The whole-device budget the parent set in Settings. Falls back to the
     * largest per-app limit so the ring still means something on a policy that
     * predates the budget field.
     */
    private fun dailyLimitMinutes(s: GuardianState.Snapshot): Int =
        if (s.policy.dailyLimitMinutes > 0) s.policy.dailyLimitMinutes
        else s.policy.limits.mapNotNull { it.split("=").getOrNull(1)?.toIntOrNull() }.maxOrNull() ?: 0

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }
}
