package com.microbeaver.guardian.monitor

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.microbeaver.guardian.App
import com.microbeaver.guardian.Prefs
import com.microbeaver.guardian.R
import com.microbeaver.guardian.admin.PolicyManager
import com.microbeaver.guardian.data.Command
import com.microbeaver.guardian.data.FirebaseRepo
import com.microbeaver.guardian.data.Policy
import com.microbeaver.guardian.ui.RoleSelectActivity
import com.microbeaver.guardian.vpn.FilterVpnService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var policyMgr: PolicyManager
    private var code: String = ""
    private var lastInsideZone = true

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
        startForeground(1, buildNotification())
        code = Prefs.getPairCode(this) ?: ""
        if (code.isNotEmpty()) {
            FirebaseRepo.listenPolicy(code) { p -> policy = p; applyPolicy(p) }
            FirebaseRepo.listenCommands(code) { c -> handleCommand(c) }
        }
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (code.isEmpty()) code = Prefs.getPairCode(this) ?: ""
        return START_STICKY
    }

    private fun cycle() {
        if (code.isEmpty()) return
        val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val usage = UsageTracker.todayUsageMinutes(this)
        usage.forEach { (pkg, min) -> FirebaseRepo.reportUsage(code, date, pkg, min) }
        AppInfoReporter.reportUsedApps(this, code, usage)
        enforceLimits(usage)
        CallLogReporter.reportRecent(this, code)
        LocationReporter.report(this, code) { lat, lng -> checkGeofence(lat, lng) }
        FirebaseRepo.setChildInfo(code, "${Build.MANUFACTURER} ${Build.MODEL}")
    }

    private fun checkGeofence(lat: Double, lng: Double) {
        if (!policy.geoEnabled) { lastInsideZone = true; return }
        val res = FloatArray(1)
        Location.distanceBetween(lat, lng, policy.geoLat, policy.geoLng, res)
        val inside = res[0] <= policy.geoRadius
        if (!inside && lastInsideZone) {
            FirebaseRepo.pushEvent(code, "GEOFENCE_EXIT", "خرج من المنطقة الآمنة (${res[0].toInt()} م)")
        }
        lastInsideZone = inside
    }

    private fun enforceLimits(usage: Map<String, Int>) {
        val overLimit = HashSet<String>()
        policy.limits.forEach { entry ->
            val parts = entry.split("=")
            if (parts.size == 2) {
                val pkg = parts[0]
                val limit = parts[1].toIntOrNull() ?: return@forEach
                if ((usage[pkg] ?: 0) >= limit) overLimit.add(pkg)
            }
        }
        val set = HashSet<String>(policy.blockedApps)
        set.addAll(overLimit)
        if (policy.locked) set.add("*")
        AppBlockService.blockedPackages = set
        overLimit.forEach { policyMgr.setAppHidden(it, true) }
    }

    private fun applyPolicy(p: Policy) {
        val set = HashSet<String>(p.blockedApps)
        if (p.locked) { set.add("*"); policyMgr.lockNow() }
        AppBlockService.blockedPackages = set
        p.blockedApps.forEach { policyMgr.setAppHidden(it, true) }

        FilterVpnService.internetBlocked = p.internetBlocked
        FilterVpnService.blockedDomains = HashSet(p.blockedDomains)
        if (p.internetBlocked) startService(Intent(this, FilterVpnService::class.java))
    }

    private fun handleCommand(c: Command) {
        when (c.type) {
            "SYNC_NOW" -> cycle()
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
            "LOCATE" -> LocationReporter.report(this, code) { lat, lng -> checkGeofence(lat, lng) }
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
            .setSmallIcon(R.drawable.ic_launcher)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(ctx: Context) {
            val i = Intent(ctx, MonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }
    }
}
