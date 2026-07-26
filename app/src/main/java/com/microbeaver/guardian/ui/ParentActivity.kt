package com.microbeaver.guardian.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.microbeaver.guardian.Prefs
import com.microbeaver.guardian.alerts.AlertNotifier
import com.microbeaver.guardian.data.Alert
import com.microbeaver.guardian.data.Command
import com.microbeaver.guardian.data.FirebaseRepo
import com.microbeaver.guardian.databinding.ActivityParentBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ParentActivity : AppCompatActivity() {
    private lateinit var b: ActivityParentBinding
    private lateinit var code: String

    /** Alerts already on the server when we attached — shown in the list, not as pop-ups. */
    private var attachedAt = 0L
    private val recentAlerts = ArrayList<Alert>()

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* alerts simply stay silent if declined */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityParentBinding.inflate(layoutInflater)
        setContentView(b.root)

        code = Prefs.getPairCode(this) ?: genCode().also { Prefs.setPairCode(this, it) }
        b.tvPairCode.text = "رمز الربط / Pairing code:  $code"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        // Become a member of this code before reading or writing anything, then
        // start listening. Everything else depends on this succeeding.
        FirebaseRepo.claimDevice(code) { ok ->
            runOnUiThread {
                if (!ok) {
                    b.tvAlerts.text =
                        "تعذّر الاتصال بقاعدة البيانات / Could not join this pairing code.\n" +
                            "تحقّق من الإنترنت وقواعد Firebase / Check connectivity and Firebase rules."
                    return@runOnUiThread
                }
                startListening()
            }
        }

        b.btnLockNow.setOnClickListener { send("LOCK_NOW") }
        b.btnUnlock.setOnClickListener { send("UNLOCK") }
        b.btnBlockInternet.setOnClickListener { send("BLOCK_INTERNET") }
        b.btnAllowInternet.setOnClickListener { send("ALLOW_INTERNET") }

        b.btnSettings.setOnClickListener {
            startActivity(Intent(this, GuardSettingsActivity::class.java))
        }

        b.btnOpenPairing.setOnClickListener {
            FirebaseRepo.openPairing(code)
            Toast.makeText(
                this,
                "يمكن لجهاز الطفل الانضمام الآن خلال 15 دقيقة / Child device can join for 15 minutes",
                Toast.LENGTH_LONG
            ).show()
        }

        b.btnAbout.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
    }

    private fun startListening() {
        attachedAt = System.currentTimeMillis()

        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        FirebaseRepo.listenUsageToday(code, today) { usage ->
            val sb = StringBuilder("استخدام اليوم / Today's usage:\n\n")
            usage.entries.sortedByDescending { it.value }.forEach { (pkg, min) ->
                sb.append("• $pkg : $min د / min\n")
            }
            runOnUiThread {
                b.tvReport.text = if (usage.isEmpty()) "لا بيانات بعد / no data yet" else sb.toString()
            }
        }

        FirebaseRepo.listenAlerts(code) { alert ->
            // Only pop a notification for things that happened after we attached,
            // otherwise opening the app would replay the whole backlog.
            if (alert.ts >= attachedAt && !alert.seen) {
                AlertNotifier.show(this, alert)
                FirebaseRepo.markAlertSeen(code, alert.id)
            }
            recentAlerts.add(0, alert)
            if (recentAlerts.size > 12) recentAlerts.removeAt(recentAlerts.size - 1)
            runOnUiThread { renderAlerts() }
        }
    }

    private fun renderAlerts() {
        if (recentAlerts.isEmpty()) {
            b.tvAlerts.text = "لا تنبيهات / No alerts yet"
            return
        }
        val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.US)
        b.tvAlerts.text = recentAlerts
            .sortedByDescending { it.ts }
            .joinToString("\n") { a ->
                "• ${fmt.format(Date(a.ts))}  ${a.title}\n   ${a.body}"
            }
    }

    private fun send(type: String) = FirebaseRepo.pushCommand(code, Command(type = type))

    private fun genCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}
