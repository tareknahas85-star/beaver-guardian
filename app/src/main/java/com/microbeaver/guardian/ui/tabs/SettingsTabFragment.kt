package com.microbeaver.guardian.ui.tabs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import com.microbeaver.guardian.alerts.AlertNotifier
import com.microbeaver.guardian.databinding.FragmentSettingsTabBinding
import com.microbeaver.guardian.ui.GuardianState

/**
 * Tab 4 — the switches.
 *
 * Every switch writes straight back to the shared policy, so the child device
 * picks the change up within a minute. [binding] is suppressed while rendering,
 * otherwise setting a switch from a policy update would fire its own listener and
 * write the same value back in a loop.
 */
class SettingsTabFragment : TabBase() {

    private var _b: FragmentSettingsTabBinding? = null
    private val b get() = _b!!

    private var binding = false

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

        // The internet switch goes through a command, not the policy, so the
        // child tears the VPN down immediately instead of waiting for the tick.
        b.swInternet.onChange { on ->
            host?.sendCommand(
                if (on) "BLOCK_INTERNET" else "ALLOW_INTERNET",
                if (on) "Internet paused" else "Internet resumed"
            )
        }

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
        binding = false
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
