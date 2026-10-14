package com.nuvio.tv.data.nuvioc

import com.nuvio.tv.domain.model.LibraryEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class NuvioCWatchedLibraryTest {
    private fun e(id: String, type: String = "movie", keys: Set<String> = emptySet()) = LibraryEntry(
        id = id, type = type, name = id, poster = null, background = null, logo = null,
        description = null, releaseInfo = null, imdbRating = null, genres = emptyList(),
        addonBaseUrl = null, listKeys = keys
    )

    private val key = NUVIO_C_TRAKT_WATCHED_KEY

    @Test
    fun rankedNewestFirst() {
        val r = NuvioCWatchedLibrary.ranked(listOf(e("a") to 10L, e("b") to 30L, e("c") to 20L), key)
        assertEquals(listOf("b", "c", "a"), r.map { it.id })
        assertEquals(listOf(0, 1, 2), r.map { it.listRanks[key] })
        assertEquals(30L, r.first().listedAt)
        assertEquals(setOf(key), r.first().listKeys)
    }

    @Test
    fun mergeKeepsOneCardPerTitle() {
        val official = listOf(e("tt1", keys = setOf("watchlist")), e("tt2", keys = setOf("watchlist")))
        val watched = NuvioCWatchedLibrary.ranked(listOf(e("tt1") to 5L, e("tt3") to 9L), key)
        val merged = NuvioCWatchedLibrary.merge(official, watched, key)
        assertEquals(listOf("tt1", "tt2", "tt3"), merged.map { it.id })
        assertEquals(setOf("watchlist", key), merged[0].listKeys)
        assertEquals(1, merged[0].listRanks[key])
        assertEquals(setOf("watchlist"), merged[1].listKeys)
    }

    @Test
    fun mergeMatchesTypeToo() {
        val official = listOf(e("tt1", type = "series", keys = setOf("watchlist")))
        val watched = NuvioCWatchedLibrary.ranked(listOf(e("tt1", type = "movie") to 1L), key)
        assertEquals(2, NuvioCWatchedLibrary.merge(official, watched, key).size)
    }

    @Test
    fun millisParsesIsoAndToleratesJunk() {
        assertEquals(0L, NuvioCWatchedLibrary.millis("not a date"))
        assertEquals(0L, NuvioCWatchedLibrary.millis(null))
        assertEquals(1_000L, NuvioCWatchedLibrary.millis("1970-01-01T00:00:01Z"))
    }
}
