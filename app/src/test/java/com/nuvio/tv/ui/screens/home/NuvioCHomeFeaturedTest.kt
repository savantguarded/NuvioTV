package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.domain.model.WatchProgress
import com.nuvio.tv.ui.components.NuvioCHeroAutoAdvance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NuvioCHomeFeaturedTest {

    private fun preview(id: String, imdb: String? = null) = MetaPreview(
        id = id,
        type = ContentType.SERIES,
        name = id,
        poster = null,
        posterShape = PosterShape.POSTER,
        background = null,
        logo = null,
        description = null,
        releaseInfo = "2022–",
        imdbRating = null,
        genres = emptyList(),
        imdbId = imdb
    )

    private fun inProgress(contentId: String, season: Int?, episode: Int?) = ContinueWatchingItem.InProgress(
        progress = WatchProgress(
            contentId = contentId,
            contentType = if (season == null) "movie" else "series",
            name = "x",
            poster = null,
            backdrop = null,
            logo = null,
            videoId = contentId,
            season = season,
            episode = episode,
            episodeTitle = null,
            position = 60_000L,
            duration = 600_000L,
            lastWatched = 100L
        )
    )

    @Test
    fun `continue watching matched by id or imdb id`() {
        val cw = listOf(inProgress("tt1", 2, 4), inProgress("tt2", null, null))
        assertEquals("tt1", NuvioCFeaturedRules.continueItemFor(listOf("tmdb:9", "tt1"), cw)?.let(NuvioCFeaturedRules::contentIdOf))
        assertNull(NuvioCFeaturedRules.continueItemFor(listOf("tmdb:9", null), cw))
        assertNull(NuvioCFeaturedRules.continueItemFor(listOf("tt1"), emptyList()))
    }

    @Test
    fun `play label`() {
        val series = NuvioCFeaturedRules.labelFor(inProgress("tt1", 2, 4))
        assertTrue(series.resume); assertEquals(2, series.season); assertEquals(4, series.episode)
        val movie = NuvioCFeaturedRules.labelFor(inProgress("tt2", null, null))
        assertTrue(movie.resume); assertNull(movie.season)
        assertFalse(NuvioCFeaturedRules.labelFor(null).resume)
    }

    @Test
    fun `paging wraps both ways`() {
        assertEquals(0, NuvioCFeaturedRules.nextIndex(2, 3))
        assertEquals(2, NuvioCFeaturedRules.previousIndex(0, 3))
        assertEquals(0, NuvioCFeaturedRules.nextIndex(0, 0))
    }

    @Test
    fun `same title kept when the list is rebuilt`() {
        val items = listOf(preview("a"), preview("b"), preview("c"))
        assertEquals(2, NuvioCHeroAutoAdvance.indexAfterListChange(items, "c", 0))
        assertEquals(1, NuvioCHeroAutoAdvance.indexAfterListChange(items, "gone", 1))
        assertEquals(2, NuvioCHeroAutoAdvance.indexAfterListChange(items, null, 7))
        assertEquals(0, NuvioCHeroAutoAdvance.indexAfterListChange(emptyList(), "a", 3))
    }

    @Test
    fun `year from release info`() {
        assertEquals("2022", NuvioCFeaturedRules.year("2022–"))
        assertNull(NuvioCFeaturedRules.year(null))
    }
}
