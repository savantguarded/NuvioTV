package com.nuvio.tv.ui.components

import android.content.Context
import android.util.Log
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.NuvioCFeatures
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

// [fork] Nuvio C round 52 `profile_bg_builtin`: the profile screen's background made in the app, from TMDB's trending
// films and shows (textless backdrop, the title's logo, a small tag and meta line), instead of the retired Vercel
// link. The pool (top 16 of the day) is refreshed in the background at most once a day. Each profile gets its own
// picture from the pool and the screen crossfades when focus moves between profiles. The pictures for the next
// launch are picked and saved during this one, so a cold start shows them at once; a profile never gets the same
// picture twice running. Offline: the last saved pool and pictures. A profile's own background (custom link or
// catalog picture) is the backup when the pool is empty.
object NuvioCTrendingBg {
    private const val TAG = "NuvioCTrendingBg"
    private const val DIR = "nuvio_c_trending"
    private const val POOL = "pool.json"
    private const val PREFS = "nuvio_c_trending"
    private const val KEY_FETCHED = "fetched_at"
    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val POOL_SIZE = 16

    data class Entry(
        val id: String,
        val title: String,
        val backdrop: String,
        val logo: String?,
        val tag: String,
        val meta: String
    )

    @Volatile private var pool: List<Entry> = emptyList()
    /** This launch's picture per profile (pool index), decided once at start. */
    private val picks = java.util.concurrent.ConcurrentHashMap<Int, Int>()
    @Volatile private var started = false

    fun onAppStart(context: Context) {
        if (!NuvioCFeatures.PROFILE_BG_BUILTIN || started) return
        started = true
        val app = context.applicationContext
        pool = runCatching { parse(File(dir(app), POOL).readText()) }.getOrDefault(emptyList())
        thread(name = TAG, isDaemon = true, priority = Thread.MIN_PRIORITY) {
            runCatching {
                val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                if (pool.isEmpty() || System.currentTimeMillis() - prefs.getLong(KEY_FETCHED, 0L) > DAY_MS) {
                    val fresh = fetchPool()
                    if (fresh.isNotEmpty()) {
                        File(dir(app), POOL).writeText(JSONArray(fresh.map { it.toJson() }).toString())
                        prefs.edit().putLong(KEY_FETCHED, System.currentTimeMillis()).apply()
                        if (pool.isEmpty()) pool = fresh // next launch uses the new pool; this one keeps what is on screen
                    }
                }
                // download every picture of the pool once, so the next cold start has them on disk
                val saved = parse(runCatching { File(dir(app), POOL).readText() }.getOrDefault("[]"))
                saved.forEach { e -> fileFor(app, e).takeIf { it.length() == 0L }?.let { download(e.backdrop, it) } }
            }.onFailure { Log.w(TAG, "trending refresh failed: ${it.message}") }
        }
    }

    /** The picture for [profileId] this launch, never the one it had last launch. Null = use the profile's own. */
    fun entryFor(context: Context, profileId: Int, profileIndex: Int): Entry? {
        if (!NuvioCFeatures.PROFILE_BG_BUILTIN) return null
        val p = pool
        if (p.isEmpty()) return null
        val index = picks.getOrPut(profileId) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val last = prefs.getInt("last_$profileId", -1)
            var i = (prefs.getInt("seed", 0) + profileIndex * 3) % p.size
            if (i == last) i = (i + 1) % p.size
            prefs.edit().putInt("last_$profileId", i).putInt("seed", prefs.getInt("seed", 0) + 1).apply()
            i
        }
        return p.getOrNull(index % p.size)
    }

    /** The saved file when it is on disk (instant), else the link. */
    fun model(context: Context, entry: Entry): Any {
        val f = fileFor(context.applicationContext, entry)
        return if (f.length() > 0) f else entry.backdrop
    }

    private fun dir(app: Context) = File(app.filesDir, DIR).apply { mkdirs() }
    private fun fileFor(app: Context, e: Entry) = File(dir(app), e.id.replace(':', '_') + ".jpg")

    private fun fetchPool(): List<Entry> {
        val key = BuildConfig.TMDB_API_KEY.takeIf { it.isNotBlank() } ?: return emptyList()
        val trending = getJson("https://api.themoviedb.org/3/trending/all/day?api_key=$key&language=en-US")?.optJSONArray("results") ?: return emptyList()
        val genres = (genreMap(key, "movie") + genreMap(key, "tv"))
        val out = ArrayList<Entry>()
        for (i in 0 until trending.length()) {
            if (out.size >= POOL_SIZE) break
            val r = trending.optJSONObject(i) ?: continue
            val type = r.optString("media_type")
            if (type != "movie" && type != "tv") continue
            if (r.optBoolean("adult")) continue
            val id = r.optInt("id")
            val images = getJson("https://api.themoviedb.org/3/$type/$id/images?api_key=$key&include_image_language=en,null") ?: continue
            val backdrop = images.optJSONArray("backdrops")?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
                    .filter { it.isNull("iso_639_1") && it.optInt("width") >= 1280 }
                    .maxByOrNull { it.optDouble("vote_average") }?.optString("file_path")
            } ?: continue
            val logo = images.optJSONArray("logos")?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
                    .filter { it.optString("iso_639_1") == "en" && !it.optString("file_path").endsWith(".svg") }
                    .maxByOrNull { it.optDouble("vote_average") }?.optString("file_path")
            }
            val title = if (type == "movie") r.optString("title") else r.optString("name")
            val year = (if (type == "movie") r.optString("release_date") else r.optString("first_air_date")).take(4)
            val genre = r.optJSONArray("genre_ids")?.let { g -> (0 until g.length()).mapNotNull { genres[g.optInt(it)] }.firstOrNull() }
            out += Entry(
                id = "$type:$id",
                title = title,
                backdrop = "https://image.tmdb.org/t/p/w1280$backdrop",
                logo = logo?.let { "https://image.tmdb.org/t/p/w500$it" },
                tag = "TRENDING TODAY · #${out.size + 1}",
                meta = listOfNotNull(if (type == "movie") "Film" else "Series", genre, year.takeIf { it.isNotBlank() }).joinToString(" · ")
            )
        }
        return out
    }

    private fun genreMap(key: String, type: String): Map<Int, String> =
        getJson("https://api.themoviedb.org/3/genre/$type/list?api_key=$key&language=en-US")?.optJSONArray("genres")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.associate { it.optInt("id") to it.optString("name") }
        }.orEmpty()

    private fun getJson(url: String): JSONObject? {
        val c = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 8_000; readTimeout = 12_000 }
        return try {
            if (c.responseCode !in 200..299) null else JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        } catch (t: Throwable) { null } finally { c.disconnect() }
    }

    private fun download(url: String, to: File): Boolean {
        val c = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 10_000; readTimeout = 20_000 }
        return try {
            if (c.responseCode !in 200..299) return false
            val tmp = File(to.parentFile, to.name + ".tmp")
            c.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            tmp.renameTo(to)
        } catch (t: Throwable) { false } finally { c.disconnect() }
    }

    private fun Entry.toJson() = JSONObject().put("id", id).put("title", title).put("backdrop", backdrop).put("logo", logo ?: "").put("tag", tag).put("meta", meta)

    internal fun parse(text: String): List<Entry> = runCatching {
        val arr = JSONArray(text)
        (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map {
            Entry(it.optString("id"), it.optString("title"), it.optString("backdrop"), it.optString("logo").takeIf { l -> l.isNotBlank() }, it.optString("tag"), it.optString("meta"))
        }.filter { it.backdrop.startsWith("http") }
    }.getOrDefault(emptyList())
}
