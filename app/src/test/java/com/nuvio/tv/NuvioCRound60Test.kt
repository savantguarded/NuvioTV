package com.nuvio.tv

import com.nuvio.tv.data.quality.CachedKind
import com.nuvio.tv.data.quality.CachedQuality
import com.nuvio.tv.data.quality.QualityEpisode
import com.nuvio.tv.data.quality.QualityLineState
import com.nuvio.tv.data.quality.QualityLookup
import com.nuvio.tv.data.quality.QualityPlan
import com.nuvio.tv.data.quality.QualityResolution
import com.nuvio.tv.data.quality.QualityStreamText
import com.nuvio.tv.data.quality.QualityTarget
import com.nuvio.tv.data.quality.QualityTitle
import com.nuvio.tv.data.quality.TitleQuality
import com.nuvio.tv.data.quality.TitleQualityParser
import com.nuvio.tv.data.quality.TitleQualityPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Round 60 `detail_quality_line`: Torrentio answers saved 2026-11-22 (trimmed), expected badges from the spec. */
class NuvioCRound60Test {
    private fun fixture(name: String): List<QualityStreamText> {
        val body = javaClass.classLoader!!.getResource("quality/$name.json")!!.readText()
        return TitleQualityParser.streamsOf(body)
    }

    private fun best(name: String, year: Int?) = TitleQualityParser.best(fixture(name), year)?.labels

    @Test fun specTableMovies() {
        assertEquals(listOf("4K", "DOLBY VISION", "ATMOS", "REMUX"), best("dune", 2024))
        assertEquals(listOf("4K", "DOLBY VISION", "ATMOS", "REMUX"), best("pirates", 2003))
        assertEquals(listOf("4K", "DOLBY VISION", "ATMOS", "WEB-DL"), best("possible_love", 2026))
        assertEquals(listOf("1080P", "WEB-DL"), best("viva_riva", 2010))
        assertEquals(listOf("1080P", "WEB-DL"), best("see_everything", 2026))
    }

    @Test fun specTableEpisodes() {
        assertEquals(listOf("1080P", "DOLBY DIGITAL", "BLURAY"), best("bleach_1_8", null))
        assertEquals(listOf("720P", "WEB-DL"), best("zeta_2_2", null))
        // a healthy (5+ seeders) release beats a 2-seeder Atmos one
        assertEquals(listOf("1080P", "WEB-DL"), best("loveisblind_3_4", null))
    }

    @Test fun fourKCorroboration() {
        listOf("dune" to 2024, "matrix" to 1999, "roma" to 2018, "possible_love" to 2026).forEach { (n, y) ->
            assertEquals(n, QualityResolution.UHD, TitleQualityParser.best(fixture(n), y)?.resolution)
        }
        listOf("fleabag_1_1", "sherlock_1_1").forEach { n ->
            assertEquals(n, QualityResolution.UHD, TitleQualityParser.best(fixture(n), null)?.resolution)
        }
        listOf("amelie" to 2001, "come_and_see" to 1985, "city_of_god" to 2002).forEach { (n, y) ->
            assertEquals(n, QualityResolution.FHD, TitleQualityParser.best(fixture(n), y)?.resolution)
        }
        listOf("wire_1_1", "casa_1_1").forEach { n ->
            assertEquals(n, QualityResolution.FHD, TitleQualityParser.best(fixture(n), null)?.resolution)
        }
    }

    @Test fun bogusIdsGiveNothing() {
        assertNull(best("bogus1", 2024))
        assertNull(best("bogus2", 2024))
    }

    private fun s(title: String, seeders: Int, name: String = "Torrentio") = QualityStreamText(name, "$title\n👤 $seeders 💾 2 GB ⚙️ X", null)

    @Test fun filtersAndOneRelease() {
        // CAM / TS dropped, zero seeders dropped
        assertNull(TitleQualityParser.best(listOf(s("Film.2024.HDCAM.1080p", 90), s("Film.2024.TS.720p", 50), s("Film.2024.1080p.WEB-DL", 0)), 2024))
        // 8K counts as 4K (two healthy releases), never 8K
        val eight = TitleQualityParser.best(listOf(s("Film.2024.4320p.8K.WEB-DL", 30), s("Film.2024.2160p.WEB-DL", 20)), 2024)
        assertEquals("4K", eight?.labels?.first())
        // one lone 4K release is not enough: best 1080p instead
        assertEquals(listOf("1080P", "BLURAY"), TitleQualityParser.best(listOf(s("Film.2024.2160p.DV.Atmos.WEB-DL", 40), s("Film.2024.1080p.BluRay.x264", 12)), 2024)?.labels)
        // tags come from one release only: DV from one, Atmos from another never combine
        val one = TitleQualityParser.best(listOf(s("Film.2024.2160p.DV.WEB-DL.DDP5.1", 30), s("Film.2024.2160p.HDR.WEB-DL.Atmos", 30)), 2024)
        assertEquals(listOf("4K", "DOLBY VISION", "DOLBY DIGITAL PLUS", "WEB-DL"), one?.labels)
        // low quality still shows
        assertEquals(listOf("480P", "DVD"), TitleQualityParser.best(listOf(s("Old.Show.S01E01.DVDRip.XviD", 3)), null)?.labels)
    }

    @Test fun qualiCacheTokens() {
        assertEquals(listOf("4K", "DOLBY VISION", "ATMOS", "REMUX"), TitleQualityParser.fromTokens(listOf("4K", "REMUX", "DV", "ATMOS"))?.labels)
        assertEquals("4K", TitleQualityParser.fromTokens(listOf("8K", "REMUX"))?.labels?.first())
        assertEquals(listOf("1080P", "BLURAY"), TitleQualityParser.fromTokens(listOf("1080P", "BLURAY"))?.labels)
        assertNull(TitleQualityParser.fromTokens(listOf("WEBDL")))
    }

    private val today = LocalDate.of(2026, 11, 22)

    @Test fun planStates() {
        assertEquals(QualityPlan.NoLine, TitleQualityPlanner.plan(QualityTitle("kitsu:1", null, true, null), today))
        assertTrue(TitleQualityPlanner.plan(QualityTitle("tmdb:550", null, false, null), today) is QualityPlan.NeedsImdb)
        assertEquals(QualityPlan.NotYetReleased, TitleQualityPlanner.plan(QualityTitle("tt1", null, false, "2027-03-01T00:00:00.000Z"), today))
        val movie = TitleQualityPlanner.plan(QualityTitle("tt15239678", null, false, "2024-02-27"), today) as QualityPlan.Lookup
        assertEquals(listOf(QualityTarget("tt15239678")), movie.targets)
        assertEquals(2024, movie.year)
        val eps = listOf(
            QualityEpisode(1, 1, "2025-01-01"), QualityEpisode(1, 2, "2025-01-08"),
            QualityEpisode(2, 1, "2026-11-01"), QualityEpisode(2, 2, "2026-12-01"), QualityEpisode(0, 1, "2026-11-20")
        )
        val show = TitleQualityPlanner.plan(QualityTitle("tt99", null, true, null, eps), today) as QualityPlan.Lookup
        assertEquals(listOf(QualityTarget("tt99", 2, 1), QualityTarget("tt99", 1, 2)), show.targets) // latest aired, then the one before
        assertEquals(QualityPlan.NotYetReleased, TitleQualityPlanner.plan(QualityTitle("tt98", null, true, null, listOf(QualityEpisode(1, 1, "2027-01-01"))), today))
    }

    @Test fun emptyLabels() {
        val nothing = QualityLookup.NothingUsable
        assertEquals(QualityLineState.NOT_YET_ON_DIGITAL, TitleQualityPlanner.emptyLabel(nothing, false, "2026-10-30", null, today))
        assertEquals(QualityLineState.NOT_YET_ON_DIGITAL, TitleQualityPlanner.emptyLabel(nothing, false, "2026-10-30", "2026-12-15", today))
        assertEquals(QualityLineState.UNAVAILABLE, TitleQualityPlanner.emptyLabel(nothing, false, "2026-10-30", "2026-11-10", today))
        assertEquals(QualityLineState.UNAVAILABLE, TitleQualityPlanner.emptyLabel(QualityLookup.Failed, false, "2026-10-30", null, today))
        assertEquals(QualityLineState.UNAVAILABLE, TitleQualityPlanner.emptyLabel(nothing, false, "1998-05-01", null, today)) // old title
        assertEquals(QualityLineState.UNAVAILABLE, TitleQualityPlanner.emptyLabel(nothing, true, "2026-10-30", null, today))
    }

    @Test fun cacheKeepsCompactResultAndExpiry() {
        val now = 1_000_000L
        val c = CachedQuality(CachedKind.FOUND, TitleQuality(QualityResolution.UHD), now, now + 5)
        assertEquals(c, CachedQuality.decode(c.encode()))
        assertFalse(c.fresh(now + 5))
        assertNull(CachedQuality.decode("FOUND||||||1|2"))
        val day = TitleQualityPlanner.DAY_MS
        // round 66 quality_refresh_v66: a new release is checked again after 12 h
        assertEquals(now + if (NuvioCFeatures.QUALITY_REFRESH_V66) day / 2 else day, TitleQualityPlanner.expiry(now, today.minusDays(10), today, null))
        assertEquals(now + 7 * day, TitleQualityPlanner.expiry(now, today.minusDays(400), today, null))
        assertEquals(now + 99, TitleQualityPlanner.expiry(now, null, today, now + 99))
    }

    // ---- round 60d: streaming reader and early stop ----
    private val all = listOf("dune", "pirates", "possible_love", "bleach_1_8", "zeta_2_2", "loveisblind_3_4", "viva_riva",
        "see_everything", "matrix", "roma", "fleabag_1_1", "sherlock_1_1", "amelie", "wire_1_1", "come_and_see", "casa_1_1",
        "city_of_god", "bogus1", "bogus2")
    private val years = mapOf("dune" to 2024, "pirates" to 2003, "possible_love" to 2026, "viva_riva" to 2010, "see_everything" to 2026,
        "matrix" to 1999, "roma" to 2018, "amelie" to 2001, "come_and_see" to 1985, "city_of_god" to 2002, "bogus1" to 2024, "bogus2" to 2024)
    private fun body(name: String) = javaClass.classLoader!!.getResource("quality/$name.json")!!.readText()

    @Test fun streamingReaderMatchesTreeParse() {
        all.forEach { n ->
            assertEquals(n, TitleQualityParser.streamsOf(body(n)), TitleQualityParser.streamsFrom(java.io.StringReader(body(n))).toList())
        }
    }

    @Test fun earlyStopPicksTheSameRelease() {
        all.forEach { n ->
            val full = TitleQualityParser.best(TitleQualityParser.streamsOf(body(n)), years[n])
            val early = TitleQualityParser.bestEarly(TitleQualityParser.streamsFrom(java.io.StringReader(body(n))), years[n])
            assertEquals(n, full, early.quality)
        }
        // big titles stop well before the end of the list
        val dune = TitleQualityParser.bestEarly(TitleQualityParser.streamsFrom(java.io.StringReader(body("dune"))), 2024)
        assertTrue("dune read ${dune.read}", dune.read < TitleQualityParser.streamsOf(body("dune")).size)
    }

    @Test fun torrentioTiers() {
        assertEquals(5, TitleQualityParser.torrentioTier("Torrentio\n4k DV | HDR"))
        assertEquals(3, TitleQualityParser.torrentioTier("Torrentio\n1080p 3D SBS"))
        assertEquals(0, TitleQualityParser.torrentioTier("Torrentio\nWEB-DL"))
    }
}
