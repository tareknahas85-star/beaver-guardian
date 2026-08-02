package com.microbeaver.guardian.ui

import com.microbeaver.guardian.data.ActivityEvent
import com.microbeaver.guardian.data.Alert
import com.microbeaver.guardian.data.Policy

/**
 * The parent's live view of the child device, shared by all four tabs.
 *
 * [MainActivity] attaches the Firebase listeners once and pushes updates in here.
 * Fragments subscribe and re-render. Doing it this way means switching tabs does
 * not tear down and rebuild listeners, which used to cost a visible reload and a
 * round trip every time.
 */
object GuardianState {

    data class Snapshot(
        val code: String = "",
        val model: String = "",
        val lastSeen: Long = 0L,
        val policy: Policy = Policy(),
        /** package name -> minutes used today */
        val usage: Map<String, Int> = emptyMap(),
        val alerts: List<Alert> = emptyList(),
        /** Live feed, newest first. */
        val events: List<ActivityEvent> = emptyList(),
        val lat: Double = 0.0,
        val lng: Double = 0.0,
        val locationTs: Long = 0L,
        /** -1 when the child has not reported it. */
        val battery: Int = -1,
        val charging: Boolean = false,
        /**
         * Real, child-reported state of the two one-time OS grants that
         * BLOCK_INTERNET and LOCK_NOW depend on. Without these, both commands
         * are silently accepted by [com.microbeaver.guardian.monitor.MonitorService]
         * and do nothing — there used to be no way for the parent to see why.
         */
        val adminActive: Boolean = false,
        val vpnReady: Boolean = false,
        /** package name -> label, as reported by the child device. */
        val installedApps: Map<String, String> = emptyMap(),
        /** Set when the database could not be reached, for display. */
        val error: String? = null
    ) {
        val isOnline: Boolean
            get() = lastSeen > 0L && System.currentTimeMillis() - lastSeen < 4 * 60_000L

        val minutesSinceSeen: Long
            get() = if (lastSeen == 0L) -1 else (System.currentTimeMillis() - lastSeen) / 60_000

        val hasLocation: Boolean get() = lat != 0.0 || lng != 0.0
    }

    @Volatile
    var snapshot = Snapshot()
        private set

    private val listeners = mutableListOf<(Snapshot) -> Unit>()

    fun observe(l: (Snapshot) -> Unit) {
        synchronized(listeners) { listeners.add(l) }
        l(snapshot)
    }

    fun stopObserving(l: (Snapshot) -> Unit) {
        synchronized(listeners) { listeners.remove(l) }
    }

    /** Applies a change and notifies every tab. */
    fun update(transform: (Snapshot) -> Snapshot) {
        snapshot = transform(snapshot)
        val copy = synchronized(listeners) { listeners.toList() }
        copy.forEach { it(snapshot) }
    }

    fun reset() {
        snapshot = Snapshot()
        synchronized(listeners) { listeners.clear() }
    }

    // ── Formatting helpers used by more than one tab ───────────────────────────

    fun formatDuration(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    /** How long ago, in words. */
    fun relativeTime(ts: Long): String {
        if (ts == 0L) return "—"
        val min = (System.currentTimeMillis() - ts) / 60_000
        return when {
            min < 1    -> "just now"
            min < 60   -> "$min min ago"
            min < 1440 -> "${min / 60} h ago"
            else       -> "${min / 1440} d ago"
        }
    }
}
