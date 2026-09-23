package com.microbeaver.guardian.monitor

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.microbeaver.guardian.Prefs
import com.microbeaver.guardian.data.ActivityEvent
import com.microbeaver.guardian.data.FirebaseRepo

/**
 * Records what happens on the child device into the live feed.
 *
 * ## Why this throttles
 * Android reports a window change every time the foreground app changes, which on
 * a phone in active use is several times a second — switching between a chat and
 * the keyboard alone produces a burst. Writing all of them would:
 *  * flood the parent's feed until it is unreadable,
 *  * exhaust the free Firebase plan's write quota in a day, and
 *  * drain the child's battery on radio wake-ups.
 *
 * So: the same app is only recorded again after [SAME_APP_COOLDOWN_MS], and any
 * event type is rate-limited to [MIN_GAP_MS] between writes. What the parent sees
 * is "Leo opened WhatsApp", not two hundred rows of app churn.
 */
object EventReporter {

    private const val TAG = "EventReporter"

    /** Do not re-report the same app inside this window. */
    private const val SAME_APP_COOLDOWN_MS = 2 * 60 * 1000L

    /** Floor between any two writes of the same event type. */
    private const val MIN_GAP_MS = 3_000L

    private val lastByKey = HashMap<String, Long>()

    /** Feed recording is on unless the parent turned it off. Mirrored locally. */
    @Volatile var feedEnabled: Boolean = true

    fun record(
        ctx: Context,
        type: String,
        title: String,
        detail: String = "",
        pkg: String = "",
        throttleKey: String = type + pkg
    ) {
        if (!feedEnabled) return
        val code = Prefs.getPairCode(ctx) ?: return
        if (code.isBlank()) return

        val now = System.currentTimeMillis()
        val gap = if (pkg.isNotEmpty() && type == ActivityEvent.APP_OPENED) {
            SAME_APP_COOLDOWN_MS
        } else {
            MIN_GAP_MS
        }
        val last = lastByKey[throttleKey] ?: 0L
        if (now - last < gap) return
        lastByKey[throttleKey] = now

        try {
            FirebaseRepo.pushEvent(
                code,
                ActivityEvent(
                    type = type,
                    title = title,
                    detail = detail,
                    pkg = pkg,
                    ts = now
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "could not record $type: ${e.message}")
        }
    }

    /** Convenience for app events, resolving the package to a readable name. */
    fun recordApp(ctx: Context, type: String, pkg: String, detail: String = "") {
        record(ctx, type, appLabel(ctx, pkg), detail, pkg)
    }

    fun appLabel(ctx: Context, pkg: String): String = try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    } catch (_: Exception) {
        pkg
    }
}
