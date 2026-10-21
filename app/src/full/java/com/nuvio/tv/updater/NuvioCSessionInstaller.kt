package com.nuvio.tv.updater

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.data.remote.dto.GitHubAssetDto
import java.io.File

// [fork] Nuvio C round 14 `install_profile` (2026-10-21): compile on install.
//
// Android pre-compiles an app at install time (dexopt reason "install-dm", filter speed-profile on
// almost every TV and box, Android 9+) when the install carries a dex-metadata file (.dm) next to the
// APK: a list of the code to compile. The build robot publishes one .dm per Android range (AGP makes
// them from the app's baseline profile, which since round 14 lists all of Nuvio C's own code). The
// updater used to hand the APK to the system installer screen, which can't take a .dm, so nothing was
// compiled until the TV was idle (or Charles ran `cmd package compile -m speed` over adb).
// Now the updater installs APK + .dm together through PackageInstaller. You still confirm on the
// usual system screen. Any failure, or no .dm for this Android version, falls back to the old way.
internal object NuvioCSessionInstaller {
    private const val TAG = "NuvioCInstall"
    private const val ACTION = "com.nuvio.tv.c.NUVIO_C_INSTALL_STATUS"

    /** The .dm asset for this device's Android version, or null (Android 8 and older, or none published). */
    fun pickProfile(assets: List<GitHubAssetDto>): GitHubAssetDto? {
        if (!NuvioCFeatures.INSTALL_PROFILE || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        val wanted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "-api31.dm" else "-api28.dm"
        return assets.firstOrNull { it.name.endsWith(wanted, ignoreCase = true) }
    }

    /**
     * Installs [apk] with [profile] in one session. Returns false (nothing started) when it can't, so
     * the caller uses the system installer screen as before.
     */
    fun install(context: Context, apk: File, profile: File?, onFallback: () -> Unit): Boolean {
        if (!NuvioCFeatures.INSTALL_PROFILE || profile == null || !profile.exists() ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.P
        ) return false
        val app = context.applicationContext
        return runCatching {
            val installer = app.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(app.packageName)
                setSize(apk.length() + profile.length())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                }
            }
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                // The .dm must carry the APK's name ("base.apk" -> "base.dm") to be matched to it.
                write(session, "base.apk", apk)
                write(session, "base.dm", profile)
                registerStatusReceiver(app, sessionId, onFallback)
                val intent = Intent(ACTION).setPackage(app.packageName).putExtra("session", sessionId)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                val pending = PendingIntent.getBroadcast(app, sessionId, intent, flags)
                NuvioCUpdatedReceiver.mark(app) // [fork] update_reopen: reopen once the new version is in
                session.commit(pending.intentSender)
            }
            Log.i(TAG, "session $sessionId committed: ${apk.name} + ${profile.name}")
            true
        }.getOrElse {
            Log.w(TAG, "session install failed, using the system installer: ${it.message}")
            false
        }
    }

    private fun write(session: PackageInstaller.Session, name: String, file: File) {
        file.inputStream().use { input ->
            session.openWrite(name, 0, file.length()).use { output ->
                input.copyTo(output)
                session.fsync(output)
            }
        }
    }

    private fun registerStatusReceiver(context: Context, sessionId: Int, onFallback: () -> Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.getIntExtra("session", -1) != sessionId) return
                when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        @Suppress("DEPRECATION")
                        val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                        if (confirm != null) {
                            // [fork] update_reopen: say what happens next, since Android shows no progress screen here
                            if (NuvioCFeatures.UPDATE_REOPEN) {
                                android.widget.Toast.makeText(
                                    ctx, ctx.getString(com.nuvio.tv.R.string.nuvio_c_update_installing), android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                            ctx.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } else {
                            Log.w(TAG, "no confirmation screen from the installer")
                            runCatching { ctx.unregisterReceiver(this) }
                            onFallback()
                        }
                    }
                    PackageInstaller.STATUS_SUCCESS -> {
                        Log.i(TAG, "installed with its compile profile")
                        runCatching { ctx.unregisterReceiver(this) }
                    }
                    else -> {
                        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                        Log.w(TAG, "session install ended with status $status ($message)")
                        runCatching { ctx.unregisterReceiver(this) }
                        // The user cancelling is not a reason to show the installer again.
                        if (status != PackageInstaller.STATUS_FAILURE_ABORTED) onFallback()
                    }
                }
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
    }
}
