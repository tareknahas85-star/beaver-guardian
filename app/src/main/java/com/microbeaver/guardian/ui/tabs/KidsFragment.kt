package com.microbeaver.guardian.ui.tabs

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.microbeaver.guardian.admin.PolicyManager
import com.microbeaver.guardian.databinding.FragmentKidsBinding
import com.microbeaver.guardian.ui.GuardianState

/** Tab 2 — the child device itself: where it is, its safe zones, how protected it is. */
class KidsFragment : TabBase() {

    private var _b: FragmentKidsBinding? = null
    private val b get() = _b!!

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _b = FragmentKidsBinding.inflate(i, c, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        b.btnLocate.setOnClickListener { host?.sendCommand("LOCATE", "Asking for a fresh position") }
        b.btnPair.setOnClickListener { host?.openPairingWindow() }
        b.btnZones.setOnClickListener { host?.openFullSettings() }

        b.btnMap.setOnClickListener {
            val s = GuardianState.snapshot
            if (!s.hasLocation) {
                Toast.makeText(context, "No position reported yet", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            try {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("geo:${s.lat},${s.lng}?q=${s.lat},${s.lng}(Child)")
                    )
                )
            } catch (_: Exception) {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://maps.google.com/?q=${s.lat},${s.lng}")
                    )
                )
            }
        }
    }

    override fun render(s: GuardianState.Snapshot) {
        b.tvDevice.text = s.model.ifBlank { if (s.lastSeen == 0L) "Not connected" else "Child device" }

        b.tvSeen.text = when {
            s.error != null      -> s.error
            s.lastSeen == 0L     -> "Enter the pairing code on the child phone while the window is open."
            s.isOnline           -> "Online now"
            s.minutesSinceSeen < 60  -> "Last seen ${s.minutesSinceSeen} min ago"
            s.minutesSinceSeen < 1440 -> "Last seen ${s.minutesSinceSeen / 60} h ago — check battery settings"
            else                 -> "Offline for ${s.minutesSinceSeen / 1440} days"
        }

        b.tvCoords.text = if (s.hasLocation) {
            "Position: %.5f, %.5f  ·  %s".format(
                s.lat, s.lng, GuardianState.relativeTime(s.locationTs)
            )
        } else {
            "No position reported yet"
        }

        b.tvZones.text = if (s.policy.zones.isEmpty()) {
            "No safe zones yet. Add one from the child's home or school so you get told when " +
                "they arrive or leave."
        } else {
            s.policy.zones.joinToString("\n") { z ->
                val alerts = listOfNotNull(
                    if (z.notifyOnEnter) "arrive" else null,
                    if (z.notifyOnExit) "leave" else null
                ).joinToString(" + ").ifBlank { "off" }
                "${z.name}  ·  ${z.radiusM} m  ·  alerts: $alerts"
            }
        }

        val ctx = context
        b.tvProtection.text = if (ctx == null) "" else {
            val pm = PolicyManager(ctx)
            when {
                pm.isDeviceOwner -> "Device Owner — the app cannot be uninstalled."
                pm.isAdminActive -> "Device Admin only — the child can still remove the app after " +
                    "turning admin off. Full protection needs the Device Owner step in docs/SETUP.md."
                else -> "No protection active on this phone."
            }
        }
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }
}
