package com.microbeaver.guardian.ui.tabs

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import com.microbeaver.guardian.R
import com.microbeaver.guardian.alerts.AlertNotifier
import com.microbeaver.guardian.data.ActivityEvent
import com.microbeaver.guardian.data.Alert
import com.microbeaver.guardian.databinding.FragmentDashboardBinding
import com.microbeaver.guardian.ui.GuardianState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * The one screen a parent actually looks at: where the child is, how long the
 * phone has been used, what happened today, and the controls.
 *
 * The old Kids tab was folded in here. It showed the device, its position and its
 * protection status — all of which belong next to the location card rather than
 * behind another tap.
 */
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
        b.btnFixDoze.setOnClickListener { host?.requestNoDoze() }
        b.btnLocate.setOnClickListener { host?.sendCommand("LOCATE", "Asking for a fresh position") }
        b.btnPair.setOnClickListener { host?.openPairingWindow() }
        b.btnMap.setOnClickListener { openMap() }

        b.sliderNetTimer.addOnChangeListener { _, value, _ ->
            b.tvNetTimerValue.text = fmt(value.toInt())
        }
        b.tvNetTimerValue.text = fmt(b.sliderNetTimer.value.toInt())
        b.btnOpenNetTimer.setOnClickListener {
            host?.openInternetTimer(b.sliderNetTimer.value.toInt())
        }

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

    /**
     * Two separate reasons alerts can fail to arrive, so check both: the
     * permission, and whether Android is allowed to freeze our listener. The
     * banner shows if either is a problem.
     */
    private fun refreshNotifWarning() {
        val ctx = context ?: return
        val canPost = AlertNotifier.canPost(ctx)
        val noDoze = host?.isBatteryUnrestricted() ?: true

        b.boxNotifWarning.visibility = if (canPost && noDoze) View.GONE else View.VISIBLE
        b.btnFixNotif.visibility = if (canPost) View.GONE else View.VISIBLE
        b.btnFixDoze.visibility = if (noDoze) View.GONE else View.VISIBLE
    }

    override fun render(s: GuardianState.Snapshot) {
        renderLocation(s)
        renderScreenTime(s)
        renderDevice(s)
        renderNetTimer(s)
        renderTodayFeed(s)
    }

    /**
     * Shows the countdown while a temporary-open window is running, so the
     * parent isn't left guessing whether it's still active.
     */
    private fun renderNetTimer(s: GuardianState.Snapshot) {
        val until = s.policy.internetTimerUntil
        val now = System.currentTimeMillis()
        b.tvNetTimerStatus.text = if (until > now) {
            val remaining = ((until - now) / 60_000L).toInt().coerceAtLeast(1)
            "Internet is open — ${fmt(remaining)} left, then it re-blocks itself"
        } else {
            "Grant a bounded internet window; it turns off by itself when time is up."
        }
    }

    private fun renderLocation(s: GuardianState.Snapshot) {
        b.tvLocation.text = when {
            s.error != null  -> "Cannot connect"
            s.hasLocation    -> "%.4f, %.4f".format(s.lat, s.lng)
            s.lastSeen == 0L -> "Not paired yet"
            else             -> "No location yet"
        }
        b.tvZoneState.text = s.error
            ?: if (s.hasLocation) "Updated ${GuardianState.relativeTime(s.locationTs)}" else ""
        b.tvConnection.text = if (s.isOnline) "Online" else "Offline"
        b.tvBattery.text = when {
            s.battery < 0 -> "—"
            s.charging    -> "${s.battery}% ⚡"
            else          -> "${s.battery}%"
        }
    }

    private fun renderScreenTime(s: GuardianState.Snapshot) {
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
    }

    private fun renderDevice(s: GuardianState.Snapshot) {
        b.tvDevice.text = s.model.ifBlank {
            if (s.lastSeen == 0L) "Not connected" else "Child device"
        }
        b.tvSeen.text = when {
            s.error != null           -> s.error
            s.lastSeen == 0L          -> "Enter the code on the child phone while the window is open."
            s.isOnline                -> "Online now"
            s.minutesSinceSeen < 60   -> "Last seen ${s.minutesSinceSeen} min ago"
            s.minutesSinceSeen < 1440 -> "Last seen ${s.minutesSinceSeen / 60} h ago — check battery settings"
            else                      -> "Offline for ${s.minutesSinceSeen / 1440} days"
        }

        // Reported by the child device itself (see MonitorService.cycle()) —
        // this used to check PolicyManager on the *parent's own* phone, which
        // is a different device and told the parent nothing true about the
        // child's actual protection state.
        b.tvProtection.text = buildString {
            append(if (s.adminActive) "Device Admin: active. " else "Device Admin: NOT active — tap Setup on the child's phone. ")
            append(if (s.vpnReady) "Internet blocking: ready." else "Internet blocking: needs setup — tap \"Start VPN\" on the child's phone.")
        }

        b.tvZonesSummary.text = if (s.policy.zones.isEmpty()) "No safe zones set."
        else "${s.policy.zones.size} safe zone(s): " + s.policy.zones.joinToString(", ") { it.name }
    }

    /**
     * Today's events only, newest first.
     *
     * The parent asked for a list that clears each day: rather than deleting
     * anything, this filters to events since local midnight, so at 00:00 the card
     * is empty again and the underlying history is still there for the Live tab.
     * Deleting would have thrown away the record.
     */
    private fun renderTodayFeed(s: GuardianState.Snapshot) {
        val midnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // Alerts matter as much as events, so show both in one time-ordered list.
        val rows = ArrayList<Triple<Long, String, String>>()
        s.events.filter { it.ts >= midnight }.forEach {
            rows.add(Triple(it.ts, it.title.ifBlank { ActivityEvent.label(it.type) },
                ActivityEvent.label(it.type)))
        }
        s.alerts.filter { it.ts >= midnight }.forEach {
            rows.add(Triple(it.ts, it.title, it.body))
        }
        rows.sortByDescending { it.first }

        b.tvFeedCount.text = if (rows.isEmpty()) "" else "${rows.size}"
        b.tvFeedEmpty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE

        val hostView = b.feedHost
        hostView.removeAllViews()
        if (rows.isEmpty()) return

        val time = SimpleDateFormat("HH:mm", Locale.US)
        val inflater = LayoutInflater.from(context)
        for ((ts, title, meta) in rows.take(30)) {
            val row = inflater.inflate(R.layout.item_event, hostView, false)
            row.findViewById<TextView>(R.id.tvIcon).text = "•"
            row.findViewById<TextView>(R.id.tvEventTitle).text = title
            row.findViewById<TextView>(R.id.tvEventMeta).text =
                "${time.format(Date(ts))}  ·  $meta"
            hostView.addView(row)
        }
    }

    private fun openMap() {
        val s = GuardianState.snapshot
        if (!s.hasLocation) {
            Toast.makeText(context, "No position reported yet", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("geo:${s.lat},${s.lng}?q=${s.lat},${s.lng}(Child)"))
            )
        } catch (_: Exception) {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=${s.lat},${s.lng}"))
            )
        }
    }

    private fun dailyLimitMinutes(s: GuardianState.Snapshot): Int =
        if (s.policy.dailyLimitMinutes > 0) s.policy.dailyLimitMinutes
        else s.policy.limits.mapNotNull { it.split("=").getOrNull(1)?.toIntOrNull() }.maxOrNull() ?: 0

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }
}
