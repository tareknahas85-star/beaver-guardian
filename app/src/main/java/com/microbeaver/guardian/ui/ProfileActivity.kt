package com.microbeaver.guardian.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.microbeaver.guardian.databinding.ActivityProfileBinding

class ProfileActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val b = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        b.toolbar.setNavigationOnClickListener { finish() }

        b.btnPermNotif.setOnClickListener { openNotif() }
        b.btnPermUsage.setOnClickListener { safeStart(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        b.btnPermAcc.setOnClickListener { safeStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        b.btnPermBattery.setOnClickListener { safeStart(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
    }

    private fun openNotif() {
        val i = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        else
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:$packageName"))
        safeStart(i)
    }

    private fun safeStart(i: Intent) { try { startActivity(i) } catch (_: Exception) {} }
}
