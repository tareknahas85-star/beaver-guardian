package com.microbeaver.guardian.calls

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Putting a name to an unknown number.
 *
 * ## Why there is no Truecaller integration here
 * Truecaller does not expose a caller-ID lookup API to third-party apps. Their
 * public SDK ("Truecaller SDK" / OAuth) only does *number verification* — it
 * confirms that the person holding the phone owns the number they typed, as a
 * login mechanism. It cannot answer "who owns 09xxxxxxxx?". Their crowdsourced
 * directory is only queryable from inside their own app, and scraping
 * truecaller.com would breach their terms of service.
 *
 * So the honest options are:
 *  1. **The device address book** — free, instant, offline. [ContactsLookup].
 *  2. **A hand-off to the Truecaller app** — [truecallerLookupIntent] opens the
 *     number in Truecaller if the parent has it installed, otherwise the web
 *     search page. The parent taps once and Truecaller shows what it knows.
 *     This is the only sanctioned way to reach their data.
 *  3. **A commercial lookup API** — Twilio Lookup, NumVerify, Abstract API and
 *     similar sell caller-name lookups (CNAM) per query. [remoteLookup] is a
 *     ready socket for one; set [apiKey] to switch it on. Left off by default
 *     because it costs money per call and sends the child's contacts' numbers
 *     to a third party.
 */
object CallerIdLookup {

    private const val TAG = "CallerIdLookup"

    private const val TRUECALLER_PACKAGE = "com.truecaller"

    /**
     * Set this to a NumVerify API key to enable [remoteLookup].
     * Leave empty to keep every lookup on-device.
     */
    var apiKey: String = ""

    /** True when a paid lookup provider has been configured. */
    val remoteEnabled: Boolean get() = apiKey.isNotBlank()

    // ── 1. On-device ──────────────────────────────────────────────────────────

    /** The only lookup that is free, instant, and private. */
    fun localName(ctx: Context, number: String?): String? =
        ContactsLookup.displayName(ctx, number)

    // ── 2. Hand off to Truecaller ─────────────────────────────────────────────

    fun isTruecallerInstalled(ctx: Context): Boolean =
        try {
            ctx.packageManager.getPackageInfo(TRUECALLER_PACKAGE, 0)
            true
        } catch (_: Exception) {
            false
        }

    /**
     * An intent that shows [number] in Truecaller, falling back to their web
     * search. Attach it to the parent's "unknown caller" notification so
     * identifying a number is one tap away.
     */
    fun truecallerLookupIntent(ctx: Context, number: String): Intent {
        val digits = NumberUtils.digitsOnly(number)
        return if (isTruecallerInstalled(ctx)) {
            Intent(Intent.ACTION_VIEW, Uri.parse("truecaller://search?q=$digits"))
                .setPackage(TRUECALLER_PACKAGE)
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.truecaller.com/search/global/$digits"))
        }
    }

    // ── 3. Optional paid provider ─────────────────────────────────────────────

    /**
     * Blocking network lookup — **never call this from
     * [GuardianCallScreeningService]**, which must answer within seconds.
     * Intended for the parent app, off the main thread.
     *
     * @return the carrier / line description, or null when unavailable.
     */
    fun remoteLookup(number: String): String? {
        if (!remoteEnabled) return null
        val digits = NumberUtils.digitsOnly(number)
        if (digits.isEmpty()) return null

        return try {
            val url = URL("https://apilayer.net/api/validate?access_key=$apiKey&number=$digits")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                requestMethod = "GET"
            }
            conn.use {
                if (it.responseCode !in 200..299) return null
                val body = it.inputStream.bufferedReader().readText()
                val json = JSONObject(body)
                if (!json.optBoolean("valid", false)) return null
                listOfNotNull(
                    json.optString("carrier").takeIf { s -> s.isNotBlank() },
                    json.optString("location").takeIf { s -> s.isNotBlank() },
                    json.optString("country_name").takeIf { s -> s.isNotBlank() }
                ).joinToString(" · ").takeIf { s -> s.isNotBlank() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "remote lookup failed: ${e.message}")
            null
        }
    }

    private inline fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T =
        try { block(this) } finally { disconnect() }

    /**
     * Best label we can produce without paying or blocking:
     * contact name, else a plain "unknown" marker.
     */
    fun bestEffortLabel(ctx: Context, number: String?): String =
        localName(ctx, number) ?: "غير معروف / Unknown"
}
