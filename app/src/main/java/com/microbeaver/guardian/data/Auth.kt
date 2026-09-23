package com.microbeaver.guardian.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth

/**
 * Gets a Firebase user id, waiting for anonymous sign-in to finish first.
 *
 * ## Why this exists
 * `App.onCreate` starts `signInAnonymously()`, which is asynchronous. Anything
 * that ran straight after — a service starting, an activity opening — read
 * `FirebaseAuth.currentUser` while it was still null, so it had no uid, so
 * [FirebaseRepo.claimDevice] failed and no database listeners were ever attached.
 * The app looked paired and did nothing at all.
 *
 * Always go through [withUid] before touching the database.
 */
object Auth {

    private const val TAG = "Auth"

    val uid: String? get() = FirebaseAuth.getInstance().currentUser?.uid

    val isSignedIn: Boolean get() = uid != null

    /**
     * Calls [onReady] with a uid as soon as one exists — immediately if already
     * signed in, otherwise after anonymous sign-in completes.
     *
     * [onFailed] runs when sign-in cannot complete, usually because Anonymous
     * sign-in is switched off in the Firebase console or there is no network.
     * The callback always arrives on the main thread.
     */
    fun withUid(onFailed: ((String) -> Unit)? = null, onReady: (String) -> Unit) {
        val auth = FirebaseAuth.getInstance()

        auth.currentUser?.uid?.let { onReady(it); return }

        auth.signInAnonymously()
            .addOnSuccessListener { result ->
                val u = result.user?.uid
                if (u != null) {
                    onReady(u)
                } else {
                    Log.e(TAG, "sign-in returned no user")
                    onFailed?.invoke("no user returned")
                }
            }
            .addOnFailureListener { e ->
                // The usual cause is Anonymous sign-in being disabled in the
                // Firebase console, which surfaces as CONFIGURATION_NOT_FOUND.
                val msg = e.message ?: "unknown error"
                Log.e(TAG, "anonymous sign-in failed: $msg")
                onFailed?.invoke(msg)
            }
    }
}
