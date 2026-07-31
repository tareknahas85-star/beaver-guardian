package com.microbeaver.guardian.data

import java.util.Calendar

/** A one-shot instruction from parent to child device. */
data class Command(
    var id: String = "",
    var type: String = "",     // LOCK_NOW, UNLOCK, BLOCK_INTERNET, ALLOW_INTERNET, BLOCK_APP, UNBLOCK_APP, LOCATE
    var payload: String = "",  // e.g. a package name for BLOCK_APP
    var ts: Long = 0L,
    var done: Boolean = false
)

/**
 * The desired supervision state; the child continuously enforces it.
 *
 * Every field needs a default value — Firebase deserialises via the no-arg
 * constructor and only fills in keys that exist in the snapshot, so older
 * records missing the newer fields still load cleanly.
 */
data class Policy(
    var internetBlocked: Boolean = false,
    var locked: Boolean = false,
    var blockedApps: List<String> = emptyList(),   // package names, always blocked
    var limits: List<String> = emptyList(),        // "com.package=30"  -> 30 min/day
    var blockedDomains: List<String> = emptyList(),

    // ---- Call filtering -------------------------------------------------
    /**
     * One of [CallMode]. A single field on purpose.
     *
     * This used to be two booleans, `blockUnknownCalls` and `allowContacts`, and
     * the combination was genuinely confusing: with both false the filter let
     * everything through, and with allowContacts false it blocked the parent's
     * own saved contacts. Worse, two different screens wrote them, so one screen
     * saving a blank form silently inverted the other's settings. One mode value
     * cannot contradict itself.
     */
    var callMode: String = CallMode.OFF,

    /** Also apply the mode to *outgoing* calls. */
    var restrictOutgoing: Boolean = false,

    /** Extra numbers allowed on top of contacts (E.164 or local form). */
    var allowedNumbers: List<String> = emptyList(),

    /** Numbers always rejected, even if saved in Contacts. */
    var blockedNumbers: List<String> = emptyList(),

    /**
     * Numbers that must always get through, whatever the mode and whatever else
     * is configured. The parent's own line belongs here. Treated as the highest
     * priority rule in [callMode] evaluation — even above [blockedNumbers].
     */
    var priorityNumbers: List<String> = emptyList(),

    // --- Legacy fields, still read so old policies keep working. Do not write. ---
    @Deprecated("Superseded by callMode")
    var callFilterEnabled: Boolean = false,
    @Deprecated("Superseded by callMode")
    var blockUnknownCalls: Boolean = true,
    @Deprecated("Superseded by callMode")
    var allowContacts: Boolean = true,

    // ---- Safe zones / geofencing ----------------------------------------
    var zones: List<GeoZone> = emptyList(),

    // ---- Time-based rules -------------------------------------------------
    var schedules: List<ScheduleRule> = emptyList(),

    // ---- Screen time -------------------------------------------------------
    /**
     * Whole-device daily screen time budget in minutes. 0 means no limit.
     * This is separate from [limits], which caps individual apps.
     */
    var dailyLimitMinutes: Int = 0,

    /** What happens when [dailyLimitMinutes] is used up. */
    var lockWhenLimitReached: Boolean = true,

    // ---- Live activity feed --------------------------------------------------
    /** Record events to the feed at all. */
    var activityFeedEnabled: Boolean = true,
    /**
     * Which event types raise a notification on the parent's phone.
     * Everything is always written to the feed; this only controls buzzing.
     *
     * App switches are deliberately not on by default: a child changes app
     * hundreds of times a day and a notification each time is noise the parent
     * will mute, which then hides the alerts that matter.
     */
    var notifyOnEvents: List<String> = listOf(
        ActivityEvent.APP_INSTALLED,
        ActivityEvent.APP_UNINSTALLED,
        ActivityEvent.APP_BLOCKED,
        ActivityEvent.UNLOCK
    ),

    // ---- Misc --------------------------------------------------------------
    /** Show the SOS button on the child's screen. */
    var sosEnabled: Boolean = true,
    /** Send the parent a weekly usage digest. */
    var weeklyReport: Boolean = true
)

/** How incoming (and optionally outgoing) calls are filtered. */
object CallMode {
    /** No filtering at all. */
    const val OFF = "OFF"

    /** Saved contacts and the allow-list get through; everything else is rejected. */
    const val BLOCK_UNKNOWN = "BLOCK_UNKNOWN"

    /**
     * Only [Policy.priorityNumbers] and [Policy.allowedNumbers] get through.
     * Saved contacts are *not* automatically trusted. The strictest setting.
     */
    const val WHITELIST_ONLY = "WHITELIST_ONLY"

    fun label(mode: String): String = when (mode) {
        BLOCK_UNKNOWN  -> "Block numbers not in contacts"
        WHITELIST_ONLY -> "Only my allowed numbers"
        else           -> "Off"
    }

    /**
     * Reads a mode out of a policy, falling back to the old boolean pair so a
     * device that has not been reconfigured keeps behaving sensibly.
     */
    @Suppress("DEPRECATION")
    fun of(p: Policy): String {
        if (p.callMode.isNotBlank() && p.callMode != OFF) return p.callMode
        if (p.callMode == OFF && !p.callFilterEnabled) return OFF
        // Legacy shape.
        if (!p.callFilterEnabled) return OFF
        return if (p.allowContacts) BLOCK_UNKNOWN else WHITELIST_ONLY
    }
}

/**
 * A circular safe zone (home, school, ...).
 * The child reports an alert when it crosses the boundary.
 */
data class GeoZone(
    var id: String = "",
    var name: String = "",
    var lat: Double = 0.0,
    var lng: Double = 0.0,
    var radiusM: Int = 200,
    var notifyOnExit: Boolean = true,
    var notifyOnEnter: Boolean = false
)

/**
 * A recurring time window during which extra restrictions apply
 * (study time, bedtime, ...).
 *
 * [daysMask] is a bit set of [Calendar.SUNDAY]..[Calendar.SATURDAY] shifted by
 * the calendar constant, i.e. bit (Calendar.MONDAY) = Monday. Use [matchesDay].
 *
 * Times are minutes since midnight, local time. A window whose [endMinute] is
 * less than or equal to [startMinute] is treated as crossing midnight
 * (e.g. 22:00 -> 07:00 for bedtime).
 */
data class ScheduleRule(
    var id: String = "",
    var name: String = "",
    var enabled: Boolean = true,
    var daysMask: Int = 0,
    var startMinute: Int = 0,
    var endMinute: Int = 0,
    var blockInternet: Boolean = false,
    var lockDevice: Boolean = false,
    var blockedApps: List<String> = emptyList()
) {
    fun matchesDay(calendarDay: Int): Boolean = (daysMask shr calendarDay) and 1 == 1

    /** True when [minuteOfDay] on [calendarDay] falls inside this window. */
    fun isActiveAt(calendarDay: Int, minuteOfDay: Int): Boolean {
        if (!enabled) return false
        val crossesMidnight = endMinute <= startMinute
        return if (!crossesMidnight) {
            matchesDay(calendarDay) && minuteOfDay >= startMinute && minuteOfDay < endMinute
        } else {
            // Evening part belongs to today, morning part to the previous day's rule.
            val prevDay = if (calendarDay == Calendar.SUNDAY) Calendar.SATURDAY else calendarDay - 1
            (matchesDay(calendarDay) && minuteOfDay >= startMinute) ||
                (matchesDay(prevDay) && minuteOfDay < endMinute)
        }
    }

    companion object {
        fun maskOf(vararg calendarDays: Int): Int =
            calendarDays.fold(0) { acc, d -> acc or (1 shl d) }

        val EVERY_DAY: Int = maskOf(
            Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
            Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY
        )
    }
}

/** Call metadata only (number/time/duration). Audio recording is not possible. */
data class CallRecord(
    var number: String = "",
    var type: String = "",     // incoming / outgoing / missed / rejected
    var ts: Long = 0L,
    var durationSec: Long = 0L,
    /** Filled in when the call was rejected by our own screening service. */
    var blockedByFilter: Boolean = false,
    /** Contact display name, or "" when the number is unknown. */
    var contactName: String = ""
)

/**
 * Something the parent should see right away: an unknown caller, a safe-zone
 * breach, an SOS press. Written by the child, read by the parent.
 */
data class Alert(
    var id: String = "",
    var type: String = "",     // UNKNOWN_CALL, BLOCKED_CALL, ZONE_EXIT, ZONE_ENTER, SOS, WEEKLY_REPORT
    var title: String = "",
    var body: String = "",
    var number: String = "",
    var zoneName: String = "",
    var lat: Double = 0.0,
    var lng: Double = 0.0,
    var ts: Long = 0L,
    var seen: Boolean = false
) {
    companion object {
        const val UNKNOWN_CALL  = "UNKNOWN_CALL"
        const val BLOCKED_CALL  = "BLOCKED_CALL"
        const val ZONE_EXIT     = "ZONE_EXIT"
        const val ZONE_ENTER    = "ZONE_ENTER"
        const val SOS           = "SOS"
        const val WEEKLY_REPORT = "WEEKLY_REPORT"
    }
}


/**
 * One thing that happened on the child device, for the live feed.
 *
 * Kept separate from [Alert]: alerts are exceptional and always notify, events
 * are the ordinary running commentary. Mixing them meant the important ones got
 * lost in the stream.
 */
data class ActivityEvent(
    var id: String = "",
    var type: String = "",
    /** Human-readable, already localised by the child device. */
    var title: String = "",
    var detail: String = "",
    /** Package name for app events, else "". */
    var pkg: String = "",
    var ts: Long = 0L
) {
    companion object {
        const val APP_OPENED      = "APP_OPENED"
        const val APP_BLOCKED     = "APP_BLOCKED"
        const val APP_INSTALLED   = "APP_INSTALLED"
        const val APP_UNINSTALLED = "APP_UNINSTALLED"
        const val SCREEN_ON       = "SCREEN_ON"
        const val SCREEN_OFF      = "SCREEN_OFF"
        const val UNLOCK          = "UNLOCK"
        const val CALL            = "CALL"
        const val POWER           = "POWER"
        const val INTERNET        = "INTERNET"
        const val LIMIT_REACHED   = "LIMIT_REACHED"
        const val BOOT            = "BOOT"

        /** Everything the parent can choose to be notified about. */
        val ALL = listOf(
            APP_OPENED, APP_BLOCKED, APP_INSTALLED, APP_UNINSTALLED,
            UNLOCK, SCREEN_ON, SCREEN_OFF, CALL, POWER, INTERNET,
            LIMIT_REACHED, BOOT
        )

        fun label(type: String): String = when (type) {
            APP_OPENED      -> "App opened"
            APP_BLOCKED     -> "Blocked app attempt"
            APP_INSTALLED   -> "App installed"
            APP_UNINSTALLED -> "App removed"
            UNLOCK          -> "Phone unlocked"
            SCREEN_ON       -> "Screen on"
            SCREEN_OFF      -> "Screen off"
            CALL            -> "Call"
            POWER           -> "Charger"
            INTERNET        -> "Internet changed"
            LIMIT_REACHED   -> "Time limit reached"
            BOOT            -> "Phone restarted"
            else            -> type
        }
    }
}
