package com.nuvio.tv.data.nuvioc

import android.content.Context
import android.util.Log
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.R
import com.nuvio.tv.core.trakt.traktBestBackdropUrl
import com.nuvio.tv.core.trakt.traktBestLogoUrl
import com.nuvio.tv.core.trakt.traktBestPosterUrl
import com.nuvio.tv.data.mdblist.MdbListItemType
import com.nuvio.tv.data.mdblist.MdbListSyncRepository
import com.nuvio.tv.data.mdblist.MdbListWatchedRecord
import com.nuvio.tv.data.remote.api.TraktApi
import com.nuvio.tv.data.repository.TraktAuthService
import com.nuvio.tv.data.repository.normalizeContentId
import com.nuvio.tv.domain.model.LibraryEntry
import com.nuvio.tv.domain.model.LibraryListTab
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// [fork] Nuvio C library_watched (2026-10-14): a read-only "Watched" tab in Library for Trakt and MDBList,
// newest first, with the usual Movies / Series filter (the type tabs work off the entries' type).
// Simkl already has the same thing as its "Completed" status tab, renamed "Watched" for consistency.
// - Trakt: /sync/watched/movies and /sync/watched/shows (full, images, no seasons), fetched when the
//   Library's items are first observed and again after 15 min or a Library refresh. In memory only.
// - MDBList: no extra request: official's sync already keeps every watched record locally
//   (MdbListSyncRepository); movies and shows (from their episodes) are projected from it.
// The tab is not a "save to" list (isMembershipDestination = false) and the providers don't claim its
// key, so the add-to-library dialogs never offer it. Hooks: one line in each provider's items / tabs.

private const val TAG = "NuvioCWatched"
const val NUVIO_C_TRAKT_WATCHED_KEY = "nuvioc:watched:trakt"
const val NUVIO_C_MDBLIST_WATCHED_KEY = "nuvioc:watched:mdblist"
private const val TRAKT_TTL_MS = 15 * 60 * 1000L

@Singleton
class NuvioCWatchedLibrary @Inject constructor(
    @ApplicationContext private val context: Context,
    private val traktApi: TraktApi,
    private val traktAuthService: TraktAuthService,
    private val mdbListSync: MdbListSyncRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val traktWatched = MutableStateFlow<List<LibraryEntry>>(emptyList())
    private val traktMutex = Mutex()
    @Volatile private var traktFetchedAt = 0L

    private val enabled get() = NuvioCFeatures.LIBRARY_WATCHED

    private fun tab(key: String, providerStorageId: String) = LibraryListTab(
        key = key,
        title = context.getString(R.string.nuvio_c_library_watched),
        type = LibraryListTab.Type.STATUS,
        trackingProviderId = providerStorageId,
        supportedContentTypes = setOf("movie", "series"),
        isMembershipDestination = false
    )

    // ---- Trakt ----

    fun traktItems(official: Flow<List<LibraryEntry>>): Flow<List<LibraryEntry>> {
        if (!enabled) return official
        return combine(official, traktWatched) { items, watched -> merge(items, watched, NUVIO_C_TRAKT_WATCHED_KEY) }
            .onStart { refreshTrakt(force = false) }
    }

    fun traktTabs(official: Flow<List<LibraryListTab>>, providerStorageId: String): Flow<List<LibraryListTab>> {
        if (!enabled) return official
        return official.map { tabs -> if (tabs.isEmpty()) tabs else tabs + tab(NUVIO_C_TRAKT_WATCHED_KEY, providerStorageId) }
    }

    fun refreshTrakt(force: Boolean) {
        if (!enabled) return
        if (!force && System.currentTimeMillis() - traktFetchedAt < TRAKT_TTL_MS) return
        scope.launch {
            traktMutex.withLock {
                if (!force && System.currentTimeMillis() - traktFetchedAt < TRAKT_TTL_MS) return@withLock
                runCatching { fetchTrakt() }
                    .onSuccess {
                        traktWatched.value = it
                        traktFetchedAt = System.currentTimeMillis()
                        Log.d(TAG, "Trakt watched: ${it.size} titles")
                    }
                    .onFailure { Log.w(TAG, "Trakt watched failed: ${it.message}") }
            }
        }
    }

    private suspend fun fetchTrakt(): List<LibraryEntry> = kotlinx.coroutines.coroutineScope {
        val movies = async {
            traktAuthService.executeAuthorizedRequest { auth ->
                traktApi.getWatched(auth, "movies", extended = "full,images")
            }?.takeIf { it.isSuccessful }?.body().orEmpty()
        }
        val shows = async {
            traktAuthService.executeAuthorizedRequest { auth ->
                traktApi.getWatchedShows(auth, extended = "full,images,noseasons")
            }?.takeIf { it.isSuccessful }?.body().orEmpty()
        }
        val out = ArrayList<Pair<LibraryEntry, Long>>()
        for (item in movies.await()) {
            val m = item.movie ?: continue
            val id = normalizeContentId(m.ids, fallback = m.ids?.trakt?.let { "trakt:$it" }).takeIf { it.isNotBlank() } ?: continue
            out += LibraryEntry(
                id = id, type = "movie", name = m.title ?: id,
                poster = m.images.traktBestPosterUrl(), background = m.images.traktBestBackdropUrl(),
                logo = m.images.traktBestLogoUrl(), description = m.overview?.takeIf { it.isNotBlank() },
                releaseInfo = m.year?.toString() ?: m.released?.take(4), imdbRating = m.rating?.toFloat(),
                genres = m.genres.orEmpty(), addonBaseUrl = null,
                imdbId = m.ids?.imdb?.takeIf { it.isNotBlank() }, tmdbId = m.ids?.tmdb, traktId = m.ids?.trakt,
                rawPosterUrl = m.images.traktBestPosterUrl(), trackingProviderId = "trakt"
            ) to millis(item.lastWatchedAt)
        }
        for (item in shows.await()) {
            val s = item.show ?: continue
            val id = normalizeContentId(s.ids, fallback = s.ids?.trakt?.let { "trakt:$it" }).takeIf { it.isNotBlank() } ?: continue
            out += LibraryEntry(
                id = id, type = "series", name = s.title ?: id,
                poster = s.images.traktBestPosterUrl(), background = s.images.traktBestBackdropUrl(),
                logo = s.images.traktBestLogoUrl(), description = s.overview?.takeIf { it.isNotBlank() },
                releaseInfo = s.year?.toString() ?: s.firstAired?.take(4), imdbRating = s.rating?.toFloat(),
                genres = s.genres.orEmpty(), addonBaseUrl = null,
                imdbId = s.ids?.imdb?.takeIf { it.isNotBlank() }, tmdbId = s.ids?.tmdb, traktId = s.ids?.trakt,
                rawPosterUrl = s.images.traktBestPosterUrl(), trackingProviderId = "trakt"
            ) to millis(item.lastWatchedAt)
        }
        ranked(out, NUVIO_C_TRAKT_WATCHED_KEY)
    }

    // ---- MDBList ----

    fun mdbListItems(official: Flow<List<LibraryEntry>>): Flow<List<LibraryEntry>> {
        if (!enabled) return official
        val watched = mdbListSync.state.map { state -> projectMdbList(state.snapshot?.watched.orEmpty()) }
        return combine(official, watched) { items, w -> merge(items, w, NUVIO_C_MDBLIST_WATCHED_KEY) }
    }

    fun mdbListTabs(official: Flow<List<LibraryListTab>>, providerStorageId: String): Flow<List<LibraryListTab>> {
        if (!enabled) return official
        return official.map { tabs -> if (tabs.isEmpty()) tabs else tabs + tab(NUVIO_C_MDBLIST_WATCHED_KEY, providerStorageId) }
    }

    private fun projectMdbList(records: List<MdbListWatchedRecord>): List<LibraryEntry> {
        if (records.isEmpty()) return emptyList()
        // One title per movie / show; a show's time is its latest watched episode.
        val latest = LinkedHashMap<String, Pair<MdbListWatchedRecord, Long>>()
        for (r in records) {
            val isMovie = r.type == MdbListItemType.MOVIE
            val key = (if (isMovie) "m:" else "s:") + runCatching { r.media.ids.key }.getOrNull()
            val at = millis(r.watchedAt)
            val prev = latest[key]
            if (prev == null || at > prev.second) latest[key] = r to at
        }
        val out = latest.values.mapNotNull { (r, at) ->
            val media = r.media
            val id = runCatching { media.ids.contentId }.getOrNull() ?: return@mapNotNull null
            val isMovie = r.type == MdbListItemType.MOVIE
            LibraryEntry(
                id = id, type = if (isMovie) "movie" else "series", name = media.title ?: id,
                poster = media.poster, background = media.backdrop, logo = null, description = null,
                releaseInfo = media.year?.toString(), imdbRating = null, genres = emptyList(), addonBaseUrl = null,
                imdbId = media.ids.imdb, tmdbId = media.ids.tmdb?.takeIf { it <= Int.MAX_VALUE }?.toInt(),
                traktId = media.ids.trakt?.takeIf { it <= Int.MAX_VALUE }?.toInt(),
                rawPosterUrl = media.poster, trackingProviderId = "mdblist",
                trackingProviderItemId = media.ids.mdblist
            ) to at
        }
        return ranked(out, NUVIO_C_MDBLIST_WATCHED_KEY)
    }

    internal companion object {
        /** Newest first: rank 0 = last watched; listedAt = when it was watched (for the date sorts). */
        fun ranked(items: List<Pair<LibraryEntry, Long>>, key: String): List<LibraryEntry> =
            items.sortedByDescending { it.second }.mapIndexed { i, (e, at) ->
                e.copy(listKeys = setOf(key), listRanks = mapOf(key to i), listedAt = at)
            }

        /**
         * Adds the watched tab's key to titles the official lists already hold (same id and type), so a
         * title on the watchlist and in the history stays one card; the rest are appended.
         */
        fun merge(official: List<LibraryEntry>, watched: List<LibraryEntry>, key: String): List<LibraryEntry> {
            if (watched.isEmpty()) return official
            val byKey = watched.associateBy { it.type + "|" + it.id }
            val used = HashSet<String>()
            val merged = official.map { e ->
                val w = byKey[e.type + "|" + e.id] ?: return@map e
                used += e.type + "|" + e.id
                e.copy(listKeys = e.listKeys + key, listRanks = e.listRanks + (key to (w.listRanks[key] ?: Int.MAX_VALUE)))
            }
            return merged + watched.filter { (it.type + "|" + it.id) !in used }
        }

        fun millis(iso: String?): Long =
            iso?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L
    }
}
