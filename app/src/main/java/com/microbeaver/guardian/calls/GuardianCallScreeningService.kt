package com.microbeaver.guardian.calls

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import com.microbeaver.guardian.Prefs
import com.microbeaver.guardian.data.Alert
import com.microbeaver.guardian.data.CallRecord
import com.microbeaver.guardian.data.FirebaseRepo

/**
 * Screens every call on the child device.
 *
 * The telecom stack binds this service, hands us the call, and waits for
 * [respondToCall]. We must answer quickly, so all inputs are read from local
 * storage ([CallPolicyStore]) and the device address book — never the network.
 * Reporting to the parent happens after we have already responded.
 *
 * ## Requirements
 * Android grants call screening through [android.app.role.RoleManager.ROLE_CALL_SCREENING]
 * (API 29+). Without that role the platform never binds this service and calls
 * flow through untouched — see [CallScreeningRole] for the request flow.
 *
 * ## Safety
 * Emergency calls are allowed unconditionally. The platform already bypasses
 * screening for them; [CallDecision] refuses to block them as a second line of
 * defence, so a misconfigured allow-list can never stop a child calling for help.
 */
@RequiresApi(Build.VERSION_CODES.N)
class GuardianCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val number = extractNumber(callDetails)
        val outgoing = isOutgoing(callDetails)
        val emergency = isEmergencyNumber(number)

        val judgement = try {
            CallDecision.judge(this, number, outgoing, emergency)
        } catch (e: Exception) {
            Log.e(TAG, "screening failed, allowing call: ${e.message}")
            CallJudgement(CallVerdict.ALLOW, null, "screening error")
        }

        // Respond first — the platform is waiting on us.
        respond(callDetails, judgement.verdict)

        Log.d(TAG, "call ${NumberUtils.display(number)} outgoing=$outgoing -> " +
            "${judgement.verdict} (${judgement.reason})")

        // Then report, on whatever thread Firebase wants.
        try {
            reportToParent(number, outgoing, judgement)
        } catch (e: Exception) {
            Log.e(TAG, "reporting failed: ${e.message}")
        }
    }

    // ── Responding ────────────────────────────────────────────────────────────

    private fun respond(details: Call.Details, verdict: CallVerdict) {
        val response = CallResponse.Builder()
        if (verdict == CallVerdict.BLOCK) {
            response.setDisallowCall(true)
                .setRejectCall(true)
                .setSkipCallLog(false)          // keep it in the log so the parent sees it
                .setSkipNotification(true)
        }
        respondToCall(details, response.build())
    }

    // ── Reporting ─────────────────────────────────────────────────────────────

    private fun reportToParent(number: String?, outgoing: Boolean, j: CallJudgement) {
        val code = Prefs.getPairCode(this) ?: return
        if (code.isBlank()) return

        val blocked = j.verdict == CallVerdict.BLOCK
        val shouldAlert = blocked || j.verdict == CallVerdict.ALLOW_AND_REPORT
        if (!shouldAlert) return

        val shown = NumberUtils.display(number)
        val direction = if (outgoing) "صادرة / outgoing" else "واردة / incoming"

        FirebaseRepo.reportCall(
            code,
            CallRecord(
                number = number ?: "",
                type = if (outgoing) "outgoing" else "incoming",
                ts = System.currentTimeMillis(),
                durationSec = 0,
                blockedByFilter = blocked,
                contactName = j.contactName ?: ""
            )
        )

        FirebaseRepo.pushAlert(
            code,
            Alert(
                type = if (blocked) Alert.BLOCKED_CALL else Alert.UNKNOWN_CALL,
                title = if (blocked) "مكالمة محجوبة / Call blocked"
                        else "رقم غير معروف / Unknown caller",
                body = "$shown — $direction",
                number = number ?: "",
                ts = System.currentTimeMillis()
            )
        )
    }

    // ── Call detail helpers ───────────────────────────────────────────────────

    private fun extractNumber(details: Call.Details): String? {
        val handle = details.handle ?: return null
        // tel: URIs arrive percent-encoded for '#' and '*'.
        return handle.schemeSpecificPart
    }

    private fun isOutgoing(details: Call.Details): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            details.callDirection == Call.Details.DIRECTION_OUTGOING
        } else {
            // Pre-Q screening only ever fires for incoming calls.
            false
        }

    /**
     * Uses the platform's own emergency-number database where available, with a
     * conservative fallback list for older releases.
     */
    private fun isEmergencyNumber(number: String?): Boolean {
        val digits = NumberUtils.digitsOnly(number)
        if (digits.isEmpty()) return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val tm = getSystemService(TelephonyManager::class.java)
                if (tm != null && tm.isEmergencyNumber(digits)) return true
            } catch (_: Exception) {
                // Some OEMs throw when there is no SIM; fall through to the list.
            }
        }
        return digits in FALLBACK_EMERGENCY
    }

    companion object {
        private const val TAG = "CallScreening"

        /**
         * Used only when [TelephonyManager.isEmergencyNumber] is unavailable or
         * throws. Deliberately broad — allowing a non-emergency number through
         * is harmless, blocking a real one is not.
         */
        private val FALLBACK_EMERGENCY = setOf(
            "112", "911", "999", "000", "110", "119", "118", "115",
            "113", "911", "102", "103", "101", "100", "108", "122", "123"
        )
    }
}
