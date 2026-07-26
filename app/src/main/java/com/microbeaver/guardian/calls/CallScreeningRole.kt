package com.microbeaver.guardian.calls

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Requesting the call-screening role.
 *
 * Android only binds [GuardianCallScreeningService] while this app holds
 * [RoleManager.ROLE_CALL_SCREENING]. The role:
 *  * exists from API 29 (Android 10) onwards,
 *  * must be granted by the user through a system dialog — it cannot be
 *    self-assigned, not even by a Device Owner,
 *  * is exclusive: granting it to us revokes it from Truecaller, Hiya, or
 *    whichever app held it before.
 *
 * Below API 29 there is no supported way for a non-dialer app to reject calls,
 * so [isSupported] returns false and the feature stays off.
 */
object CallScreeningRole {

    const val REQUEST_CODE = 1201

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    private fun roleManager(ctx: Context): RoleManager? =
        if (isSupported()) ctx.getSystemService(RoleManager::class.java) else null

    /** True when the system will actually route calls to our screening service. */
    fun isHeld(ctx: Context): Boolean {
        val rm = roleManager(ctx) ?: return false
        return try {
            rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) &&
                rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        } catch (_: Exception) {
            false
        }
    }

    fun isAvailable(ctx: Context): Boolean {
        val rm = roleManager(ctx) ?: return false
        return try { rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) } catch (_: Exception) { false }
    }

    /**
     * Shows the system consent dialog.
     * @return false when the role cannot be requested on this device.
     */
    fun request(activity: Activity): Boolean {
        val rm = roleManager(activity) ?: return false
        return try {
            if (!rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) return false
            if (rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) return true
            val intent: Intent = rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
            activity.startActivityForResult(intent, REQUEST_CODE)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Human-readable state for the setup screen. */
    fun statusText(ctx: Context): String = when {
        !isSupported()      -> "غير مدعوم (يتطلب Android 10+) / Not supported (needs Android 10+)"
        !isAvailable(ctx)   -> "غير متاح على هذا الجهاز / Not available on this device"
        isHeld(ctx)         -> "مفعّل ✔ / Active"
        else                -> "غير مفعّل / Not active — tap to enable"
    }
}
