package com.nuvio.tv.updater

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nuvio.tv.NuvioCFeatures

// [fork] Nuvio C round 17 `update_reopen`.
//
// The in-app update installs through a PackageInstaller session so Android compiles Nuvio C during the
// install (round 14 `install_profile`). Session installs have no progress screen: Nuvio C closes when you
// confirm and can't be opened until Android has installed and compiled it. Android then tells the new
// version "you were updated" (MY_PACKAGE_REPLACED); if the update came from the in-app updater in the last
// 15 minutes, Nuvio C opens itself again. adb / file-manager installs leave no mark, so they never reopen it.
// If the TV refuses to let an app open itself from the background, nothing happens (open it from Home).
class NuvioCUpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED || !NuvioCFeatures.UPDATE_REOPEN) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val markedAt = prefs.getLong(KEY, 0L)
        prefs.edit().remove(KEY).apply()
        val age = System.currentTimeMillis() - markedAt
        if (markedAt <= 0L || age !in 0..WINDOW_MS) return
        runCatching {
            val launch = context.packageManager.getLeanbackLaunchIntentForPackage(context.packageName)
                ?: context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: return
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Log.i(TAG, "updated, reopening Nuvio C")
        }.onFailure { Log.w(TAG, "updated, couldn't reopen: ${it.message}") }
    }

    companion object {
        private const val TAG = "NuvioCInstall"
        private const val PREFS = "nuvio_c_update"
        private const val KEY = "reopen_marked_at"
        private const val WINDOW_MS = 15 * 60 * 1000L

        /** Called right before the install session is handed to Android. */
        fun mark(context: Context) {
            if (!NuvioCFeatures.UPDATE_REOPEN) return
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putLong(KEY, System.currentTimeMillis()).commit()
        }
    }
}
