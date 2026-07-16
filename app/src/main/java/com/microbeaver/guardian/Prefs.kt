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
}
