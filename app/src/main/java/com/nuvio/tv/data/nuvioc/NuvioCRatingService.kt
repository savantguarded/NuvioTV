package com.nuvio.tv.data.nuvioc

// [fork] Nuvio C "Rate" from the details page (2026-10-10). One way only: your score is sent to
// Trakt and/or MDBList (POST /sync/ratings, 1-10, overwrites what is there; /sync/ratings/remove
// to clear it). Nothing is read back from either service. The score you sent is remembered on
// this TV per Nuvio profile, so the star shows as rated and the dialog reopens on it.
// Simkl is left out on purpose: rating there adds the title to your Simkl list as watched.
//
// Trakt pull (2026-10-11): your Trakt movie and show ratings are kept on the TV per Nuvio profile,
// so a title you rated on Trakt (website, phone) shows as rated here too. No timer: when a details
// page opens and the last check is over 15 minutes old, one small `sync/last_activities` call asks
// Trakt whether your ratings changed; the full lists (`sync/ratings/movies|shows`, ~250 KB for
// 1000+ ratings) are downloaded only when they did. Lookups are then from memory.

import android.content.Context
import androidx.core.content.edit
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.data.mdblist.MdbListApiClient
import com.nuvio.tv.data.mdblist.MdbListAuthStore
import com.nuvio.tv.data.repository.TraktAuthService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import retrofit2.Response
import retrofit2.Retrofit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

enum class NuvioCTracker(val label: String) { TRAKT("Trakt"), MDBLIST("MDBList") }

/** What is being rated. At least one id is set. */
data class NuvioCRatingTarget(val isShow: Boolean, val imdbId: String?, val tmdbId: Int?) {
    val key: String get() = (if (isShow) "show:" else "movie:") + (imdbId ?: "tmdb$tmdbId")

    /** Every id this title can be matched by (IMDb and TMDB), for the Trakt copy. */
    fun keys(): List<String> {
        val p = if (isShow) "show:" else "movie:"
        return listOfNotNull(imdbId?.let { p + it }, tmdbId?.let { p + "tmdb" + it })
    }
}

internal interface NuvioCTraktRatingsApi {
    @POST("sync/ratings")
    suspend fun add(@Header("Authorization") authorization: String, @Body body: RequestBody): Response<ResponseBody>

    @POST("sync/ratings/remove")
    suspend fun remove(@Header("Authorization") authorization: String, @Body body: RequestBody): Response<ResponseBody>

    @GET("sync/last_activities")
    suspend fun lastActivities(@Header("Authorization") authorization: String): Response<ResponseBody>

    @GET("sync/ratings/movies")
    suspend fun movieRatings(@Header("Authorization") authorization: String): Response<ResponseBody>

    @GET("sync/ratings/shows")
    suspend fun showRatings(@Header("Authorization") authorization: String): Response<ResponseBody>
}

@Singleton
class NuvioCRatingService @Inject constructor(
    @ApplicationContext private val context: Context,
    @Named("trakt") traktRetrofit: Retrofit,
    private val traktAuth: TraktAuthService,
    private val mdbApi: MdbListApiClient,
    private val mdbAuth: MdbListAuthStore,
    private val profiles: ProfileManager
) {
    private val traktApi = traktRetrofit.create(NuvioCTraktRatingsApi::class.java)
    private val prefs by lazy { context.getSharedPreferences("nuvio_c_ratings", Context.MODE_PRIVATE) }
    private fun profileKey(key: String) = "p${profiles.activeProfileId.value}:$key"

    /** Trackers you are signed in to (only these get a row in the dialog). */
    suspend fun connected(): List<NuvioCTracker> = buildList {
        val trakt = runCatching { traktAuth.hasRequiredCredentials() && !traktAuth.getCurrentAuthState().accessToken.isNullOrBlank() }
            .getOrDefault(false)
        if (trakt) add(NuvioCTracker.TRAKT)
        if (runCatching { mdbAuth.authorization() != null }.getOrDefault(false)) add(NuvioCTracker.MDBLIST)
    }

    // ---- Trakt pull -------------------------------------------------------------------------
    private class TraktRatings(val profile: Int, val movies: String?, val shows: String?, val scores: MutableMap<String, Int>)
    private val traktMutex = Mutex()
    @Volatile private var trakt: TraktRatings? = null
    @Volatile private var lastCheck = 0L
    @Volatile private var lastCheckProfile = -1

    private fun traktFile(profile: Int) = File(context.filesDir, "nuvio_c_trakt_ratings_p$profile.txt")

    /**
     * Your Trakt score for this title, or null. Refreshes first when the last check is over
     * [CHECK_EVERY_MS] old (one tiny request; the lists only when your ratings changed).
     */
    suspend fun traktScore(target: NuvioCRatingTarget): Int? = runCatching {
        if (NuvioCTracker.TRAKT !in connected()) return null
        refreshTrakt()
        val scores = trakt?.scores ?: return null
        target.keys().firstNotNullOfOrNull { scores[it] }
    }.getOrNull()

    private suspend fun refreshTrakt() = traktMutex.withLock {
        val profile = profiles.activeProfileId.value
        if (trakt?.profile != profile) trakt = loadTrakt(profile)
        val now = System.currentTimeMillis()
        if (lastCheckProfile == profile && now - lastCheck < CHECK_EVERY_MS) return@withLock
        val activities = traktAuth.executeAuthorizedRequest { traktApi.lastActivities(it) } ?: return@withLock
        val body = activities.body()?.use { it.string() }
        if (!activities.isSuccessful || body == null) return@withLock
        lastCheck = now
        lastCheckProfile = profile
        val root = Json.parseToJsonElement(body).jsonObject
        val moviesAt = root["movies"]?.jsonObject?.get("rated_at")?.jsonPrimitive?.contentOrNull
        val showsAt = root["shows"]?.jsonObject?.get("rated_at")?.jsonPrimitive?.contentOrNull
        val old = trakt
        if (old != null && old.movies == moviesAt && old.shows == showsAt) return@withLock
        val movies = fetchRatings { traktApi.movieRatings(it) } ?: return@withLock
        val shows = fetchRatings { traktApi.showRatings(it) } ?: return@withLock
        val scores = HashMap<String, Int>(movies.size + shows.size)
        scores.putAll(movies)
        scores.putAll(shows)
        trakt = TraktRatings(profile, moviesAt, showsAt, scores)
        saveTrakt(trakt!!)
    }

    private suspend fun fetchRatings(call: suspend (String) -> Response<ResponseBody>): Map<String, Int>? {
        val response = traktAuth.executeAuthorizedRequest(call) ?: return null
        val body = response.body()?.use { it.string() }
        if (!response.isSuccessful || body == null) return null
        return parseTraktRatings(body)
    }

    private fun loadTrakt(profile: Int): TraktRatings? = runCatching {
        val lines = traktFile(profile).takeIf { it.exists() }?.readLines() ?: return null
        val movies = lines.getOrNull(0)?.takeIf { it.isNotEmpty() }
        val shows = lines.getOrNull(1)?.takeIf { it.isNotEmpty() }
        val scores = HashMap<String, Int>()
        lines.drop(2).forEach { line ->
            val i = line.lastIndexOf('=')
            if (i > 0) line.substring(i + 1).toIntOrNull()?.let { scores[line.substring(0, i)] = it }
        }
        TraktRatings(profile, movies, shows, scores)
    }.getOrNull()

    private fun saveTrakt(r: TraktRatings) = runCatching {
        val text = buildString {
            append(r.movies.orEmpty()).append('\n').append(r.shows.orEmpty()).append('\n')
            r.scores.forEach { (k, v) -> append(k).append('=').append(v).append('\n') }
        }
        traktFile(r.profile).writeText(text)
    }

    /** Keeps the pulled copy in step after a save from this TV (the next check refetches anyway). */
    private fun updateTraktCopy(target: NuvioCRatingTarget, score: Int?) {
        val r = trakt ?: return
        if (r.profile != profiles.activeProfileId.value) return
        target.keys().forEach { if (score == null) r.scores.remove(it) else r.scores[it] = score }
    }

    /** Score last sent from this TV for this profile, or null. */
    fun savedScore(target: NuvioCRatingTarget): Int? =
        prefs.getInt(profileKey(target.key), 0).takeIf { it in 1..10 }

    /** Trackers ticked last time (default: all connected). */
    fun lastChoice(): Set<NuvioCTracker>? = prefs.getStringSet(profileKey("trackers"), null)
        ?.mapNotNull { name -> NuvioCTracker.entries.firstOrNull { it.name == name } }?.toSet()

    /**
     * Sends [score] (1-10), or removes the rating when null, to every tracker in [to] at once.
     * Returns the trackers that failed (empty = all good). The local memory follows the result.
     */
    suspend fun send(target: NuvioCRatingTarget, score: Int?, to: Set<NuvioCTracker>): Set<NuvioCTracker> {
        prefs.edit { putStringSet(profileKey("trackers"), to.map { it.name }.toSet()) }
        val body = payload(target, score)
        val failed = coroutineScope {
            to.map { tracker ->
                async {
                    val ok = runCatching {
                        when (tracker) {
                            NuvioCTracker.TRAKT -> sendTrakt(body, score == null).also { if (it) updateTraktCopy(target, score) }
                            NuvioCTracker.MDBLIST -> sendMdbList(body, score == null)
                        }
                    }.getOrDefault(false)
                    tracker.takeUnless { ok }
                }
            }.mapNotNull { it.await() }.toSet()
        }
        if (failed.size < to.size) {
            prefs.edit {
                if (score == null) remove(profileKey(target.key)) else putInt(profileKey(target.key), score)
            }
        }
        return failed
    }

    private suspend fun sendTrakt(body: String, remove: Boolean): Boolean {
        val request = body.toRequestBody(JSON)
        val response = traktAuth.executeAuthorizedWriteRequest { auth ->
            if (remove) traktApi.remove(auth, request) else traktApi.add(auth, request)
        } ?: return false
        response.body()?.close()
        return response.isSuccessful
    }

    private suspend fun sendMdbList(body: String, remove: Boolean): Boolean {
        val response = mdbApi.post(if (remove) "/sync/ratings/remove" else "/sync/ratings", body)
        return response.status in 200..299
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private const val CHECK_EVERY_MS = 15 * 60 * 1000L

        /** Trakt `sync/ratings/{movies|shows}` answer -> "movie:tt…" / "movie:tmdb123" / "show:…" -> score. */
        fun parseTraktRatings(body: String): Map<String, Int> {
            val out = HashMap<String, Int>()
            Json.parseToJsonElement(body).jsonArray.forEach { el ->
                val item = el as? JsonObject ?: return@forEach
                val rating = item["rating"]?.jsonPrimitive?.intOrNull?.takeIf { it in 1..10 } ?: return@forEach
                val kind = item["type"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                if (kind != "movie" && kind != "show") return@forEach
                val ids = item[kind]?.jsonObject?.get("ids")?.jsonObject ?: return@forEach
                NuvioCRatingTarget(
                    isShow = kind == "show",
                    imdbId = ids["imdb"]?.jsonPrimitive?.contentOrNull?.takeIf { it.startsWith("tt") },
                    tmdbId = ids["tmdb"]?.jsonPrimitive?.intOrNull
                ).keys().forEach { out[it] = rating }
            }
            return out
        }

        /** {"movies"|"shows": [{"ids": {imdb, tmdb}, "rating": n}]} (rating left out to remove). */
        fun payload(target: NuvioCRatingTarget, score: Int?): String = buildJsonObject {
            val item = buildJsonObject {
                put("ids", buildJsonObject {
                    target.imdbId?.let { put("imdb", it) }
                    target.tmdbId?.let { put("tmdb", it) }
                })
                score?.let { put("rating", it) }
            }
            put(if (target.isShow) "shows" else "movies", JsonArray(listOf(item)))
        }.toString()
    }
}
