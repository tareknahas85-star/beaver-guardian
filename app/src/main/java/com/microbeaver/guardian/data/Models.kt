package com.microbeaver.guardian.data

/** A one-shot instruction from parent to child device. */
data class Command(
    var id: String = "",
    var type: String = "",     // LOCK_NOW, UNLOCK, BLOCK_INTERNET, ALLOW_INTERNET, BLOCK_APP, UNBLOCK_APP, LOCATE
    var payload: String = "",  // e.g. a package name for BLOCK_APP
    var ts: Long = 0L,
    var done: Boolean = false
)

/** The desired supervision state; the child continuously enforces it. */
data class Policy(
    var internetBlocked: Boolean = false,
    var locked: Boolean = false,
    var blockedApps: List<String> = emptyList(),   // package names, always blocked
    var limits: List<String> = emptyList(),         // "com.package=30"  -> 30 min/day
    var blockedDomains: List<String> = emptyList()
)

/** Call metadata only (number/time/duration). Audio recording is not possible. */
data class CallRecord(
    var number: String = "",
    var type: String = "",     // incoming / outgoing / missed / rejected
    var ts: Long = 0L,
    var durationSec: Long = 0L
)
