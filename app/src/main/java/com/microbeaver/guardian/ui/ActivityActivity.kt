package com.microbeaver.guardian.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.InputType
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.microbeaver.guardian.Prefs
import com.microbeaver.guardian.R
import com.microbeaver.guardian.data.Command
import com.microbeaver.guardian.data.FirebaseRepo
import com.microbeaver.guardian.data.Policy
import com.microbeaver.guardian.databinding.ActivityActivityBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ActivityActivity : AppCompatActivity() {
    private lateinit var b: ActivityActivityBinding
    private lateinit var code: String
    private var ready = false
    private var haveLoc = false
    private var lastLat = 0.0
    private var lastLng = 0.0
    private var policy = Policy()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityActivityBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        b.toolbar.setNavigationOnClickListener { finish() }

        code = Prefs.getPairCode(this) ?: ""
        if (code.isEmpty()) { finish(); return }

        b.web.settings.javaScriptEnabled = true
        b.web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) { ready = true; pushMap() }
        }
        b.web.loadUrl("file:///android_asset/map.html")

        b.btnLocate.setOnClickListener { FirebaseRepo.pushCommand(code, Command(type = "LOCATE")) }
        b.switchGeo.setOnCheckedChangeListener { _, c -> FirebaseRepo.updatePolicy(code, mapOf("geoEnabled" to c)) }
        b.btnZone.setOnClickListener { zoneDialog() }

        FirebaseRepo.listenLocation(code) { lat, lng, _ -> lastLat = lat; lastLng = lng; haveLoc = true; pushMap() }
        FirebaseRepo.listenPolicy(code) { p ->
            policy = p
            runOnUiThread { if (b.switchGeo.isChecked != p.geoEnabled) b.switchGeo.isChecked = p.geoEnabled }
            pushMap()
        }
        FirebaseRepo.listenRecentEvents(code, 3) { list -> runOnUiThread { renderEvents(list) } }
    }

    private fun pushMap() {
        if (!ready) return
        val js = "update($lastLat,$lastLng,${policy.geoRadius},${policy.geoLat},${policy.geoLng},${policy.geoEnabled})"
        runOnUiThread { b.web.evaluateJavascript(js, null) }
    }

    private fun renderEvents(list: List<Triple<String, String, Long>>) {
        b.llEvents.removeAllViews()
        if (list.isEmpty()) {
            val tv = TextView(this)
            tv.text = "لا أحداث بعد"
            tv.setTextColor(ContextCompat.getColor(this, R.color.slate))
            b.llEvents.addView(tv)
            return
        }
        val fmt = SimpleDateFormat("HH:mm", Locale.US)
        for (t in list) {
            val row = TextView(this)
            row.text = "• ${t.second}   (${fmt.format(Date(t.third))})"
            row.textSize = 14f
            row.setPadding(0, dp(6), 0, dp(6))
            row.setTextColor(ContextCompat.getColor(this, R.color.on_surface))
            b.llEvents.addView(row)
        }
    }

    private fun zoneDialog() {
        if (!haveLoc) return
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(policy.geoRadius.toInt().toString())
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("المنطقة الآمنة")
            .setView(input)
            .setPositiveButton("تفعيل") { _, _ ->
                val r = input.text.toString().toDoubleOrNull() ?: 300.0
                FirebaseRepo.updatePolicy(code, mapOf("geoEnabled" to true, "geoLat" to lastLat, "geoLng" to lastLng, "geoRadius" to r))
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
