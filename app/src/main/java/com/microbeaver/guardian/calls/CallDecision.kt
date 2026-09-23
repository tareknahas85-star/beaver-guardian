package com.microbeaver.guardian.calls

import android.content.Context
import com.microbeaver.guardian.data.CallMode

/**
 * Pure decision logic for one call, kept free of Android framework types so it
 * can be reasoned about on its own.
 */
enum class CallVerdict {
    /** Let it through untouched. */
    ALLOW,

    /** Reject the call, and tell the parent. */
    BLOCK,

    /** Let it through, but tell the parent an unrecognised number got in. */
    ALLOW_AND_REPORT
}

data class CallJudgement(
    val verdict: CallVerdict,
    val contactName: String?,
    val reason: String
)

object CallDecision {

    /**
     * Decide what to do with a call. Rules are checked in this order, and the
     * first match wins:
     *
     *  1. Emergency numbers — always allowed, no exceptions.
     *  2. Priority numbers — the parent's own line. Always allowed, and
     *     deliberately checked *before* the block list so a typo in the block
     *     list can never cut the parent off from their child.
     *  3. Block list.
     *  4. Allow list.
     *  5. Saved contacts, in BLOCK_UNKNOWN mode only.
     *  6. Everything else, per mode.
     *
     * @param outgoing true for a call the child is placing.
     * @param isEmergency the platform's own emergency-number check.
     */
    fun judge(
        ctx: Context,
        number: String?,
        outgoing: Boolean,
        isEmergency: Boolean,
        snap: CallPolicyStore.Snapshot = CallPolicyStore.load(ctx)
    ): CallJudgement {

        // 1. Emergency services are never, under any circumstances, blocked.
        if (isEmergency) {
            return CallJudgement(CallVerdict.ALLOW, null, "emergency number")
        }

        // 2. The parent's own numbers always ring. Checked before everything
        //    else — a child must always be reachable by their parent.
        if (NumberUtils.matchesAny(number, snap.priority)) {
            return CallJudgement(
                CallVerdict.ALLOW,
                ContactsLookup.displayName(ctx, number),
                "priority number"
            )
        }

        // 3. Filtering off -> nothing more to do.
        if (snap.mode == CallMode.OFF) {
            return CallJudgement(CallVerdict.ALLOW, null, "filter off")
        }

        // 4. Outgoing calls are only restricted when the parent asked for it.
        if (outgoing && !snap.restrictOutgoing) {
            return CallJudgement(CallVerdict.ALLOW, null, "outgoing not restricted")
        }

        val contactName = ContactsLookup.displayName(ctx, number)

        // 5. Explicit block wins over contacts and the allow list.
        if (NumberUtils.matchesAny(number, snap.blocked)) {
            return CallJudgement(CallVerdict.BLOCK, contactName, "on the block list")
        }

        // 6. Explicit allow list.
        if (NumberUtils.matchesAny(number, snap.allowed)) {
            return CallJudgement(CallVerdict.ALLOW, contactName, "on the allow list")
        }

        // 7. Saved contacts — trusted in BLOCK_UNKNOWN, ignored in WHITELIST_ONLY.
        if (snap.mode == CallMode.BLOCK_UNKNOWN && contactName != null) {
            return CallJudgement(CallVerdict.ALLOW, contactName, "saved contact")
        }

        // 8. Anything left is not permitted by the mode. Withheld caller ID has no
        //    digits, so it can never match an allow-list and lands here too.
        return CallJudgement(
            CallVerdict.BLOCK,
            contactName,
            if (snap.mode == CallMode.WHITELIST_ONLY) "not on the allow list"
            else "not in contacts"
        )
    }
}
