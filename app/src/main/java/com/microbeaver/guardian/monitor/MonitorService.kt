package com.microbeaver.guardian.monitor

import android.app.ActivityManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.microbeaver.guardian.App
import com.microbeaver.guardian.Prefs
import com.microbeaver.guardian.R
import com.microbeaver.guardian.admin.PolicyManager
import com.microbeaver.guardian.calls.CallPolicyStore
import com.microbeaver.guardian.data.ActivityEvent
import com.microbeaver.guardian.data.Alert
import com.microbeaver.guardian.data.Command
import com.microbeaver.guardian.data.FirebaseRepo
import com.microbeaver.guardian.data.Policy
import com.microbeaver.guardian.ui.RoleSelectActivity
import com.microbeaver.guardian.vpn.FilterVpnService
import com.microbeaver.guardian.work.WeeklyReportWorker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persistent foreground service on the child device. It:
 *  - listens to Policy + Commands from the parent (near real-time),
 *  - every minute: reports usage, calls and location, then enforces
 *    app limits, the parent's schedule rules and the safe-zone fences.
 */
class MonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var policyMgr: PolicyManager
    private lateinit var geofences: GeofenceEvaluator
    private var code: String = ""
    private var listenersAttached = false
    private var lastAppListReport = 0L
    private var lastEventTrim = 0L
    private var eventReceiver: DeviceEventReceiver? = null
    private var lastInternetOff: Boolean? = null
    private var lastOverBudget = false
    private var wasPinned = false

    @Volatile
    private var policy: Policy = Policy()

    private val tick = object : Runnable {
        override fun run() {
            try { cycle() } catch (_: Exception) {}
            handler.postDelayed(this, 60_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        policyMgr = PolicyManager(this)
        geofences = GeofenceEvaluator(this)
        startForeground(1, buildNotification())
        eventReceiver = DeviceEventReceiver.register(this)
        code = Prefs.getPairCode(this) ?: ""
        if (code.isNotEmpty()) attachListeners()
        handler.post(tick)
    }

    /**
     * Attaches the policy and command listeners.
     *
     * These must attach even if [FirebaseRepo.claimDevice] reports failure. With
     * the old code a failed claim meant no listeners at all, so the child stopped
     * responding to everything — including the command to turn the internet back
     * on. Claiming is about database permissions; losing it must never cost us
     * the ability to listen.
     */
    private fun attachListeners() {
        if (listenersAttached) return
        listenersAttached = true
        FirebaseRepo.claimDevice(code) { ok ->
            if (!ok) Log.w(TAG, "claimDevice failed for $code — listening anyway")
            // Both callbacks are wrapped here as a last line of defence: this is
            // the one place every incoming policy update and every incoming
            // command passes through. Anything unexpected either one does — an
            // OEM API throwing, a future field that doesn't parse the way it's
            // used — must never be allowed to escape this closure, because an
            // uncaught exception here kills the listener for good, silently, and
            // that takes LOCK_NOW/BLOCK_INTERNET/camera-disable/app-limits down
            // with it, not just whichever update triggered it. This is exactly
            // the failure mode that made everything look "connected" (the child's
            // own outbound reporting in cycle() is separately try/caught and kept
            // working) while nothing sent from the parent ever took effect.
            FirebaseRepo.listenPolicy(code) { p ->
                try {
                    policy = p
                    applyPolicy(p)
                } catch (e: Exception) {
                    Log.e(TAG, "applyPolicy crashed, ignoring so the listener survives: ${e.message}")
                }
            }
            FirebaseRepo.listenCommands(code) { c ->
                try {
                    handleCommand(c)
                } catch (e: Exception) {
                    Log.e(TAG, "handleCommand crashed for ${c.type}, ignoring so the listener survives: ${e.message}")
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Re-read the pair code every time we're (re)started, not just when it
        // was empty. This service is a long-running singleton: `code` used to
        // be cached once in onCreate() and never refreshed, so re-pairing to a
        // new code while the service was already alive looked exactly like the
        // app "disconnecting" from the child — the service kept listening on
        // the old code forever and never noticed Prefs had a new one.
        val current = Prefs.getPairCode(this) ?: ""
        if (current.isNotEmpty() && current != code) {
            code = current
            listenersAttached = false
            attachListeners()
        }
        if (intent?.action == ACTION_SOS && code.isNotEmpty()) {
            SosReporter.trigger(this, code)
        }
        return START_STICKY
    }

    private fun cycle() {
        if (code.isEmpty()) return
        val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val usage = UsageTracker.todayUsageMinutes(this)
        usage.forEach { (pkg, min) -> FirebaseRepo.reportUsage(code, date, pkg, min) }

        enforce(usage)
        checkScreenPinning()

        CallLogReporter.reportRecent(this, code)
        reportLocationAndFences()

        val batt = DeviceInfo.battery(this)
        // LOCK_NOW and BLOCK_INTERNET are no-ops without these two one-time OS
        // grants — report the real state every cycle so the parent can see it
        // instead of a command silently doing nothing (see handleCommand()).
        FirebaseRepo.setChildInfo(
            code, "${Build.MANUFACTURER} ${Build.MODEL}", batt.percent, batt.charging,
            adminActive = policyMgr.isAdminActive,
            vpnReady = VpnService.prepare(this) == null,
            accessibilityActive = AppBlockService.isEnabled(this)
        )

        // The installed list barely changes; once an hour is plenty and keeps the
        // write volume down on the free Firebase plan.
        val now = System.currentTimeMillis()
        if (now - lastAppListReport > APP_LIST_INTERVAL_MS) {
            lastAppListReport = now
            FirebaseRepo.reportInstalledApps(code, DeviceInfo.launchableApps(this))
        }

        if (now - lastEventTrim > EVENT_TRIM_INTERVAL_MS) {
            lastEventTrim = now
            FirebaseRepo.trimEvents(code)
        }
    }

    /**
     * Single place where every restriction is combined, so the three sources
     * — the standing policy, per-app daily limits and the active schedule —
     * can never disagree about what is blocked.
     */
    private fun enforce(usage: Map<String, Int>) {
        val schedule = ScheduleEvaluator.evaluate(policy.schedules)

        val overLimit = HashSet<String>()
        policy.limits.forEach { entry ->
            val parts = entry.split("=")
            if (parts.size == 2) {
                val pkg = parts[0]
                val limit = parts[1].toIntOrNull() ?: return@forEach
                if ((usage[pkg] ?: 0) >= limit) overLimit.add(pkg)
            }
        }

        // Whole-device daily budget. "*" tells AppBlockService to bounce every app.
        val dailyBudget = policy.dailyLimitMinutes
        val totalToday = usage.filterKeys { it != packageName }.values.sum()
        val overDailyBudget = dailyBudget > 0 && totalToday >= dailyBudget

        val blocked = HashSet<String>(policy.blockedApps)
        blocked.addAll(overLimit)
        blocked.addAll(schedule.blockedApps)
        if (policy.locked || schedule.lockDevice) blocked.add("*")
        if (overDailyBudget && policy.lockWhenLimitReached) blocked.add("*")

        AppBlockService.blockedPackages = blocked

        // Device Owner can hard-hide apps, which survives a task-switch.
        overLimit.forEach { policyMgr.setAppHidden(it, true) }
        schedule.blockedApps.forEach { policyMgr.setAppHidden(it, true) }

        if (schedule.lockDevice) policyMgr.lockNow()
        if (overDailyBudget && policy.lockWhenLimitReached) policyMgr.lockNow()

        // A temporary-open window overrides the standing block while it's
        // active. Once it passes, re-block and clear the timer ourselves —
        // write it back to Firebase so the parent's dashboard reflects it
        // too, and don't rely on the parent remembering to re-block by hand.
        var effectiveBlocked = policy.internetBlocked
        val timerUntil = policy.internetTimerUntil
        if (timerUntil > 0) {
            if (System.currentTimeMillis() >= timerUntil) {
                effectiveBlocked = true
                policy = policy.copy(internetBlocked = true, internetTimerUntil = 0L)
                FirebaseRepo.updatePolicy(
                    code, mapOf("internetBlocked" to true, "internetTimerUntil" to 0L)
                )
                EventReporter.record(
                    this, ActivityEvent.INTERNET,
                    "Internet timer ended", "Auto re-blocked"
                )
            } else {
                effectiveBlocked = false
            }
        }

        // Drive the VPN through explicit start/stop so the tunnel is actually
        // torn down when the internet should come back, and refresh the
        // watchdog so a still-intended block does not fail open.
        val internetOff = effectiveBlocked || schedule.blockInternet
        if (internetOff) {
            FilterVpnService.confirmStillBlocked()
            FilterVpnService.block(this)
        } else {
            FilterVpnService.unblock(this)
        }

        // Only report the change, not the state, or the feed fills with one row a
        // minute saying nothing happened.
        if (lastInternetOff != internetOff) {
            lastInternetOff = internetOff
            EventReporter.record(
                this, ActivityEvent.INTERNET,
                if (internetOff) "Internet paused" else "Internet resumed"
            )
        }
        if (overDailyBudget && !lastOverBudget) {
            EventReporter.record(
                this, ActivityEvent.LIMIT_REACHED,
                "Daily screen time used up",
                "Budget was ${dailyBudget} min"
            )
        }
        lastOverBudget = overDailyBudget
    }

    /**
     * Safety net for the screen-pinning bypass: [PolicyManager.applyBaselineOwnerPolicies]
     * already removes every package from the lock-task allow-list, which should stop the
     * child from being able to turn pinning on at all on a Device Owner phone. This check
     * covers the gap if that ever doesn't hold — an OEM-specific "lock this app" feature
     * outside the standard API, or a device that's Device Admin only — by detecting the
     * state directly rather than trusting prevention alone. If it's ever active, force the
     * screen to lock (so the pinned app stops being usable without a fresh unlock) and tell
     * the parent immediately, once per episode rather than spamming every cycle.
     */
    private fun checkScreenPinning() {
        val am = getSystemService(ActivityManager::class.java) ?: return
        val pinned = try {
            am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
        } catch (_: Exception) {
            false
        }

        if (pinned && !wasPinned) {
            policyMgr.lockNow()
            val pkg = AppBlockService.lastForegroundPkg
            val body = if (!pkg.isNullOrBlank()) {
                "Tried to pin \"${EventReporter.appLabel(this, pkg)}\" open. The screen was locked automatically."
            } else {
                "Screen pinning was detected. The screen was locked automatically."
            }
            FirebaseRepo.pushAlert(
                code,
                Alert(type = Alert.PIN_ATTEMPT, title = "Tried to pin an app", body = body, ts = System.currentTimeMillis())
            )
            EventReporter.record(this, ActivityEvent.PIN_ATTEMPT, "Tried to pin an app", body, pkg ?: "")
        }
        wasPinned = pinned
    }

    /** One location fix, used both for the parent's map and the safe zones. */
    private fun reportLocationAndFences() {
        if (ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        try {
            LocationServices.getFusedLocationProviderClient(this)
                .getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    CancellationTokenSource().token
                )
                .addOnSuccessListener { loc ->
                    if (loc == null) return@addOnSuccessListener
                    FirebaseRepo.reportLocation(code, loc.latitude, loc.longitude)
                    geofences.evaluate(code, policy.zones, loc)
                }
        } catch (_: SecurityException) {
        }
    }

    private fun applyPolicy(p: Policy) {
        val set = HashSet<String>(p.blockedApps)
        if (p.locked) { set.add("*"); policyMgr.lockNow() }
        AppBlockService.blockedPackages = set
        p.blockedApps.forEach { policyMgr.setAppHidden(it, true) }

        FilterVpnService.blockedDomains = HashSet(p.blockedDomains)
        if (p.internetBlocked) {
            FilterVpnService.confirmStillBlocked()
            FilterVpnService.block(this)
        } else {
            FilterVpnService.unblock(this)
        }

        // Mirror the call rules locally — the screening service is started cold
        // by the telecom stack and cannot wait on Firebase.
        CallPolicyStore.save(this, p)
        EventReporter.feedEnabled = p.activityFeedEnabled

        // Camera disable is a plain Device Admin capability (no Device Owner
        // needed), same tier as lockNow.
        policyMgr.setCameraDisabled(p.cameraDisabled)

        // Keep uninstall protection asserted; a Device Owner can lose it after
        // an OTA or a policy reset.
        policyMgr.applyBaselineOwnerPolicies()

        if (p.weeklyReport) WeeklyReportWorker.schedule(this)
        else WeeklyReportWorker.cancel(this)
    }

    private fun handleCommand(c: Command) {
        when (c.type) {
            "LOCK_NOW" -> {
                if (!policyMgr.isAdminActive) {
                    reportSetupNeeded(
                        "Couldn't lock the phone",
                        "Device Admin isn't active on the child's phone. Open Beaver " +
                            "Guardian there → Setup → \"Enable Device Admin\"."
                    )
                } else {
                    policyMgr.lockNow()
                    FirebaseRepo.updatePolicy(code, mapOf("locked" to true))
                }
            }
            "UNLOCK" -> FirebaseRepo.updatePolicy(code, mapOf("locked" to false))
            "BLOCK_INTERNET" -> {
                if (VpnService.prepare(this) != null) {
                    reportSetupNeeded(
                        "Couldn't turn off internet",
                        "VPN permission isn't granted on the child's phone. Open Beaver " +
                            "Guardian there → Setup → \"Start VPN\" and accept the prompt."
                    )
                } else {
                    FilterVpnService.confirmStillBlocked()
                    FilterVpnService.block(this)
                    // Clear any pending temporary-open window — a manual block
                    // means now, not "until the timer says otherwise".
                    FirebaseRepo.updatePolicy(
                        code, mapOf("internetBlocked" to true, "internetTimerUntil" to 0L)
                    )
                }
            }
            "ALLOW_INTERNET" -> {
                // Tear the tunnel down for real, not just flip a flag.
                FilterVpnService.unblock(this)
                FirebaseRepo.updatePolicy(
                    code, mapOf("internetBlocked" to false, "internetTimerUntil" to 0L)
                )
            }
            "BLOCK_APP" -> policyMgr.setAppHidden(c.payload, true)
            "UNBLOCK_APP" -> policyMgr.setAppHidden(c.payload, false)
            "LOCATE" -> LocationReporter.reportOnce(this, code)
        }
        FirebaseRepo.markDone(code, c.id)
    }

    /**
     * Surfaces a command that was accepted but couldn't actually run because a
     * one-time OS grant is missing on this device. Before this, LOCK_NOW and
     * BLOCK_INTERNET failed exactly like this with zero trace — the parent saw
     * "Lock sent" / "Internet paused" toast on their own phone and nothing ever
     * happened on the child's, with no way to tell why.
     */
    private fun reportSetupNeeded(title: String, body: String) {
        FirebaseRepo.pushAlert(
            code,
            Alert(type = Alert.SETUP_NEEDED, title = title, body = body, ts = System.currentTimeMillis())
        )
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, RoleSelectActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, App.CH_MONITOR)
            .setContentTitle("Beaver Guardian")
            .setContentText("Supervised device")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    override fun onDestroy() {
        DeviceEventReceiver.unregister(this, eventReceiver)
        eventReceiver = null
        handler.removeCallbacks(tick)
        // START_STICKY + BootReceiver bring the service back; do not relaunch here
        // (starting an FGS from background is restricted on Android 12+).
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "MonitorService"
        private const val APP_LIST_INTERVAL_MS = 60 * 60 * 1000L
        private const val EVENT_TRIM_INTERVAL_MS = 6 * 60 * 60 * 1000L
        const val ACTION_SOS = "com.microbeaver.guardian.SOS"

        fun start(ctx: Context) {
            val i = Intent(ctx, MonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }

        fun sendSos(ctx: Context) {
            val i = Intent(ctx, MonitorService::class.java).setAction(ACTION_SOS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }
    }
}
