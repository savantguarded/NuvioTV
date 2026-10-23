package com.nuvio.tv.ui.screens.library

// [fork] library_poster_fallback: posters for Library items whose source (MDBList) gave none.

import android.content.Context
import android.util.Log
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.data.mdblist.nuvioCLibraryPosterFallback
import com.nuvio.tv.domain.model.LibraryEntry
import com.nuvio.tv.domain.repository.MetaRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NuvioCLibraryPostersEntryPoint {
    fun tmdbService(): TmdbService
}

/**
 * Finds a poster for each Library item shown without one, one at a time: the poster by IMDb id (the IMDb id from TMDB
 * when the item only has a TMDB id), else the add-on's own poster. Each item is looked up once per screen.
 */
internal class NuvioCLibraryPosters(
    private val context: Context,
    private val metaRepository: MetaRepository,
    private val scope: CoroutineScope
) {
    private val _found = MutableStateFlow<Map<String, String>>(emptyMap())
    val found: StateFlow<Map<String, String>> = _found
    private val asked = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val oneAtATime = Mutex()
    private val tmdb: TmdbService? by lazy {
        runCatching { EntryPointAccessors.fromApplication(context, NuvioCLibraryPostersEntryPoint::class.java).tmdbService() }.getOrNull()
    }

    /** Items with a found poster get it; the others are returned as they are. */
    fun apply(items: List<LibraryEntry>): List<LibraryEntry> {
        if (!com.nuvio.tv.NuvioCFeatures.LIBRARY_POSTER_FALLBACK) return items
        val map = _found.value
        if (map.isEmpty()) return items
        return items.map { entry ->
            if (!entry.poster.isNullOrBlank()) entry
            else map[key(entry)]?.let { entry.copy(poster = it, rawPosterUrl = entry.rawPosterUrl ?: it) } ?: entry
        }
    }

    /** Starts lookups for the items still without a poster; [onFound] runs after each one found. */
    fun request(items: List<LibraryEntry>, onFound: () -> Unit) {
        if (!com.nuvio.tv.NuvioCFeatures.LIBRARY_POSTER_FALLBACK) return
        val missing = items.filter { it.poster.isNullOrBlank() && asked.add(key(it)) }
        if (missing.isEmpty()) return
        scope.launch {
            oneAtATime.withLock {
                missing.forEach { entry ->
                    val url = try {
                        lookup(entry)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Log.w(TAG, "No poster for ${entry.name}: ${error.javaClass.simpleName}")
                        null
                    }
                    if (url != null) {
                        _found.update { it + (key(entry) to url) }
                        onFound()
                    }
                }
            }
        }
    }

    private suspend fun lookup(entry: LibraryEntry): String? {
        val type = if (entry.type.equals("movie", ignoreCase = true)) "movie" else "series"
        val imdb = entry.imdbId?.takeIf { it.startsWith("tt") }
            ?: entry.id.takeIf { it.startsWith("tt") }
            ?: entry.tmdbId?.let { id -> withTimeoutOrNull(LOOKUP_MS) { tmdb?.tmdbToImdb(id, type) } }?.takeIf { it.startsWith("tt") }
        nuvioCLibraryPosterFallback(imdb)?.let { return it }
        val result = withTimeoutOrNull(LOOKUP_MS) {
            metaRepository.getMetaFromAllAddons(type, entry.id).first { it !is NetworkResult.Loading }
        }
        return (result as? NetworkResult.Success)?.data?.poster?.takeIf { it.isNotBlank() }
    }

    private fun key(entry: LibraryEntry) = "${entry.type}:${entry.id}"

    private companion object {
        const val TAG = "NuvioCLibraryPosters"
        const val LOOKUP_MS = 8_000L
    }
}
