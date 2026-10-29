package com.nuvio.tv.ui.components

import android.content.Context
import android.util.Log
import com.nuvio.tv.NuvioCFeatures
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.concurrent.thread

// [fork] Nuvio C round 31 (switch `profile_bg_preload`): an instant, always-different profile background.
//
// A custom background link (the rotating-background endpoint) gives a new picture every time it is fetched,
// so the profile screen used to wait for that download on every cold open. Now:
//  - At app start, the picture downloaded during the previous launch (kept in the fork's own file, never the
//    shared image cache, so nothing can freeze on an old copy) becomes this launch's picture, and the next one
//    starts downloading in the background for next time.
//  - The profile screen and the start-up screen show this launch's file at once.
//  - A file is used for one launch only. Its fingerprint is compared with the picture shown last time; if it
//    matches, or no file was saved (offline, or the app was closed before the download finished), the screen
//    falls back to fetching the link live, exactly as official does. The background download retries once when
//    it gets the picture just shown.
// So every cold open shows a different picture from the last one, and it never gets stuck.
object NuvioCProfileBg {
    private const val TAG = "NuvioCProfileBg"
    private const val PREFS = "nuvio_c_profile_bg"
    private const val KEY_URL = "url"
    private const val KEY_SHOWN = "shown_sha"
    private const val DIR = "nuvio_c_profile_bg"
    private const val NEXT = "next.img"
    private const val CURRENT = "current.img"

    @Volatile private var claimedUrl: String? = null
    @Volatile private var claimedFile: File? = null
    @Volatile private var started = false

    /** Called once from Application.onCreate: claim last launch's download, then fetch the next one. */
    fun onAppStart(context: Context) {
        if (!NuvioCFeatures.PROFILE_BG_PRELOAD || started) return
        started = true
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val url = prefs.getString(KEY_URL, null)?.takeIf { it.startsWith("http") } ?: return
        val dir = File(app.filesDir, DIR).apply { mkdirs() }
        val next = File(dir, NEXT)
        val current = File(dir, CURRENT)
        runCatching {
            current.delete()
            if (next.length() > 0) {
                val sha = sha(next)
                if (sha != null && sha != prefs.getString(KEY_SHOWN, null) && next.renameTo(current)) {
                    claimedUrl = url
                    claimedFile = current
                    prefs.edit().putString(KEY_SHOWN, sha).apply()
                } else {
                    next.delete()
                }
            }
        }.onFailure { Log.w(TAG, "claim failed: ${it.message}") }
        prefetch(app, url)
    }

    /**
     * What to load for the custom background [url]: this launch's saved file when it belongs to [url], else
     * [url] itself (official behaviour). Remembers [url] so the next launches can preload it.
     */
    fun model(context: Context, url: String): Any {
        if (!NuvioCFeatures.PROFILE_BG_PRELOAD || !url.startsWith("http")) return url
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_URL, null) != url) {
            // A new link (first time, or the background was changed): start preloading it for next time.
            prefs.edit().putString(KEY_URL, url).apply()
            prefetch(app, url)
        }
        val file = claimedFile
        return if (claimedUrl == url && file != null && file.length() > 0) file else url
    }

    /** Cache key that changes with every launch's file, so a picture held in memory is never reused by mistake. */
    fun cacheKey(url: String): String? {
        val file = claimedFile ?: return null
        return if (claimedUrl == url) "nuvio-c-profile-bg-${file.lastModified()}" else null
    }

    @Volatile private var fetching = false

    private fun prefetch(app: Context, url: String) {
        if (fetching) return
        fetching = true
        thread(name = "NuvioCProfileBg", isDaemon = true, priority = Thread.MIN_PRIORITY) {
            try {
                val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val dir = File(app.filesDir, DIR).apply { mkdirs() }
                repeat(2) { attempt ->
                    val temp = File(dir, "download.tmp")
                    if (!download(url, temp)) return@thread
                    val sha = sha(temp)
                    val shown = prefs.getString(KEY_SHOWN, null)
                    if (sha != null && sha == shown && attempt == 0) {
                        temp.delete()
                        return@repeat // same picture as the one on screen: ask once more
                    }
                    if (sha == null || sha == shown) {
                        temp.delete()
                        return@thread
                    }
                    val next = File(dir, NEXT)
                    next.delete()
                    if (!temp.renameTo(next)) temp.delete()
                    return@thread
                }
            } catch (t: Throwable) {
                Log.w(TAG, "prefetch failed: ${t.message}")
            } finally {
                fetching = false
            }
        }
    }

    private fun download(url: String, to: File): Boolean {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("Cache-Control", "no-cache")
        }
        return try {
            if (connection.responseCode !in 200..299) return false
            val type = connection.contentType.orEmpty()
            if (type.isNotEmpty() && !type.startsWith("image/")) return false
            connection.inputStream.use { input -> to.outputStream().use { input.copyTo(it) } }
            to.length() in 1..(16L * 1024 * 1024)
        } catch (t: Throwable) {
            to.delete()
            false
        } finally {
            connection.disconnect()
        }
    }

    private fun sha(file: File): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()
}
