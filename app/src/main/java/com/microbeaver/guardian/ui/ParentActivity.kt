package com.microbeaver.guardian.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.microbeaver.guardian.Prefs
import com.microbeaver.guardian.R
import com.microbeaver.guardian.data.Command
import com.microbeaver.guardian.data.FirebaseRepo
import com.microbeaver.guardian.data.Policy
import com.microbeaver.guardian.databinding.ActivityParentBinding
import com.microbeaver.guardian.parent.ParentEventService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ParentActivity : AppCompatActivity() {
    private lateinit var b: ActivityParentBinding
    private lateinit var code: String
    private lateinit var adapter: AppUsageAdapter

    private val usage = HashMap<String, Int>()
    private val names = HashMap<String, String>()
    private val icons = HashMap<String, String>()
    private var policy = Policy()
    private val ui = Handler(Looper.getMainLooper())

    private val segColors by lazy {
        intArrayOf(
            ContextCompat.getColor(this, R.color.brand),
            ContextCompat.getColor(this, R.color.accent),
            ContextCompat.getColor(this, R.color.amber),
            ContextCompat.getColor(this, R.color.emerald),
            ContextCompat.getColor(this, R.color.slate)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityParentBinding.inflate(layoutInflater)
        setContentView(b.root)

        code = Prefs.getPairCode(this) ?: genCode().also { Prefs.setPairCode(this, it) }
        b.tvPairCode.text = "رمز الربط: $code"

        adapter = AppUsageAdapter(onEdit = { showEditDialog(it) })
        b.recycler.layoutManager = LinearLayoutManager(this)
        b.recycler.adapter = adapter

        b.sliderDaily.valueFrom = 30f
        b.sliderDaily.valueTo = 480f
        b.sliderDaily.stepSize = 10f
        b.sliderDaily.value = 240f
        b.sliderDaily.addOnChangeListener { _, value, fromUser ->
            b.tvDailyLimit.text = fmt(value.toInt())
            if (fromUser) FirebaseRepo.updatePolicy(code, mapOf("dailyLimitMinutes" to value.toInt()))
        }

        b.swipe.setOnRefreshListener { refresh() }
        b.btnRefresh.setOnClickListener { refresh() }
        b.btnMap.setOnClickListener { startActivity(Intent(this, ActivityActivity::class.java)) }
        b.btnNet.setOnClickListener {
            if (policy.internetBlocked) send("ALLOW_INTERNET") else send("BLOCK_INTERNET")
        }
        b.cardDowntime.setOnClickListener {
            if (policy.locked) send("UNLOCK") else send("LOCK_NOW")
        }
        b.cardBedtime.setOnClickListener { pickBedtime() }

        b.bottomNav.selectedItemId = R.id.nav_dashboard
        b.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_activity -> { startActivity(Intent(this, ActivityActivity::class.java)); false }
                R.id.nav_about -> { startActivity(Intent(this, AboutActivity::class.java)); false }
                R.id.nav_settings -> { startActivity(Intent(this, ProfileActivity::class.java)); false }
                else -> true
            }
        }

        requestNotifPerm()
        ParentEventService.start(this)
        listenAll()
    }

    private fun requestNotifPerm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 200)
        }
    }

    private fun listenAll() {
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        FirebaseRepo.listenUsageToday(code, today) { u -> usage.clear(); usage.putAll(u); render() }
        FirebaseRepo.listenApps(code) { n, ic ->
            names.clear(); names.putAll(n); icons.clear(); icons.putAll(ic); render()
        }
        FirebaseRepo.listenPolicy(code) { p -> policy = p; render() }
    }

    private fun refresh() {
        send("SYNC_NOW")
        b.swipe.isRefreshing = true
        ui.postDelayed({ b.swipe.isRefreshing = false }, 1500)
    }

    private fun limitOf(pkg: String): Int {
        val e = policy.limits.firstOrNull { it.startsWith("$pkg=") } ?: return 0
        return e.substringAfter("=").toIntOrNull() ?: 0
    }

    private fun render() {
        val pkgs = (usage.keys + names.keys).toMutableSet()
        val rows = pkgs.map { pkg ->
            AppRow(pkg, names[pkg] ?: pkg, icons[pkg] ?: "", usage[pkg] ?: 0, limitOf(pkg), policy.blockedApps.contains(pkg))
        }.sortedByDescending { it.minutes }
        val total = rows.sumOf { it.minutes }

        ui.post {
            adapter.submit(rows)
            b.tvEmpty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
            b.tvTotalUsage.text = fmt(total)
            b.tvDowntime.text = if (policy.locked) "Active" else "Off"
            b.tvBedtime.text = if (policy.bedtimeEnabled) String.format("%02d:%02d", policy.bedtimeHour, policy.bedtimeMinute) else "Off"
            b.tvStatus.text = if (policy.locked) "مقفول 🔒" else ""
            b.btnNet.text = if (policy.internetBlocked) "سماح النت" else "قطع النت"
            b.btnNet.setIconResource(if (policy.internetBlocked) R.drawable.ic_wifi else R.drawable.ic_wifi_off)

            val dl = if (policy.dailyLimitMinutes in 30..480) policy.dailyLimitMinutes else 240
            val rounded = (Math.round(dl / 10.0) * 10).toInt().coerceIn(30, 480)
            if (b.sliderDaily.value.toInt() != rounded) b.sliderDaily.value = rounded.toFloat()
            b.tvDailyLimit.text = fmt(if (policy.dailyLimitMinutes > 0) policy.dailyLimitMinutes else rounded)

            renderUsageBar(rows, total)
        }
    }

    private fun renderUsageBar(rows: List<AppRow>, total: Int) {
        b.llUsageBar.removeAllViews()
        b.llLegend.removeAllViews()
        if (total <= 0) return
        val top = rows.filter { it.minutes > 0 }.take(4)
        top.forEachIndexed { i, row ->
            val color = segColors[i % segColors.size]
            val seg = View(this)
            seg.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, row.minutes.toFloat())
            seg.setBackgroundColor(color)
            b.llUsageBar.addView(seg)

            val legendRow = LinearLayout(this)
            legendRow.orientation = LinearLayout.HORIZONTAL
            legendRow.gravity = Gravity.CENTER_VERTICAL
            legendRow.setPadding(0, dp(3), 0, dp(3))
            val dot = View(this)
            dot.layoutParams = LinearLayout.LayoutParams(dp(10), dp(10))
            val gd = GradientDrawable(); gd.shape = GradientDrawable.OVAL; gd.setColor(color)
            dot.background = gd
            val tv = TextView(this)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.marginStart = dp(8)
            tv.layoutParams = lp
            tv.text = "${row.name} (${fmt(row.minutes)})"
            tv.textSize = 13f
            tv.setTextColor(ContextCompat.getColor(this, R.color.on_surface_variant))
            legendRow.addView(dot); legendRow.addView(tv)
            b.llLegend.addView(legendRow)
        }
    }

    private fun pickBedtime() {
        val dlg = android.app.TimePickerDialog(this, { _, h, m ->
            FirebaseRepo.updatePolicy(code, mapOf("bedtimeEnabled" to true, "bedtimeHour" to h, "bedtimeMinute" to m))
        }, policy.bedtimeHour, policy.bedtimeMinute, true)
        dlg.setButton(android.app.TimePickerDialog.BUTTON_NEUTRAL, "إيقاف") { _, _ ->
            FirebaseRepo.updatePolicy(code, mapOf("bedtimeEnabled" to false))
        }
        dlg.show()
    }

    private fun showEditDialog(row: AppRow) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(if (row.limit > 0) row.limit.toString() else "")
            hint = "دقائق باليوم"
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(row.name)
            .setMessage("حدّد الوقت المسموح يومياً (دقائق). فارغ = بدون حد.")
            .setView(input)
            .setPositiveButton("حفظ") { _, _ -> setLimit(row.pkg, input.text.toString().toIntOrNull() ?: 0) }
            .setNeutralButton(if (row.blocked) "رفع الحظر" else "حظر التطبيق") { _, _ -> setBlocked(row.pkg, !row.blocked) }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun setLimit(pkg: String, minutes: Int) {
        val list = policy.limits.filterNot { it.startsWith("$pkg=") }.toMutableList()
        if (minutes > 0) list.add("$pkg=$minutes")
        FirebaseRepo.updatePolicy(code, mapOf("limits" to list))
    }

    private fun setBlocked(pkg: String, blocked: Boolean) {
        val set = policy.blockedApps.toMutableSet()
        if (blocked) set.add(pkg) else set.remove(pkg)
        FirebaseRepo.updatePolicy(code, mapOf("blockedApps" to set.toList()))
    }

    private fun send(type: String) = FirebaseRepo.pushCommand(code, Command(type = type))

    private fun fmt(min: Int): String {
        val h = min / 60; val m = min % 60
        return if (h > 0) "${h}h ${String.format("%02d", m)}m" else "${m}m"
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun genCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}
