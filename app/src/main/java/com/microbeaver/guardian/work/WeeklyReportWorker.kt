package com.microbeaver.guardian.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.microbeaver.guardian.Prefs
import com.microbeaver.guardian.data.Alert
import com.microbeaver.guardian.data.FirebaseRepo
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Rolls the last seven days of app usage into one digest and posts it as an
 * [Alert], so the parent gets a weekly summary without having to remember to
 * open the app.
 *
 * Runs on the **child** device, which is where the usage data originates.
 */
class WeeklyReportWorker(
    ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val code = Prefs.getPairCode(applicationContext) ?: return Result.success()
        if (code.isBlank()) return Result.success()

        val fmt = SimpleDateFormat("yyyyMMdd", Locale.US)
        val cal = Calendar.getInstance()

        // Sum each package across the last 7 days.
        val totals = HashMap<String, Int>()
        repeat(7) {
            val date = fmt.format(cal.time)
            val day = readUsage(code, date)
            day.forEach { (pkg, minutes) -> totals[pkg] = (totals[pkg] ?: 0) + minutes }
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }

        if (totals.isEmpty()) return Result.success()

        val grandTotal = totals.values.sum()
        val top = totals.entries.sortedByDescending { it.value }.take(5)

        val body = buildString {
            append("إجمالي الاستخدام: ${formatDuration(grandTotal)}")
            append(" / Total screen time: ${formatDuration(grandTotal)}\n")
            append("متوسط يومي / Daily average: ${formatDuration(grandTotal / 7)}\n\n")
            append("الأكثر استخداماً / Most used:\n")
            top.forEachIndexed { i, (pkg, minutes) ->
                append("${i + 1}. ${shortName(pkg)} — ${formatDuration(minutes)}\n")
            }
        }

        val end = SimpleDateFormat("d MMM", Locale.US).format(Date())
        FirebaseRepo.pushAlert(
            code,
            Alert(
                type = Alert.WEEKLY_REPORT,
                title = "التقرير الأسبوعي / Weekly report — $end",
                body = body.trim(),
                ts = System.currentTimeMillis()
            )
        )
        return Result.success()
    }

    private suspend fun readUsage(code: String, date: String): Map<String, Int> =
        suspendCancellableCoroutine { cont ->
            FirebaseRepo.readUsageForDate(code, date) { data ->
                if (cont.isActive) cont.resume(data)
            }
        }

    /** "com.whatsapp" -> "whatsapp" — good enough without a PackageManager round trip. */
    private fun shortName(pkg: String): String =
        pkg.substringAfterLast('.').ifBlank { pkg }

    private fun formatDuration(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return if (h > 0) "${h}س ${m}د / ${h}h ${m}m" else "${m}د / ${m}m"
    }

    companion object {
        private const val WORK_NAME = "weekly_report"

        /** Schedules the digest. Safe to call on every app start. */
        fun schedule(ctx: Context) {
            val request = PeriodicWorkRequestBuilder<WeeklyReportWorker>(7, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setInitialDelay(initialDelayHours(), TimeUnit.HOURS)
                .build()

            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(ctx: Context) {
            WorkManager.getInstance(ctx).cancelUniqueWork(WORK_NAME)
        }

        /** Next Sunday at 12:00 noon, as the parent asked. */
        private fun initialDelayHours(): Long {
            val now = Calendar.getInstance()
            val target = (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 12)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                while (get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY || before(now)) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            val diffMs = target.timeInMillis - now.timeInMillis
            return (diffMs / (60 * 60 * 1000)).coerceAtLeast(1)
        }
    }
}
