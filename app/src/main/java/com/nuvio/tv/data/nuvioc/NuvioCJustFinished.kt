package com.nuvio.tv.data.nuvioc

import android.content.Context
import android.util.Log

// [fork] Nuvio C round 28 `post_play_rating`: the player notes a movie you just finished (played past the point where
// the saved progress counts as watched, having actually watched most of what you covered). When that movie's details
// page shows, it asks for your rating once.
//
// Round 47 `rating_reliable`: the note is kept in a small private file (it survives the app being closed or killed
// while you're away), it lasts 30 minutes from the last moment the player was on that film (credits included, so a
// long end-credits sit no longer runs it out), and it matches any of the film's ids (IMDb, TMDB, the add-on's).
// Every decision is logged under NuvioCRate (`adb logcat -s NuvioCRate`).
object NuvioCJustFinished {
    private const val TAG = "NuvioCRate"
    private const val WINDOW_MS = 30 * 60 * 1000L
    private const val PREFS = "nuvio_c_just_finished"
    @Volatile private var ids: Set<String> = emptySet()
    @Volatile private var at = 0L
    @Volatile private var context: Context? = null

    fun init(app: Context) {
        context = app.applicationContext
        if (!com.nuvio.tv.NuvioCFeatures.RATING_RELIABLE) return
        runCatching {
            val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ids = p.getStringSet("ids", emptySet()).orEmpty().toSet()
            at = p.getLong("at", 0L)
        }
    }

    fun log(message: String) {
        if (com.nuvio.tv.NuvioCFeatures.RATING_RELIABLE) Log.i(TAG, message)
    }

    fun mark(contentId: String, extra: Collection<String?> = emptyList()) {
        ids = (listOf(contentId) + extra).filterNotNull().filter { it.isNotBlank() }.flatMap(::variants).toSet()
        at = System.currentTimeMillis()
        save()
        log("marked ${ids.joinToString(",")}")
    }

    /** The player is still on the film after the mark (credits): the window runs from now. */
    fun touch(contentId: String) {
        if (!com.nuvio.tv.NuvioCFeatures.RATING_RELIABLE) return
        if (variants(contentId).none { it in ids }) return
        val now = System.currentTimeMillis()
        if (now - at > 15_000L) { at = now; save() }
    }

    /** True once, for the details page of the movie just finished. */
    fun take(candidates: Collection<String?>): Boolean {
        val pending = ids
        if (pending.isEmpty()) return false
        val window = if (com.nuvio.tv.NuvioCFeatures.RATING_RELIABLE) WINDOW_MS else 10 * 60 * 1000L
        if (System.currentTimeMillis() - at > window) {
            log("expired ${pending.joinToString(",")}")
            clear()
            return false
        }
        val mine = candidates.filterNotNull().filter { it.isNotBlank() }.flatMap(::variants).toSet()
        if (mine.none { it in pending }) {
            log("details ${mine.joinToString(",")} is not the finished film")
            return false
        }
        clear()
        log("details matched, asking")
        return true
    }

    private fun clear() {
        ids = emptySet()
        at = 0L
        save()
    }

    /** "tt123", "tmdb:42", "42" style ids, so one page's ids meet the player's whichever form each uses. */
    private fun variants(id: String): List<String> {
        val t = id.trim()
        if (!com.nuvio.tv.NuvioCFeatures.RATING_RELIABLE) return listOf(t)
        val out = mutableListOf(t)
        Regex("tt\\d{5,}").find(t)?.value?.let { out += it }
        if (t.startsWith("tmdb:")) out += "tmdb:" + t.removePrefix("tmdb:").substringBefore(':')
        return out.distinct()
    }

    private fun save() {
        if (!com.nuvio.tv.NuvioCFeatures.RATING_RELIABLE) return
        val c = context ?: return
        runCatching {
            c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet("ids", ids).putLong("at", at).apply()
        }
    }
}
