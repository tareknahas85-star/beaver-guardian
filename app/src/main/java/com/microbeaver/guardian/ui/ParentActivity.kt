package com.microbeaver.guardian.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.microbeaver.guardian.Prefs
import com.microbeaver.guardian.data.Command
import com.microbeaver.guardian.data.FirebaseRepo
import com.microbeaver.guardian.data.Policy
import com.microbeaver.guardian.databinding.ActivityParentBinding
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityParentBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        b.toolbar.subtitle = "v2.0"

        code = Prefs.getPairCode(this) ?: genCode().also { Prefs.setPairCode(this, it) }
        b.tvPairCode.text = code

        adapter = AppUsageAdapter(
            onSetLimit = { showLimitDialog(it) },
            onToggleBlock = { row, blocked -> setBlocked(row.pkg, blocked) }
        )
        b.recycler.layoutManager = LinearLayoutManager(this)
        b.recycler.adapter = adapter

        b.swipe.setOnRefreshListener { refresh() }
        b.btnRefresh.setOnClickListener { refresh() }
        b.btnLock.setOnClickListener { send("LOCK_NOW") }
        b.btnUnlock.setOnClickListener { send("UNLOCK") }
        b.btnNetOff.setOnClickListener { send("BLOCK_INTERNET") }
        b.btnNetOn.setOnClickListener { send("ALLOW_INTERNET") }

        listenAll()
    }

    private fun listenAll() {
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        FirebaseRepo.listenUsageToday(code, today) { u ->
            usage.clear(); usage.putAll(u); render()
        }
        FirebaseRepo.listenApps(code) { n, ic ->
            names.clear(); names.putAll(n)
            icons.clear(); icons.putAll(ic)
            render()
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
            AppRow(
                pkg = pkg,
                name = names[pkg] ?: pkg,
                iconB64 = icons[pkg] ?: "",
                minutes = usage[pkg] ?: 0,
                limit = limitOf(pkg),
                blocked = policy.blockedApps.contains(pkg)
            )
        }.sortedByDescending { it.minutes }
        ui.post {
            adapter.submit(rows)
            b.tvEmpty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
            b.tvStatus.text = if (policy.locked) "الجهاز مقفول 🔒" else "الجهاز مفتوح ✅"
        }
    }

    private fun showLimitDialog(row: AppRow) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(if (row.limit > 0) row.limit.toString() else "")
            hint = "دقائق باليوم"
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(row.name)
            .setMessage("حدّد الوقت المسموح يومياً (بالدقائق). اتركه فارغاً لإزالة الحد.")
            .setView(input)
            .setPositiveButton("حفظ") { _, _ ->
                setLimit(row.pkg, input.text.toString().toIntOrNull() ?: 0)
            }
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

    private fun genCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}
