package com.microbeaver.guardian.ui.tabs

import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.materialswitch.MaterialSwitch
import com.microbeaver.guardian.LocaleManager
import com.microbeaver.guardian.R
import com.microbeaver.guardian.alerts.AlertNotifier
import com.microbeaver.guardian.calls.NumberUtils
import com.microbeaver.guardian.data.CallMode
import com.microbeaver.guardian.data.GeoZone
import com.microbeaver.guardian.databinding.FragmentSettingsTabBinding
import com.microbeaver.guardian.monitor.ScheduleEvaluator
import com.microbeaver.guardian.ui.GuardianState

/**
 * Everything configurable, in one place.
 *
 * Safe zones, time rules and the number lists used to live on a second screen as
 * well. Two screens writing the same policy fields caused a real bug: that screen
 * rendered once, so opening it before the policy arrived left every switch false,
 * and its Save button then wrote those blanks over the parent's real settings —
 * which is how call filtering ended up inverted. There is now exactly one editor
 * per setting.
 */
class SettingsTabFragment : TabBase() {

    private var _b: FragmentSettingsTabBinding? = null
    private val b get() = _b!!

    private var binding = false
    private var appFilter = ""
    private var lastApps: Map<String, String> = emptyMap()
    private var lastBlocked: Set<String> = emptySet()

    /** True once the parent has typed in a number box, so render stops overwriting it. */
    private var numbersDirty = false

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentSettingsTabBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ── Call mode ──
        b.rgCallMode.setOnCheckedChangeListener { _, id ->
            if (binding) return@setOnCheckedChangeListener
            val mode = when (id) {
                R.id.rbCallUnknown   -> CallMode.BLOCK_UNKNOWN
                R.id.rbCallWhitelist -> CallMode.WHITELIST_ONLY
                else                 -> CallMode.OFF
            }
            host?.savePolicy { it.callMode = mode }
        }
        b.swOutgoing.onChange { on -> host?.savePolicy { it.restrictOutgoing = on } }

        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { numbersDirty = true }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        }
        b.etPriorityNumbers.addTextChangedListener(watcher)
        b.etAllowedNumbers.addTextChangedListener(watcher)
        b.etBlockedNumbers.addTextChangedListener(watcher)

        b.btnSaveNumbers.setOnClickListener { saveNumbers() }

        // ── Other switches ──
        b.swSos.onChange { on -> host?.savePolicy { it.sosEnabled = on } }
        b.swWeekly.onChange { on -> host?.savePolicy { it.weeklyReport = on } }
        b.swFeed.onChange { on -> host?.savePolicy { it.activityFeedEnabled = on } }
        b.swLockAtLimit.onChange { on -> host?.savePolicy { it.lockWhenLimitReached = on } }
        b.swInternet.onChange { on ->
            host?.sendCommand(
                if (on) "BLOCK_INTERNET" else "ALLOW_INTERNET",
                if (on) "Internet paused" else "Internet resumed"
            )
        }

        b.sliderLimit.addOnChangeListener { _, value, _ ->
            b.tvLimitValue.text = limitLabel(value.toInt())
        }
        b.sliderLimit.addOnSliderTouchListener(
            object : com.google.android.material.slider.Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(s: com.google.android.material.slider.Slider) {}
                override fun onStopTrackingTouch(s: com.google.android.material.slider.Slider) {
                    if (!binding) host?.savePolicy { it.dailyLimitMinutes = s.value.toInt() }
                }
            }
        )

        b.etAppSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                appFilter = s?.toString()?.trim()?.lowercase() ?: ""
                renderApps(lastApps, lastBlocked)
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        // ── Zones and rules ──
        b.btnAddZone.setOnClickListener { addZoneHere() }
        b.btnClearZones.setOnClickListener { host?.savePolicy { it.zones = emptyList() } }
        b.btnAddBedtime.setOnClickListener { addRule(ScheduleEvaluator.bedtime()) }
        b.btnAddStudy.setOnClickListener { addRule(ScheduleEvaluator.studyTime()) }
        b.btnClearRules.setOnClickListener { host?.savePolicy { it.schedules = emptyList() } }

        // ── Language ──
        b.rgLang.setOnCheckedChangeListener { _, id ->
            if (binding) return@setOnCheckedChangeListener
            val tag = when (id) {
                R.id.rbLangEn -> LocaleManager.EN
                else          -> LocaleManager.SYSTEM
            }
            LocaleManager.apply(tag)
        }

        b.btnNotifSettings.setOnClickListener { host?.openNotificationSettings() }
        b.btnAbout.setOnClickListener { host?.openAbout() }
    }

    override fun onResume() {
        super.onResume()
        val ctx = context ?: return
        b.tvNotifState.text = if (AlertNotifier.canPost(ctx)) {
            "Alerts can reach this phone."
        } else {
            "Alerts are blocked. SOS and unknown callers will not reach you."
        }
    }

    override fun render(s: GuardianState.Snapshot) {
        binding = true

        when (CallMode.of(s.policy)) {
            CallMode.BLOCK_UNKNOWN  -> b.rbCallUnknown.isChecked = true
            CallMode.WHITELIST_ONLY -> b.rbCallWhitelist.isChecked = true
            else                    -> b.rbCallOff.isChecked = true
        }
        b.swOutgoing.isChecked    = s.policy.restrictOutgoing
        b.swInternet.isChecked    = s.policy.internetBlocked
        b.swSos.isChecked         = s.policy.sosEnabled
        b.swWeekly.isChecked      = s.policy.weeklyReport
        b.swFeed.isChecked        = s.policy.activityFeedEnabled
        b.swLockAtLimit.isChecked = s.policy.lockWhenLimitReached

        val limit = s.policy.dailyLimitMinutes.coerceIn(0, 480)
        b.sliderLimit.value = limit.toFloat()
        b.tvLimitValue.text = limitLabel(limit)

        // Do not stomp on half-typed input.
        if (!numbersDirty) {
            b.etPriorityNumbers.setText(s.policy.priorityNumbers.joinToString("\n"))
            b.etAllowedNumbers.setText(s.policy.allowedNumbers.joinToString("\n"))
            b.etBlockedNumbers.setText(s.policy.blockedNumbers.joinToString("\n"))
        }

        when (LocaleManager.current(context)) {
            LocaleManager.EN -> b.rbLangEn.isChecked = true
            else             -> b.rbLangSystem.isChecked = true
        }

        binding = false

        b.tvCallHint.text = buildString {
            append("Emergency numbers are never blocked. ")
            if (s.policy.priorityNumbers.isNotEmpty()) {
                append("${s.policy.priorityNumbers.size} number(s) always ring.")
            }
        }

        b.tvZonesList.text = if (s.policy.zones.isEmpty()) "No safe zones yet."
        else s.policy.zones.joinToString("\n") { "${it.name}  ·  ${it.radiusM} m" }

        b.tvRulesList.text = if (s.policy.schedules.isEmpty()) "No time rules yet."
        else s.policy.schedules.joinToString("\n") { r ->
            "${r.name}  ·  ${ScheduleEvaluator.formatMinute(r.startMinute)}" +
                "–${ScheduleEvaluator.formatMinute(r.endMinute)}"
        }

        lastApps = s.installedApps
        lastBlocked = s.policy.blockedApps.toSet()
        renderApps(lastApps, lastBlocked)
    }

    // ── Numbers ───────────────────────────────────────────────────────────────

    private fun saveNumbers() {
        val priority = parse(b.etPriorityNumbers.text.toString())
        val allowed  = parse(b.etAllowedNumbers.text.toString())
        val blocked  = parse(b.etBlockedNumbers.text.toString())

        host?.savePolicy {
            it.priorityNumbers = priority
            it.allowedNumbers = allowed
            it.blockedNumbers = blocked
        }
        numbersDirty = false

        // A number that is too short to be a real line is almost always a typo,
        // and in whitelist mode a typo means that person gets blocked.
        val suspicious = (priority + allowed).filter { NumberUtils.digitsOnly(it).length in 1..8 }
        val msg = if (suspicious.isEmpty()) {
            "Saved"
        } else {
            "Saved, but check these — they look too short: ${suspicious.joinToString(", ")}"
        }
        Toast.makeText(context, msg, if (suspicious.isEmpty()) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
    }

    private fun parse(raw: String): List<String> =
        raw.split('\n', ',', ';').map { it.trim() }.filter { it.isNotBlank() }.distinct()

    // ── Zones and rules ───────────────────────────────────────────────────────

    private fun addRule(rule: com.microbeaver.guardian.data.ScheduleRule) {
        host?.savePolicy { p ->
            p.schedules = p.schedules.filter { it.id != rule.id } + rule
        }
    }

    private fun addZoneHere() {
        val ctx = context ?: return
        val act = activity ?: return
        if (ContextCompat.checkSelfPermission(
                ctx, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                act, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), 301
            )
            return
        }
        val existing = GuardianState.snapshot.policy.zones.size
        val name = b.etZoneName.text.toString().trim().ifBlank { "Zone ${existing + 1}" }
        val radius = b.etZoneRadius.text.toString().trim().toIntOrNull()?.coerceIn(50, 5000) ?: 200

        Toast.makeText(ctx, "Locating…", Toast.LENGTH_SHORT).show()
        try {
            LocationServices.getFusedLocationProviderClient(ctx)
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
                .addOnSuccessListener { loc ->
                    if (loc == null) {
                        Toast.makeText(ctx, "Could not get a position", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }
                    host?.savePolicy { p ->
                        p.zones = p.zones + GeoZone(
                            id = "z${System.currentTimeMillis()}",
                            name = name, lat = loc.latitude, lng = loc.longitude,
                            radiusM = radius, notifyOnExit = true, notifyOnEnter = true
                        )
                    }
                    b.etZoneName.setText("")
                }
        } catch (_: SecurityException) {
        }
    }

    // ── Apps ──────────────────────────────────────────────────────────────────

    private fun limitLabel(minutes: Int) = if (minutes <= 0) "Off" else fmt(minutes)

    private fun renderApps(apps: Map<String, String>, blocked: Set<String>) {
        val hostView = b.appsHost
        hostView.removeAllViews()

        if (apps.isEmpty()) {
            b.tvAppsHint.text = "The child device has not sent its app list yet. " +
                "It reports once an hour."
            return
        }

        val matching = apps.entries
            .filter { appFilter.isEmpty() || it.value.lowercase().contains(appFilter) }
            .sortedWith(
                compareByDescending<Map.Entry<String, String>> { it.key in blocked }
                    .thenBy { it.value.lowercase() }
            )

        b.tvAppsHint.text = "${blocked.size} restricted of ${apps.size} apps" +
            if (appFilter.isNotEmpty()) "  ·  ${matching.size} matching" else ""

        val inflater = LayoutInflater.from(context)
        for ((pkg, label) in matching.take(40)) {
            val row = inflater.inflate(R.layout.item_app_toggle, hostView, false)
            row.findViewById<TextView>(R.id.tvAppName).text = label
            val isBlocked = pkg in blocked
            row.findViewById<TextView>(R.id.tvAppState).text =
                if (isBlocked) "Restricted" else "Allowed"
            val sw = row.findViewById<MaterialSwitch>(R.id.swApp)
            sw.setOnCheckedChangeListener(null)
            sw.isChecked = isBlocked
            sw.setOnCheckedChangeListener { _, checked ->
                host?.savePolicy { p ->
                    val set = p.blockedApps.toMutableSet()
                    if (checked) set.add(pkg) else set.remove(pkg)
                    p.blockedApps = set.toList()
                }
            }
            hostView.addView(row)
        }
        if (matching.size > 40) {
            hostView.addView(TextView(context).apply {
                text = "…and ${matching.size - 40} more. Use the search box."
                textSize = 12f
                setPadding(0, 12, 0, 0)
            })
        }
    }

    private fun CompoundButton.onChange(action: (Boolean) -> Unit) {
        setOnCheckedChangeListener { _, checked -> if (!binding) action(checked) }
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }
}
