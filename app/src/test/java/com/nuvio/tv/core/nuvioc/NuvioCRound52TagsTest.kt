package com.nuvio.tv.core.nuvioc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** [fork] Nuvio C round 52 featured_tags_v2. */
class NuvioCRound52TagsTest {
    private val today = LocalDate.of(2026, 11, 19)
    private val zone = ZoneId.of("Africa/Lagos")
    private fun tag(f: NuvioCTagFacts) = NuvioCFeaturedTagRules.tag(f, today, zone, Locale.ENGLISH)

    @Test fun filmUpNextFirst() =
        assertEquals("UP NEXT · 42 MIN LEFT", tag(NuvioCTagFacts(false, resumeMinutesLeft = 42, digitalDate = today.minusDays(3))))

    @Test fun filmDigital() {
        assertEquals("NEW ON DIGITAL · 3 DAYS AGO", tag(NuvioCTagFacts(false, digitalDate = today.minusDays(3))))
        assertEquals("NEW ON DIGITAL · TODAY", tag(NuvioCTagFacts(false, digitalDate = today)))
        assertEquals("ON DIGITAL TOMORROW", tag(NuvioCTagFacts(false, digitalDate = today.plusDays(1))))
    }

    @Test fun filmCinema() =
        assertEquals(if (com.nuvio.tv.NuvioCFeatures.TAGS_NO_CINEMA) null else "IN CINEMAS NOW", tag(NuvioCTagFacts(false, cinemaDate = today.minusDays(20), digitalDate = today.plusDays(40))))

    @Test fun filmPersonal() {
        assertEquals("YOU RATED THIS 9/10 · WATCHED 2023", tag(NuvioCTagFacts(false, userRating = 9, watchedYear = 2023)))
        assertEquals("YOU'VE SEEN 2 OF 3 · DUNE COLLECTION", tag(NuvioCTagFacts(false, collectionSeen = Triple(2, 3, "Dune Collection"))))
        assertEquals("FROM DENIS VILLENEUVE · 5 WATCHED", tag(NuvioCTagFacts(false, director = "Denis Villeneuve" to 5)))
        assertNull(tag(NuvioCTagFacts(false, director = "Someone" to 2)))
        assertNull(tag(NuvioCTagFacts(false, collectionSeen = Triple(3, 3, "Done"))))
    }

    @Test fun filmAnniversary() =
        assertEquals("25 YEARS AGO THIS WEEK", tag(NuvioCTagFacts(false, releaseDate = LocalDate.of(2001, 11, 21))))

    @Test fun nothingSpecialNoTag() = assertNull(tag(NuvioCTagFacts(false, releaseDate = LocalDate.of(2019, 3, 2))))

    @Test fun showsPersonal() {
        assertEquals("3 NEW EPISODES SINCE YOU STOPPED", tag(NuvioCTagFacts(true, newSinceStopped = 3, upNext = 2 to 5)))
        assertEquals("UP NEXT · S2 E4", tag(NuvioCTagFacts(true, upNext = 2 to 4)))
        assertEquals("YOU RATED THIS 8/10", tag(NuvioCTagFacts(true, userRating = 8)))
        assertEquals("COMPLETE · 4 SEASONS", tag(NuvioCTagFacts(true, ended = true, seasons = 4)))
        // round 66b: a started, ended show still gets a true line
        assertEquals(if (com.nuvio.tv.NuvioCFeatures.TAGS_V66B) "COMPLETE SERIES · 4 SEASONS" else null, tag(NuvioCTagFacts(true, ended = true, started = true, seasons = 4)))
    }

    @Test fun airingBeatsPersonal() =
        assertEquals("NEW EPISODE TODAY", tag(NuvioCTagFacts(true, next = NuvioCTagEpisode(2, 5, today), upNext = 2 to 4)))
}
