package com.nuvio.tv.core.nuvioc.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Round 67 Service health, board 5d: addresses, redirects, text cleaning, keys never leaking, parsers on fixtures. */
class NuvioCHealthSecurityTest {
    private fun fixture(name: String) = javaClass.classLoader!!.getResource("health/$name")!!.readText()
    private fun ok(raw: String) = (NuvioCHealthRules.checkSourceAddress(raw) as? NuvioCHealthRules.Checked.Ok)?.address
    private fun refused(raw: String?) = (NuvioCHealthRules.checkSourceAddress(raw) as? NuvioCHealthRules.Checked.Refused)?.why

    // ---- URL validation ------------------------------------------------------------------------------------------
    @Test fun acceptsPlainHttpsStatusPages() {
        assertEquals("https://uptime.ibbylabs.dev", ok("https://uptime.ibbylabs.dev/"))
        assertEquals("https://uptime.ibbylabs.dev", ok("uptime.ibbylabs.dev"))
        assertEquals("https://status.example.com/status/main", ok("https://status.example.com/status/main#top"))
    }

    @Test fun refusesUnsafeAddresses() {
        assertEquals(NuvioCHealthRules.Refusal.NOT_HTTPS, refused("http://uptime.ibbylabs.dev"))
        assertEquals(NuvioCHealthRules.Refusal.NOT_HTTPS, refused("ftp://x.example.com"))
        assertEquals(NuvioCHealthRules.Refusal.HAS_LOGIN, refused("https://user:pass@status.example.com"))
        assertEquals(NuvioCHealthRules.Refusal.HAS_LOGIN, refused("https://user@status.example.com"))
        assertEquals(NuvioCHealthRules.Refusal.HAS_KEY, refused("https://status.example.com/?token=abc"))
        assertEquals(NuvioCHealthRules.Refusal.HAS_KEY, refused("https://status.example.com/?apikey=abc"))
        assertEquals(NuvioCHealthRules.Refusal.HAS_KEY, refused("https://status.example.com/?KEY=abc"))
        assertEquals(NuvioCHealthRules.Refusal.HAS_KEY, refused("https://status.example.com/?password=x"))
        assertEquals(NuvioCHealthRules.Refusal.HAS_KEY, refused("https://status.example.com/?auth=x"))
        assertEquals(NuvioCHealthRules.Refusal.TOO_LONG, refused("https://status.example.com/" + "a".repeat(300)))
        assertEquals(NuvioCHealthRules.Refusal.EMPTY, refused("  "))
        assertEquals(NuvioCHealthRules.Refusal.EMPTY, refused(null))
        assertEquals(NuvioCHealthRules.Refusal.NOT_AN_ADDRESS, refused("https://status example.com"))
        assertNull(ok("javascript:alert(1)"))
        assertNull(ok("https://status.example.com/\u0000x"))
    }

    @Test fun savedSourcesAreCheckedAgainOnLoad() {
        val raw = "1|https://uptime.ibbylabs.dev\n0|http://evil.example.com\n1|https://a:b@x.example.com\n0|https://gatus.example.com\njunk"
        val list = NuvioCHealthRules.decodeSources(raw)
        assertEquals(listOf(HealthSourceSetting("https://uptime.ibbylabs.dev", true), HealthSourceSetting("https://gatus.example.com", false)), list)
        assertEquals("1|https://uptime.ibbylabs.dev\n0|https://gatus.example.com", NuvioCHealthRules.encodeSources(list))
        val many = (1..6).joinToString("\n") { "1|https://s$it.example.com" }
        assertEquals(4, NuvioCHealthRules.decodeSources(many).size)
    }

    // ---- redirect rule -------------------------------------------------------------------------------------------
    @Test fun oneRedirectSameHostHttpsOnly() {
        val from = "https://status.example.com/v1/status"
        assertTrue(NuvioCHealthRules.redirectAllowed(from, "https://status.example.com/v1/status/", 0))
        assertFalse(NuvioCHealthRules.redirectAllowed(from, "https://status.example.com/x", 1)) // a second redirect
        assertFalse(NuvioCHealthRules.redirectAllowed(from, "http://status.example.com/x", 0))
        assertFalse(NuvioCHealthRules.redirectAllowed(from, "https://evil.example.net/x", 0))
        assertFalse(NuvioCHealthRules.redirectAllowed(from, "https://status.example.com:8443/x", 0))
        assertFalse(NuvioCHealthRules.redirectAllowed(from, null, 0))
    }

    @Test fun detectionStaysOnTheSameHost() {
        val c = NuvioCHealthParsers.candidates("https://status.example.com/status/main")
        assertTrue(c.all { it.url.startsWith("https://status.example.com/") && (it.extraUrl == null || it.extraUrl!!.startsWith("https://status.example.com/")) })
        assertTrue(c.any { it.format == SourceFormat.UPTIME_KUMA && it.url.endsWith("/api/status-page/main") })
        assertTrue(NuvioCHealthParsers.candidates("https://uptime.ibbylabs.dev").none { it.format == SourceFormat.UPTIME_KUMA })
    }

    // ---- text cleaning -------------------------------------------------------------------------------------------
    @Test fun controlAndBidiCharactersAreStripped() {
        assertEquals("Torrentio down", NuvioCHealthRules.cleanText("Torrentio‮ down"))
        assertEquals("ab", NuvioCHealthRules.cleanText("a​b"))
        assertEquals("ab", NuvioCHealthRules.cleanText("a⁦⁩b"))
        assertEquals("a b", NuvioCHealthRules.cleanText("a\n\tb"))
        assertEquals("a b", NuvioCHealthRules.cleanText("a b"))
        assertEquals("one line", NuvioCHealthRules.cleanText("  one\r\nline  "))
        val long = NuvioCHealthRules.cleanText("x".repeat(200))!!
        assertEquals(60, long.length); assertTrue(long.endsWith("…"))
        assertNull(NuvioCHealthRules.cleanText("‪‬ "))
        assertNull(NuvioCHealthRules.cleanText(null))
    }

    @Test fun numbersAreClamped() {
        assertEquals(60_000, NuvioCHealthRules.clampLatency(9e12))
        assertEquals(0, NuvioCHealthRules.clampLatency(-5.0))
        assertNull(NuvioCHealthRules.clampLatency(Double.NaN))
        assertEquals(100.0, NuvioCHealthRules.clampUptime(250.0)!!, 0.0)
        assertEquals(0.0, NuvioCHealthRules.clampUptime(-1.0)!!, 0.0)
    }

    // ---- addresses with keys shown as hostnames only -------------------------------------------------------------
    @Test fun addonAddressesShowOnlyTheirHost() {
        assertEquals("torrentio.strem.fun", NuvioCHealthRules.hostOnly("https://torrentio.strem.fun/torbox=SECRETKEY123|sort=qualitysize/manifest.json"))
        assertEquals("aiostreams.example.com", NuvioCHealthRules.hostOnly("https://aiostreams.example.com/stremio/uuid/ENCRYPTEDCONFIG/manifest.json"))
        assertEquals("x.example.com", NuvioCHealthRules.hostOnly("https://user:pw@x.example.com:8443/a?key=1"))
        assertEquals("?", NuvioCHealthRules.hostOnly(null))
    }

    @Test fun manifestAddressKeepsTheInstalledOne() {
        assertEquals("https://a.example.com/cfg/manifest.json", NuvioCHealthRules.manifestUrl("https://a.example.com/cfg/"))
        assertEquals("https://a.example.com/cfg/manifest.json?x=1", NuvioCHealthRules.manifestUrl("https://a.example.com/cfg?x=1"))
        assertEquals("https://a.example.com/manifest.json", NuvioCHealthRules.manifestUrl("https://a.example.com/manifest.json"))
    }

    @Test fun torBoxKeyNeverAppearsInAnyProducedStringOrState() {
        val key = "SENTINEL-KEY-7f3a9c"
        val addonUrl = "https://torrentio.strem.fun/debridoptions=nodownloadlinks|torbox=$key/manifest.json"
        val start = AddonHealth(addonUrl, "Torrentio", NuvioCHealthRules.hostOnly(addonUrl), TvCheck.RUNNING, viaTorBox = NuvioCHealthRules.usesTorBox(addonUrl))
        val reading = SourceReading("https://uptime.ibbylabs.dev", "IbbyLabs", "IbbyLabs status",
            NuvioCHealthParsers.parseIbbyLabs(fixture("ibbylabs_status.json"))!!.services, 0L)
        val used = listOf(UsedService("torbox", ServiceGroup.DEBRID, "TorBox", null, listOf("torbox.app")))
        val tv = mapOf(addonUrl to Triple(TvCheck.NO_ANSWER, 6000, "Timed out"))
        val viaTv = NuvioCHealthCombine.combine(listOf(start), used, tv, null, TorBoxHealth(false, unwell = true))
        val state = NuvioCHealthCombine.combine(listOf(start), used, tv, reading, TorBoxHealth(true, "Pro", 23, 120))
        val texts = buildList {
            add(state.toString().replace(addonUrl, "")) // the state keeps the card's own address (its key); nothing else may
            add(viaTv.toString().replace(addonUrl, ""))
            viaTv.addons.forEach { add(it.tvReason.orEmpty()) }
            state.addons.forEach { add(it.host); add(it.name); add(it.tvReason.orEmpty()); add(it.sourceName.orEmpty()) }
            add(NuvioCHealthRules.hostOnly(addonUrl))
            add(NuvioCHealthHttp.reasonFor(403).orEmpty())
            add(NuvioCHealthRules.encodeSources(listOf(HealthSourceSetting("https://uptime.ibbylabs.dev"))))
        }
        texts.forEach { assertFalse("key leaked into: $it", it.contains(key)) }
        assertTrue(state.addons.single().viaTorBox)
    }

    @Test fun shortReasonsNeverCarryAnAddress() {
        listOf(401, 403, 404, 429, 500, 302).forEach { code ->
            val r = NuvioCHealthHttp.reasonFor(code)!!
            assertFalse(r.contains("http")); assertFalse(r.contains("/"))
        }
    }

    @Test fun bodyReadIsCappedAt2Mb() {
        val big = java.io.ByteArrayInputStream(ByteArray((NuvioCHealthRules.MAX_BYTES + 10).toInt()) { 'a'.code.toByte() })
        assertNull(NuvioCHealthHttp.readCapped(big))
        assertEquals("abc", NuvioCHealthHttp.readCapped("abc".byteInputStream()))
    }

    // ---- parsers -------------------------------------------------------------------------------------------------
    @Test fun ibbyLabsFixture() {
        val p = NuvioCHealthParsers.parseIbbyLabs(fixture("ibbylabs_status.json"))!!
        assertEquals("IbbyLabs Uptime Tracker", p.name)
        assertFalse(p.stale)
        val torrentio = p.services.first { it.host == "torrentio.strem.fun" }
        assertEquals(SourceState.UP, torrentio.state)
        assertEquals(SourceState.DEGRADED, p.services.first { it.name == "Sootio" }.state)
        assertTrue(p.services.all { (it.latencyMs ?: 0) in 0..60_000 })
        // matching by hostname: Torrentio's add-on host finds its own instance, not the mirrors
        val m = NuvioCHealthRules.matchService("torrentio.strem.fun", p.services)!!
        assertEquals("torrentio.strem.fun", m.host)
    }

    @Test fun gatusFixture() {
        val p = NuvioCHealthParsers.parseGatus(fixture("gatus_statuses.json"))!!
        assertEquals(3, p.services.size)
        assertTrue(p.services.all { it.state == SourceState.UP })
        assertEquals("twin.sh", p.services.first { it.name == "blog-external" }.host)
    }

    @Test fun betterStackFixture() {
        val body = fixture("betterstack_index.json")
        val p = NuvioCHealthParsers.parseBetterStack(body)!!
        assertEquals("TorBox", p.name)
        assertEquals(4, p.services.size)
        val (overall, reports) = NuvioCHealthParsers.betterStackOverall(body, "2026-11-23T08:00:00Z")!!
        assertEquals(SourceState.UP, overall)
        assertTrue(reports.any { it.maintenance && it.title == "Update Services Maintenance" })
        assertTrue(NuvioCHealthParsers.betterStackOverall(body, "2026-11-27T00:00:00Z")!!.second.none { it.title == "Update Services Maintenance" })
    }

    @Test fun otherFormatsAndHostileInput() {
        val kuma = """{"config":{"title":"My ‮status"},"publicGroupList":[{"name":"Addons","monitorList":[{"id":1,"name":"Comet","url":"https://comet.example.com/x"}]}]}"""
        val beats = """{"heartbeatList":{"1":[{"status":0,"ping":99999999,"msg":"<script>alert(1)</script>\n down"}]},"uptimeList":{"1_24":0.5}}"""
        val k = NuvioCHealthParsers.parseUptimeKuma(kuma, beats)!!
        assertEquals("My status", k.name)
        val comet = k.services.single()
        assertEquals(SourceState.DOWN, comet.state); assertEquals("comet.example.com", comet.host); assertEquals(60_000, comet.latencyMs)
        assertEquals("<script>alert(1)</script> down", comet.detail) // plain text only, never rendered as HTML
        assertEquals(50.0, comet.uptime7d!!, 0.001)
        val sp = """{"page":{"name":"Trakt"},"components":[{"name":"API","status":"major_outage"},{"name":"Group","status":"operational","group":true}]}"""
        assertEquals(listOf(SourceState.DOWN), NuvioCHealthParsers.parseStatuspage(sp)!!.services.map { it.state })
        val ins = """{"page":{"name":"Simkl","status":"HASISSUES"},"activeIncidents":[{"name":"Slow sync"}]}"""
        assertEquals("Slow sync", NuvioCHealthParsers.parseInstatus(ins)!!.services.single().detail)
        // not the format / broken JSON / lenient-only JSON: nothing
        assertNull(NuvioCHealthParsers.parseIbbyLabs("""{"services":"nope"}"""))
        assertNull(NuvioCHealthParsers.parseGatus("<html></html>"))
        assertNull(NuvioCHealthParsers.parseStatuspage("""{page:{name:'x'},components:[]}"""))
        assertNull(NuvioCHealthParsers.parseBetterStack("""{"data":{"type":"something_else"}}"""))
    }

    @Test fun torBoxAccountReadsOnlyPlanAndExpiry() {
        val me = """{"success":true,"data":{"plan":2,"premium_expires_at":"2026-12-20T10:00:00Z","email":"someone@example.com","auth_id":"x"}}"""
        assertEquals("Pro" to "2026-12-20T10:00:00Z", NuvioCHealthParsers.parseTorBoxMe(me))
        assertNotNull(NuvioCHealthParsers.parseTorBoxMe("""{"data":{}}"""))
        assertFalse(NuvioCHealthParsers.parseTorBoxMe(me).toString().contains("example.com"))
    }
}
