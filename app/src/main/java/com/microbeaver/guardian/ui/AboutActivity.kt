package com.microbeaver.guardian.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import androidx.appcompat.app.AppCompatActivity
import com.microbeaver.guardian.R
import com.microbeaver.guardian.databinding.ActivityAboutBinding

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val b = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        b.toolbar.setNavigationOnClickListener { finish() }

        try {
            b.tvVersion.text = "v" + packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: Exception) {}

        loadRaw(R.raw.app_icon)?.let { b.imgAppIcon.setImageBitmap(it) }
        loadRaw(R.raw.rt_signature)?.let { b.imgSignature.setImageBitmap(it) }

        b.tvEmail.setOnClickListener {
            safeStart(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:tareknahas85@gmail.com")))
        }
        b.tvGithub.setOnClickListener {
            safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/tareknahas85-star")))
        }
        b.tvPhone.setOnClickListener {
            safeStart(Intent(Intent.ACTION_DIAL, Uri.parse("tel:+963945830002")))
        }
    }

    private fun loadRaw(resId: Int) = try {
        val b64 = resources.openRawResource(resId).bufferedReader().use { it.readText() }.trim()
        val bytes = Base64.decode(b64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (_: Exception) { null }

    private fun safeStart(i: Intent) { try { startActivity(i) } catch (_: Exception) {} }
}
