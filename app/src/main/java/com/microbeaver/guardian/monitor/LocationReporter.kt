package com.microbeaver.guardian.monitor

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.microbeaver.guardian.data.FirebaseRepo

object LocationReporter {
    fun report(ctx: Context, code: String, onLoc: ((Double, Double) -> Unit)? = null) {
        if (ContextCompat.checkSelfPermission(
                ctx, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val client = LocationServices.getFusedLocationProviderClient(ctx)
        val cts = CancellationTokenSource()
        try {
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        FirebaseRepo.reportLocation(code, loc.latitude, loc.longitude)
                        onLoc?.invoke(loc.latitude, loc.longitude)
                    }
                }
        } catch (_: SecurityException) {
        }
    }
}
