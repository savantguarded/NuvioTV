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
}
