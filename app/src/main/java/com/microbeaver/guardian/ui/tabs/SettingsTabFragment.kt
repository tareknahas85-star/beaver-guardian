package com.microbeaver.guardian.ui.tabs

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.TextView
import com.google.android.material.materialswitch.MaterialSwitch
import com.microbeaver.guardian.R
import com.microbeaver.guardian.alerts.AlertNotifier
import com.microbeaver.guardian.databinding.FragmentSettingsTabBinding
import com.microbeaver.guardian.ui.GuardianState

/**
 * Tab 4 — the switches, the daily screen time budget, and the list of apps on the
 * child's phone.
 *
 * Every control writes straight back to the shared policy, so the child picks the
 * change up within a minute. [binding] is raised while rendering: without it,
 * populating a switch from a policy update would fire its own listener and write
 * the same value straight back, in a loop.
 */
class SettingsTabFragment : TabBase() {

    private var _b: FragmentSettingsTabBinding? = null
    private val b get() = _b!!

    private var binding = false
    private var appFilter = ""

    /** Last rendered app list, so typing in the search box does not need the network. */
    private var lastApps: Map<String, String> = emptyMap()
    private var lastBlocked: Set<String> = emptySet()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentSettingsTabBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        b.swCallFilter.onChange { on ->
            host?.savePolicy { it.callFilterEnabled = on; it.blockUnknownCalls = on }
        }
        b.swAllowContacts.onChange { on -> host?.savePolicy { it.allowContacts = on } }
        b.swOutgoing.onChange { on -> host?.savePolicy { it.restrictOutgoing = on } }
        b.swSos.onChange { on -> host?.savePolicy { it.sosEnabled = on } }
        b.swWeekly.onChange { on -> host?.savePolicy { it.weeklyReport = on } }
        b.swLockAtLimit.onChange { on -> host?.savePolicy { it.lockWhenLimitReached = on } }

        // The internet switch goes through a command, not the policy, so the child
        // tears the VPN down at once instead of waiting for the next tick.
        b.swInternet.onChange { on ->
            host?.sendCommand(
                if (on) "BLOCK_INTERNET" else "ALLOW_INTERNET",
                if (on) "Internet paused" else "Internet resumed"
            )
        }

        // Label follows the thumb live; only the release writes to the database, so
        // dragging does not produce dozens of writes.
        b.sliderLimit.addOnChangeListener { _, value, _ ->
            b.tvLimitValue.text = limitLabel(value.toInt())
        }
        b.sliderLimit.addOnSliderTouchListener(
            object : com.google.android.material.slider.Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: com.google.android.material.slider.Slider) {}
                override fun onStopTrackingTouch(slider: com.google.android.material.slider.Slider) {
                    if (!binding) host?.savePolicy { it.dailyLimitMinutes = slider.value.toInt() }
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

        b.btnNotifSettings.setOnClickListener { host?.openNotificationSettings() }
        b.btnAdvanced.setOnClickListener { host?.openFullSettings() }
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
        b.swCallFilter.isChecked    = s.policy.callFilterEnabled
        b.swAllowContacts.isChecked = s.policy.allowContacts
        b.swOutgoing.isChecked      = s.policy.restrictOutgoing
        b.swInternet.isChecked      = s.policy.internetBlocked
        b.swSos.isChecked           = s.policy.sosEnabled
        b.swWeekly.isChecked        = s.policy.weeklyReport
        b.swLockAtLimit.isChecked   = s.policy.lockWhenLimitReached

        val limit = s.policy.dailyLimitMinutes.coerceIn(0, 480)
        b.sliderLimit.value = limit.toFloat()
        b.tvLimitValue.text = limitLabel(limit)
        binding = false

        lastApps = s.installedApps
        lastBlocked = s.policy.blockedApps.toSet()
        renderApps(lastApps, lastBlocked)
    }

    private fun limitLabel(minutes: Int) = if (minutes <= 0) "Off" else fmt(minutes)

    /**
     * The app list comes from the child device, which reports it once an hour.
     * Blocked apps float to the top so the parent can see what is restricted
     * without scrolling a hundred rows.
     */
    private fun renderApps(apps: Map<String, String>, blocked: Set<String>) {
        val hostView = b.appsHost
        hostView.removeAllViews()

        if (apps.isEmpty()) {
            b.tvAppsHint.text =
                "The child device has not sent its app list yet. It reports once an hour, " +
                    "so this fills in shortly after pairing."
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
            val more = TextView(context).apply {
                text = "…and ${matching.size - 40} more. Use the search box to narrow it down."
                textSize = 12f
                setPadding(0, 12, 0, 0)
            }
            hostView.addView(more)
        }
    }

    /** Ignores changes made while [render] is populating the controls. */
    private fun CompoundButton.onChange(action: (Boolean) -> Unit) {
        setOnCheckedChangeListener { _, checked -> if (!binding) action(checked) }
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }
}
