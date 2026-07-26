package com.microbeaver.guardian.monitor

import android.content.Context
import android.location.Location
import com.microbeaver.guardian.data.Alert
import com.microbeaver.guardian.data.FirebaseRepo
import com.microbeaver.guardian.data.GeoZone

/**
 * Safe-zone (geofence) crossing detection.
 *
 * Rather than the Play Services geofencing API — which caps out at 100 fences,
 * needs its own PendingIntent plumbing, and is unreliable when the device is
 * dozing — we piggyback on the location fix [MonitorService] already takes each
 * minute. For zone radii measured in hundreds of metres that is plenty precise.
 *
 * ## Hysteresis
 * A child sitting near a boundary would otherwise generate an alert every
 * minute as GPS noise pushes them back and forth. So exiting requires being
 * [HYSTERESIS_M] metres *beyond* the radius, while entering requires being that
 * far inside it. The dead band between the two produces no events at all.
 */
class GeofenceEvaluator(private val ctx: Context) {

    /** zoneId -> was the child inside at the previous fix? */
    private val lastInside = HashMap<String, Boolean>()

    fun evaluate(code: String, zones: List<GeoZone>, location: Location) {
        if (code.isBlank() || zones.isEmpty()) return

        // Forget zones the parent deleted.
        lastInside.keys.retainAll(zones.mapNotNull { it.id.takeIf(String::isNotBlank) }.toSet())

        for (zone in zones) {
            val id = zone.id.takeIf { it.isNotBlank() } ?: continue
            val distance = distanceMeters(location.latitude, location.longitude, zone.lat, zone.lng)

            val previous = lastInside[id]
            val inside = when (previous) {
                // First fix for this zone: no hysteresis, just record where we are.
                null  -> distance <= zone.radiusM
                // Already inside — stay inside until clearly beyond the boundary.
                true  -> distance <= zone.radiusM + HYSTERESIS_M
                // Already outside — stay outside until clearly within it.
                false -> distance <= (zone.radiusM - HYSTERESIS_M).coerceAtLeast(MIN_INNER_RADIUS_M)
            }
            lastInside[id] = inside
            FirebaseRepo.setZoneState(code, id, inside)

            if (previous == null || previous == inside) continue

            if (!inside && zone.notifyOnExit) {
                push(code, zone, location, Alert.ZONE_EXIT,
                    "خروج من ${zone.name} / Left ${zone.name}",
                    "غادر المنطقة الآمنة / Left the safe zone")
            } else if (inside && zone.notifyOnEnter) {
                push(code, zone, location, Alert.ZONE_ENTER,
                    "وصل إلى ${zone.name} / Arrived at ${zone.name}",
                    "دخل المنطقة الآمنة / Entered the safe zone")
            }
        }
    }

    private fun push(
        code: String, zone: GeoZone, loc: Location,
        type: String, title: String, body: String
    ) {
        FirebaseRepo.pushAlert(
            code,
            Alert(
                type = type,
                title = title,
                body = body,
                zoneName = zone.name,
                lat = loc.latitude,
                lng = loc.longitude,
                ts = System.currentTimeMillis()
            )
        )
    }

    companion object {
        /** Dead band around the boundary, in metres. Roughly consumer-GPS error. */
        const val HYSTERESIS_M = 60.0

        /** Never shrink the "entering" radius below this. */
        const val MIN_INNER_RADIUS_M = 25.0

        /** Great-circle distance in metres (haversine). */
        fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6_371_000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
            return 2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        }
    }
}
