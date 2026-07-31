package com.microbeaver.guardian.calls

import android.content.Context
import com.microbeaver.guardian.data.CallMode
import com.microbeaver.guardian.data.Policy

/**
 * A local snapshot of the call-filtering part of the [Policy].
 *
 * [GuardianCallScreeningService] is started cold by the telecom stack and has
 * only a few seconds to answer, far too little to wait on a Firebase round
 * trip. So every time the child receives a policy update we mirror the call
 * rules into SharedPreferences, and screening reads them synchronously.
 */
object CallPolicyStore {
    private const val FILE = "call_policy"

    private const val K_MODE     = "mode"
    private const val K_OUTGOING = "restrictOutgoing"
    private const val K_ALLOWED  = "allowed"
    private const val K_BLOCKED  = "blocked"
    private const val K_PRIORITY = "priority"

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun save(ctx: Context, p: Policy) {
        sp(ctx).edit()
            .putString(K_MODE, CallMode.of(p))
            .putBoolean(K_OUTGOING, p.restrictOutgoing)
            .putStringSet(K_ALLOWED, p.allowedNumbers.toSet())
            .putStringSet(K_BLOCKED, p.blockedNumbers.toSet())
            .putStringSet(K_PRIORITY, p.priorityNumbers.toSet())
            .apply()
    }

    data class Snapshot(
        val mode: String,
        val restrictOutgoing: Boolean,
        val allowed: Set<String>,
        val blocked: Set<String>,
        /** Never blocked, whatever else is set. */
        val priority: Set<String>
    )

    fun load(ctx: Context): Snapshot {
        val s = sp(ctx)
        return Snapshot(
            mode             = s.getString(K_MODE, CallMode.OFF) ?: CallMode.OFF,
            restrictOutgoing = s.getBoolean(K_OUTGOING, false),
            allowed          = s.getStringSet(K_ALLOWED, emptySet()) ?: emptySet(),
            blocked          = s.getStringSet(K_BLOCKED, emptySet()) ?: emptySet(),
            priority         = s.getStringSet(K_PRIORITY, emptySet()) ?: emptySet()
        )
    }
}
