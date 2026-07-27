package com.microbeaver.guardian.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.microbeaver.guardian.BuildConfig
import com.microbeaver.guardian.Prefs
import com.microbeaver.guardian.alerts.AlertNotifier
import com.microbeaver.guardian.alerts.ParentAlertService
import com.microbeaver.guardian.data.Command
import com.microbeaver.guardian.data.FirebaseRepo
import com.microbeaver.guardian.databinding.ActivityMainBinding
import com.microbeaver.guardian.ui.tabs.ActivityTabFragment
import com.microbeaver.guardian.ui.tabs.DashboardFragment
import com.microbeaver.guardian.ui.tabs.KidsFragment
import com.microbeaver.guardian.ui.tabs.SettingsTabFragment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The parent app's single host activity.
 *
 * The app used to be one long scrolling screen, which is why it "only showed one
 * page". Everything now lives in four tabs, following the SafeGuard navigation
 * spec: Dashboard, Kids, Activity, Settings.
 *
 * This activity owns all the Firebase listeners and publishes into
 * [GuardianState]; the tabs are pure renderers. Attaching listeners per tab meant
 * a reload on every tab switch.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var code: String

    private var attachedAt = 0L
    private val alerts = ArrayList<com.microbeaver.guardian.data.Alert>()

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshHeader() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        code = Prefs.getPairCode(this) ?: genCode().also { Prefs.setPairCode(this, it) }
        GuardianState.update { it.copy(code = code) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        b.bottomNav.setOnItemSelectedListener { item ->
            show(
                when (item.itemId) {
                    com.microbeaver.guardian.R.id.tab_kids     -> KidsFragment()
                    com.microbeaver.guardian.R.id.tab_activity -> ActivityTabFragment()
                    com.microbeaver.guardian.R.id.tab_settings -> SettingsTabFragment()
                    else -> DashboardFragment()
                }
            )
            true
        }
        if (savedInstanceState == null) {
            b.bottomNav.selectedItemId = com.microbeaver.guardian.R.id.tab_dashboard
        }

        b.tvBell.setOnClickListener { openNotificationSettings() }

        ParentAlertService.start(this)
        attachListeners()
    }

    override fun onResume() {
        super.onResume()
        refreshHeader()
    }

    private fun show(f: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(com.microbeaver.guardian.R.id.tabContent, f)
            .commit()
    }

    private fun refreshHeader() {
        val notifOk = AlertNotifier.canPost(this)
        b.tvHeaderSub.text = buildString {
            append("v${BuildConfig.VERSION_NAME}")
            append("  ·  ")
            append(code)
            if (!notifOk) append("  ·  alerts off")
        }
    }

    // ── Data ──────────────────────────────────────────────────────────────────

    private fun attachListeners() {
        attachedAt = System.currentTimeMillis()

        FirebaseRepo.claimDevice(code) { ok ->
            if (!ok) {
                GuardianState.update {
                    it.copy(
                        error = "Cannot reach the database. Check the connection, that " +
                            "Anonymous sign-in is enabled in Firebase, and that the rules " +
                            "were published."
                    )
                }
            }

            FirebaseRepo.listenChildInfo(code) { model, lastSeen, _ ->
                GuardianState.update { it.copy(model = model, lastSeen = lastSeen, error = null) }
            }

            FirebaseRepo.listenPolicy(code) { p ->
                GuardianState.update { it.copy(policy = p) }
            }

            val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
            FirebaseRepo.listenUsageToday(code, today) { usage ->
                GuardianState.update { it.copy(usage = usage.filterKeys { k -> k != packageName }) }
            }

            FirebaseRepo.listenLocation(code) { lat, lng, ts ->
                GuardianState.update { it.copy(lat = lat, lng = lng, locationTs = ts) }
            }

            FirebaseRepo.listenAlerts(code) { alert ->
                if (alert.ts >= attachedAt && !alert.seen) {
                    AlertNotifier.show(this, alert)
                    FirebaseRepo.markAlertSeen(code, alert.id)
                }
                alerts.removeAll { it.id == alert.id }
                alerts.add(alert)
                val sorted = alerts.sortedByDescending { it.ts }.take(20)
                GuardianState.update { it.copy(alerts = sorted) }
            }
        }
    }

    // ── Actions the tabs call into ────────────────────────────────────────────

    fun sendCommand(type: String, confirmation: String) {
        FirebaseRepo.pushCommand(code, Command(type = type))
        Toast.makeText(this, confirmation, Toast.LENGTH_SHORT).show()
    }

    fun savePolicy(mutate: (com.microbeaver.guardian.data.Policy) -> Unit) {
        val p = GuardianState.snapshot.policy
        mutate(p)
        FirebaseRepo.setPolicy(code, p)
    }

    fun openPairingWindow() {
        FirebaseRepo.openPairing(code)
        Toast.makeText(
            this,
            "The child device can join for the next 15 minutes",
            Toast.LENGTH_LONG
        ).show()
    }

    fun openNotificationSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !AlertNotifier.canPost(this)
        ) {
            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        try {
            startActivity(
                Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
            )
        } catch (_: Exception) {
            Toast.makeText(this, "Open Settings > Apps > SafeGuard > Notifications",
                Toast.LENGTH_LONG).show()
        }
    }

    fun openAbout() = startActivity(Intent(this, AboutActivity::class.java))

    fun openFullSettings() = startActivity(Intent(this, GuardSettingsActivity::class.java))

    private fun genCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}
