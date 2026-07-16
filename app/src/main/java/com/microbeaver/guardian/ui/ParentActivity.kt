package com.microbeaver.guardian.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.microbeaver.guardian.Prefs
import com.microbeaver.guardian.data.Command
import com.microbeaver.guardian.data.FirebaseRepo
import com.microbeaver.guardian.databinding.ActivityParentBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ParentActivity : AppCompatActivity() {
    private lateinit var b: ActivityParentBinding
    private lateinit var code: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityParentBinding.inflate(layoutInflater)
        setContentView(b.root)

        code = Prefs.getPairCode(this) ?: genCode().also { Prefs.setPairCode(this, it) }
        b.tvPairCode.text = "رمز الربط / Pairing code:  $code"

        b.btnLockNow.setOnClickListener { send("LOCK_NOW") }
        b.btnUnlock.setOnClickListener { send("UNLOCK") }
        b.btnBlockInternet.setOnClickListener { send("BLOCK_INTERNET") }
        b.btnAllowInternet.setOnClickListener { send("ALLOW_INTERNET") }

        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        FirebaseRepo.listenUsageToday(code, today) { usage ->
            val sb = StringBuilder("استخدام اليوم / Today's usage:\n\n")
            usage.entries.sortedByDescending { it.value }.forEach { (pkg, min) ->
                sb.append("• $pkg : $min د / min\n")
            }
            runOnUiThread {
                b.tvReport.text = if (usage.isEmpty()) "لا بيانات بعد / no data yet" else sb.toString()
            }
        }
    }

    private fun send(type: String) = FirebaseRepo.pushCommand(code, Command(type = type))

    private fun genCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}
