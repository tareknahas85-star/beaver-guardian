package com.microbeaver.guardian.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.InputType
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.microbeaver.guardian.Prefs
import com.microbeaver.guardian.data.Command
import com.microbeaver.guardian.data.FirebaseRepo
import com.microbeaver.guardian.data.Policy
import com.microbeaver.guardian.databinding.ActivityMapBinding

class MapActivity : AppCompatActivity() {
    private lateinit var b: ActivityMapBinding
    private lateinit var code: String
    private var ready = false
    private var haveLoc = false
    private var lastLat = 0.0
    private var lastLng = 0.0
    private var policy = Policy()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMapBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        b.toolbar.setNavigationOnClickListener { finish() }

        code = Prefs.getPairCode(this) ?: ""
        if (code.isEmpty()) { finish(); return }

        b.web.settings.javaScriptEnabled = true
        b.web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                ready = true
                pushToMap()
            }
        }
        b.web.loadUrl("file:///android_asset/map.html")

        b.btnLocate.setOnClickListener {
            FirebaseRepo.pushCommand(code, Command(type = "LOCATE"))
            Toast.makeText(this, "جاري تحديد الموقع...", Toast.LENGTH_SHORT).show()
        }
        b.btnZone.setOnClickListener { setZoneDialog() }
        b.switchGeo.setOnCheckedChangeListener { _, checked ->
            FirebaseRepo.updatePolicy(code, mapOf("geoEnabled" to checked))
        }

        FirebaseRepo.listenLocation(code) { lat, lng, _ ->
            lastLat = lat; lastLng = lng; haveLoc = true; pushToMap()
        }
        FirebaseRepo.listenPolicy(code) { p ->
            policy = p
            runOnUiThread {
                if (b.switchGeo.isChecked != p.geoEnabled) b.switchGeo.isChecked = p.geoEnabled
            }
            pushToMap()
        }
    }

    private fun pushToMap() {
        if (!ready) return
        val js = "update($lastLat,$lastLng,${policy.geoRadius},${policy.geoLat},${policy.geoLng},${policy.geoEnabled})"
        runOnUiThread { b.web.evaluateJavascript(js, null) }
    }

    private fun setZoneDialog() {
        if (!haveLoc) {
            Toast.makeText(this, "لا يوجد موقع بعد — اضغط تحديد الآن أولاً", Toast.LENGTH_LONG).show()
            return
        }
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(policy.geoRadius.toInt().toString())
            hint = "نصف القطر بالأمتار"
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("المنطقة الآمنة")
            .setMessage("ستُضبط المنطقة حول آخر موقع للطفل. حدد نصف القطر (متر).")
            .setView(input)
            .setPositiveButton("تفعيل") { _, _ ->
                val r = input.text.toString().toDoubleOrNull() ?: 300.0
                FirebaseRepo.updatePolicy(
                    code,
                    mapOf(
                        "geoEnabled" to true,
                        "geoLat" to lastLat,
                        "geoLng" to lastLng,
                        "geoRadius" to r
                    )
                )
                Toast.makeText(this, "تم تفعيل المنطقة الآمنة ✔", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }
}
