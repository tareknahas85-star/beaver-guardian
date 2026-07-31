package com.microbeaver.guardian.monitor

import com.microbeaver.guardian.data.ScheduleRule
import java.util.Calendar

/**
 * Works out which time-based rules apply right now.
 *
 * The child evaluates this locally every minute, so restrictions kick in even
 * when the device is offline — the parent's schedule is part of the policy
 * snapshot rather than something pushed at the moment it should take effect.
 */
object ScheduleEvaluator {

    /** The combined effect of every rule active at this instant. */
    data class Effect(
        val active: Boolean,
        val blockInternet: Boolean,
        val lockDevice: Boolean,
        val blockedApps: Set<String>,
        val activeRuleNames: List<String>
    ) {
        companion object {
            val NONE = Effect(false, false, false, emptySet(), emptyList())
        }
    }

    fun evaluate(rules: List<ScheduleRule>, now: Calendar = Calendar.getInstance()): Effect {
        if (rules.isEmpty()) return Effect.NONE

        val day = now.get(Calendar.DAY_OF_WEEK)
        val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        val active = rules.filter { it.isActiveAt(day, minute) }
        if (active.isEmpty()) return Effect.NONE

        return Effect(
            active = true,
            blockInternet = active.any { it.blockInternet },
            lockDevice = active.any { it.lockDevice },
            blockedApps = active.flatMap { it.blockedApps }.toSet(),
            activeRuleNames = active.map { it.name }
        )
    }

    /** "22:00" for 1320. */
    fun formatMinute(minuteOfDay: Int): String {
        val h = (minuteOfDay / 60) % 24
        val m = minuteOfDay % 60
        return String.format("%02d:%02d", h, m)
    }

    /** Handy presets for the parent UI. */
    fun bedtime(): ScheduleRule = ScheduleRule(
        id = "bedtime",
        name = "Bedtime",
        daysMask = ScheduleRule.EVERY_DAY,
        startMinute = 22 * 60,      // 22:00
        endMinute = 7 * 60,         // 07:00 next morning
        blockInternet = true,
        lockDevice = true
    )

    fun studyTime(): ScheduleRule = ScheduleRule(
        id = "study",
        name = "Study time",
        daysMask = ScheduleRule.maskOf(
            Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY,
            Calendar.WEDNESDAY, Calendar.THURSDAY
        ),
        startMinute = 16 * 60,      // 16:00
        endMinute = 18 * 60,        // 18:00
        blockInternet = false,
        lockDevice = false
    )
}
