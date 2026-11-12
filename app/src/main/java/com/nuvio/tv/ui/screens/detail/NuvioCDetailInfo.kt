package com.nuvio.tv.ui.screens.detail

// [fork] Nuvio C round 31 (switches `detail_info_block`, `detail_next_ep_badge`).
//
// - detail_info_block: an "Information" block at the very end of the details page, under the last row (the
//   production / network logos), styled like those rows' titles. Films: Theatrical, Digital, Runtime, Budget,
//   Box office, Original language. Shows: First aired, Airs (day and time of the next episode in your time zone),
//   Status, Seasons, Original language. Not focusable, so no extra stop: the page's scroll to its end (round 9
//   `detail_scroll_end`, when the logo rows get focus) brings it into view. Missing values are left out.
//   With the block on, the film's full release date moves here and the top of the page shows the year.
// - detail_next_ep_badge: a show's status badge ("ONGOING") reads the next episode's countdown when it has a
//   date ("S5 E4 IN 3 DAYS", "TOMORROW 3:00 AM", "TONIGHT 9:00 PM"). Same badge, same place.
//
// Data: one TMDB call (film: details + release dates, US like the Coming soon list; show: details) and, for
// shows, one TVmaze lookup for the exact air time. Both run once the page is up, off the main thread, and
// are kept for 6 h in memory, so going back and forth costs nothing. Your meta add-on stays the main source;
// this only adds what it doesn't carry.

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.R
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.data.remote.api.TvMazeApi
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.Meta
import com.nuvio.tv.ui.theme.NuvioTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject
import javax.inject.Named

internal interface NuvioCDetailInfoApi {
    @GET("{type}/{id}")
    suspend fun details(
        @Path("type") type: String,
        @Path("id") id: Int,
        @Query("api_key") apiKey: String,
        @Query("append_to_response") append: String?,
        @Query("language") language: String = "en-US"
    ): Response<ResponseBody>
}

/** One label / value line of the Information block. */
@Immutable
data class NuvioCInfoRow(val label: Int, val value: String)

/** The next episode of a show: its number and when it airs (exact time when TVmaze knows it). */
@Immutable
data class NuvioCNextEpisode(val season: Int?, val episode: Int?, val date: LocalDate, val at: Instant?)

@Immutable
data class NuvioCDetailInfo(
    val metaId: String,
    val rows: List<NuvioCInfoRow> = emptyList(),
    val next: NuvioCNextEpisode? = null,
    // [fork] round 43 detail_info_slots / detail_awards_bodies / detail_guide
    val show: Boolean = false,
    val loaded: Boolean = false,
    val rated: String? = null,
    val awards: NuvioCAwards? = null,
    val awardsText: String? = null,
    val guide: com.nuvio.tv.data.repository.NuvioCGuide? = null,
    val guideChecked: Boolean = false,
    val imdbFromTmdb: String? = null
)

/** The info of the details page on screen; consumers check [NuvioCDetailInfo.metaId] against their title. */
object NuvioCDetailInfoStore {
    private val _current = MutableStateFlow<NuvioCDetailInfo?>(null)
    val current: StateFlow<NuvioCDetailInfo?> = _current.asStateFlow()
    internal fun publish(info: NuvioCDetailInfo) { _current.value = info }

    private val cache = LinkedHashMap<String, Pair<Long, NuvioCDetailInfo>>()
    private const val TTL_MS = 6 * 60 * 60 * 1000L

    @Synchronized internal fun cached(id: String): NuvioCDetailInfo? =
        cache[id]?.takeIf { System.currentTimeMillis() - it.first < TTL_MS }?.second

    @Synchronized internal fun put(info: NuvioCDetailInfo) {
        cache.remove(info.metaId)
        cache[info.metaId] = System.currentTimeMillis() to info
        while (cache.size > 30) cache.remove(cache.keys.first())
    }
}

@HiltViewModel
class NuvioCDetailInfoViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context, // [fork] round 37 omdb_awards
    @Named("tmdb") tmdbRetrofit: Retrofit,
    private val tmdbService: TmdbService,
    private val tvMaze: TvMazeApi,
    private val parentalGuide: com.nuvio.tv.data.repository.ParentalGuideRepository // [fork] round 43 detail_guide
) : ViewModel() {
    private val api = tmdbRetrofit.create(NuvioCDetailInfoApi::class.java)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var loadingFor: String? = null

    /** Shows this title's info (from the cache at once when it's there), loading it once the page has settled. */
    fun load(meta: Meta) {
        if (!NuvioCFeatures.DETAIL_INFO_BLOCK && !NuvioCFeatures.DETAIL_NEXT_EP_BADGE) return
        NuvioCDetailInfoStore.cached(meta.id)?.let { NuvioCDetailInfoStore.publish(it); return }
        // [fork] round 39 detail_info_polish: the block appears once, complete, instead of showing the add-on's two
        // or three lines first and then changing shape when TMDB answers (the add-on lines stay the fallback).
        NuvioCDetailInfoStore.publish(NuvioCDetailInfo(meta.id, rows = if (NuvioCFeatures.DETAIL_INFO_POLISH) emptyList() else localRows(meta)))
        if (loadingFor == meta.id) return
        loadingFor = meta.id
        viewModelScope.launch {
            delay(SETTLE_MS) // after the page, trailer and ratings have started
            val fetched = withContext(Dispatchers.IO) { runCatching { fetch(meta) }.getOrNull() } ?: NuvioCDetailInfo(meta.id, localRows(meta))
            if (NuvioCFeatures.DETAIL_INFO_SLOTS) {
                // [fork] round 43: the slots show at once (fixed places, dashes until filled); awards and the guide fill in
                // their own slots when they answer, so nothing moves.
                var info = fetched.copy(show = isShow(meta), loaded = true, rated = meta.ageRating?.trim()?.takeIf { it.isNotBlank() })
                NuvioCDetailInfoStore.publish(info)
                val imdb = meta.imdbId?.takeIf { it.startsWith("tt") } ?: meta.id.takeIf { it.startsWith("tt") } ?: fetched.imdbFromTmdb
                val awardsJob = viewModelScope.async {
                    if (!NuvioCFeatures.DETAIL_AWARDS_BODIES || imdb == null) null
                    else withTimeoutOrNull(15_000) { NuvioCWikidataAwards.awards(appContext, imdb) }
                }
                val totalJob = viewModelScope.async { withAwards(NuvioCDetailInfo(meta.id), meta, imdb).rows.firstOrNull()?.value }
                val guideJob = viewModelScope.async {
                    if (!NuvioCFeatures.DETAIL_GUIDE || imdb == null) null
                    else withTimeoutOrNull(10_000) { withContext(Dispatchers.IO) { parentalGuide.nuvioCGuide(imdb) } }
                }
                val guide = guideJob.await()
                info = info.copy(guide = guide, guideChecked = true)
                if (NuvioCDetailInfoStore.current.value?.metaId == meta.id) NuvioCDetailInfoStore.publish(info)
                info = info.copy(awards = awardsJob.await(), awardsText = totalJob.await())
                NuvioCDetailInfoStore.put(info)
                if (NuvioCDetailInfoStore.current.value?.metaId == meta.id) NuvioCDetailInfoStore.publish(info)
                return@launch
            }
            val info = withAwards(fetched, meta) // [fork] round 37 omdb_awards
            NuvioCDetailInfoStore.put(info)
            NuvioCDetailInfoStore.publish(info)
        }
    }

    private val isShow: (Meta) -> Boolean = { it.type == ContentType.SERIES || it.type == ContentType.TV }

    /**
     * [fork] round 37 omdb_awards: an Awards line last in the block. Your meta add-on's awards text when it has one,
     * else OMDb with your key (Settings > Integrations). Nothing when neither has awards.
     */
    private suspend fun withAwards(info: NuvioCDetailInfo, meta: Meta, imdbFallback: String? = null): NuvioCDetailInfo {
        if (!NuvioCFeatures.OMDB_AWARDS || info.rows.any { it.label == R.string.nuvio_c_info_awards }) return info
        val text = com.nuvio.tv.core.nuvioc.NuvioCOmdb.tidy(meta.awards) ?: run {
            val imdb = meta.imdbId?.takeIf { it.startsWith("tt") } ?: meta.id.takeIf { it.startsWith("tt") } ?: imdbFallback ?: return info
            withTimeoutOrNull(8_000) { runCatching { com.nuvio.tv.core.nuvioc.NuvioCOmdb.awards(appContext, imdb) }.getOrNull() }
        } ?: return info
        return info.copy(rows = info.rows + NuvioCInfoRow(R.string.nuvio_c_info_awards, text))
    }

    /** What the meta add-on already gives, so the block is never empty while TMDB answers (or if it can't). */
    private fun localRows(meta: Meta): List<NuvioCInfoRow> = buildList {
        if (!isShow(meta)) {
            nuvioCDate(meta.released)?.let { add(NuvioCInfoRow(R.string.nuvio_c_info_theatrical, it)) }
            meta.runtime?.let { com.nuvio.tv.ui.util.nuvioCRuntime(it) }?.takeIf { it.isNotBlank() }
                ?.let { add(NuvioCInfoRow(R.string.nuvio_c_info_runtime, it)) }
        }
        meta.language?.let(::languageName)?.let { add(NuvioCInfoRow(R.string.nuvio_c_info_language, it)) }
        if (NuvioCFeatures.DETAIL_INFO_SLOTS) meta.country?.trim()?.takeIf { it.isNotBlank() }?.let { add(NuvioCInfoRow(R.string.nuvio_c_info_country, it)) }
    }

    private suspend fun fetch(meta: Meta): NuvioCDetailInfo? {
        val show = isShow(meta)
        val tmdbId = withTimeoutOrNull(6_000) {
            runCatching { tmdbService.ensureTmdbId(meta.id, if (show) "series" else "movie") }.getOrNull()
        }?.toIntOrNull() ?: return null
        val body = withTimeoutOrNull(8_000) {
            api.details(
                if (show) "tv" else "movie", tmdbId, tmdbService.apiKey(),
                when { // [fork] round 43: credits for "Based on"
                    NuvioCFeatures.DETAIL_INFO_SLOTS && show -> "external_ids,credits"
                    NuvioCFeatures.DETAIL_INFO_SLOTS -> "release_dates,credits,external_ids"
                    show -> "external_ids"
                    else -> "release_dates"
                }
            )
                .takeIf { it.isSuccessful }?.body()?.use { it.string() }
        } ?: return null
        val o = json.parseToJsonElement(body).jsonObject
        return if (show) showInfo(meta, o) else filmInfo(meta, o)
    }

    private fun filmInfo(meta: Meta, o: JsonObject): NuvioCDetailInfo {
        val us = (o.obj("release_dates")?.get("results") as? JsonArray).orEmpty()
            .mapNotNull { it as? JsonObject }.firstOrNull { it.str("iso_3166_1") == REGION }
        val dates = (us?.get("release_dates") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        fun earliest(vararg types: Int) = dates.filter { (it.int("type") ?: 0) in types }
            .mapNotNull { it.str("release_date")?.take(10) }.minOrNull()
        val theatrical = earliest(3, 2) ?: o.str("release_date") ?: meta.released?.take(10)
        val digital = earliest(4)
        val runtime = o.int("runtime")?.takeIf { it > 0 }?.let { com.nuvio.tv.ui.util.nuvioCRuntimeText(it) }
            ?: meta.runtime?.let { com.nuvio.tv.ui.util.nuvioCRuntime(it) }
        val rows = buildList {
            nuvioCDate(theatrical)?.let { add(NuvioCInfoRow(R.string.nuvio_c_info_theatrical, it)) }
            nuvioCDate(digital)?.let { add(NuvioCInfoRow(R.string.nuvio_c_info_digital, it)) }
            runtime?.takeIf { it.isNotBlank() }?.let { add(NuvioCInfoRow(R.string.nuvio_c_info_runtime, it)) }
            o.long("budget")?.takeIf { it > 0 }?.let { add(NuvioCInfoRow(R.string.nuvio_c_info_budget, money(it))) }
            o.long("revenue")?.takeIf { it > 0 }?.let { add(NuvioCInfoRow(R.string.nuvio_c_info_box_office, money(it))) }
            (o.str("original_language") ?: meta.language)?.let(::languageName)?.let { add(NuvioCInfoRow(R.string.nuvio_c_info_language, it)) }
            if (NuvioCFeatures.DETAIL_INFO_SLOTS) addAll(slotExtras(meta, o, show = false))
        }
        return NuvioCDetailInfo(meta.id, rows, imdbFromTmdb = o.str("imdb_id") ?: o.obj("external_ids")?.str("imdb_id"))
    }

    private suspend fun showInfo(meta: Meta, o: JsonObject): NuvioCDetailInfo {
        val nextTmdb = o.obj("next_episode_to_air")
        var next = nextTmdb?.let { e ->
            e.str("air_date")?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
                ?.let { NuvioCNextEpisode(e.int("season_number"), e.int("episode_number"), it, null) }
        }
        // TVmaze has the exact time; only asked when TMDB says an episode is on its way.
        val imdb = meta.id.takeIf { it.startsWith("tt") } ?: o.obj("external_ids")?.str("imdb_id")
        if (next != null && imdb != null) {
            val maze = withTimeoutOrNull(6_000) {
                runCatching {
                    val id = tvMaze.lookupByImdb(imdb).body()?.id ?: return@runCatching null
                    tvMaze.getShow(id).body()?.embedded?.nextEpisode
                }.getOrNull()
            }
            val at = maze?.airstamp?.let { runCatching { Instant.parse(it) }.getOrNull() }
            if (at != null) {
                next = NuvioCNextEpisode(
                    season = maze.season ?: next.season,
                    episode = maze.number ?: next.episode,
                    date = at.atZone(ZoneId.systemDefault()).toLocalDate(),
                    at = at
                )
            }
        }
        val status = when (o.str("status")?.lowercase()) {
            "returning series" -> R.string.nuvio_c_info_status_returning
            "ended" -> R.string.nuvio_c_info_status_ended
            "canceled", "cancelled" -> R.string.nuvio_c_info_status_cancelled
            "in production", "planned", "pilot" -> R.string.nuvio_c_info_status_upcoming
            else -> null
        }
        val seasons = o.int("number_of_seasons")?.takeIf { it > 0 }
        val episodes = o.int("number_of_episodes")?.takeIf { it > 0 }
        val rows = buildList {
            nuvioCDate(o.str("first_air_date"))?.let { add(NuvioCInfoRow(R.string.nuvio_c_info_first_aired, it)) }
            next?.at?.let { at ->
                val local = at.atZone(ZoneId.systemDefault())
                val day = local.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                add(NuvioCInfoRow(R.string.nuvio_c_info_airs, if (NuvioCFeatures.DETAIL_INFO_POLISH) "${day}s at ${local.format(TIME)}" else "${day}s, ${local.format(TIME)}"))
            }
            status?.let { add(NuvioCInfoRow(R.string.nuvio_c_info_status, "\u0000$it")) } // resolved in the UI
            if (seasons != null) {
                add(NuvioCInfoRow(R.string.nuvio_c_info_seasons, when {
                    // [fork] round 39 detail_info_polish: "3 seasons · 30 episodes", spelled out like the other values
                    NuvioCFeatures.DETAIL_INFO_POLISH && episodes != null -> "$seasons ${if (seasons == 1) "season" else "seasons"} · $episodes ${if (episodes == 1) "episode" else "episodes"}"
                    NuvioCFeatures.DETAIL_INFO_POLISH -> "$seasons ${if (seasons == 1) "season" else "seasons"}"
                    episodes != null -> "$seasons · $episodes episodes"
                    else -> "$seasons"
                }))
            }
            (o.str("original_language") ?: meta.language)?.let(::languageName)?.let { add(NuvioCInfoRow(R.string.nuvio_c_info_language, it)) }
            if (NuvioCFeatures.DETAIL_INFO_SLOTS) {
                if (o.str("status")?.lowercase() in setOf("ended", "canceled", "cancelled")) {
                    nuvioCDate(o.str("last_air_date"))?.let { add(NuvioCInfoRow(R.string.nuvio_c_info_last_aired, it)) }
                }
                episodes?.let { add(NuvioCInfoRow(R.string.nuvio_c_info_episodes, "$it")) }
                val length = (o["episode_run_time"] as? JsonArray)?.firstNotNullOfOrNull { (it as? JsonPrimitive)?.intOrNull?.takeIf { m -> m > 0 } }
                    ?: o.obj("last_episode_to_air")?.int("runtime")?.takeIf { it > 0 }
                length?.let { add(NuvioCInfoRow(R.string.nuvio_c_info_episode_length, "about $it min")) }
                addAll(slotExtras(meta, o, show = true))
            }
        }
        return NuvioCDetailInfo(meta.id, rows, next, imdbFromTmdb = imdb)
    }

    /** [fork] round 43 detail_info_slots: Country, Based on, Original title (when it differs). */
    private fun slotExtras(meta: Meta, o: JsonObject, show: Boolean): List<NuvioCInfoRow> = buildList {
        val countries = if (show) {
            (o["origin_country"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                .map { Locale("", it).getDisplayCountry(Locale.ENGLISH).ifBlank { it } }
        } else {
            (o["production_countries"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.str("name") }
                .map { it.replace("United States of America", "United States").replace("United Kingdom of Great Britain and Northern Ireland", "United Kingdom") }
        }
        (countries.takeIf { it.isNotEmpty() }?.take(3)?.joinToString(", ") ?: meta.country?.trim()?.takeIf { it.isNotBlank() })
            ?.let { add(NuvioCInfoRow(R.string.nuvio_c_info_country, it)) }
        nuvioCBasedOn(o.obj("credits"))?.let { add(NuvioCInfoRow(R.string.nuvio_c_info_based_on, it)) }
        val original = o.str(if (show) "original_name" else "original_title")
        val shown = o.str(if (show) "name" else "title") ?: meta.name
        if (original != null && !original.equals(shown, ignoreCase = true) && !original.equals(meta.name, ignoreCase = true)) {
            add(NuvioCInfoRow(R.string.nuvio_c_info_original_title, original))
        }
    }

    private companion object {
        const val SETTLE_MS = 1_200L
        const val REGION = "US"
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    }
}

/**
 * [fork] round 43: "Novel by Jim Thompson" from TMDB credits (crew jobs for source material), or null.
 * Order: novel, book, short story, comic / graphic novel, play, memoir, article, characters.
 */
internal fun nuvioCBasedOn(credits: JsonObject?): String? {
    val crew = (credits?.get("crew") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
    val order = listOf("Novel", "Book", "Short Story", "Comic Book", "Graphic Novel", "Play", "Memoir", "Article", "Characters")
    for (job in order) {
        val names = crew.filter { it.str("job").equals(job, ignoreCase = true) }.mapNotNull { it.str("name") }.distinct()
        if (names.isNotEmpty()) {
            val what = if (job == "Characters") "Characters" else job.lowercase().replaceFirstChar { it.uppercase() }
            return "$what by ${names.take(2).joinToString(" and ")}"
        }
    }
    return null
}

// ---- formatting ---------------------------------------------------------------------------------------

private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/** "2024-03-01" -> "1 Mar 2024"; anything else -> null. */
internal fun nuvioCDate(raw: String?): String? =
    raw?.take(10)?.let { runCatching { LocalDate.parse(it).format(DATE) }.getOrNull() }

/** 190000000 -> "$190M", 714800000 -> "$714.8M", 1200000000 -> "$1.2B", 850000 -> "$850K". */
internal fun money(amount: Long): String {
    fun trim(v: Double) = String.format(Locale.US, "%.1f", v).removeSuffix(".0")
    return when {
        amount >= 1_000_000_000L -> "$" + trim(amount / 1_000_000_000.0) + "B"
        amount >= 1_000_000L -> "$" + trim(amount / 1_000_000.0) + "M"
        amount >= 1_000L -> "$" + trim(amount / 1_000.0) + "K"
        else -> "$$amount"
    }
}

private fun languageName(code: String): String? {
    val c = code.trim().lowercase().takeIf { it.length in 2..3 } ?: return code.trim().takeIf { it.length > 3 }
    return Locale(c).getDisplayLanguage(Locale.ENGLISH).takeIf { it.isNotBlank() && it.lowercase() != c }
}

/**
 * The status badge text for a show's next episode, or null to keep the official badge:
 * "TONIGHT 9:00 PM", "TODAY 3:00 PM", "TOMORROW 3:00 AM", "S5 E4 IN 3 DAYS" (up to 30 days ahead).
 */
internal fun nuvioCNextEpisodeBadge(next: NuvioCNextEpisode?, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): String? {
    if (next == null) return null
    val local = next.at?.atZone(zone)
    val date = local?.toLocalDate() ?: next.date
    val days = ChronoUnit.DAYS.between(today, date)
    if (days < 0 || days > 30) return null
    val time = local?.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
    val code = if (next.season != null && next.episode != null) "S${next.season} E${next.episode}" else null
    return when {
        days == 0L && time != null && local != null && local.hour >= 17 -> "TONIGHT $time"
        days == 0L && time != null -> "TODAY $time"
        days == 0L -> "TODAY"
        days == 1L && time != null -> "TOMORROW $time"
        days == 1L -> "TOMORROW"
        code != null -> "$code IN $days DAYS"
        else -> "IN $days DAYS"
    }?.uppercase(Locale.ENGLISH)
}

// ---- the block -------------------------------------------------------------------------------------------

/** The Information block for [metaId], or nothing until there's something to show. */
@Composable
internal fun NuvioCDetailInfoBlock(metaId: String) {
    if (!NuvioCFeatures.DETAIL_INFO_BLOCK) return
    val info by NuvioCDetailInfoStore.current.collectAsState()
    if (NuvioCFeatures.DETAIL_INFO_SLOTS) { // [fork] round 43 detail_info_slots: fixed places, Awards, Parental guidance
        val current = info?.takeIf { it.metaId == metaId && it.loaded } ?: return
        Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 36.dp)) {
            NuvioCInfoSlots(current, com.nuvio.tv.ui.components.nuvioCDetailMargin)
        }
        return
    }
    val rows = info?.takeIf { it.metaId == metaId }?.rows.orEmpty()
        .filter { !com.nuvio.tv.NuvioCFeatures.DETAIL_INFO_TRIM || it.label !in NUVIO_C_INFO_TRIMMED } // [fork] round 32 detail_info_trim
    if (rows.isEmpty()) return
    val margin = com.nuvio.tv.ui.components.nuvioCDetailMargin
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 36.dp)
    ) {
        Text(
            text = stringResource(R.string.nuvio_c_info_title),
            style = MaterialTheme.typography.titleLarge,
            color = NuvioTheme.colors.TextPrimary,
            modifier = Modifier.padding(horizontal = margin)
        )
        Spacer(Modifier.height(14.dp))
        if (NuvioCFeatures.DETAIL_INFO_POLISH) {
            NuvioCInfoPolished(rows, margin)
            return@Column
        }
        if (NuvioCFeatures.DETAIL_INFO_LEDGER) {
            NuvioCInfoLedger(rows, margin)
            return@Column
        }
        // Three across, rows of equal columns; long values ellipsize inside their column, never clip.
        rows.chunked(3).forEach { line ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = margin, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                line.forEach { row ->
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(row.label),
                            style = MaterialTheme.typography.labelMedium,
                            color = NuvioTheme.extendedColors.textTertiary,
                            maxLines = 1
                        )
                        Spacer(Modifier.height(2.dp))
                        val value = if (row.value.startsWith("\u0000")) {
                            row.value.drop(1).toIntOrNull()?.let { stringResource(it) }.orEmpty()
                        } else row.value
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodyMedium,
                            color = NuvioTheme.colors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                repeat(3 - line.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * [fork] round 33 detail_info_ledger (option A): two columns of label left / value right, a hairline above
 * each line, read left to right. A long value ellipsizes inside its half; labels never shrink.
 */
@Composable
private fun NuvioCInfoLedger(rows: List<NuvioCInfoRow>, margin: androidx.compose.ui.unit.Dp) {
    val hairline = NuvioTheme.colors.TextPrimary.copy(alpha = 0.08f)
    // [fork] round 37 omdb_awards: Awards runs the full width on its own line, so it's rarely cut short
    val awards = rows.firstOrNull { it.label == R.string.nuvio_c_info_awards }
    val paired = if (awards != null) rows - awards else rows
    Column(Modifier.fillMaxWidth().padding(horizontal = margin)) {
        paired.chunked(2).forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(56.dp), modifier = Modifier.fillMaxWidth()) {
                line.forEach { row ->
                    val value = if (row.value.startsWith("\u0000")) {
                        row.value.drop(1).toIntOrNull()?.let { stringResource(it) }.orEmpty()
                    } else row.value
                    Column(Modifier.weight(1f)) {
                        androidx.compose.foundation.layout.Box(
                            Modifier.fillMaxWidth().height(1.dp).then(Modifier.background(hairline))
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(row.label),
                                style = MaterialTheme.typography.bodyMedium,
                                color = NuvioTheme.extendedColors.textTertiary,
                                maxLines = 1
                            )
                            Spacer(Modifier.width(24.dp))
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodyMedium,
                                color = NuvioTheme.colors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                if (line.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (awards != null) {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(1.dp).then(Modifier.background(hairline)))
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(awards.label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NuvioTheme.extendedColors.textTertiary,
                    maxLines = 1
                )
                Spacer(Modifier.width(24.dp))
                Text(
                    text = awards.value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = NuvioTheme.colors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * [fork] round 39 detail_info_polish: the ledger with one rule for every line. Two columns; in each, the label sits
 * in a fixed-width column (muted) and the value starts at the same place on every line (left aligned), hairlines
 * above every line and one closing the block, an odd last line keeps its half. Awards spans both columns and wraps
 * to two lines instead of being cut.
 */
@Composable
private fun NuvioCInfoPolished(rows: List<NuvioCInfoRow>, margin: androidx.compose.ui.unit.Dp) {
    val hairline = NuvioTheme.colors.TextPrimary.copy(alpha = 0.08f)
    val awards = rows.firstOrNull { it.label == R.string.nuvio_c_info_awards }
    val paired = (if (awards != null) rows - awards else rows).sortedBy { NUVIO_C_INFO_ORDER.indexOf(it.label).let { i -> if (i < 0) 99 else i } }
    val labelWidth = 132.dp
    @Composable fun valueOf(row: NuvioCInfoRow): String =
        if (row.value.startsWith("\u0000")) row.value.drop(1).toIntOrNull()?.let { stringResource(it) }.orEmpty() else row.value
    @Composable fun Line(row: NuvioCInfoRow, modifier: Modifier, maxLines: Int = 1) {
        Row(modifier.padding(vertical = 12.dp), verticalAlignment = androidx.compose.ui.Alignment.Top) {
            Text(
                text = stringResource(row.label),
                style = MaterialTheme.typography.bodyMedium,
                color = NuvioTheme.extendedColors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(labelWidth)
            )
            Text(
                text = valueOf(row),
                style = MaterialTheme.typography.bodyMedium,
                color = NuvioTheme.colors.TextPrimary,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = margin)) {
        paired.chunked(2).forEach { line ->
            androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(1.dp).background(hairline))
            Row(horizontalArrangement = Arrangement.spacedBy(48.dp), modifier = Modifier.fillMaxWidth()) {
                line.forEach { row -> Line(row, Modifier.weight(1f)) }
                if (line.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (awards != null) {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(1.dp).background(hairline))
            Line(awards, Modifier.fillMaxWidth(), maxLines = 2)
        }
        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(1.dp).background(hairline))
    }
}

/** [fork] round 39 detail_info_polish: one order for films and shows (dates, length, money, status, language). */
private val NUVIO_C_INFO_ORDER = listOf(
    R.string.nuvio_c_info_theatrical, R.string.nuvio_c_info_digital, R.string.nuvio_c_info_first_aired, R.string.nuvio_c_info_airs,
    R.string.nuvio_c_info_runtime, R.string.nuvio_c_info_seasons, R.string.nuvio_c_info_status,
    R.string.nuvio_c_info_budget, R.string.nuvio_c_info_box_office, R.string.nuvio_c_info_language
)

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

/** [fork] round 32 detail_info_trim: rows the details page already shows elsewhere. */
private val NUVIO_C_INFO_TRIMMED = setOf(R.string.nuvio_c_info_runtime, R.string.nuvio_c_info_language)
