package com.microbeaver.guardian.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class GuardianDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        PolicyManager(context).applyBaselineOwnerPolicies()
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        PolicyManager(context).applyBaselineOwnerPolicies()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        // Shown if someone tries to deactivate admin. Uninstall is separately blocked
        // by Device Owner, so this is a second line of defense.
        return "إلغاء الإشراف يحتاج موافقة وليّ الأمر. Removing supervision requires the parent."
    }
}
