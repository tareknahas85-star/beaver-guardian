package com.microbeaver.guardian.calls

import android.content.Context
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

    private const val K_ENABLED    = "enabled"
    private const val K_BLOCK_UNK  = "blockUnknown"
    private const val K_OUTGOING   = "restrictOutgoing"
    private const val K_CONTACTS   = "allowContacts"
    private const val K_ALLOWED    = "allowed"
    private const val K_BLOCKED    = "blocked"

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun save(ctx: Context, p: Policy) {
        sp(ctx).edit()
            .putBoolean(K_ENABLED, p.callFilterEnabled)
            .putBoolean(K_BLOCK_UNK, p.blockUnknownCalls)
            .putBoolean(K_OUTGOING, p.restrictOutgoing)
            .putBoolean(K_CONTACTS, p.allowContacts)
            .putStringSet(K_ALLOWED, p.allowedNumbers.toSet())
            .putStringSet(K_BLOCKED, p.blockedNumbers.toSet())
            .apply()
    }

    data class Snapshot(
        val enabled: Boolean,
        val blockUnknown: Boolean,
        val restrictOutgoing: Boolean,
        val allowContacts: Boolean,
        val allowed: Set<String>,
        val blocked: Set<String>
    )

    fun load(ctx: Context): Snapshot {
        val s = sp(ctx)
        return Snapshot(
            enabled          = s.getBoolean(K_ENABLED, false),
            blockUnknown     = s.getBoolean(K_BLOCK_UNK, true),
            restrictOutgoing = s.getBoolean(K_OUTGOING, false),
            allowContacts    = s.getBoolean(K_CONTACTS, true),
            allowed          = s.getStringSet(K_ALLOWED, emptySet()) ?: emptySet(),
            blocked          = s.getStringSet(K_BLOCKED, emptySet()) ?: emptySet()
        )
    }
}
