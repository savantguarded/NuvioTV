package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import org.junit.Assert.assertEquals
import org.junit.Test

class NuvioCHeroPoolTest {
    private fun preview(id: String) = MetaPreview(
        id = id, type = ContentType.MOVIE, name = id, poster = null, posterShape = PosterShape.POSTER,
        background = null, logo = null, description = null, releaseInfo = null, imdbRating = null,
        genres = emptyList()
    )

    @Test
    fun poolIsBiggerThanOfficial() {
        assertEquals(18, NuvioCHeroPool.size)
    }

    @Test
    fun titlesAlreadyShownKeepTheirPlace() {
        val items = (1..10).map { preview("tt$it") }
        val first = NuvioCHeroPool.order(items, emptyList())
        val again = NuvioCHeroPool.order(items.reversed() + preview("tt99"), first.map { it.id })
        assertEquals(first.map { it.id }, again.take(10).map { it.id })
        assertEquals("tt99", again.last().id)
    }

    @Test
    fun orderIsAPermutation() {
        val items = (1..18).map { preview("id$it") }
        assertEquals(items.map { it.id }.toSet(), NuvioCHeroPool.order(items, emptyList()).map { it.id }.toSet())
    }
}
