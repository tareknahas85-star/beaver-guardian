package com.microbeaver.guardian.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
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

    private val uid: String? get() = FirebaseAuth.getInstance().currentUser?.uid

    // ---------- Membership / pairing ----------

    /** Adds this device's UID to the code's member list. Safe to call repeatedly. */
    fun claimDevice(code: String, onResult: ((Boolean) -> Unit)? = null) {
        val u = uid
        if (code.isBlank() || u == null) { onResult?.invoke(false); return }
        root(code).child("members").child(u).setValue(true)
            .addOnSuccessListener { onResult?.invoke(true) }
            .addOnFailureListener { e ->
                Log.e(TAG, "claimDevice failed for $code: ${e.message}")
                onResult?.invoke(false)
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

    fun setChildInfo(code: String, model: String) {
        val m = mapOf("model" to model, "role" to "child", "lastSeen" to System.currentTimeMillis())
        root(code).child("info").updateChildren(m)
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
