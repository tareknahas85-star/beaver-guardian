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
        // Every other DevicePolicyManager call in this class is guarded — these
        // two (added later, for LOCK_NOW and the camera toggle) were not. On some
        // OEM builds dpm calls that are perfectly legal on stock Android can throw,
        // and this method runs straight out of MonitorService's Firebase policy/
        // command listeners with nothing else catching it: an unguarded throw here
        // used to be able to crash that whole long-running listener, taking every
        // other command down with it, not just this one.
        try {
            if (isAdminActive) dpm.lockNow()
        } catch (_: Exception) {
        }
    }

    fun setUninstallBlocked(blocked: Boolean) {
        if (isDeviceOwner) dpm.setUninstallBlocked(admin, ctx.packageName, blocked)
    }

    fun setCameraDisabled(disabled: Boolean) {
        try {
            if (isAdminActive) dpm.setCameraDisabled(admin, disabled)
        } catch (_: Exception) {
        }
    }

    /** Applied automatically once the app becomes Device Owner. */
    fun applyBaselineOwnerPolicies() {
        if (!isDeviceOwner) return
        try {
            dpm.setUninstallBlocked(admin, ctx.packageName, true)
            dpm.addUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET)
            dpm.addUserRestriction(admin, UserManager.DISALLOW_ADD_USER)
            dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
            // Screen pinning (the Recents "pin"/"lock this app" feature, whatever
            // an OEM's launcher calls it) lets a blocked app be trapped in the
            // foreground: pinning disables Home/Recents system-wide while active,
            // so AppBlockService's Home-bounce never lands. With a Device Owner
            // present, an empty allow-list here removes the feature entirely —
            // no package, including the launcher, may enter lock-task/pinning
            // mode, so there is nothing left for the child to turn on.
            dpm.setLockTaskPackages(admin, emptyArray())
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
