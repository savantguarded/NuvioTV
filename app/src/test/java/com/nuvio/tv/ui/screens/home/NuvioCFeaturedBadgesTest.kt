package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.core.di.NuvioCFeaturedInfo
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NuvioCFeaturedBadgesTest {
    private val today = LocalDate.parse("2026-10-17")

    private fun preview(type: ContentType, released: String? = null) = MetaPreview(
        id = "tt1", type = type, name = "x", poster = null, posterShape = PosterShape.POSTER,
        background = null, logo = null, description = null, releaseInfo = null, imdbRating = null,
        genres = emptyList(), released = released
    )

    @Test
    fun rankOnlyForRankedCatalogsUpTo20() {
        assertEquals(3, NuvioCFeaturedBadgeRules.rank(NuvioCHeroSource.Entry("Top 20 Movies", 3)))
        assertEquals(12, NuvioCFeaturedBadgeRules.rank(NuvioCHeroSource.Entry("Trending Shows", 12)))
        assertNull(NuvioCFeaturedBadgeRules.rank(NuvioCHeroSource.Entry("Popular", 21)))
        assertNull(NuvioCFeaturedBadgeRules.rank(NuvioCHeroSource.Entry("Because you watched Dune", 2)))
        assertNull(NuvioCFeaturedBadgeRules.rank(NuvioCHeroSource.Entry("Stop-motion", 1)))
        assertNull(NuvioCFeaturedBadgeRules.rank(null))
    }

    @Test
    fun movieNewWithin60Days() {
        val movie = preview(ContentType.MOVIE)
        assertEquals(NuvioCFeaturedBadgeRules.Status.NEW,
            NuvioCFeaturedBadgeRules.status(movie, NuvioCFeaturedInfo(releaseDate = "2026-09-01"), today))
        assertNull(NuvioCFeaturedBadgeRules.status(movie, NuvioCFeaturedInfo(releaseDate = "2026-06-01"), today))
        assertNull(NuvioCFeaturedBadgeRules.status(movie, NuvioCFeaturedInfo(releaseDate = "2026-11-01"), today))
        assertEquals(NuvioCFeaturedBadgeRules.Status.NEW,
            NuvioCFeaturedBadgeRules.status(preview(ContentType.MOVIE, "2026-10-10T00:00:00.000Z"), null, today))
    }

    @Test
    fun seriesNewSeasonOrNewShow() {
        val show = preview(ContentType.SERIES)
        assertEquals(NuvioCFeaturedBadgeRules.Status.NEW_SEASON,
            NuvioCFeaturedBadgeRules.status(show, NuvioCFeaturedInfo(latestSeason = 3, latestSeasonAirDate = "2026-10-01"), today))
        assertEquals(NuvioCFeaturedBadgeRules.Status.NEW,
            NuvioCFeaturedBadgeRules.status(show, NuvioCFeaturedInfo(latestSeason = 1, latestSeasonAirDate = "2026-09-20"), today))
        assertNull(NuvioCFeaturedBadgeRules.status(show, NuvioCFeaturedInfo(latestSeason = 3, latestSeasonAirDate = "2025-10-01"), today))
        assertNull(NuvioCFeaturedBadgeRules.status(show, null, today))
    }
}
