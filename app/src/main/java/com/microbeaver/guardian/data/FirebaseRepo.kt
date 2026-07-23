package com.microbeaver.guardian.data

import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/** All parent<->child sync goes through Realtime Database under /devices/{pairCode}. */
object FirebaseRepo {
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
            override fun onCancelled(e: DatabaseError) {}
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
            override fun onCancelled(e: DatabaseError) {}
        }
        ref.addChildEventListener(l)
        return l
    }

    fun markDone(code: String, cmdId: String) {
        if (cmdId.isNotEmpty()) root(code).child("commands").child(cmdId).child("done").setValue(true)
    }

    // ---------- Events (child activity feed) ----------
    fun pushEvent(code: String, type: String, text: String) {
        val e = mapOf<String, Any>("type" to type, "text" to text, "ts" to System.currentTimeMillis())
        root(code).child("events").push().setValue(e)
    }

    fun listenEvents(code: String, onEvent: (String, String, Long) -> Unit): ChildEventListener {
        val ref = root(code).child("events").limitToLast(50)
        val l = object : ChildEventListener {
            override fun onChildAdded(s: DataSnapshot, prev: String?) {
                val type = s.child("type").getValue(String::class.java) ?: ""
                val text = s.child("text").getValue(String::class.java) ?: ""
                val ts = s.child("ts").getValue(Long::class.java) ?: 0L
                onEvent(type, text, ts)
            }
            override fun onChildChanged(s: DataSnapshot, prev: String?) {}
            override fun onChildRemoved(s: DataSnapshot) {}
            override fun onChildMoved(s: DataSnapshot, prev: String?) {}
            override fun onCancelled(e: DatabaseError) {}
        }
        ref.addChildEventListener(l)
        return l
    }

    // ---------- Reports (child -> parent) ----------
    fun reportUsage(code: String, date: String, pkg: String, minutes: Int) {
        root(code).child("reports").child("usage").child(date)
            .child(pkg.replace('.', '_')).setValue(minutes)
    }

    fun reportApp(code: String, pkg: String, name: String, iconB64: String) {
        val m = mapOf<String, Any>("name" to name, "icon" to iconB64)
        root(code).child("apps").child(pkg.replace('.', '_')).updateChildren(m)
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
            override fun onCancelled(e: DatabaseError) {}
        }
        ref.addValueEventListener(l)
        return l
    }

    fun listenApps(code: String, onData: (Map<String, String>, Map<String, String>) -> Unit): ValueEventListener {
        val ref = root(code).child("apps")
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                val names = HashMap<String, String>()
                val icons = HashMap<String, String>()
                for (c in s.children) {
                    val pkg = c.key?.replace('_', '.') ?: continue
                    names[pkg] = c.child("name").getValue(String::class.java) ?: pkg
                    icons[pkg] = c.child("icon").getValue(String::class.java) ?: ""
                }
                onData(names, icons)
            }
            override fun onCancelled(e: DatabaseError) {}
        }
        ref.addValueEventListener(l)
        return l
    }

    fun listenLocation(code: String, cb: (Double, Double, Long) -> Unit): ValueEventListener {
        val ref = root(code).child("reports").child("location").child("latest")
        val l = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                val lat = s.child("lat").getValue(Double::class.java) ?: return
                val lng = s.child("lng").getValue(Double::class.java) ?: return
                val ts = s.child("ts").getValue(Long::class.java) ?: 0L
                cb(lat, lng, ts)
            }
            override fun onCancelled(e: DatabaseError) {}
        }
        ref.addValueEventListener(l)
        return l
    }
}
