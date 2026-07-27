package com.microbeaver.guardian.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import com.microbeaver.guardian.BuildConfig
import com.microbeaver.guardian.Prefs
import com.microbeaver.guardian.alerts.AlertNotifier
import com.microbeaver.guardian.alerts.ParentAlertService
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

    private var attachedAt = 0L
    private val recentAlerts = ArrayList<Alert>()

    /** Cache of package name -> app label, so the usage list is readable. */
    private val labelCache = HashMap<String, String>()

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { renderNotificationState() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityParentBinding.inflate(layoutInflater)
        setContentView(b.root)

        code = Prefs.getPairCode(this) ?: genCode().also { Prefs.setPairCode(this, it) }
        b.tvPairCode.text = "Pairing code:  $code"
        b.tvVersionBadge.text = "v${BuildConfig.VERSION_NAME}  ·  build ${BuildConfig.VERSION_CODE}"

        FirebaseRepo.claimDevice(code) { ok ->
            runOnUiThread {
                if (!ok) {
                    b.tvStatus.text = "Cannot reach the database. Check your connection, " +
                        "that Anonymous sign-in is on in Firebase, and that the rules are published."
                }
                startListening()
            }
        }

        ParentAlertService.start(this)

        b.btnLockNow.setOnClickListener { send("LOCK_NOW", "Lock sent") }
        b.btnUnlock.setOnClickListener { send("UNLOCK", "Unlock sent") }
        b.btnBlockInternet.setOnClickListener { send("BLOCK_INTERNET", "Internet off sent") }
        b.btnAllowInternet.setOnClickListener { send("ALLOW_INTERNET", "Internet on sent") }

        b.btnSettings.setOnClickListener {
            startActivity(Intent(this, GuardSettingsActivity::class.java))
        }
        b.btnAbout.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }

        b.btnOpenPairing.setOnClickListener {
            FirebaseRepo.openPairing(code)
            toast("The child device can join for the next 15 minutes")
        }

        b.btnFixNotif.setOnClickListener { fixNotifications() }
        b.tvBell.setOnClickListener { fixNotifications() }

        // One tap to prove alerts actually reach this phone.
        b.btnTestNotif.setOnClickListener {
            val shown = AlertNotifier.show(
                this,
                Alert(
                    id = "test-${System.currentTimeMillis()}",
                    type = Alert.SOS,
                    title = "Test alert",
                    body = "If you can see this, alerts are working.",
                    ts = System.currentTimeMillis()
                )
            )
            toast(
                if (shown) "Sent — check your notification shade"
                else "Blocked. Notifications are switched off for this app."
            )
            renderNotificationState()
        }
    }

    override fun onResume() {
        super.onResume()
        renderNotificationState()
    }

    // ── Notifications ─────────────────────────────────────────────────────────

    /**
     * Alerts used to be dropped in silence when the permission was missing: the
     * child sent an SOS, the parent app marked it seen, and nothing appeared.
     * Now the state is always visible on screen.
     */
    private fun renderNotificationState() {
        val allowed = AlertNotifier.canPost(this) &&
            NotificationManagerCompat.from(this).areNotificationsEnabled()
        b.boxNotifWarning.visibility = if (allowed) View.GONE else View.VISIBLE
    }

    private fun fixNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        // Either already granted, or the user denied it twice and Android will no
        // longer show the dialog — send them straight to the settings page.
        try {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            )
        } catch (_: Exception) {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")
                )
            )
        }
    }

    // ── Listening ─────────────────────────────────────────────────────────────

    private fun startListening() {
        attachedAt = System.currentTimeMillis()

        FirebaseRepo.listenChildInfo(code) { model, lastSeen, internetBlocked ->
            runOnUiThread { renderStatus(model, lastSeen, internetBlocked) }
        }

        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        FirebaseRepo.listenUsageToday(code, today) { usage ->
            runOnUiThread { renderUsage(usage) }
        }

        FirebaseRepo.listenAlerts(code) { alert ->
            if (alert.ts >= attachedAt && !alert.seen) {
                AlertNotifier.show(this, alert)
                FirebaseRepo.markAlertSeen(code, alert.id)
            }
            recentAlerts.removeAll { it.id == alert.id }
            recentAlerts.add(alert)
            if (recentAlerts.size > 15) {
                recentAlerts.sortByDescending { it.ts }
                while (recentAlerts.size > 15) recentAlerts.removeAt(recentAlerts.size - 1)
            }
            runOnUiThread { renderAlerts() }
        }
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    private fun renderStatus(model: String, lastSeen: Long, internetBlocked: Boolean) {
        if (lastSeen == 0L) {
            b.tvChildName.text = "Not connected"
            b.tvStatus.text = "Open the pairing window, then enter the code on the child phone."
            return
        }
        b.tvChildName.text = model.ifBlank { "Child device" }
        val ageMin = (System.currentTimeMillis() - lastSeen) / 60_000
        val health = when {
            ageMin <= 3L    -> "Online now"
            ageMin <= 30L   -> "Last seen ${ageMin} min ago"
            ageMin <= 1440L -> "Last seen ${ageMin / 60} h ago — check battery settings"
            else            -> "Offline for ${ageMin / 1440} days — the service is not running"
        }
        b.tvStatus.text = if (internetBlocked) "$health  ·  Internet is OFF" else health
    }

    private fun renderUsage(usage: Map<String, Int>) {
        if (usage.isEmpty()) {
            b.tvUsageTotal.text = "—"
            b.tvUsagePill.visibility = View.GONE
            b.tvUsageList.text =
                "No usage data yet.\n\nIf the child has been using the phone, Usage Access is " +
                    "probably not granted. On the child phone open the app and use button 1."
            return
        }

        // Our own app's time is not interesting to a parent.
        val apps = usage.filterKeys { it != packageName }
        val total = apps.values.sum()

        b.tvUsageTotal.text = formatDuration(total)
        b.tvUsagePill.visibility = View.VISIBLE
        b.tvUsagePill.text = "${apps.size} apps"

        b.tvUsageList.text = apps.entries
            .sortedByDescending { it.value }
            .take(8)
            .joinToString("\n") { (pkg, min) -> "${appLabel(pkg)}  ·  ${formatDuration(min)}" }
    }

    private fun renderAlerts() {
        if (recentAlerts.isEmpty()) {
            b.tvAlerts.text = "Nothing yet"
            return
        }
        val fmt = SimpleDateFormat("MMM d, HH:mm", Locale.US)
        b.tvAlerts.text = recentAlerts
            .sortedByDescending { it.ts }
            .take(8)
            .joinToString("\n\n") { a ->
                "${fmt.format(Date(a.ts))}\n${a.title}\n${a.body}"
            }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Package name -> the name a person would recognise. */
    private fun appLabel(pkg: String): String = labelCache.getOrPut(pkg) {
        try {
            val ai = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(ai).toString()
        } catch (_: Exception) {
            // Not installed on the parent phone, which is normal. Fall back to
            // the last part of the package name: com.whatsapp -> whatsapp
            pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }

    private fun formatDuration(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    private fun send(type: String, confirmation: String) {
        FirebaseRepo.pushCommand(code, Command(type = type))
        toast(confirmation)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun genCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}
