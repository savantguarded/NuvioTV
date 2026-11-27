package com.nuvio.tv.data.quality

// [fork] Nuvio C round 67 `QUALITY_DIGITAL_GATE`: a film in cinemas within the last year with no digital (TMDB type 4)
// or disc (type 5) date on or before today in ANY country shows NOT YET ON DIGITAL and makes no Torrentio or
// QualiCache call, so early or fake WEB-DL releases can't put badges on it. Series are never gated.

import com.nuvio.tv.core.tmdb.TmdbService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** Release dates that decide the gate: the cinema date and the earliest digital / disc date in any country (ISO). */
data class NuvioCDigitalDates(val theatrical: String?, val anyHomeRelease: String?)

object NuvioCDigitalGate {
    /** How far back a film's release may be for the gate to matter (the gate only covers the last year). */
    private const val MAY_GATE_DAYS = 400L

    /**
     * True when the film shows NOT YET ON DIGITAL and makes no quality lookup: cinema date in the last 365 days (not in
     * the future) and no digital / disc date on or before today in any country. Pure, for tests.
     */
    fun gated(isSeries: Boolean, theatrical: String?, anyHomeRelease: String?, today: LocalDate): Boolean {
        if (isSeries) return false
        val cinema = TitleQualityPlanner.date(theatrical) ?: return false
        if (cinema.isAfter(today) || cinema.isBefore(today.minusDays(TitleQualityPlanner.RECENT_DAYS))) return false
        val home = TitleQualityPlanner.date(anyHomeRelease)
        return home == null || home.isAfter(today)
    }

    /** Whether waiting for the dates is worth it: films whose release is unknown or within ~a year. Pure, for tests. */
    fun mayBeGated(isSeries: Boolean, released: String?, today: LocalDate): Boolean {
        if (isSeries) return false
        val d = TitleQualityPlanner.date(released)
        if (d != null) return !d.isBefore(today.minusDays(MAY_GATE_DAYS))
        // a year only ("2019", "2024–"): anything before last year can't be in cinemas within the last 365 days
        val year = released?.let { Regex("(19|20)\\d\\d").find(it)?.value?.toIntOrNull() } ?: return true
        return year >= today.year - 1
    }

    /** Earliest TMDB release date of [types] across every country of a `release_dates` block. Pure, for tests. */
    fun earliestAnyCountry(releaseDates: JsonObject?, vararg types: Int): String? =
        (releaseDates?.get("results") as? JsonArray).orEmpty()
            .mapNotNull { it as? JsonObject }
            .flatMap { (it["release_dates"] as? JsonArray).orEmpty() }
            .mapNotNull { it as? JsonObject }
            .filter { ((it["type"] as? JsonPrimitive)?.intOrNull ?: 0) in types }
            .mapNotNull { (it["release_date"] as? JsonPrimitive)?.contentOrNull?.take(10)?.takeIf { d -> TitleQualityPlanner.date(d) != null } }
            .minOrNull()

    /** Earliest cinema date (types 2, 3) across countries, for titles whose US entry is missing. Pure, for tests. */
    fun earliestCinemaAnyCountry(releaseDates: JsonObject?): String? = earliestAnyCountry(releaseDates, 2, 3)
}

internal interface NuvioCDigitalDatesApi {
    @GET("movie/{id}")
    suspend fun movie(
        @Path("id") id: Int,
        @Query("api_key") apiKey: String,
        @Query("append_to_response") append: String = "release_dates"
    ): Response<ResponseBody>
}

/**
 * Release dates for the carousel, which has no Information block: one TMDB call per recent film, kept in memory
 * (12 h, 60 titles). The details page's own Information answer is used first when it is there.
 */
@Singleton
class NuvioCDigitalDatesSource @Inject constructor(
    @Named("tmdb") retrofit: Retrofit,
    private val tmdbService: TmdbService
) {
    private val api = retrofit.create(NuvioCDigitalDatesApi::class.java)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val cache = LinkedHashMap<String, Pair<Long, NuvioCDigitalDates>>()

    @Synchronized private fun cached(id: String): NuvioCDigitalDates? =
        cache[id]?.takeIf { System.currentTimeMillis() - it.first < TTL_MS }?.second

    @Synchronized private fun put(id: String, d: NuvioCDigitalDates) {
        cache.remove(id); cache[id] = System.currentTimeMillis() to d
        while (cache.size > 60) cache.remove(cache.keys.first())
    }

    /** Peek without any call. */
    fun peek(id: String): NuvioCDigitalDates? = cached(id)

    suspend fun datesFor(id: String, imdbId: String?): NuvioCDigitalDates? {
        cached(id)?.let { return it }
        val tmdbId = withTimeoutOrNull(4_000) {
            runCatching { tmdbService.ensureTmdbId(id, "movie", fallbackImdbId = imdbId) }.getOrNull()
        }?.toIntOrNull() ?: return null
        val body = withTimeoutOrNull(6_000) {
            withContext(Dispatchers.IO) {
                runCatching { api.movie(tmdbId, tmdbService.apiKey()).takeIf { it.isSuccessful }?.body()?.use { it.string() } }.getOrNull()
            }
        } ?: return null
        val o = withContext(Dispatchers.Default) { runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() } ?: return null
        val rd = o["release_dates"] as? JsonObject
        val dates = NuvioCDigitalDates(
            theatrical = NuvioCDigitalGate.earliestCinemaAnyCountry(rd) ?: (o["release_date"] as? JsonPrimitive)?.contentOrNull?.take(10),
            anyHomeRelease = NuvioCDigitalGate.earliestAnyCountry(rd, 4, 5)
        )
        put(id, dates)
        return dates
    }

    private companion object { const val TTL_MS = 12 * 60 * 60_000L }
}
