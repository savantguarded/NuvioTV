package com.nuvio.tv

import com.nuvio.tv.core.nuvioc.NuvioCCloudSettings
import com.nuvio.tv.data.nuvioc.NuvioCTasteCard
import com.nuvio.tv.data.nuvioc.NuvioCTasteTitle
import com.nuvio.tv.data.nuvioc.computeTasteCardPage
import com.nuvio.tv.domain.model.Video
import com.nuvio.tv.domain.model.WatchedItem
import com.nuvio.tv.ui.components.nuvioCReelWindow
import com.nuvio.tv.ui.screens.detail.nuvioCGraphModel
import com.nuvio.tv.ui.screens.detail.nuvioCGraphWindow
import com.nuvio.tv.ui.screens.player.NuvioCStatsRow
import com.nuvio.tv.ui.screens.player.NuvioCStatsSection
import com.nuvio.tv.ui.screens.player.nuvioCStatsTemplate
import com.nuvio.tv.ui.screens.player.nuvioCSteadySections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Round 65: series graph model, stream info rows, sync keys, reel window, Taste card pages. */
class NuvioCRound65Test {
    private fun ep(s: Int, e: Int, released: String? = "2018-01-01") =
        Video(id = "tt1:$s:$e", title = "Ep $e", released = released, thumbnail = null, season = s, episode = e, overview = null)

    @Test fun graphAveragesAndBestLowest() {
        val videos = listOf(ep(0, 1), ep(1, 1), ep(1, 2), ep(1, 3), ep(2, 1, "2019-05-01"), ep(2, 2, "2019-05-08"))
        val m = nuvioCGraphModel(videos, mapOf((1 to 1) to 8.0, (1 to 2) to 9.0, (2 to 1) to 6.0, (2 to 2) to 7.0))
        assertEquals(listOf(1, 2), m.seasons.map { it.number }) // specials left out
        assertEquals(5, m.episodeCount)
        assertEquals(8.5, m.seasons[0].average!!, 1e-9) // unrated E3 not counted
        assertEquals(6.5, m.seasons[1].average!!, 1e-9)
        assertEquals(7.5, m.average!!, 1e-9)
        assertEquals(1 to 2, m.best!!.season to m.best!!.episode)
        assertEquals(2 to 1, m.lowest!!.season to m.lowest!!.episode)
        assertEquals("2019", m.seasons[1].year)
        assertEquals(3, m.maxEpisodes)
    }

    @Test fun graphWindowScrollsOneAtATime() {
        assertEquals(0, nuvioCGraphWindow(0, 6, 7, 34))
        assertEquals(1, nuvioCGraphWindow(0, 7, 7, 34))
        assertEquals(27, nuvioCGraphWindow(27, 33, 7, 34))
        assertEquals(26, nuvioCGraphWindow(27, 26, 7, 34))
        assertEquals(0, nuvioCGraphWindow(0, 3, 7, 4)) // fewer seasons than the window
    }

    @Test fun statsRowsFixedFromTheStart() {
        val first = nuvioCSteadySections(listOf(NuvioCStatsSection("SYSTEM", listOf(NuvioCStatsRow("CPU", "12%")))), isMpv = false, full = true)
        val later = nuvioCSteadySections(listOf(
            NuvioCStatsSection("SYSTEM", listOf(NuvioCStatsRow("CPU", "40%"), NuvioCStatsRow("Thermal", "Hot"))),
            NuvioCStatsSection("VIDEO", listOf(NuvioCStatsRow("Dropped", "3")))
        ), isMpv = false, full = true)
        assertEquals(first.map { s -> s.header to s.rows.map { it.label } }, later.map { s -> s.header to s.rows.map { it.label } })
        val system = first.first { it.header == "SYSTEM" }.rows.associate { it.label to it.value }
        assertEquals("OK", system["Thermal"])
        assertEquals("OK", system["Paging"])
        assertEquals("—", first.first { it.header == "VIDEO" }.rows.first { it.label == "Dropped" }.value)
        assertTrue("Cache" !in nuvioCStatsTemplate(false, true).flatMap { it.second })
        assertTrue("Cache" in nuvioCStatsTemplate(true, true).flatMap { it.second })
    }

    @Test fun rouletteCatalogsRoundTrip() {
        assertEquals("auto", NuvioCCloudSettings.encodeRouletteCatalogs(null))
        assertNull(NuvioCCloudSettings.decodeRouletteCatalogs("auto"))
        val keys = setOf("addon|movie|top", "addon|series|new")
        assertEquals(keys, NuvioCCloudSettings.decodeRouletteCatalogs(NuvioCCloudSettings.encodeRouletteCatalogs(keys)))
        assertEquals(emptySet<String>(), NuvioCCloudSettings.decodeRouletteCatalogs(NuvioCCloudSettings.encodeRouletteCatalogs(emptySet())))
    }

    @Test fun reelWindowLoops() {
        assertEquals(listOf("b", "c", "a", "b"), nuvioCReelWindow(listOf("a", "b", "c"), 1, 4))
        assertEquals(listOf("c", "a"), nuvioCReelWindow(listOf("a", "b", "c"), -1, 2))
    }

    private val zone = ZoneId.of("UTC")
    private fun at(d: String, hour: Int = 21) = LocalDate.parse(d).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun tasteGenresAndTimeline() {
        val titles = mapOf(
            "movie:tt1" to NuvioCTasteTitle(key = "movie:tt1", isShow = false, tmdbId = 1, imdbId = "tt1", name = "Film A", year = 2024, genres = listOf("Drama", "Thriller"), runtime = 120),
            "movie:tt2" to NuvioCTasteTitle(key = "movie:tt2", isShow = false, tmdbId = 2, imdbId = "tt2", name = "Film B", year = 1999, genres = listOf("Drama"), runtime = 90),
            "tv:tt3" to NuvioCTasteTitle(key = "tv:tt3", isShow = true, tmdbId = 3, imdbId = "tt3", name = "Show C", year = 2020, genres = listOf("Comedy"), runtime = 30)
        )
        val history = listOf(
            WatchedItem("tt1", "movie", "Film A", watchedAt = at("2026-11-03")),
            WatchedItem("tt2", "movie", "Film B", watchedAt = at("2026-11-10")),
            WatchedItem("tt3", "series", "Show C", season = 1, episode = 1, watchedAt = at("2026-11-10", 22)),
            WatchedItem("tt3", "series", "Show C", season = 1, episode = 2, watchedAt = at("2026-11-10", 23)),
            WatchedItem("tt1", "movie", "Film A", watchedAt = at("2026-10-01")) // outside the month
        )
        val month = at("2026-11-01", 0)..(at("2026-12-01", 0) - 1)
        val genres = computeTasteCardPage(NuvioCTasteCard.GENRES, "Comedy", history, titles, emptyMap(), month, zone, at("2026-11-25"))
        assertEquals("Drama", genres.groups.first().label)
        assertEquals(2, genres.groups.first().items.size)
        assertEquals("Film B", genres.groups.first().items.first().name) // newest first
        assertEquals(genres.groups.indexOfFirst { it.label == "Comedy" }, genres.focusGroup)

        val time = computeTasteCardPage(NuvioCTasteCard.BIGGEST_DAY, null, history, titles, emptyMap(), month, zone, at("2026-11-25"))
        assertEquals("BY DAY", time.barsTitle)
        assertEquals(25, time.bars.size) // 1..25 Nov, up to today
        assertEquals(9, time.focusBar) // 10 Nov: 90 + 60 min
        assertEquals(150, time.bars[9].minutes)
        assertEquals("2 episodes · S1 E1–E2", time.bars[9].items.first { it.name == "Show C" }.sub)

        val vs = computeTasteCardPage(NuvioCTasteCard.VS_TMDB, null, history, titles.mapValues { it.value.copy(vote = 7.0) }, mapOf("movie:tt1" to 9), month, zone)
        assertEquals(1, vs.ranked.size)
        assertEquals(9, vs.ranked.first().you)
    }
}
