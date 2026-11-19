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
        val meta: String,
        // [fork] round 53 profile_bg_text_v2: the line under the logo without a rank (year · genres)
        val line: String = ""
    )

    @Volatile private var pool: List<Entry> = emptyList()
    /** This launch's picture per profile (pool index), decided once at start. */
    private val picks = java.util.concurrent.ConcurrentHashMap<Int, Int>()
    @Volatile private var started = false

    // ---- [fork] round 53d profile_bg_catalogs: up to three catalogs from the app, as in Ambient mode ---------------
    private const val CATALOG_POOL = "pool_catalogs.json"
    private const val KEY_CATALOG_ON = "catalogs_on"
    private const val KEY_CATALOG_KEYS = "catalog_keys"
    val catalogOn = androidx.compose.runtime.mutableStateOf(false)
    val catalogKeys = androidx.compose.runtime.mutableStateOf<List<String>>(emptyList())

    fun loadSettings(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        catalogOn.value = NuvioCFeatures.PROFILE_BG_CATALOGS && p.getBoolean(KEY_CATALOG_ON, false)
        catalogKeys.value = p.getString(KEY_CATALOG_KEYS, "").orEmpty().split('|').filter { it.isNotBlank() }
    }

    fun setCatalogOn(context: Context, on: Boolean) {
        catalogOn.value = on
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_CATALOG_ON, on).apply()
        switchPool(context.applicationContext)
    }

    fun setCatalogKeys(context: Context, keys: List<String>) {
        catalogKeys.value = keys
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_CATALOG_KEYS, keys.joinToString("|")).apply()
    }

    /**
     * New pictures from the chosen catalogs (logo, genres). Saved for the next cold start; used at once when this
     * launch has nothing from the catalogs yet. The next picture of every profile is downloaded in the background.
     */
    fun setCatalogPool(context: Context, entries: List<Entry>) {
        if (entries.isEmpty()) return
        val app = context.applicationContext
        thread(name = TAG, isDaemon = true, priority = Thread.MIN_PRIORITY) {
            runCatching {
                File(dir(app), CATALOG_POOL).writeText(JSONArray(entries.map { it.toJson() }).toString())
                if (catalogOn.value && (pool.isEmpty() || poolIsTrending)) { pool = entries; poolIsTrending = false; picks.clear() }
                val keep = (entries + parse(runCatching { File(dir(app), POOL).readText() }.getOrDefault("[]"))).map { fileFor(app, it).name }.toSet()
                dir(app).listFiles()?.filter { it.name.endsWith(".jpg") && it.name !in keep }?.forEach { it.delete() }
                val heads = nextIds(app).toSet()
                val missing = entries.filter { fileFor(app, it).length() == 0L }
                (missing.filter { it.id in heads } + missing.filter { it.id !in heads }.take(8)).forEach { e -> download(e.backdrop, fileFor(app, e)) }
            }.onFailure { Log.w(TAG, "catalog pool failed: ${it.message}") }
        }
    }

    @Volatile private var poolIsTrending = true

    private fun switchPool(app: Context) {
        val catalogs = if (catalogOn.value) parse(runCatching { File(dir(app), CATALOG_POOL).readText() }.getOrDefault("[]")) else emptyList()
        if (catalogs.isNotEmpty()) { pool = catalogs; poolIsTrending = false }
        else { pool = parse(runCatching { File(dir(app), POOL).readText() }.getOrDefault("[]")); poolIsTrending = true }
        picks.clear()
    }

    fun onAppStart(context: Context) {
        if (!NuvioCFeatures.PROFILE_BG_BUILTIN || started) return
        started = true
        val app = context.applicationContext
        loadSettings(app)
        pool = runCatching { parse(File(dir(app), POOL).readText()) }.getOrDefault(emptyList())
        if (catalogOn.value) switchPool(app)
        thread(name = TAG, isDaemon = true, priority = Thread.MIN_PRIORITY) {
            runCatching {
                val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                if (pool.isEmpty() || (NuvioCFeatures.PROFILE_BG_POOL_WIDE && pool.size < 30) || System.currentTimeMillis() - prefs.getLong(KEY_FETCHED, 0L) > DAY_MS) {
                    val fresh = fetchPool()
                    if (fresh.isNotEmpty()) {
                        File(dir(app), POOL).writeText(JSONArray(fresh.map { it.toJson() }).toString())
                        prefs.edit().putLong(KEY_FETCHED, System.currentTimeMillis()).apply()
                        if (pool.isEmpty() && !catalogOn.value) pool = fresh // next launch uses the new pool; this one keeps what is on screen
                    }
                }
                // download every picture of the pool once, so the next cold start has them on disk
                val saved = parse(runCatching { File(dir(app), POOL).readText() }.getOrDefault("[]"))
                if (NuvioCFeatures.PROFILE_BG_POOL_WIDE) {
                    // [fork] round 53: keep the folder to the pool, fetch what profiles show next, then up to 8 more a launch
                    val keep = (saved + parse(runCatching { File(dir(app), CATALOG_POOL).readText() }.getOrDefault("[]"))).map { fileFor(app, it).name }.toSet()
                    dir(app).listFiles()?.filter { it.name.endsWith(".jpg") && it.name !in keep }?.forEach { it.delete() }
                    val heads = nextIds(app).toSet()
                    val missing = saved.filter { fileFor(app, it).length() == 0L }
                    (missing.filter { it.id in heads } + missing.filter { it.id !in heads }.take(8)).forEach { e -> download(e.backdrop, fileFor(app, e)) }
                } else
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
            if (NuvioCFeatures.PROFILE_BG_POOL_WIDE) return@getOrPut nextFromDeck(prefs, profileId, p)
            val last = prefs.getInt("last_$profileId", -1)
            var i = (prefs.getInt("seed", 0) + profileIndex * 3) % p.size
            if (i == last) i = (i + 1) % p.size
            prefs.edit().putInt("last_$profileId", i).putInt("seed", prefs.getInt("seed", 0) + 1).apply()
            i
        }
        return p.getOrNull(index % p.size)
    }

    /**
     * [fork] round 53 profile_bg_pool_wide: each profile draws from its own shuffled deck of the pool (by title id, so
     * a refreshed pool keeps what is left), never the same picture twice running, and only reshuffles once the deck
     * is used up. Profiles shuffle independently, so two profiles rarely show the same picture.
     */
    private fun nextFromDeck(prefs: android.content.SharedPreferences, profileId: Int, p: List<Entry>): Int {
        val ids = p.map { it.id }
        val last = prefs.getString("lastid_$profileId", null)
        var deck = prefs.getString("deck_$profileId", null).orEmpty().split('|').filter { it.isNotBlank() && it in ids }
        if (deck.isEmpty()) {
            deck = ids.shuffled(java.util.Random(System.nanoTime() xor profileId.toLong())).let { d ->
                if (d.size > 1 && d.first() == last) d.drop(1) + d.first() else d
            }
        }
        val pick = deck.first()
        prefs.edit().putString("deck_$profileId", deck.drop(1).joinToString("|")).putString("lastid_$profileId", pick).apply()
        return ids.indexOf(pick).coerceAtLeast(0)
    }

    /** [fork] round 53 profile_bg_no_url_flash: what the launch splash shows for [profileId] (a saved file only). */
    fun splashUrlFor(context: Context, profileId: Int): String? {
        if (!NuvioCFeatures.PROFILE_BG_BUILTIN) return null
        val e = entryFor(context, profileId, 0) ?: return null
        return (model(context, e) as? File)?.toURI()?.toString()
    }

    /** The ids every known profile shows next, so their pictures are on disk before the next cold start. */
    private fun nextIds(app: Context): List<String> {
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.all.filterKeys { it.startsWith("deck_") }.values.mapNotNull { (it as? String)?.split('|')?.firstOrNull { id -> id.isNotBlank() } }
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
        val trending = if (NuvioCFeatures.PROFILE_BG_POOL_WIDE) widePool(key) else
            getJson("https://api.themoviedb.org/3/trending/all/day?api_key=$key&language=en-US")?.optJSONArray("results") ?: return emptyList()
        val genres = (genreMap(key, "movie") + genreMap(key, "tv"))
        val out = ArrayList<Entry>()
        val limit = if (NuvioCFeatures.PROFILE_BG_POOL_WIDE) WIDE_POOL_SIZE else POOL_SIZE
        for (i in 0 until trending.length()) {
            if (out.size >= limit) break
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
            val genreNames = r.optJSONArray("genre_ids")?.let { g -> (0 until g.length()).mapNotNull { genres[g.optInt(it)] } }.orEmpty()
            val genre = genreNames.firstOrNull()
            out += Entry(
                id = "$type:$id",
                title = title,
                backdrop = "https://image.tmdb.org/t/p/w1280$backdrop",
                logo = logo?.let { "https://image.tmdb.org/t/p/w500$it" },
                tag = "TRENDING TODAY · #${out.size + 1}",
                meta = listOfNotNull(if (type == "movie") "Film" else "Series", genre, year.takeIf { it.isNotBlank() }).joinToString(" · "),
                line = (listOfNotNull(year.takeIf { it.isNotBlank() }) + genreNames.take(2)).joinToString(" · ")
            )
        }
        return out
    }

    private const val WIDE_POOL_SIZE = 72

    /**
     * [fork] round 53 profile_bg_pool_wide: trending today and this week, then what is popular in the US (films and
     * shows), merged without repeats and interleaved so the pool isn't all one source. Each item carries media_type.
     */
    private fun widePool(key: String): JSONArray {
        fun results(url: String, type: String? = null): List<JSONObject> =
            getJson(url)?.optJSONArray("results")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } }.orEmpty()
                .onEach { if (type != null) it.put("media_type", type) }
        val base = "https://api.themoviedb.org/3"
        val lists = listOf(
            results("$base/trending/all/day?api_key=$key&language=en-US"),
            results("$base/trending/all/week?api_key=$key&language=en-US"),
            results("$base/trending/all/week?api_key=$key&language=en-US&page=2"),
            results("$base/movie/popular?api_key=$key&language=en-US&region=US", "movie"),
            results("$base/discover/tv?api_key=$key&language=en-US&watch_region=US&with_watch_monetization_types=flatrate&sort_by=popularity.desc", "tv"),
            results("$base/movie/now_playing?api_key=$key&language=en-US&region=US", "movie")
        )
        val seen = HashSet<String>()
        val out = JSONArray()
        val longest = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until longest) for (l in lists) {
            val r = l.getOrNull(i) ?: continue
            val k = r.optString("media_type") + ":" + r.optInt("id")
            if (r.optInt("vote_count") < 50 && r.optString("media_type") == "movie" && l !== lists[5]) continue
            if (seen.add(k)) out.put(r)
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

    private fun Entry.toJson() = JSONObject().put("id", id).put("title", title).put("backdrop", backdrop).put("logo", logo ?: "").put("tag", tag).put("meta", meta).put("line", line)

    internal fun parse(text: String): List<Entry> = runCatching {
        val arr = JSONArray(text)
        (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map {
            Entry(it.optString("id"), it.optString("title"), it.optString("backdrop"), it.optString("logo").takeIf { l -> l.isNotBlank() }, it.optString("tag"), it.optString("meta"), it.optString("line"))
        }.filter { it.backdrop.startsWith("http") }
    }.getOrDefault(emptyList())
}
