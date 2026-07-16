package com.microbeaver.guardian.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager

/**
 * Wraps DevicePolicyManager. Two capability tiers:
 *  - Device Admin (user-approved): lockNow, disable camera, keyguard features.
 *  - Device Owner (ADB-provisioned): block uninstall, factory-reset lock, hide/suspend apps.
 */
class PolicyManager(private val ctx: Context) {
    private val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin = ComponentName(ctx, GuardianDeviceAdminReceiver::class.java)

    val isAdminActive: Boolean get() = dpm.isAdminActive(admin)
    val isDeviceOwner: Boolean get() = dpm.isDeviceOwnerApp(ctx.packageName)

    fun lockNow() {
        if (isAdminActive) dpm.lockNow()
    }

    fun setUninstallBlocked(blocked: Boolean) {
        if (isDeviceOwner) dpm.setUninstallBlocked(admin, ctx.packageName, blocked)
    }

    fun setCameraDisabled(disabled: Boolean) {
        if (isAdminActive) dpm.setCameraDisabled(admin, disabled)
    }

    /** Applied automatically once the app becomes Device Owner. */
    fun applyBaselineOwnerPolicies() {
        if (!isDeviceOwner) return
        try {
            dpm.setUninstallBlocked(admin, ctx.packageName, true)
            dpm.addUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET)
            dpm.addUserRestriction(admin, UserManager.DISALLOW_ADD_USER)
            dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
        } catch (_: Exception) {
        }
    }

    /** Device Owner can make an app disappear entirely (great for games). */
    fun setAppHidden(pkg: String, hidden: Boolean): Boolean {
        if (pkg.isBlank() || pkg == ctx.packageName) return false
        return if (isDeviceOwner) {
            try { dpm.setApplicationHidden(admin, pkg, hidden) } catch (_: Exception) { false }
        } else false
    }

    fun suspendApps(pkgs: List<String>, suspended: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isDeviceOwner) {
            try { dpm.setPackagesSuspended(admin, pkgs.toTypedArray(), suspended) } catch (_: Exception) {}
        }
    }
}
