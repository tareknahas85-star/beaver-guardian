package com.microbeaver.guardian.calls

import android.content.Context

/**
 * Pure decision logic for one call, kept free of Android framework types so it
 * can be reasoned about (and unit-tested) on its own.
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
     * Decide what to do with a call.
     *
     * @param outgoing true for a call the child is placing.
     * @param isEmergency the platform's own emergency-number check. **Emergency
     *        calls are always allowed** — no policy can override this.
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

        // 2. Filtering switched off -> nothing to do.
        if (!snap.enabled) {
            return CallJudgement(CallVerdict.ALLOW, null, "filter disabled")
        }

        // 3. Outgoing calls are only restricted when the parent asked for it.
        if (outgoing && !snap.restrictOutgoing) {
            return CallJudgement(CallVerdict.ALLOW, null, "outgoing not restricted")
        }

        val contactName = ContactsLookup.displayName(ctx, number)

        // 4. An explicit block always wins, even for saved contacts.
        if (NumberUtils.matchesAny(number, snap.blocked)) {
            return CallJudgement(CallVerdict.BLOCK, contactName, "on parent's block list")
        }

        // 5. Explicit allow list.
        if (NumberUtils.matchesAny(number, snap.allowed)) {
            return CallJudgement(CallVerdict.ALLOW, contactName, "on parent's allow list")
        }

        // 6. Saved contacts, when the parent trusts the address book.
        if (snap.allowContacts && contactName != null) {
            return CallJudgement(CallVerdict.ALLOW, contactName, "saved contact")
        }

        // 7. Anything left is unknown. Withheld caller ID counts as unknown.
        return if (snap.blockUnknown) {
            CallJudgement(CallVerdict.BLOCK, null, "unknown number")
        } else {
            CallJudgement(CallVerdict.ALLOW_AND_REPORT, null, "unknown number, reporting only")
        }
    }
}
