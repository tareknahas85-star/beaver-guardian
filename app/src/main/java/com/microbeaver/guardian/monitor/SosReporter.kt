package com.microbeaver.guardian.monitor

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.microbeaver.guardian.data.Alert
import com.microbeaver.guardian.data.FirebaseRepo

/**
 * The child's panic button.
 *
 * Design decisions that matter when someone is actually in trouble:
 *  * The alert is sent **immediately**, before waiting for a location fix, so a
 *    dead GPS or a denied permission can never swallow it.
 *  * A second alert with coordinates follows as soon as a fix arrives.
 *  * Nothing here depends on the monitor service being alive.
 */
object SosReporter {

    /** Highest accuracy we can get — this is the one fix worth the battery. */
    fun trigger(ctx: Context, code: String, note: String = "") {
        if (code.isBlank()) return

        val model = "${Build.MANUFACTURER} ${Build.MODEL}"

        // 1. Fire first, locate second.
        FirebaseRepo.pushAlert(
            code,
            Alert(
                type = Alert.SOS,
                title = "🆘 SOS",
                body = buildString {
                    append("SOS pressed on $model")
                    if (note.isNotBlank()) append("\n$note")
                },
                ts = System.currentTimeMillis()
            )
        )

        // 2. Follow up with coordinates when we have them.
        if (ContextCompat.checkSelfPermission(
                ctx, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        try {
            LocationServices.getFusedLocationProviderClient(ctx)
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
                .addOnSuccessListener { loc ->
                    if (loc == null) return@addOnSuccessListener
                    FirebaseRepo.reportLocation(code, loc.latitude, loc.longitude)
                    FirebaseRepo.pushAlert(
                        code,
                        Alert(
                            type = Alert.SOS,
                            title = "🆘 SOS location",
                            body = "https://maps.google.com/?q=${loc.latitude},${loc.longitude}",
                            lat = loc.latitude,
                            lng = loc.longitude,
                            ts = System.currentTimeMillis()
                        )
                    )
                }
        } catch (_: SecurityException) {
        }
    }
}
