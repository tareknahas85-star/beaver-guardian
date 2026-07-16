package com.microbeaver.guardian.monitor

import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import com.microbeaver.guardian.data.CallRecord
import com.microbeaver.guardian.data.FirebaseRepo

/**
 * Reads call-log METADATA only: number, direction, time, duration.
 * Audio recording of calls is not possible on modern Android.
 */
object CallLogReporter {
    private var lastTs = 0L

    fun reportRecent(ctx: Context, code: String) {
        if (ContextCompat.checkSelfPermission(
                ctx, android.Manifest.permission.READ_CALL_LOG
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val cols = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION
        )
        val cursor = ctx.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            cols,
            "${CallLog.Calls.DATE} > ?",
            arrayOf(lastTs.toString()),
            "${CallLog.Calls.DATE} ASC"
        ) ?: return

        cursor.use { c ->
            val iNum = c.getColumnIndex(CallLog.Calls.NUMBER)
            val iType = c.getColumnIndex(CallLog.Calls.TYPE)
            val iDate = c.getColumnIndex(CallLog.Calls.DATE)
            val iDur = c.getColumnIndex(CallLog.Calls.DURATION)
            while (c.moveToNext()) {
                val date = c.getLong(iDate)
                FirebaseRepo.reportCall(
                    code,
                    CallRecord(
                        number = c.getString(iNum) ?: "",
                        type = typeName(c.getInt(iType)),
                        ts = date,
                        durationSec = c.getLong(iDur)
                    )
                )
                if (date > lastTs) lastTs = date
            }
        }
    }

    private fun typeName(t: Int) = when (t) {
        CallLog.Calls.INCOMING_TYPE -> "incoming"
        CallLog.Calls.OUTGOING_TYPE -> "outgoing"
        CallLog.Calls.MISSED_TYPE -> "missed"
        CallLog.Calls.REJECTED_TYPE -> "rejected"
        else -> "other"
    }
}
