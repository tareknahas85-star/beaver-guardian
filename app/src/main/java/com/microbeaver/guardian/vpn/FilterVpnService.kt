package com.microbeaver.guardian.vpn

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.microbeaver.guardian.App
import com.microbeaver.guardian.R
import com.microbeaver.guardian.ui.RoleSelectActivity
import java.io.FileInputStream
import kotlin.concurrent.thread

/**
 * Local VPN used to switch the child's internet off.
 *
 * When blocking, it opens a tun interface that captures traffic and drops it, so
 * nothing reaches the network. This works on Wi-Fi and mobile data with no router
 * involved.
 *
 * ## The bug this class used to have
 * The tunnel captured **every** app, including Beaver Guardian itself. So the
 * moment the internet was cut, the child device lost its own connection to
 * Firebase and could never receive the "allow internet" command. The internet
 * stayed off forever and the parent had no way to restore it.
 *
 * Two things prevent that now:
 *
 * 1. [Builder.addDisallowedApplication] keeps this app — and Google Play
 *    Services, which carries push messages — outside the tunnel. The child's
 *    other apps are blocked; ours keeps talking to Firebase, so the unblock
 *    command always arrives.
 * 2. A fail-open watchdog. If the tunnel has been up for [MAX_BLOCK_MS] without
 *    the monitor service confirming the policy still says "blocked", it opens the
 *    internet by itself. A parent must never be able to leave a child with a
 *    permanently dead phone, and a child must never be unreachable.
 *
 * The service is also driven by explicit [ACTION_START] / [ACTION_STOP] intents.
 * Flipping a static flag was not enough: `onStartCommand` never ran again, and
 * the reader thread was parked in a blocking `read()`, so nothing tore the
 * tunnel down.
 */
class FilterVpnService : VpnService() {

    private var tun: ParcelFileDescriptor? = null
    @Volatile private var running = false
    @Volatile private var reader: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Log.d(TAG, "ACTION_STOP -> opening internet")
                internetBlocked = false
                stopTunnel()
                return START_NOT_STICKY
            }
            ACTION_START -> internetBlocked = true
        }

        if (!internetBlocked) {
            // Anything that starts us without asking to block means "open".
            stopTunnel()
            return START_NOT_STICKY
        }

        startForeground(NOTIF_ID, notification())
        if (!running) startTunnel()
        return START_STICKY
    }

    // ── Tunnel ────────────────────────────────────────────────────────────────

    private fun startTunnel() {
        try {
            val builder = Builder()
                .setSession("BeaverGuardian")
                .addAddress("10.111.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("10.111.0.1")

            // Critical: never trap our own traffic, or the unblock command can
            // never reach this device. Play Services is excluded too so push
            // notifications keep working while the child is offline.
            for (pkg in listOf(packageName, "com.google.android.gms")) {
                try {
                    builder.addDisallowedApplication(pkg)
                } catch (e: Exception) {
                    Log.w(TAG, "could not exclude $pkg: ${e.message}")
                }
            }

            tun = builder.establish()
            if (tun == null) {
                Log.e(TAG, "establish() returned null — VPN permission missing?")
                internetBlocked = false
                stopTunnel()
                return
            }

            blockedSince = System.currentTimeMillis()
            running = true
            reader = thread(start = true, name = "vpn-reader") { loop() }
            Log.d(TAG, "tunnel up")
        } catch (e: Exception) {
            Log.e(TAG, "startTunnel failed: ${e.message}")
            internetBlocked = false
            stopTunnel()
        }
    }

    private fun loop() {
        val fd = tun?.fileDescriptor ?: return
        val input = FileInputStream(fd)
        val buf = ByteArray(32767)
        while (running) {
            try {
                if (!internetBlocked || watchdogExpired()) {
                    Log.d(TAG, "loop noticed we should open up")
                    break
                }
                // Blocks until a packet arrives. Closing the descriptor from
                // stopTunnel() makes this throw, which is how we wake up.
                val n = input.read(buf)
                if (n <= 0) Thread.sleep(50)
            } catch (_: InterruptedException) {
                break
            } catch (_: Exception) {
                // Descriptor closed, or a transient read error.
                if (!running) break
                Thread.sleep(100)
            }
        }
        if (watchdogExpired()) {
            Log.w(TAG, "watchdog expired -> failing open")
            internetBlocked = false
        }
        stopTunnel()
    }

    /**
     * True when the tunnel has been up too long without the monitor service
     * refreshing [lastPolicyConfirmMs].
     */
    private fun watchdogExpired(): Boolean {
        val since = blockedSince
        if (since == 0L) return false
        val quietFor = System.currentTimeMillis() - maxOf(since, lastPolicyConfirmMs)
        return quietFor > MAX_BLOCK_MS
    }

    private fun stopTunnel() {
        running = false
        blockedSince = 0L
        try { tun?.close() } catch (_: Exception) {}
        tun = null
        reader?.interrupt()
        reader = null
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION") stopForeground(true)
            }
        } catch (_: Exception) {}
        stopSelf()
        Log.d(TAG, "tunnel down")
    }

    override fun onRevoke() {
        // The user turned the VPN off from Android settings. Respect it — this is
        // also the manual escape hatch if the app ever gets stuck again.
        Log.w(TAG, "VPN revoked by user")
        internetBlocked = false
        stopTunnel()
        super.onRevoke()
    }

    override fun onDestroy() {
        running = false
        try { tun?.close() } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun notification() = NotificationCompat.Builder(this, App.CH_VPN)
        .setContentTitle("Beaver Guardian")
        .setContentText("Internet is off")
        .setSmallIcon(R.drawable.ic_notification)
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0, Intent(this, RoleSelectActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    companion object {
        private const val TAG = "FilterVpn"
        private const val NOTIF_ID = 2

        const val ACTION_START = "com.microbeaver.guardian.VPN_START"
        const val ACTION_STOP  = "com.microbeaver.guardian.VPN_STOP"

        /**
         * Longest the internet may stay off without the monitor service
         * confirming the policy. Six hours covers a normal bedtime block while
         * still guaranteeing the phone recovers on its own if something breaks.
         */
        const val MAX_BLOCK_MS = 6 * 60 * 60 * 1000L

        @Volatile var internetBlocked: Boolean = false
        @Volatile var blockedDomains: HashSet<String> = HashSet()

        /** When the current block started. 0 when not blocking. */
        @Volatile private var blockedSince: Long = 0L

        /** Refreshed by MonitorService each time it confirms the policy. */
        @Volatile private var lastPolicyConfirmMs: Long = 0L

        /** Called once a minute by MonitorService while a block is intended. */
        fun confirmStillBlocked() { lastPolicyConfirmMs = System.currentTimeMillis() }

        /** Turn the internet off. */
        fun block(ctx: Context) {
            internetBlocked = true
            val i = Intent(ctx, FilterVpnService::class.java).setAction(ACTION_START)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
                else ctx.startService(i)
            } catch (e: Exception) {
                Log.e(TAG, "block() failed: ${e.message}")
            }
        }

        /**
         * Turn the internet back on. Sends a real intent so `onStartCommand`
         * runs and tears the tunnel down — setting the flag alone did nothing.
         */
        fun unblock(ctx: Context) {
            internetBlocked = false
            val i = Intent(ctx, FilterVpnService::class.java).setAction(ACTION_STOP)
            try {
                ctx.startService(i)
            } catch (e: Exception) {
                Log.e(TAG, "unblock() failed: ${e.message}")
            }
        }
    }
}
