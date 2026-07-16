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
 * traffic and simply does NOT forward it -> the child's internet is cut cleanly
 * (works for Wi-Fi and mobile data, no router needed). When it is false the
 * tunnel tears itself down so normal traffic flows again.
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
            tun = Builder()
                .setSession("BeaverGuardian")
                .addAddress("10.111.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("10.111.0.1")
                .establish()
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
                // Outbound packets are intentionally dropped -> no connectivity.
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
