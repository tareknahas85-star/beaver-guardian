package com.microbeaver.guardian.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.microbeaver.guardian.App
import com.microbeaver.guardian.R
import java.io.FileInputStream
import java.nio.ByteBuffer
import kotlin.concurrent.thread

/**
 * Local VPN used for internet control.
 *
 * When [internetBlocked] is true we establish a tun interface that captures all
 * traffic and drops it -> the child's internet is cut. CRUCIALLY we exclude our
 * own package from the tunnel (addDisallowedApplication) so Beaver Guardian keeps
 * its Firebase connection alive even while everything else is blocked. Without
 * this, cutting the internet would also cut our control channel and the parent's
 * "restore internet" command could never reach the child.
 */
class FilterVpnService : VpnService() {

    private var tun: ParcelFileDescriptor? = null
    @Volatile private var running = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            2,
            NotificationCompat.Builder(this, App.CH_VPN)
                .setContentTitle("Beaver Guardian — Internet filter")
                .setContentText("فلتر الإنترنت / Internet filter active")
                .setSmallIcon(R.drawable.ic_launcher)
                .setOngoing(true)
                .build()
        )
        if (internetBlocked && !running) startTunnel() else if (!internetBlocked) stopTunnel()
        return START_STICKY
    }

    private fun startTunnel() {
        try {
            val builder = Builder()
                .setSession("BeaverGuardian")
                .addAddress("10.111.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("10.111.0.1")
            // Keep our own app OFF the VPN so Firebase stays reachable.
            try { builder.addDisallowedApplication(packageName) } catch (_: Exception) {}
            tun = builder.establish()
            running = true
            thread(start = true) { loop() }
        } catch (_: Exception) {
        }
    }

    private fun loop() {
        val fd = tun?.fileDescriptor ?: return
        val input = FileInputStream(fd)
        val buffer = ByteBuffer.allocate(32767)
        while (running) {
            try {
                if (!internetBlocked) { stopTunnel(); return }
                val n = input.read(buffer.array())
                // Outbound packets from other apps are dropped -> no connectivity.
                buffer.clear()
                if (n <= 0) Thread.sleep(40)
            } catch (_: Exception) {
                Thread.sleep(100)
            }
        }
    }

    private fun stopTunnel() {
        running = false
        try { tun?.close() } catch (_: Exception) {}
        tun = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        try { tun?.close() } catch (_: Exception) {}
        super.onDestroy()
    }

    companion object {
        @Volatile var internetBlocked: Boolean = false
        @Volatile var blockedDomains: HashSet<String> = HashSet()
    }
}
