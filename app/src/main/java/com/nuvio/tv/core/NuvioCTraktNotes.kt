package com.nuvio.tv.core

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// [fork] Nuvio C round 14 `trakt_keep_login` (2026-10-21): why Trakt last dropped (or nearly dropped)
// the login, kept on the device so it can be shown in Settings > Tracking > Trakt and read over adb
// (`logcat -s NuvioCTrakt`). Official only logged this in debug builds, so a random disconnect left
// no trace. Holds a reason and a time; no tokens or account details.
object NuvioCTraktNotes {
    private const val TAG = "NuvioCTrakt"
    private const val PREFS = "nuvio_c_prefs"
    private const val KEY_REASON = "trakt_last_disconnect_reason"
    private const val KEY_AT = "trakt_last_disconnect_at"

    fun record(context: Context, reason: String, disconnected: Boolean) {
        Log.w(TAG, (if (disconnected) "login removed: " else "login kept: ") + reason)
        if (!disconnected) return
        runCatching {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_REASON, reason.take(160))
                .putLong(KEY_AT, System.currentTimeMillis())
                .apply()
        }
    }

    /** "21 Oct 09:14 · Trakt said the login is no longer valid (HTTP 401)", or null. */
    fun lastDisconnect(context: Context): String? = runCatching {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val reason = prefs.getString(KEY_REASON, null) ?: return null
        val at = prefs.getLong(KEY_AT, 0L).takeIf { it > 0L } ?: return reason
        SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(at)) + " · " + reason
    }.getOrNull()
}
