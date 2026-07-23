package com.microbeaver.guardian.data

/** A one-shot instruction from parent to child device. */
data class Command(
    var id: String = "",
    var type: String = "",
    var payload: String = "",
    var ts: Long = 0L,
    var done: Boolean = false
)

/** The desired supervision state; the child continuously enforces it. */
data class Policy(
    var internetBlocked: Boolean = false,
    var locked: Boolean = false,
    var blockedApps: List<String> = emptyList(),
    var limits: List<String> = emptyList(),
    var blockedDomains: List<String> = emptyList(),
    var geoEnabled: Boolean = false,
    var geoLat: Double = 0.0,
    var geoLng: Double = 0.0,
    var geoRadius: Double = 300.0
)

/** Call metadata only (number/time/duration). */
data class CallRecord(
    var number: String = "",
    var type: String = "",
    var ts: Long = 0L,
    var durationSec: Long = 0L
)
