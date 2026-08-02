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
import com.microbeaver.guardian.ui.tabs.LiveFragment
import com.microbeaver.guardian.ui.tabs.SettingsTabFragment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The parent app's single host activity.
 *
 * The app used to be one long scrolling screen, which is why it "only showed one
 * page". Everything now lives in four tabs, following the SafeGuard navigation
 * spec: Dashboard, Activity, Live, Settings. The Kids tab was folded into the
 * dashboard — it duplicated what was already there.
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
    private val events = ArrayList<com.microbeaver.guardian.data.ActivityEvent>()

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
                    com.microbeaver.guardian.R.id.tab_activity -> ActivityTabFragment()
                    com.microbeaver.guardian.R.id.tab_live     -> LiveFragment()
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

            FirebaseRepo.listenChildInfo(code) { model, lastSeen, _, battery, charging ->
                GuardianState.update {
                    it.copy(
                        model = model, lastSeen = lastSeen,
                        battery = battery, charging = charging, error = null
                    )
                }
            }

            FirebaseRepo.listenInstalledApps(code) { apps ->
                GuardianState.update { it.copy(installedApps = apps) }
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

            FirebaseRepo.listenEvents(code) { e ->
                events.removeAll { it.id == e.id }
                events.add(e)
                val recent = events.sortedByDescending { it.ts }.take(120)
                GuardianState.update { it.copy(events = recent) }
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

    /**
     * Opens internet for exactly [minutes], then it re-blocks itself.
     *
     * Written straight to the policy rather than through [sendCommand]: the
     * child's [com.microbeaver.guardian.monitor.MonitorService] already reacts
     * to a policy change immediately (it un-tunnels the VPN in `applyPolicy`),
     * so a command round-trip isn't needed — and using one here would race
     * against `internetTimerUntil`, since BLOCK_INTERNET/ALLOW_INTERNET clear
     * that field to keep a manual toggle from fighting a stale timer.
     */
    fun openInternetTimer(minutes: Int) {
        savePolicy {
            it.internetBlocked = false
            it.internetTimerUntil = System.currentTimeMillis() + minutes * 60_000L
        }
        Toast.makeText(
            this,
            "Internet open for ${GuardianState.formatDuration(minutes)} — it re-blocks itself after that",
            Toast.LENGTH_LONG
        ).show()
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

    /**
     * Asks Android to stop dozing this app.
     *
     * The alert listener is a foreground service, but on Doze-aggressive OEMs
     * (Huawei among the worst) it still gets frozen, which is why notifications
     * arrived in batches instead of at once. Being on the unrestricted list is the
     * only supported fix; the dialog is Android's, so the parent has to accept it.
     */
    fun requestNoDoze() {
        try {
            val pm = getSystemService(android.os.PowerManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                pm?.isIgnoringBatteryOptimizations(packageName) == false
            ) {
                startActivity(
                    Intent(
                        android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        android.net.Uri.parse("package:$packageName")
                    )
                )
            } else {
                Toast.makeText(this, "Battery is already unrestricted", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
            try {
                startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) {
                Toast.makeText(this, "Open Settings > Battery > Unrestricted", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** True when Android will not freeze our listener. */
    fun isBatteryUnrestricted(): Boolean = try {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            getSystemService(android.os.PowerManager::class.java)
                ?.isIgnoringBatteryOptimizations(packageName) == true
    } catch (_: Exception) { true }

    private fun ignoreBatteryOptimisations() = Unit

    private fun genCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}
