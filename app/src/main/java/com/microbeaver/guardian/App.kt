package com.microbeaver.guardian

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CH_MONITOR, "Supervision", NotificationManager.IMPORTANCE_LOW)
            )
            nm.createNotificationChannel(
                NotificationChannel(CH_VPN, "Internet filter", NotificationManager.IMPORTANCE_MIN)
            )
        }
    }

    companion object {
        const val CH_MONITOR = "monitor"
        const val CH_VPN = "vpn_filter"
    }
}
