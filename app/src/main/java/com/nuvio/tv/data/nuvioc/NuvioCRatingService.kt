package com.nuvio.tv.data.nuvioc

// [fork] Nuvio C "Rate" from the details page (2026-10-10). One way only: your score is sent to
// Trakt and/or MDBList (POST /sync/ratings, 1-10, overwrites what is there; /sync/ratings/remove
// to clear it). Nothing is read back from either service. The score you sent is remembered on
// this TV per Nuvio profile, so the star shows as rated and the dialog reopens on it.
// Simkl is left out on purpose: rating there adds the title to your Simkl list as watched.

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
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

enum class NuvioCTracker(val label: String) { TRAKT("Trakt"), MDBLIST("MDBList") }

/** What is being rated. At least one id is set. */
data class NuvioCRatingTarget(val isShow: Boolean, val imdbId: String?, val tmdbId: Int?) {
    val key: String get() = (if (isShow) "show:" else "movie:") + (imdbId ?: "tmdb$tmdbId")
}

internal interface NuvioCTraktRatingsApi {
    @POST("sync/ratings")
    suspend fun add(@Header("Authorization") authorization: String, @Body body: RequestBody): Response<ResponseBody>

    @POST("sync/ratings/remove")
    suspend fun remove(@Header("Authorization") authorization: String, @Body body: RequestBody): Response<ResponseBody>
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
                            NuvioCTracker.TRAKT -> sendTrakt(body, score == null)
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
