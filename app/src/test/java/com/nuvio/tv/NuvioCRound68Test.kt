package com.nuvio.tv

import com.nuvio.tv.core.nuvioc.health.AddonHealth
import com.nuvio.tv.core.nuvioc.health.HealthSourceSetting
import com.nuvio.tv.core.nuvioc.health.HealthVerdict
import com.nuvio.tv.core.nuvioc.health.NuvioCHealthCombine
import com.nuvio.tv.core.nuvioc.health.NuvioCHealthRules
import com.nuvio.tv.core.nuvioc.health.ServiceGroup
import com.nuvio.tv.core.nuvioc.health.SourceReading
import com.nuvio.tv.core.nuvioc.health.SourceService
import com.nuvio.tv.core.nuvioc.health.SourceState
import com.nuvio.tv.core.nuvioc.health.TorBoxHealth
import com.nuvio.tv.core.nuvioc.health.TvCheck
import com.nuvio.tv.core.nuvioc.health.UsedService
import com.nuvio.tv.data.nuvioc.NuvioCTasteCard
import com.nuvio.tv.data.nuvioc.NuvioCTastePerson
import com.nuvio.tv.data.nuvioc.NuvioCTasteTitle
import com.nuvio.tv.data.nuvioc.computeTasteCardPage
import com.nuvio.tv.data.nuvioc.nuvioCHour12
import com.nuvio.tv.data.nuvioc.tasteFaceHint
import com.nuvio.tv.data.nuvioc.tasteScopeWords
import com.nuvio.tv.domain.model.WatchedItem
import com.nuvio.tv.ui.screens.addon.NuvioCHealthText
import com.nuvio.tv.ui.screens.calendar.NuvioCCalendar68
import com.nuvio.tv.ui.screens.player.NuvioCPlayerRatings
import com.nuvio.tv.ui.screens.player.nuvioCAboutRatingsGone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** Round 68: Service Health hold, texts and check order; OSD About; Taste faces and hours; Calendar side card. */
class NuvioCRound68Test {

    // ---- 1. hold and texts ------------------------------------------------------------------------------------------
    @Test fun holdOfFiveMinutes() {
        assertEquals(0L, NuvioCHealthRules.holdLeft(1_000L, null))
        assertEquals(5 * 60_000L, NuvioCHealthRules.holdLeft(10_000L, 10_000L))
        assertEquals(60_000L, NuvioCHealthRules.holdLeft(4 * 60_000L, 0L))
        assertEquals(0L, NuvioCHealthRules.holdLeft(5 * 60_000L, 0L))
        assertEquals(0L, NuvioCHealthRules.holdLeft(0L, 10 * 60_000L)) // clock went back: never held longer than 5 min
        assertEquals("Available in 5 min", NuvioCHealthText.availableIn(5 * 60_000L))
        assertEquals("Available in 4 min", NuvioCHealthText.availableIn(3 * 60_000L + 1))
        assertEquals("Available in 1 min", NuvioCHealthText.availableIn(1_000L))
        assertNull(NuvioCHealthText.availableIn(0L))
    }

    @Test fun agoTexts() {
        assertEquals("just now", NuvioCHealthText.ago(0L, 29_000L))
        assertEquals("30 seconds ago", NuvioCHealthText.ago(0L, 30_000L))
        assertEquals("1 minute ago", NuvioCHealthText.ago(0L, 60_000L))
        assertEquals("2 minutes ago", NuvioCHealthText.ago(0L, 125_000L))
        assertEquals("1 hour ago", NuvioCHealthText.ago(0L, 3_600_000L))
        assertEquals("3 hours ago", NuvioCHealthText.ago(0L, 3 * 3_600_000L + 5))
        assertEquals("Checked 2 minutes ago", NuvioCHealthText.checked(0L, 125_000L))
    }

    // ---- 2. check order ---------------------------------------------------------------------------------------------
    private val tv = HealthSourceSetting("https://one.example.com", true)
    private val two = HealthSourceSetting("https://two.example.com", true)
    private val off = HealthSourceSetting("https://off.example.com", false)

    @Test fun orderSaveLoadAndOldData() {
        // any number on, list order kept through save / load
        val raw = NuvioCHealthRules.encodeSources(listOf(two, tv, off))
        assertEquals(listOf(two, tv, off), NuvioCHealthRules.decodeSources(raw))
        // 67b data (one on) loads as-is
        assertEquals(listOf(true, false), NuvioCHealthRules.decodeSources("1|https://one.example.com\n0|https://two.example.com").map { it.enabled })
        assertEquals(listOf(tv, two), NuvioCHealthRules.move(listOf(two, tv), tv.address, up = true))
        assertEquals(listOf(tv, two), NuvioCHealthRules.move(listOf(tv, two), tv.address, up = true)) // top stays
        assertEquals(listOf(two, tv), NuvioCHealthRules.move(listOf(tv, two), tv.address, up = false))
        assertEquals(listOf(true, true, true), NuvioCHealthRules.toggle(listOf(tv, two, off), off.address, true).map { it.enabled })
        assertEquals(listOf(1, null, 2), NuvioCHealthRules.positions(listOf(tv, off, two)))
    }

    private val start = listOf(
        AddonHealth("https://comet.elfhosted.com/KEY/manifest.json", "Comet", "comet.elfhosted.com", TvCheck.RUNNING),
        AddonHealth("https://torrentio.strem.fun/torbox=KEY/manifest.json", "Torrentio", "torrentio.strem.fun", TvCheck.RUNNING, viaTorBox = true),
        AddonHealth("https://mine.example.org/manifest.json", "Mine", "mine.example.org", TvCheck.RUNNING)
    )
    private val used = listOf(
        UsedService("torbox", ServiceGroup.DEBRID, "TorBox", null, listOf("torbox.app")),
        UsedService("trakt", ServiceGroup.TRACKING, "Trakt", "https://api.trakt.tv/", listOf("trakt.tv")),
        UsedService("tmdb", ServiceGroup.WEBSITE, "TMDB", "https://api.themoviedb.org/3/", listOf("themoviedb.org", "tmdb"))
    )
    private val first = SourceReading("https://a.example", "First", "Gatus", listOf(
        SourceService("Comet", "comet.elfhosted.com", null, SourceState.DOWN),
        SourceService("Torrentio", "torrentio.strem.fun", null, SourceState.UNKNOWN), // hands over
        SourceService("TorBox", "api.torbox.app", null, SourceState.UP)
    ), 0L)
    private val second = SourceReading("https://b.example", "Second", "Uptime Kuma", listOf(
        SourceService("Comet", "comet.elfhosted.com", null, SourceState.UP), // not reached: First decided
        SourceService("Torrentio", "torrentio.strem.fun", null, SourceState.DEGRADED),
        SourceService("Trakt", "trakt.tv", null, SourceState.UP)
    ), 0L)
    private val broken = SourceReading("https://c.example", "Broken", "", listOf(
        SourceService("Mine", "mine.example.org", null, SourceState.DOWN)
    ), 0L, error = "Rate-limited")

    @Test fun chainFollowsOrderAndTvFillsTheRest() {
        val usable = listOf(broken, first, second).filter { NuvioCHealthRules.usable(it) }
        assertEquals(listOf(first, second), usable)
        val (addonsLeft, servicesLeft) = NuvioCHealthCombine.leftovers(start, used, usable)
        assertEquals(listOf("Mine"), addonsLeft.map { it.name }) // the broken source is skipped for everything
        assertEquals(listOf("tmdb"), servicesLeft.map { it.key })
        val tvAnswers = mapOf(start[2].baseUrl to Triple(TvCheck.NO_ANSWER, 6000, "Timed out"), "svc:tmdb" to Triple(TvCheck.OK, 100, null))
        val s = NuvioCHealthCombine.combineChain(start, used, listOf(broken, first, second), tvAnswers, TorBoxHealth(true, "Pro", 23))
        assertEquals(HealthVerdict.DOWN, s.addons[0].verdict); assertEquals("First", s.addons[0].sourceName)
        assertEquals(HealthVerdict.TROUBLE, s.addons[1].verdict); assertEquals("Second", s.addons[1].sourceName)
        assertEquals(HealthVerdict.CANT_REACH, s.addons[2].verdict); assertNull(s.addons[2].sourceName)
        val svc = s.services.associateBy { it.key }
        assertEquals("First", svc.getValue("torbox").decidedBy)
        assertEquals("Second", svc.getValue("trakt").decidedBy)
        assertNull(svc.getValue("tmdb").decidedBy)
        assertEquals(HealthVerdict.WORKING, svc.getValue("tmdb").verdict)
        // everything has a status; TorBox's line always from its own account
        assertTrue((s.addons.map { it.verdict } + s.services.map { it.verdict }).none { it == HealthVerdict.NOT_TRACKED || it == HealthVerdict.CHECKING })
        assertEquals("api.torbox.app · Pro · 23 days left", NuvioCHealthText.hostLine(svc.getValue("torbox")))
    }

    @Test fun noSourceOnMeansThisTvChecksEverything() {
        val (addonsLeft, servicesLeft) = NuvioCHealthCombine.leftovers(start, used, emptyList())
        assertEquals(3, addonsLeft.size); assertEquals(used, servicesLeft)
        val stale = first.copy(stale = true)
        assertFalse(NuvioCHealthRules.usable(stale))
    }

    @Test fun hostLinesNeverCarryAPath() {
        val tvAnswers = mapOf(start[0].baseUrl to Triple(TvCheck.NO_ANSWER, 6000, "Timed out"))
        val s = NuvioCHealthCombine.combineChain(start, used, emptyList(), tvAnswers, null)
        assertEquals("comet.elfhosted.com · Timed out", NuvioCHealthText.hostLine(s.addons[0]))
        s.addons.forEach { a -> NuvioCHealthText.hostLine(a).let { assertFalse(it, it.contains("/") || it.contains("KEY")) } }
        assertEquals("api.trakt.tv", s.services.first { it.key == "trakt" }.host)
        assertEquals("api.themoviedb.org", s.services.first { it.key == "tmdb" }.host)
    }

    // ---- 4. OSD About -----------------------------------------------------------------------------------------------
    @Test fun aboutDropsTheRatingsSpaceOnceLoadedEmpty() {
        val loading = NuvioCPlayerRatings(key = "tt1|movie")
        assertFalse(nuvioCAboutRatingsGone(loading, "tt1", "movie"))
        assertTrue(nuvioCAboutRatingsGone(loading.copy(loaded = true), "tt1", "movie"))
        assertFalse(nuvioCAboutRatingsGone(loading.copy(loaded = true), "tt2", "movie")) // another title's result
    }

    // ---- 6. Taste ---------------------------------------------------------------------------------------------------
    @Test fun twelveHourAxis() {
        assertEquals(listOf("12am", "6am", "12pm", "6pm", "11pm"), listOf(0, 6, 12, 18, 23).map { nuvioCHour12(it) })
    }

    private val actor = NuvioCTastePerson(7, "Ada Actor", "https://img/ada.jpg")
    private val boss = NuvioCTastePerson(9, "Dee Director")
    private val titles = mapOf(
        "movie:tt1" to NuvioCTasteTitle("movie:tt1", false, 1, "tt1", "Film One", 2024, runtime = 120, cast = listOf(actor)),
        "tv:tt2" to NuvioCTasteTitle("tv:tt2", true, 2, "tt2", "Show Two", 2020, runtime = 50, cast = listOf(actor), directors = listOf(boss)),
        "movie:tt3" to NuvioCTasteTitle("movie:tt3", false, 3, "tt3", "Film Three", 2010, runtime = 90, directors = listOf(boss))
    )
    private val day = 86_400_000L
    private val history = listOf(
        WatchedItem("tt1", "movie", "Film One", watchedAt = 10 * day),
        WatchedItem("tt2", "series", "Show Two", 1, 1, watchedAt = 20 * day),
        WatchedItem("tt2", "series", "Show Two", 1, 2, watchedAt = 21 * day),
        WatchedItem("tt2", "series", "Show Two", 1, 3, watchedAt = 22 * day),
        WatchedItem("tt3", "movie", "Film Three", watchedAt = 30 * day)
    )

    @Test fun faceListFromSavedCast() {
        val p = computeTasteCardPage(NuvioCTasteCard.FACE, tasteFaceHint(7, false, "of all time"), history, titles, emptyMap(), null, ZoneId.of("UTC"))
        assertEquals("Ada Actor", p.title)
        assertEquals("https://img/ada.jpg", p.photo)
        assertEquals(listOf("Show Two", "Film One"), p.ranked.map { it.name }) // by hours: 150 min, then 120 min
        assertEquals("Show · 3 episodes watched", p.ranked[0].sub)
        assertEquals("Film · 2024", p.ranked[1].sub)
        assertEquals("2 titles you watched of all time · 4.5 h together", p.summary)
    }

    @Test fun faceListForADirectorAndAScope() {
        val dir = computeTasteCardPage(NuvioCTasteCard.FACE, tasteFaceHint(9, true, "this month"), history, titles, emptyMap(), null, ZoneId.of("UTC"))
        assertEquals(listOf("Show Two", "Film Three"), dir.ranked.map { it.name })
        // a scope with one title still gives a one-row list
        val window = (29 * day)..(31 * day)
        val one = computeTasteCardPage(NuvioCTasteCard.FACE, tasteFaceHint(9, true, "this month"), history, titles, emptyMap(), window, ZoneId.of("UTC"))
        assertEquals(listOf("Film Three"), one.ranked.map { it.name })
        assertEquals("1 title you watched this month · 1.5 h together", one.summary)
        // another profile's history (no titles with this person) gives nothing
        val other = computeTasteCardPage(NuvioCTasteCard.FACE, tasteFaceHint(7, false, ""), history.take(0), titles, emptyMap(), null, ZoneId.of("UTC"))
        assertTrue(other.empty)
        assertEquals("of all time", tasteScopeWords("All time"))
        assertEquals("in 2025", tasteScopeWords("2025"))
    }

    // ---- 7. Calendar ------------------------------------------------------------------------------------------------
    @Test fun calendarSynopsisLines() {
        assertEquals(6, NuvioCCalendar68.synopsisLines(hasNext = true))
        assertEquals(9, NuvioCCalendar68.synopsisLines(hasNext = false))
    }
}
