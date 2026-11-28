package com.nuvio.tv

import com.nuvio.tv.core.nuvioc.health.AddonHealth
import com.nuvio.tv.core.nuvioc.health.HealthSourceSetting
import com.nuvio.tv.core.nuvioc.health.HealthState
import com.nuvio.tv.core.nuvioc.health.HealthTone
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
import com.nuvio.tv.ui.screens.addon.NuvioCHealthText
import com.nuvio.tv.ui.screens.addon.nuvioCCheckedLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Round 67b Service health: one source at a time, only what you use, no addresses or timings in any text. */
class NuvioCRound67bTest {
    private val a = HealthSourceSetting("https://a.example.com", true)
    private val b = HealthSourceSetting("https://b.example.com", true)
    private val c = HealthSourceSetting("https://c.example.com", false)

    @Test fun onlyOneSourceOn() {
        assertEquals(listOf(a, b.copy(enabled = false), c), NuvioCHealthRules.oneActive(listOf(a, b, c)))
        val decoded = NuvioCHealthRules.decodeSources("1|https://a.example.com\n1|https://b.example.com")
        // round 68 HEALTH_CHAIN_V68: any number can be on (check order); before it, one at most
        assertEquals(if (NuvioCFeatures.HEALTH_CHAIN_V68) 2 else 1, decoded.count { it.enabled })
        // switching one on switches the others off; switching it off leaves none on (This TV takes over)
        val picked = NuvioCHealthRules.select(listOf(a.copy(enabled = false), b.copy(enabled = false), c), c.address, true)
        assertEquals(listOf(false, false, true), picked.map { it.enabled })
        val swapped = NuvioCHealthRules.select(picked, a.address, true)
        assertEquals(listOf(true, false, false), swapped.map { it.enabled })
        assertTrue(NuvioCHealthRules.select(swapped, a.address, false).none { it.enabled })
    }

    @Test fun singleSourceVerdicts() {
        assertEquals(HealthVerdict.WORKING, NuvioCHealthRules.verdictFromTv(TvCheck.OK))
        assertEquals(HealthVerdict.CANT_REACH, NuvioCHealthRules.verdictFromTv(TvCheck.NO_ANSWER))
        assertEquals(HealthVerdict.RATE_LIMITED, NuvioCHealthRules.verdictFromTv(TvCheck.RATE_LIMITED))
        assertEquals(HealthVerdict.BLOCKED, NuvioCHealthRules.verdictFromTv(TvCheck.BLOCKED))
        assertEquals(HealthVerdict.OFF, NuvioCHealthRules.verdictFromTv(TvCheck.OFF))
        assertEquals(HealthVerdict.WORKING, NuvioCHealthRules.verdictFromSource(SourceState.UP))
        assertEquals(HealthVerdict.DOWN, NuvioCHealthRules.verdictFromSource(SourceState.DOWN))
        assertEquals(HealthVerdict.TROUBLE, NuvioCHealthRules.verdictFromSource(SourceState.DEGRADED))
        assertEquals(HealthVerdict.MAINTENANCE, NuvioCHealthRules.verdictFromSource(SourceState.MAINTENANCE))
        assertEquals(HealthVerdict.NOT_TRACKED, NuvioCHealthRules.verdictFromSource(null))
        assertEquals(HealthTone.AMBER, NuvioCHealthRules.tone(HealthVerdict.TROUBLE))
        assertEquals(HealthTone.GREY, NuvioCHealthRules.tone(HealthVerdict.NOT_TRACKED))
    }

    @Test fun websiteChecksCountAnyAnswerAsUp() {
        assertEquals(TvCheck.OK, NuvioCHealthRules.tvCheckService(401))
        assertEquals(TvCheck.OK, NuvioCHealthRules.tvCheckService(404))
        assertEquals(TvCheck.RATE_LIMITED, NuvioCHealthRules.tvCheckService(429))
        assertEquals(TvCheck.NO_ANSWER, NuvioCHealthRules.tvCheckService(503))
        assertEquals(TvCheck.NO_ANSWER, NuvioCHealthRules.tvCheckService(null))
    }

    @Test fun instancesByNameOnly() {
        assertEquals("ElfHosted", NuvioCHealthRules.instanceName("aiostreams.elfhosted.com"))
        assertEquals("ElfHosted", NuvioCHealthRules.instanceName("elfhosted.com"))
        assertNull(NuvioCHealthRules.instanceName("torrentio.strem.fun"))
        assertNull(NuvioCHealthRules.instanceName("notelfhosted.com"))
    }

    private val start = listOf(
        AddonHealth("https://aiostreams.elfhosted.com/stremio/KEY/manifest.json", "AIOStreams", "aiostreams.elfhosted.com", TvCheck.RUNNING),
        AddonHealth("https://torrentio.strem.fun/torbox=KEY/manifest.json", "Torrentio", "torrentio.strem.fun", TvCheck.RUNNING, viaTorBox = true),
        AddonHealth("https://off.example.com/manifest.json", "Off one", "off.example.com", TvCheck.OFF)
    )
    private val used = listOf(
        UsedService("torbox", ServiceGroup.DEBRID, "TorBox", null, listOf("torbox.app")),
        UsedService("trakt", ServiceGroup.TRACKING, "Trakt", "https://api.trakt.tv/", listOf("trakt.tv")),
        UsedService("tmdb", ServiceGroup.WEBSITE, "TMDB", "https://api.themoviedb.org/3/", listOf("themoviedb.org", "tmdb"))
    )

    @Test fun thisTvDecidesWhenNoSourceIsOn() {
        val tv = mapOf(
            start[0].baseUrl to Triple(TvCheck.NO_ANSWER, 6000, "Timed out"),
            start[1].baseUrl to Triple(TvCheck.OK, 900, null),
            "svc:trakt" to Triple(TvCheck.OK, 300, null),
            "svc:tmdb" to Triple(TvCheck.OK, 200, null)
        )
        val s = NuvioCHealthCombine.combine(start, used, tv, null, TorBoxHealth(true, "Pro", 23, 150))
        assertEquals(listOf(HealthVerdict.CANT_REACH, HealthVerdict.WORKING, HealthVerdict.OFF), s.addons.map { it.verdict })
        assertEquals(listOf(HealthVerdict.WORKING, HealthVerdict.WORKING, HealthVerdict.WORKING), s.services.map { it.verdict })
        assertEquals("Pro · 23 days left", s.services.first { it.key == "torbox" }.sub)
        assertEquals(4, s.working); assertEquals(1, s.problems); assertEquals(0, s.down)
        assertNull(s.sourceName)
    }

    @Test fun aStatusSourceDecidesAlone() {
        val reading = SourceReading("https://uptime.ibbylabs.dev", "IbbyLabs", "IbbyLabs status", listOf(
            SourceService("AIOStreams (ElfHosted)", "aiostreams.elfhosted.com", null, SourceState.DOWN),
            SourceService("TorBox", "api.torbox.app", null, SourceState.DEGRADED),
            SourceService("Trakt", "trakt.tv", null, SourceState.UP),
            SourceService("Comet", "comet.example.com", null, SourceState.DOWN) // not used: never shown or counted
        ), 0L)
        val s = NuvioCHealthCombine.combine(start, used, emptyMap(), reading, TorBoxHealth(true, "Pro", 23, 150))
        assertEquals(HealthVerdict.DOWN, s.addons[0].verdict)
        assertEquals(HealthVerdict.NOT_TRACKED, s.addons[1].verdict)
        assertEquals("TorBox is having trouble", s.addons[1].tvReason)
        assertEquals(HealthVerdict.TROUBLE, s.services.first { it.key == "torbox" }.verdict)
        assertEquals(HealthVerdict.WORKING, s.services.first { it.key == "trakt" }.verdict)
        assertEquals(HealthVerdict.NOT_TRACKED, s.services.first { it.key == "tmdb" }.verdict)
        assertEquals(1, s.down); assertEquals(1, s.problems); assertEquals(1, s.working)
        assertEquals("IbbyLabs", s.sourceName)
        assertFalse((s.addons.map { it.name } + s.services.map { it.name }).any { it.contains("Comet") })
    }

    @Test fun textsHaveNoAddressesOrTimings() {
        val tv = mapOf(start[0].baseUrl to Triple(TvCheck.NO_ANSWER, 6000, "Timed out"), start[1].baseUrl to Triple(TvCheck.OK, 912, null))
        val s = NuvioCHealthCombine.combine(start, used, tv, null, TorBoxHealth(true, "Pro", 23, 150))
        val texts = s.addons.flatMap { listOfNotNull(NuvioCHealthText.line(it), NuvioCHealthText.sub(it)) } +
            NuvioCHealthText.summary(s, 0L)
        texts.forEach { t ->
            assertFalse(t, Regex("\\d+(\\.\\d+)? ?(ms|s)\\b").containsMatchIn(t))
            assertFalse(t, t.contains("http") || t.contains(".com") || t.contains(".fun"))
        }
        assertEquals("Can't reach it from here · Timed out", NuvioCHealthText.line(s.addons[0]))
        assertEquals("ElfHosted · Timed out", NuvioCHealthText.sub(s.addons[0]))
        assertEquals("Working", NuvioCHealthText.line(s.addons[1]))
    }

    @Test fun summaryCountsAreHighlighted() {
        val s = HealthState(addons = listOf(AddonHealth("u", "A", "a.example.com", TvCheck.NO_ANSWER, verdict = HealthVerdict.CANT_REACH)), checkedAtMs = 0L,
            torBox = TorBoxHealth(true, "Pro", 23))
        val parts = NuvioCHealthText.summaryParts(s, 0L)
        assertEquals(HealthTone.AMBER, parts.first().tone)
        assertEquals("1 PROBLEM", parts.first().text)
        assertEquals(if (NuvioCFeatures.HEALTH_V68) "Checked just now" else "checked just now", parts.last().text) // round 68 capital C
        assertTrue(parts.any { it.text == "TorBox online, Pro" })
    }

    @Test fun checkedLine() {
        assertEquals(if (NuvioCFeatures.HEALTH_V68) "Checked 2 minutes ago" else "Checked 2 min ago", nuvioCCheckedLine(0L, 125_000L)) // round 68 texts
        assertEquals("Not checked yet", nuvioCCheckedLine(null, 0L))
    }
}
