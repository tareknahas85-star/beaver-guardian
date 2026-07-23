package com.microbeaver.guardian.ui

import android.os.Bundle
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
        try {
            b.tvVersion.text = "v" + packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: Exception) {
        }
    }
}
