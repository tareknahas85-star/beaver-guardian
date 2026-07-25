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
 * IMPORTANT: google-services.json contains placeholder values.
 *            Replace it with the real file from your Firebase Console
 *            before building a production APK.
 */
object FirebaseRepo {
    private const val TAG = "FirebaseRepo"
    private val db get() = FirebaseDatabase.getInstance()
    private fun root(code: String) = db.getReference("devices").child(code)

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
}
