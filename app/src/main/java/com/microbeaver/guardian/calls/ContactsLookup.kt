package com.microbeaver.guardian.calls

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

/**
 * Resolves a phone number against the device's own address book.
 *
 * `PhoneLookup` does the number normalisation for us and is indexed, so this is
 * cheap enough to call from [GuardianCallScreeningService], which must answer
 * within a few seconds.
 */
object ContactsLookup {

    fun hasPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(
            ctx, android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * @return the contact's display name, or `null` when the number is not saved
     *         (or we cannot read contacts).
     */
    fun displayName(ctx: Context, number: String?): String? {
        if (number.isNullOrBlank() || !hasPermission(ctx)) return null

        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(number)
        )
        return try {
            ctx.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() } else null
            }
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    fun isSavedContact(ctx: Context, number: String?): Boolean =
        displayName(ctx, number) != null
}
