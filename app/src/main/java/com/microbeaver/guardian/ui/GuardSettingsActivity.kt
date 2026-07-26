package com.microbeaver.guardian.ui

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.microbeaver.guardian.Prefs
import com.microbeaver.guardian.calls.CallScreeningRole
import com.microbeaver.guardian.data.FirebaseRepo
import com.microbeaver.guardian.data.GeoZone
import com.microbeaver.guardian.data.Policy
import com.microbeaver.guardian.data.ScheduleRule
import com.microbeaver.guardian.databinding.ActivityGuardSettingsBinding
import com.microbeaver.guardian.monitor.ScheduleEvaluator

/**
 * Parent-side configuration for the supervision features: call filtering,
 * safe zones, schedule rules, SOS and the weekly digest.
 *
 * Everything is written into the shared [Policy] under the pairing code; the
 * child picks the change up through its policy listener within seconds.
 *
 * Zones are added from *this* device's current position, which is the practical
 * way to record "home" or "school" without embedding a map SDK — the parent
 * stands where the zone should be and taps the button.
 */
class GuardSettingsActivity : AppCompatActivity() {

    private lateinit var b: ActivityGuardSettingsBinding
    private var code: String = ""

    /** Working copy; only pushed when the parent hits Save. */
    private var policy: Policy = Policy()
    private var loaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityGuardSettingsBinding.inflate(layoutInflater)
        setContentView(b.root)

        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "الإعدادات / Settings"
        b.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        code = Prefs.getPairCode(this) ?: ""
        if (code.isBlank()) {
            Toast.makeText(this, "لا يوجد رمز ربط / No pairing code", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        FirebaseRepo.claimDevice(code)
        FirebaseRepo.listenPolicy(code) { p ->
            // Only take the remote copy once, so we do not stomp on edits in progress.
            if (loaded) return@listenPolicy
            loaded = true
            policy = p
            runOnUiThread { render() }
        }

        b.btnAddZoneHere.setOnClickListener { addZoneAtCurrentLocation() }
        b.btnClearZones.setOnClickListener {
            policy.zones = emptyList(); render()
        }

        b.btnAddBedtime.setOnClickListener { addSchedule(ScheduleEvaluator.bedtime()) }
        b.btnAddStudy.setOnClickListener { addSchedule(ScheduleEvaluator.studyTime()) }
        b.btnClearSchedules.setOnClickListener {
            policy.schedules = emptyList(); render()
        }

        b.tvScreeningStatus.setOnClickListener { CallScreeningRole.request(this) }

        b.btnSave.setOnClickListener { save() }
    }

    override fun onResume() {
        super.onResume()
        b.tvScreeningStatus.text =
            "حالة صلاحية فلترة المكالمات على هذا الجهاز: ${CallScreeningRole.statusText(this)}"
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    private fun render() {
        b.swCallFilter.isChecked       = policy.callFilterEnabled
        b.swBlockUnknown.isChecked     = policy.blockUnknownCalls
        b.swAllowContacts.isChecked    = policy.allowContacts
        b.swRestrictOutgoing.isChecked = policy.restrictOutgoing
        b.swSos.isChecked              = policy.sosEnabled
        b.swWeekly.isChecked           = policy.weeklyReport

        b.etAllowedNumbers.setText(policy.allowedNumbers.joinToString("\n"))
        b.etBlockedNumbers.setText(policy.blockedNumbers.joinToString("\n"))

        b.tvZones.text = if (policy.zones.isEmpty()) {
            "لا مناطق / No zones yet"
        } else {
            policy.zones.joinToString("\n") { z ->
                "• ${z.name} — ${z.radiusM}م / ${z.radiusM}m " +
                    "(${"%.4f".format(z.lat)}, ${"%.4f".format(z.lng)})"
            }
        }

        b.tvSchedules.text = if (policy.schedules.isEmpty()) {
            "لا قواعد / No rules yet"
        } else {
            policy.schedules.joinToString("\n") { r ->
                "• ${r.name} — ${ScheduleEvaluator.formatMinute(r.startMinute)}" +
                    "–${ScheduleEvaluator.formatMinute(r.endMinute)}" +
                    (if (r.lockDevice) "  🔒" else "") +
                    (if (r.blockInternet) "  📶✖" else "")
            }
        }
    }

    // ── Editing ───────────────────────────────────────────────────────────────

    private fun addSchedule(rule: ScheduleRule) {
        // Replace any rule with the same id rather than stacking duplicates.
        policy.schedules = policy.schedules.filter { it.id != rule.id } + rule
        render()
    }

    private fun addZoneAtCurrentLocation() {
        if (ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), 301
            )
            return
        }

        val name = b.etZoneName.text.toString().trim()
            .ifBlank { "منطقة ${policy.zones.size + 1} / Zone ${policy.zones.size + 1}" }
        val radius = b.etZoneRadius.text.toString().trim().toIntOrNull()?.coerceIn(50, 5000) ?: 200

        Toast.makeText(this, "جاري تحديد الموقع… / Locating…", Toast.LENGTH_SHORT).show()
        try {
            LocationServices.getFusedLocationProviderClient(this)
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
                .addOnSuccessListener { loc ->
                    if (loc == null) {
                        Toast.makeText(this, "تعذّر تحديد الموقع / Could not get a fix",
                            Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }
                    policy.zones = policy.zones + GeoZone(
                        id = "z${System.currentTimeMillis()}",
                        name = name,
                        lat = loc.latitude,
                        lng = loc.longitude,
                        radiusM = radius,
                        notifyOnExit = true,
                        notifyOnEnter = true
                    )
                    b.etZoneName.setText("")
                    render()
                }
        } catch (_: SecurityException) {
        }
    }

    // ── Saving ────────────────────────────────────────────────────────────────

    private fun save() {
        policy.callFilterEnabled = b.swCallFilter.isChecked
        policy.blockUnknownCalls = b.swBlockUnknown.isChecked
        policy.allowContacts     = b.swAllowContacts.isChecked
        policy.restrictOutgoing  = b.swRestrictOutgoing.isChecked
        policy.sosEnabled        = b.swSos.isChecked
        policy.weeklyReport      = b.swWeekly.isChecked
        policy.allowedNumbers    = parseNumbers(b.etAllowedNumbers.text.toString())
        policy.blockedNumbers    = parseNumbers(b.etBlockedNumbers.text.toString())

        FirebaseRepo.setPolicy(code, policy)
            .addOnSuccessListener {
                Toast.makeText(this, "تم الحفظ ✓ / Saved", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "فشل الحفظ: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun parseNumbers(raw: String): List<String> =
        raw.split('\n', ',', ';')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed(); return true
        }
        return super.onOptionsItemSelected(item)
    }
}
