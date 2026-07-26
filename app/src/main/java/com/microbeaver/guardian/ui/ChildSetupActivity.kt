package com.microbeaver.guardian.ui

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.microbeaver.guardian.Prefs
import com.microbeaver.guardian.admin.GuardianDeviceAdminReceiver
import com.microbeaver.guardian.admin.PolicyManager
import com.microbeaver.guardian.calls.CallScreeningRole
import com.microbeaver.guardian.data.FirebaseRepo
import com.microbeaver.guardian.databinding.ActivityChildSetupBinding
import com.microbeaver.guardian.monitor.MonitorService
import com.microbeaver.guardian.vpn.FilterVpnService

class ChildSetupActivity : AppCompatActivity() {
    private lateinit var b: ActivityChildSetupBinding
    private lateinit var policyMgr: PolicyManager

    private val vpnLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        startService(Intent(this, FilterVpnService::class.java))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityChildSetupBinding.inflate(layoutInflater)
        setContentView(b.root)
        policyMgr = PolicyManager(this)

        Prefs.getPairCode(this)?.let { b.etPairCode.setText(it) }

        b.btnPair.setOnClickListener {
            val code = b.etPairCode.text.toString().trim().uppercase()
            if (code.length < 4) {
                b.tvStatus.text = "رمز غير صالح / invalid code"
                return@setOnClickListener
            }
            b.tvStatus.text = "جاري الربط… / pairing…"
            // Join the code first; without membership every later write is rejected
            // by the database rules. The parent must have opened a pairing window.
            FirebaseRepo.claimDevice(code) { ok ->
                runOnUiThread {
                    if (!ok) {
                        b.tvStatus.text =
                            "فشل الربط ✖ / Pairing failed.\n" +
                                "اطلب من وليّ الأمر الضغط على \"فتح نافذة الربط\" ثم أعد المحاولة.\n" +
                                "Ask the parent to tap \"Open pairing window\", then retry."
                        return@runOnUiThread
                    }
                    Prefs.setPairCode(this, code)
                    FirebaseRepo.setChildInfo(code, "${Build.MANUFACTURER} ${Build.MODEL}")
                    MonitorService.start(this)
                    b.tvStatus.text = "تم الربط ✔ / Paired — code: $code"
                }
            }
        }

        b.btnPermUsage.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        b.btnPermAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        b.btnPermOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        }
        b.btnPermLocation.setOnClickListener { requestRuntimePerms() }
        b.btnPermAdmin.setOnClickListener { requestAdmin() }
        b.btnStartVpn.setOnClickListener { prepareVpn() }

        b.btnPermCallScreening.setOnClickListener {
            if (!CallScreeningRole.request(this)) {
                Toast.makeText(
                    this,
                    CallScreeningRole.statusText(this),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        b.btnSos.setOnClickListener { confirmSos() }
    }

    override fun onResume() {
        super.onResume()
        b.tvCallScreeningStatus.text = "فلترة المكالمات: ${CallScreeningRole.statusText(this)}"
        b.tvProtectionStatus.text = protectionSummary()
        // Re-assert the owner policies whenever this screen is opened; an OTA or a
        // policy reset can silently clear them.
        policyMgr.applyBaselineOwnerPolicies()
    }

    /**
     * A short, honest summary of how hard the app is to remove — a parent should
     * not be left believing the child cannot uninstall it when they can.
     */
    private fun protectionSummary(): String = when {
        policyMgr.isDeviceOwner ->
            "الحماية: مالك الجهاز ✔ لا يمكن إلغاء التثبيت / Device Owner — uninstall blocked"
        policyMgr.isAdminActive ->
            "الحماية: مشرف جهاز فقط — يمكن إلغاء التثبيت بعد تعطيل المشرف\n" +
                "Device Admin only — can still be removed after disabling admin. " +
                "For full protection provision as Device Owner (see docs/SETUP.md)."
        else ->
            "الحماية: غير مفعّلة / No protection active — enable Device Admin (step 5)"
    }

    private fun confirmSos() {
        val code = Prefs.getPairCode(this)
        if (code.isNullOrBlank()) {
            Toast.makeText(this, "اربط الجهاز أولاً / Pair the device first", Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("إرسال استغاثة؟ / Send SOS?")
            .setMessage("سيتم إرسال تنبيه وموقعك فوراً لوليّ أمرك.\nAn alert and your location will be sent to your parent immediately.")
            .setPositiveButton("إرسال / Send") { _, _ ->
                MonitorService.sendSos(this)
                Toast.makeText(this, "تم إرسال الاستغاثة ✔ / SOS sent", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("إلغاء / Cancel", null)
            .show()
    }

    private fun requestRuntimePerms() {
        val perms = mutableListOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.READ_CALL_LOG,
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.READ_PHONE_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        ActivityCompat.requestPermissions(this, perms.toTypedArray(), 101)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Background location has to be asked for separately on Android 10+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                102
            )
        }
    }

    private fun requestAdmin() {
        val admin = ComponentName(this, GuardianDeviceAdminReceiver::class.java)
        val i = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
            .putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "تفعيل الإشراف الأبوي / Enable parental supervision"
            )
        startActivity(i)
    }

    private fun prepareVpn() {
        val prep = VpnService.prepare(this)
        if (prep != null) vpnLauncher.launch(prep)
        else startService(Intent(this, FilterVpnService::class.java))
    }
}
