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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.microbeaver.guardian.Prefs
import com.microbeaver.guardian.admin.GuardianDeviceAdminReceiver
import com.microbeaver.guardian.data.FirebaseRepo
import com.microbeaver.guardian.databinding.ActivityChildSetupBinding
import com.microbeaver.guardian.monitor.MonitorService
import com.microbeaver.guardian.vpn.FilterVpnService

class ChildSetupActivity : AppCompatActivity() {
    private lateinit var b: ActivityChildSetupBinding

    private val vpnLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        startService(Intent(this, FilterVpnService::class.java))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityChildSetupBinding.inflate(layoutInflater)
        setContentView(b.root)

        Prefs.getPairCode(this)?.let { b.etPairCode.setText(it) }

        b.btnPair.setOnClickListener {
            val code = b.etPairCode.text.toString().trim().uppercase()
            if (code.length >= 4) {
                Prefs.setPairCode(this, code)
                FirebaseRepo.setChildInfo(code, "${Build.MANUFACTURER} ${Build.MODEL}")
                MonitorService.start(this)
                b.tvStatus.text = "تم الربط ✔ / Paired — code: $code"
            } else {
                b.tvStatus.text = "رمز غير صالح / invalid code"
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
    }

    private fun requestRuntimePerms() {
        val perms = mutableListOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.READ_CALL_LOG
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
