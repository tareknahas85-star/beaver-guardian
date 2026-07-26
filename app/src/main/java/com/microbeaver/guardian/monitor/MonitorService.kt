package com.microbeaver.guardian.monitor

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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
        code = Prefs.getPairCode(this) ?: ""
        if (code.isNotEmpty()) {
            // Make sure this UID is a member before trying to read anything.
            FirebaseRepo.claimDevice(code) {
                FirebaseRepo.listenPolicy(code) { p -> policy = p; applyPolicy(p) }
                FirebaseRepo.listenCommands(code) { c -> handleCommand(c) }
            }
        }
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (code.isEmpty()) code = Prefs.getPairCode(this) ?: ""
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

        CallLogReporter.reportRecent(this, code)
        reportLocationAndFences()
        FirebaseRepo.setChildInfo(code, "${Build.MANUFACTURER} ${Build.MODEL}")
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

        val blocked = HashSet<String>(policy.blockedApps)
        blocked.addAll(overLimit)
        blocked.addAll(schedule.blockedApps)
        if (policy.locked || schedule.lockDevice) blocked.add("*")

        AppBlockService.blockedPackages = blocked

        // Device Owner can hard-hide apps, which survives a task-switch.
        overLimit.forEach { policyMgr.setAppHidden(it, true) }
        schedule.blockedApps.forEach { policyMgr.setAppHidden(it, true) }

        if (schedule.lockDevice) policyMgr.lockNow()

        val internetOff = policy.internetBlocked || schedule.blockInternet
        FilterVpnService.internetBlocked = internetOff
        if (internetOff) startService(Intent(this, FilterVpnService::class.java))
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

        FilterVpnService.internetBlocked = p.internetBlocked
        FilterVpnService.blockedDomains = HashSet(p.blockedDomains)
        if (p.internetBlocked) startService(Intent(this, FilterVpnService::class.java))

        // Mirror the call rules locally — the screening service is started cold
        // by the telecom stack and cannot wait on Firebase.
        CallPolicyStore.save(this, p)

        // Keep uninstall protection asserted; a Device Owner can lose it after
        // an OTA or a policy reset.
        policyMgr.applyBaselineOwnerPolicies()

        if (p.weeklyReport) WeeklyReportWorker.schedule(this)
        else WeeklyReportWorker.cancel(this)
    }

    private fun handleCommand(c: Command) {
        when (c.type) {
            "LOCK_NOW" -> { policyMgr.lockNow(); FirebaseRepo.updatePolicy(code, mapOf("locked" to true)) }
            "UNLOCK" -> FirebaseRepo.updatePolicy(code, mapOf("locked" to false))
            "BLOCK_INTERNET" -> {
                FilterVpnService.internetBlocked = true
                startService(Intent(this, FilterVpnService::class.java))
                FirebaseRepo.updatePolicy(code, mapOf("internetBlocked" to true))
            }
            "ALLOW_INTERNET" -> {
                FilterVpnService.internetBlocked = false
                FirebaseRepo.updatePolicy(code, mapOf("internetBlocked" to false))
            }
            "BLOCK_APP" -> policyMgr.setAppHidden(c.payload, true)
            "UNBLOCK_APP" -> policyMgr.setAppHidden(c.payload, false)
            "LOCATE" -> LocationReporter.reportOnce(this, code)
        }
        FirebaseRepo.markDone(code, c.id)
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, RoleSelectActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, App.CH_MONITOR)
            .setContentTitle("Beaver Guardian")
            .setContentText("هذا الجهاز تحت إشراف وليّ الأمر / Supervised device")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        // START_STICKY + BootReceiver bring the service back; do not relaunch here
        // (starting an FGS from background is restricted on Android 12+).
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
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
