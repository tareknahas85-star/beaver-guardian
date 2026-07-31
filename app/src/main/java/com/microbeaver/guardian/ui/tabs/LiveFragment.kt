package com.microbeaver.guardian.ui.tabs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.microbeaver.guardian.R
import com.microbeaver.guardian.data.ActivityEvent
import com.microbeaver.guardian.databinding.FragmentLiveBinding
import com.microbeaver.guardian.ui.GuardianState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The live feed: everything that happens on the child device, newest first, plus
 * control over which of those events also raise a notification.
 *
 * ## Why notifications are opt-in per type
 * A child changes app hundreds of times a day. Notifying on every one produces a
 * stream the parent mutes within an hour, and a muted channel then hides the
 * things that actually matter — an SOS, an unknown caller. So everything is
 * always written to this feed, and the parent picks what is worth a buzz.
 * Installs, removals, blocked-app attempts and unlocks are on by default.
 */
class LiveFragment : TabBase() {

    private var _b: FragmentLiveBinding? = null
    private val b get() = _b!!

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentLiveBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        b.tvToggleChooser.setOnClickListener { chooseEventTypes() }
    }

    override fun render(s: GuardianState.Snapshot) {
        val chosen = s.policy.notifyOnEvents
        b.tvNotifySummary.text = if (chosen.isEmpty()) {
            "Nothing. Events are logged below but your phone stays quiet."
        } else {
            chosen.joinToString(", ") { ActivityEvent.label(it) }
        }

        b.tvLiveHint.text = when {
            !s.policy.activityFeedEnabled ->
                "Recording is off. Turn the activity feed on in Settings."
            s.events.isEmpty() && s.lastSeen == 0L ->
                "Nothing yet — pair the child device first."
            s.events.isEmpty() ->
                "Nothing yet. App opens need the Accessibility service on the child phone (button 2)."
            else -> "${s.events.size} recent events"
        }

        renderFeed(s)
    }

    private fun renderFeed(s: GuardianState.Snapshot) {
        val hostView = b.feedHost
        hostView.removeAllViews()
        if (s.events.isEmpty()) return

        val time = SimpleDateFormat("HH:mm", Locale.US)
        val day = SimpleDateFormat("MMM d", Locale.US)
        val today = day.format(Date())
        val inflater = LayoutInflater.from(context)

        for (e in s.events.sortedByDescending { it.ts }.take(60)) {
            val row = inflater.inflate(R.layout.item_event, hostView, false)
            row.findViewById<TextView>(R.id.tvIcon).text = icon(e.type)
            row.findViewById<TextView>(R.id.tvEventTitle).text =
                e.title.ifBlank { ActivityEvent.label(e.type) }

            val stamp = day.format(Date(e.ts)).let { d ->
                if (d == today) time.format(Date(e.ts)) else "$d ${time.format(Date(e.ts))}"
            }
            val meta = listOfNotNull(
                stamp,
                ActivityEvent.label(e.type),
                e.detail.takeIf { it.isNotBlank() }
            ).joinToString("  ·  ")
            row.findViewById<TextView>(R.id.tvEventMeta).text = meta

            hostView.addView(row)
        }
    }

    private fun icon(type: String) = when (type) {
        ActivityEvent.APP_OPENED      -> "▶"
        ActivityEvent.APP_BLOCKED     -> "⛔"
        ActivityEvent.APP_INSTALLED   -> "＋"
        ActivityEvent.APP_UNINSTALLED -> "－"
        ActivityEvent.UNLOCK          -> "🔓"
        ActivityEvent.SCREEN_ON       -> "☀"
        ActivityEvent.SCREEN_OFF      -> "☾"
        ActivityEvent.CALL            -> "📞"
        ActivityEvent.POWER           -> "⚡"
        ActivityEvent.INTERNET        -> "🌐"
        ActivityEvent.LIMIT_REACHED   -> "⏳"
        ActivityEvent.BOOT            -> "⟳"
        else                          -> "•"
    }

    /** Multi-select of the event types that should notify. */
    private fun chooseEventTypes() {
        val ctx = context ?: return
        val types = ActivityEvent.ALL
        val labels = types.map { ActivityEvent.label(it) }.toTypedArray()
        val current = GuardianState.snapshot.policy.notifyOnEvents.toMutableSet()
        val checked = BooleanArray(types.size) { types[it] in current }

        AlertDialog.Builder(ctx)
            .setTitle("Notify me about")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                if (isChecked) current.add(types[which]) else current.remove(types[which])
            }
            .setPositiveButton("Save") { _, _ ->
                host?.savePolicy { it.notifyOnEvents = current.toList() }
            }
            .setNeutralButton("Everything") { _, _ ->
                host?.savePolicy { it.notifyOnEvents = ActivityEvent.ALL }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }
}
