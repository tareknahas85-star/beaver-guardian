package com.microbeaver.guardian

import android.content.Context

/** Local, on-device settings. The child's role is locked once chosen. */
object Prefs {
    private const val FILE = "guardian_prefs"
    const val ROLE_PARENT = "parent"
    const val ROLE_CHILD = "child"

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun setRole(c: Context, role: String) = sp(c).edit().putString("role", role).apply()
    fun getRole(c: Context): String? = sp(c).getString("role", null)

    fun setPairCode(c: Context, code: String) = sp(c).edit().putString("pair", code).apply()
    fun getPairCode(c: Context): String? = sp(c).getString("pair", null)

    /**
     * One-shot upgrade flag: turns "notify me about" up to every event type on
     * this parent's phone the first time the policy loads after this feature
     * shipped, without overriding a deliberate narrower choice made afterwards.
     */
    fun isNotifyAllMigrated(c: Context): Boolean = sp(c).getBoolean("notify_all_migrated", false)
    fun setNotifyAllMigrated(c: Context) = sp(c).edit().putBoolean("notify_all_migrated", true).apply()
}
