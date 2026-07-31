package com.microbeaver.guardian

import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Arabic / English / follow-the-system.
 *
 * Uses AppCompat's per-app locale API rather than swapping `Configuration`
 * by hand. On Android 13+ AppCompat hands the choice to the platform, so it
 * shows up in Android's own per-app language settings and survives reinstalls;
 * below 13 AppCompat persists it itself. Either way it applies immediately and
 * recreates the activities, so no restart prompt is needed.
 */
object LocaleManager {

    const val SYSTEM = ""
    const val AR = "ar"
    const val EN = "en"

    private const val PREFS = "locale_prefs"
    private const val KEY = "tag"

    /** Applies a language and remembers it. Pass [SYSTEM] to follow the device. */
    fun apply(tag: String) {
        val locales = if (tag.isBlank()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    /**
     * The language currently in force, as [SYSTEM], [AR] or [EN].
     *
     * Below Android 13 AppCompat is the source of truth; from 13 the platform is,
     * and AppCompat reads through to it, so the same call works on both.
     */
    fun current(ctx: Context?): String {
        val list = AppCompatDelegate.getApplicationLocales()
        if (list.isEmpty) return SYSTEM
        return when (list[0]?.language) {
            "ar" -> AR
            "en" -> EN
            else -> SYSTEM
        }
    }

    /**
     * Re-applies a saved choice on cold start.
     *
     * Only needed as a safety net: AppCompat already restores its own storage.
     * Kept because a Device Owner provisioning flow can start the app before
     * AppCompat has initialised.
     */
    fun restore(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
        val saved = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        if (!saved.isNullOrBlank() && AppCompatDelegate.getApplicationLocales().isEmpty) {
            apply(saved)
        }
    }
}
