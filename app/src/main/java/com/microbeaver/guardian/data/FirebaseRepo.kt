package com.microbeaver.guardian.data

import android.util.Log
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * All parent<->child sync goes through Realtime Database under /devices/{pairCode}.
 * The pairing code is the shared key that links the two installs.
 *
 * ## Access control
 * Security rules (see `database.rules.json` at the repo root) only let a device
 * touch `/devices/{code}` once its Firebase UID is listed under
 * `/devices/{code}/members`. A UID may add itself when either
 *   * the code has no members yet (the parent claiming a fresh code), or
 *   * `pairingOpenUntil` is in the future (a pairing window the parent opened).
 *
 * So the flow is: parent calls [claimDevice] -> [openPairing], child calls
 * [claimDevice] while the window is open. Call [claimDevice] on every start;
 * it is a no-op once the UID is already a member.
 */
object FirebaseRepo {
    private const val TAG = "FirebaseRepo"

    /** How long a pairing window stays open. Rules cap this at 1 hour. */
    const val PAIRING_WINDOW_MS = 15 * 60 * 1000L

    private val db get() = FirebaseDatabase.getInstance()
    private fun root(code: String) = db.getReference("devices").child(code)

    // ---------- Membership / pairing ----------

    /**
     * Adds this device's UID to the code's member list. Safe to call repeatedly.
     *
     * Waits for anonymous sign-in via [Auth.withUid] first. Calling this before
     * sign-in completed used to fail silently and leave the app with no database
     * listeners at all, which looked exactly like "paired but nothing happens".
     */
    fun claimDevice(code: String, onResult: ((Boolean) -> Unit)? = null) {
        if (code.isBlank()) { onResult?.invoke(false); return }
        Auth.withUid(
            onFailed = { msg ->
                Log.e(TAG, "claimDevice($code): no uid — $msg")
                onResult?.invoke(false)
            }
        ) { u ->
            root(code).child("members").child(u).setValue(true)
                .addOnSuccessListener { onResult?.invoke(true) }
                .addOnFailureListener { e ->
                    Log.e(TAG, "claimDevice failed for $code: ${e.message}")
                    onResult?.invoke(false)
                }
        }
    }

    /** Parent-side: lets a second device join for the next [PAIRING_WINDOW_MS]. */
    fun openPairing(code: String) {
        if (code.isBlank()) return
        root(code).child("pairingOpenUntil")
            .setValue(System.currentTimeMillis() + PAIRING_WINDOW_MS)
            .addOnFailureListener { e -> Log.e(TAG, "openPairing failed: ${e.message}") }
    }

    fun closePairing(code: String) {
        if (code.isBlank()) return
        root(code).child("pairingOpenUntil").setValue(0L)
    }

    // ---------- Policy ----------
    fun setPolicy(code: String, policy: Policy) = root(code).child("policy").setValue(policy)

    fun updatePolicy(code: String, updates: Map<String, Any?>) =
        root(code).child("policy").updateChildren(updates)

    fun listenPolicy(code: String, onChange: (Policy) -> Unit): ValueEventListener {
        val ref = root(code).child("policy")
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                onChange(s.getValue(Policy::class.java) ?: Policy())
            }
            override fun onCancelled(e: DatabaseError) {
                Log.e(TAG, "DB Error [policy/$code]: ${e.message}")
            }
        }
        ref.addValueEventListener(l)
        return l
    }

    // ---------- Commands ----------
    fun pushCommand(code: String, cmd: Command) {
        val ref = root(code).child("commands").push()
        cmd.id = ref.key ?: ""
        cmd.ts = System.currentTimeMillis()
        ref.setValue(cmd)
    }

    fun listenCommands(code: String, onCmd: (Command) -> Unit): ChildEventListener {
        val ref = root(code).child("commands")
        val l = object : ChildEventListener {
            override fun onChildAdded(s: DataSnapshot, prev: String?) {
                val c = s.getValue(Command::class.java) ?: return
                if (!c.done) onCmd(c)
            }
            override fun onChildChanged(s: DataSnapshot, prev: String?) {}
            override fun onChildRemoved(s: DataSnapshot) {}
            override fun onChildMoved(s: DataSnapshot, prev: String?) {}
            override fun onCancelled(e: DatabaseError) {
                Log.e(TAG, "DB Error [commands/$code]: ${e.message}")
            }
        }
        ref.addChildEventListener(l)
        return l
    }

    fun markDone(code: String, cmdId: String) {
        if (cmdId.isNotEmpty()) root(code).child("commands").child(cmdId).child("done").setValue(true)
    }

    // ---------- Alerts (child -> parent, high priority) ----------

    fun pushAlert(code: String, alert: Alert) {
        if (code.isBlank()) return
        val ref = root(code).child("alerts").push()
        alert.id = ref.key ?: ""
        if (alert.ts == 0L) alert.ts = System.currentTimeMillis()
        ref.setValue(alert)
    }

    /** Fires for every alert added, including the backlog already stored. */
    fun listenAlerts(code: String, onAlert: (Alert) -> Unit): ChildEventListener {
        val ref = root(code).child("alerts")
        val l = object : ChildEventListener {
            override fun onChildAdded(s: DataSnapshot, prev: String?) {
                s.getValue(Alert::class.java)?.let(onAlert)
            }
            override fun onChildChanged(s: DataSnapshot, prev: String?) {}
            override fun onChildRemoved(s: DataSnapshot) {}
            override fun onChildMoved(s: DataSnapshot, prev: String?) {}
            override fun onCancelled(e: DatabaseError) {
                Log.e(TAG, "DB Error [alerts/$code]: ${e.message}")
            }
        }
        ref.addChildEventListener(l)
        return l
    }

    fun markAlertSeen(code: String, alertId: String) {
        if (alertId.isNotEmpty()) root(code).child("alerts").child(alertId).child("seen").setValue(true)
    }

    // ---------- Live activity feed (child -> parent) ----------

    /**
     * Appends one event. Capped by [trimEvents] so the feed cannot grow forever
     * on the free plan.
     */
    fun pushEvent(code: String, event: ActivityEvent) {
        if (code.isBlank()) return
        val ref = root(code).child("events").push()
        event.id = ref.key ?: ""
        if (event.ts == 0L) event.ts = System.currentTimeMillis()
        ref.setValue(event)
    }

    /** Newest [limit] events, oldest first within the window. */
    fun listenEvents(
        code: String,
        limit: Int = 100,
        onEvent: (ActivityEvent) -> Unit
    ): ChildEventListener {
        val ref = root(code).child("events").limitToLast(limit)
        val l = object : ChildEventListener {
            override fun onChildAdded(s: DataSnapshot, prev: String?) {
                s.getValue(ActivityEvent::class.java)?.let(onEvent)
            }
            override fun onChildChanged(s: DataSnapshot, prev: String?) {}
            override fun onChildRemoved(s: DataSnapshot) {}
            override fun onChildMoved(s: DataSnapshot, prev: String?) {}
            override fun onCancelled(e: DatabaseError) {
                Log.e(TAG, "DB Error [events/$code]: ${e.message}")
            }
        }
        ref.addChildEventListener(l)
        return l
    }

    /**
     * Deletes all but the newest [keep] events. Called occasionally by the child;
     * without it the feed would grow without bound on a free database.
     */
    fun trimEvents(code: String, keep: Int = 300) {
        if (code.isBlank()) return
        root(code).child("events").orderByChild("ts").limitToLast(keep).get()
            .addOnSuccessListener { newest ->
                val keepIds = newest.children.mapNotNull { it.key }.toSet()
                root(code).child("events").get().addOnSuccessListener { all ->
                    val doomed = all.children.mapNotNull { it.key }.filter { it !in keepIds }
                    if (doomed.isEmpty()) return@addOnSuccessListener
                    val updates = doomed.associate { "$it" to null as Any? }
                    root(code).child("events").updateChildren(updates)
                }
            }
            .addOnFailureListener { e -> Log.e(TAG, "trimEvents failed: ${e.message}") }
    }

    // ---------- Reports (child -> parent) ----------
    fun reportUsage(code: String, date: String, pkg: String, minutes: Int) {
        // Firebase keys cannot contain '.', so store package with '_'.
        root(code).child("reports").child("usage").child(date)
            .child(pkg.replace('.', '_')).setValue(minutes)
    }

    fun reportCall(code: String, rec: CallRecord) {
        root(code).child("reports").child("calls").push().setValue(rec)
    }

    fun reportLocation(code: String, lat: Double, lng: Double) {
        val m = mapOf("lat" to lat, "lng" to lng, "ts" to System.currentTimeMillis())
        root(code).child("reports").child("location").child("latest").setValue(m)
    }

    fun setChildInfo(
        code: String, model: String, batteryPct: Int = -1, charging: Boolean = false,
        adminActive: Boolean? = null, vpnReady: Boolean? = null
    ) {
        val m = HashMap<String, Any>()
        m["model"] = model
        m["role"] = "child"
        m["lastSeen"] = System.currentTimeMillis()
        if (batteryPct in 0..100) {
            m["battery"] = batteryPct
            m["charging"] = charging
        }
        // Whether LOCK_NOW / BLOCK_INTERNET can actually do anything on this
        // device right now — see the comment on GuardianState.Snapshot.
        adminActive?.let { m["adminActive"] = it }
        vpnReady?.let { m["vpnReady"] = it }
        root(code).child("info").updateChildren(m)
    }

    /**
     * The child's launchable apps, so the parent can pick what to restrict
     * without typing package names. Written once an hour; the list rarely moves.
     */
    fun reportInstalledApps(code: String, apps: Map<String, String>) {
        if (code.isBlank() || apps.isEmpty()) return
        // Firebase keys cannot contain a dot.
        val safe = apps.entries.associate { (pkg, label) -> pkg.replace('.', '_') to label }
        root(code).child("reports").child("apps").setValue(safe)
    }

    /** Parent side: package name -> app label. */
    fun listenInstalledApps(code: String, onData: (Map<String, String>) -> Unit): ValueEventListener {
        val ref = root(code).child("reports").child("apps")
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                val out = HashMap<String, String>()
                for (c in s.children) {
                    val pkg = c.key?.replace('_', '.') ?: continue
                    out[pkg] = c.getValue(String::class.java) ?: pkg
                }
                onData(out)
            }
            override fun onCancelled(e: DatabaseError) {
                Log.e(TAG, "DB Error [apps/$code]: ${e.message}")
            }
        }
        ref.addValueEventListener(l)
        return l
    }

    /**
     * Live health of the child device: model, when it last checked in, and
     * whether its internet is currently off. Lets the parent screen tell
     * "nothing happened" apart from "nothing is connected".
     */
    fun listenChildInfo(
        code: String,
        onData: (model: String, lastSeen: Long, internetBlocked: Boolean,
                 battery: Int, charging: Boolean, adminActive: Boolean, vpnReady: Boolean) -> Unit
    ): ValueEventListener {
        val ref = root(code)
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                val info = s.child("info")
                onData(
                    info.child("model").getValue(String::class.java) ?: "",
                    info.child("lastSeen").getValue(Long::class.java) ?: 0L,
                    s.child("policy").child("internetBlocked").getValue(Boolean::class.java) ?: false,
                    info.child("battery").getValue(Int::class.java) ?: -1,
                    info.child("charging").getValue(Boolean::class.java) ?: false,
                    info.child("adminActive").getValue(Boolean::class.java) ?: false,
                    info.child("vpnReady").getValue(Boolean::class.java) ?: false
                )
            }
            override fun onCancelled(e: DatabaseError) {
                Log.e(TAG, "DB Error [info/$code]: ${e.message}")
                onData("", 0L, false, -1, false, false, false)
            }
        }
        ref.addValueEventListener(l)
        return l
    }

    /** Live last-known position of the child device. */
    fun listenLocation(
        code: String,
        onData: (lat: Double, lng: Double, ts: Long) -> Unit
    ): ValueEventListener {
        val ref = root(code).child("reports").child("location").child("latest")
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                onData(
                    s.child("lat").getValue(Double::class.java) ?: 0.0,
                    s.child("lng").getValue(Double::class.java) ?: 0.0,
                    s.child("ts").getValue(Long::class.java) ?: 0L
                )
            }
            override fun onCancelled(e: DatabaseError) {
                Log.e(TAG, "DB Error [location/$code]: ${e.message}")
            }
        }
        ref.addValueEventListener(l)
        return l
    }

    /** Remembers which side of each safe zone the child was on, to detect crossings. */
    fun setZoneState(code: String, zoneId: String, inside: Boolean) {
        root(code).child("state").child("zones").child(zoneId).setValue(inside)
    }

    // ---------- Reports (parent view) ----------
    fun listenUsageToday(code: String, date: String, onData: (Map<String, Int>) -> Unit): ValueEventListener {
        val ref = root(code).child("reports").child("usage").child(date)
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                val map = HashMap<String, Int>()
                for (child in s.children) {
                    val v = child.getValue(Int::class.java) ?: 0
                    map[child.key?.replace('_', '.') ?: ""] = v
                }
                onData(map)
            }
            override fun onCancelled(e: DatabaseError) {
                Log.e(TAG, "DB Error [usage/$code/$date]: ${e.message}")
            }
        }
        ref.addValueEventListener(l)
        return l
    }

    /** One-shot read of a single day's usage — used by the weekly digest. */
    fun readUsageForDate(code: String, date: String, onData: (Map<String, Int>) -> Unit) {
        root(code).child("reports").child("usage").child(date)
            .get()
            .addOnSuccessListener { s ->
                val map = HashMap<String, Int>()
                for (child in s.children) {
                    map[child.key?.replace('_', '.') ?: ""] = child.getValue(Int::class.java) ?: 0
                }
                onData(map)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "readUsageForDate failed: ${e.message}")
                onData(emptyMap())
            }
    }
}
