package com.nuvio.tv.data.nuvioc

import org.junit.Assert.assertEquals
import org.junit.Test

class NuvioCRatingPayloadTest {
    @Test
    fun movieRating() {
        assertEquals(
            """{"movies":[{"ids":{"imdb":"tt0111161","tmdb":278},"rating":9}]}""",
            NuvioCRatingService.payload(NuvioCRatingTarget(false, "tt0111161", 278), 9)
        )
    }

    @Test
    fun showRemoveHasNoRating() {
        assertEquals(
            """{"shows":[{"ids":{"tmdb":1399}}]}""",
            NuvioCRatingService.payload(NuvioCRatingTarget(true, null, 1399), null)
        )
    }

    @Test
    fun traktRatingsParsedByImdbAndTmdb() {
        val body = """[
          {"rated_at":"2026-01-01T00:00:00.000Z","rating":8,"type":"movie","movie":{"title":"A","ids":{"trakt":1,"imdb":"tt0111161","tmdb":278}}},
          {"rated_at":"2026-01-01T00:00:00.000Z","rating":6,"type":"show","show":{"title":"B","ids":{"trakt":2,"imdb":null,"tmdb":1399}}},
          {"rated_at":"2026-01-01T00:00:00.000Z","rating":0,"type":"movie","movie":{"ids":{"imdb":"tt1"}}}
        ]"""
        val map = NuvioCRatingService.parseTraktRatings(body)
        assertEquals(8, map["movie:tt0111161"])
        assertEquals(8, map["movie:tmdb278"])
        assertEquals(6, map["show:tmdb1399"])
        assertEquals(3, map.size)
    }

    @Test
    fun targetKeysCoverBothIds() {
        assertEquals(listOf("movie:tt0111161", "movie:tmdb278"), NuvioCRatingTarget(false, "tt0111161", 278).keys())
        assertEquals(listOf("show:tmdb1399"), NuvioCRatingTarget(true, null, 1399).keys())
    }
}
