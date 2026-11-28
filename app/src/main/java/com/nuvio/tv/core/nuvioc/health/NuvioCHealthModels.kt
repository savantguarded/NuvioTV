package com.nuvio.tv.core.nuvioc.health

// [fork] Nuvio C round 67 `ADDON_HEALTH`: Service health on the Add-ons page. Models and the pure rules (verdict
// table, URL checks, text cleaning, hostname-only display), kept free of Android so the JVM tests cover them.

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** What this TV's own check of an add-on's manifest found. */
enum class TvCheck { RUNNING, OK, RATE_LIMITED, BLOCKED, NO_ANSWER, OFF }

/** One service's state on a status source. */
enum class SourceState { UP, DEGRADED, DOWN, MAINTENANCE, UNKNOWN }

/** What an add-on card and the summary show. */
enum class HealthVerdict {
    CHECKING, WORKING, WORKING_SOURCE_DISAGREES, CANT_REACH, RATE_LIMITED, BLOCKED, DOWN, OFF,
    // [fork] round 67b: a status source alone decides (one source at a time)
    TROUBLE, MAINTENANCE, NOT_TRACKED
}

/** [fork] round 67b: the non-add-on things you use, in the summary's second and third columns. */
enum class ServiceGroup { DEBRID, TRACKING, WEBSITE }

/** [fork] round 67b: one debrid / tracker / sync / website you use. [sub] is never an address or a timing. */
data class ServiceHealth(
    val key: String,
    val group: ServiceGroup,
    val name: String,
    val sub: String? = null,
    val verdict: HealthVerdict = HealthVerdict.CHECKING,
    val reason: String? = null,
    /** [fork] round 68: hostname shown under the name (never a path), e.g. api.trakt.tv. */
    val host: String? = null,
    /** [fork] round 68: the status source that decided, null = This TV (logs and tests only, never shown). */
    val decidedBy: String? = null
)

/** Dot colour of a verdict. */
enum class HealthTone { GREEN, AMBER, RED, GREY }

/** One service read from a status source; text already cleaned, numbers clamped. */
data class SourceService(
    val name: String,
    val host: String?,
    val group: String?,
    val state: SourceState,
    val latencyMs: Int? = null,
    val detail: String? = null,
    val uptime7d: Double? = null
)

/** One status source's answer. [stale] = the source says its own data is old. */
data class SourceReading(
    val address: String,
    val name: String,
    val format: String,
    val services: List<SourceService>,
    val fetchedAtMs: Long,
    val stale: Boolean = false,
    val error: String? = null
)

/** The result line for an add-on. [host] is the hostname only, never the address (paths hold keys). */
data class AddonHealth(
    val baseUrl: String,
    val name: String,
    val host: String,
    val tv: TvCheck,
    val tvLatencyMs: Int? = null,
    val tvReason: String? = null,
    val source: SourceState? = null,
    val sourceName: String? = null,
    val viaTorBox: Boolean = false,
    val verdict: HealthVerdict = HealthVerdict.CHECKING
)

/** TorBox account and status, never the key. */
data class TorBoxHealth(
    val reachable: Boolean,
    val plan: String? = null,
    val daysLeft: Int? = null,
    val latencyMs: Int? = null,
    val unwell: Boolean = false,
    val incident: String? = null,
    val incidentSince: String? = null,
    val maintenanceUntil: String? = null
)

/** Everything the Add-ons page and the summary page draw. */
data class HealthState(
    val running: Boolean = false,
    val checkedAtMs: Long? = null,
    val addons: List<AddonHealth> = emptyList(),
    val sources: List<SourceReading> = emptyList(),
    val torBox: TorBoxHealth? = null,
    /** [fork] round 67b: TorBox, trackers and sync, websites: only the ones this profile uses. */
    val services: List<ServiceHealth> = emptyList(),
    /** [fork] round 67b: the status source that decided this run, null = this TV's own checks. */
    val sourceName: String? = null
) {
    private val verdicts: List<HealthVerdict> get() = addons.map { it.verdict } + services.map { it.verdict }
    val working: Int get() = verdicts.count { it == HealthVerdict.WORKING || it == HealthVerdict.WORKING_SOURCE_DISAGREES }
    val problems: Int get() = verdicts.count { NuvioCHealthRules.tone(it) == HealthTone.AMBER }
    val down: Int get() = verdicts.count { it == HealthVerdict.DOWN }
}

/** A saved status source: the normalised address and whether it's used. */
data class HealthSourceSetting(val address: String, val enabled: Boolean = true)

object NuvioCHealthRules {
    const val MAX_SOURCES = 4
    const val MAX_TEXT = 60
    const val MAX_ADDRESS = 300
    const val MAX_BYTES = 2L * 1024 * 1024
    const val TIMEOUT_MS = 6_000L
    const val PARALLEL = 3
    const val TORBOX_API_HOST = "api.torbox.app"
    const val TORBOX_ME_URL = "https://api.torbox.app/v1/api/user/me"

    /** The verdict table (board 5b). Pure, for tests. */
    fun verdict(tv: TvCheck, source: SourceState?): HealthVerdict = when (tv) {
        TvCheck.RUNNING -> HealthVerdict.CHECKING
        TvCheck.OFF -> HealthVerdict.OFF
        TvCheck.RATE_LIMITED -> HealthVerdict.RATE_LIMITED
        TvCheck.BLOCKED -> HealthVerdict.BLOCKED
        TvCheck.OK -> if (source == SourceState.DOWN) HealthVerdict.WORKING_SOURCE_DISAGREES else HealthVerdict.WORKING
        TvCheck.NO_ANSWER -> if (source == SourceState.DOWN) HealthVerdict.DOWN else HealthVerdict.CANT_REACH
    }

    fun tone(v: HealthVerdict): HealthTone = when (v) {
        HealthVerdict.WORKING, HealthVerdict.WORKING_SOURCE_DISAGREES -> HealthTone.GREEN
        HealthVerdict.CANT_REACH, HealthVerdict.RATE_LIMITED, HealthVerdict.BLOCKED,
        HealthVerdict.TROUBLE, HealthVerdict.MAINTENANCE -> HealthTone.AMBER
        HealthVerdict.DOWN -> HealthTone.RED
        HealthVerdict.CHECKING, HealthVerdict.OFF, HealthVerdict.NOT_TRACKED -> HealthTone.GREY
    }

    // ---- [fork] round 67b: one source at a time ----------------------------------------------------------------
    /** This TV's own check decides alone. Pure, for tests. */
    fun verdictFromTv(tv: TvCheck): HealthVerdict = when (tv) {
        TvCheck.RUNNING -> HealthVerdict.CHECKING
        TvCheck.OFF -> HealthVerdict.OFF
        TvCheck.OK -> HealthVerdict.WORKING
        TvCheck.RATE_LIMITED -> HealthVerdict.RATE_LIMITED
        TvCheck.BLOCKED -> HealthVerdict.BLOCKED
        TvCheck.NO_ANSWER -> HealthVerdict.CANT_REACH
    }

    /** A status source decides alone; null = it doesn't watch this one. Pure, for tests. */
    fun verdictFromSource(state: SourceState?): HealthVerdict = when (state) {
        null, SourceState.UNKNOWN -> HealthVerdict.NOT_TRACKED
        SourceState.UP -> HealthVerdict.WORKING
        SourceState.DEGRADED -> HealthVerdict.TROUBLE
        SourceState.MAINTENANCE -> HealthVerdict.MAINTENANCE
        SourceState.DOWN -> HealthVerdict.DOWN
    }

    /** This TV's check of a website or service (not a manifest): any answer below 500 means it's up. Pure, for tests. */
    fun tvCheckService(code: Int?): TvCheck = when {
        code == null -> TvCheck.NO_ANSWER
        code == 429 -> TvCheck.RATE_LIMITED
        code < 500 -> TvCheck.OK
        else -> TvCheck.NO_ANSWER
    }

    private val HOSTING = linkedMapOf(
        "elfhosted.com" to "ElfHosted", "baby-beamup.club" to "BeamUp", "beamup.dev" to "BeamUp", "koyeb.app" to "Koyeb",
        "hf.space" to "Hugging Face", "vercel.app" to "Vercel", "onrender.com" to "Render", "fly.dev" to "Fly.io",
        "railway.app" to "Railway", "herokuapp.com" to "Heroku"
    )

    /** The instance an add-on runs on, by name (e.g. ElfHosted), never the address; null when unknown. Pure, for tests. */
    fun instanceName(host: String?): String? {
        val h = host?.lowercase() ?: return null
        return HOSTING.entries.firstOrNull { (d, _) -> h == d || h.endsWith(".$d") }?.value
    }

    /** At most one status source on: the first one switched on wins. Pure, for tests. */
    fun oneActive(list: List<HealthSourceSetting>): List<HealthSourceSetting> {
        val first = list.indexOfFirst { it.enabled }
        return list.mapIndexed { i, s -> if (s.enabled && i != first) s.copy(enabled = false) else s }
    }

    /** Switch [address] on (all others off) or off (this TV takes over). Pure, for tests. */
    fun select(list: List<HealthSourceSetting>, address: String, on: Boolean): List<HealthSourceSetting> =
        list.map { s -> if (s.address == address) s.copy(enabled = on) else if (on) s.copy(enabled = false) else s }

    /** TV check from an HTTP answer. [validManifest] = a 2xx body that parsed as a manifest. Pure, for tests. */
    fun tvCheck(code: Int?, validManifest: Boolean): TvCheck = when {
        code == null -> TvCheck.NO_ANSWER
        code == 429 -> TvCheck.RATE_LIMITED
        code == 401 || code == 403 -> TvCheck.BLOCKED
        code in 200..299 && validManifest -> TvCheck.OK
        else -> TvCheck.NO_ANSWER
    }

    // ---- text from a source -------------------------------------------------------------------------------------
    private val BIDI = Regex("[\\u202A-\\u202E\\u2066-\\u2069\\u200B-\\u200F\\uFEFF]")
    private val CONTROL = Regex("[\\p{Cc}\\p{Cf}]")
    private val SPACES = Regex("[\\s\\u00A0\\u2028\\u2029]+")

    /** Strip control and bidi characters, collapse whitespace, one line, at most 60 characters. Pure, for tests. */
    fun cleanText(raw: String?, max: Int = MAX_TEXT): String? {
        if (raw == null) return null
        val t = raw.replace(BIDI, "").replace(CONTROL, " ").replace(SPACES, " ").trim()
        if (t.isEmpty()) return null
        return if (t.length <= max) t else t.take(max - 1).trimEnd() + "…"
    }

    fun clampLatency(ms: Double?): Int? = ms?.takeIf { !it.isNaN() }?.coerceIn(0.0, 60_000.0)?.toInt()
    fun clampUptime(p: Double?): Double? = p?.takeIf { !it.isNaN() }?.coerceIn(0.0, 100.0)

    // ---- addresses ----------------------------------------------------------------------------------------------
    /** Hostname only, for display and logs: add-on paths (Torrentio, AIOStreams) hold keys. Pure, for tests. */
    fun hostOnly(address: String?): String {
        if (address.isNullOrBlank()) return "?"
        address.trim().toHttpUrlOrNull()?.let { return it.host }
        val noScheme = address.trim().substringAfter("://")
        return noScheme.substringBefore('/').substringBefore('?').substringBefore('#').substringAfterLast('@').substringBefore(':').ifBlank { "?" }
    }

    /** The manifest address of an installed add-on (the address it is installed from). */
    fun manifestUrl(baseUrl: String): String {
        val clean = baseUrl.trim()
        val q = clean.indexOf('?')
        val path = (if (q >= 0) clean.substring(0, q) else clean).trimEnd('/')
        val query = if (q >= 0) clean.substring(q) else ""
        return if (path.endsWith("/manifest.json")) path + query else "$path/manifest.json$query"
    }

    private val KEYISH = setOf("key", "token", "apikey", "api_key", "auth", "password", "pass", "secret", "access_token")

    /** Why an address can't be used as a status source, or null when it can. */
    enum class Refusal { EMPTY, TOO_LONG, NOT_HTTPS, NOT_AN_ADDRESS, HAS_LOGIN, HAS_KEY }

    sealed interface Checked {
        data class Ok(val address: String) : Checked
        data class Refused(val why: Refusal) : Checked
    }

    /**
     * A status source address: https only, no user or password, no key-like query parameters, at most 300
     * characters. Stored normalised (scheme, host, port, path; fragment dropped). Pure, for tests.
     */
    fun checkSourceAddress(raw: String?): Checked {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty()) return Checked.Refused(Refusal.EMPTY)
        if (t.length > MAX_ADDRESS) return Checked.Refused(Refusal.TOO_LONG)
        if (t.any { it.isISOControl() || it.isWhitespace() }) return Checked.Refused(Refusal.NOT_AN_ADDRESS)
        val withScheme = if (!t.contains("://")) "https://$t" else t
        if (!withScheme.startsWith("https://", ignoreCase = true)) return Checked.Refused(Refusal.NOT_HTTPS)
        val u: HttpUrl = withScheme.toHttpUrlOrNull() ?: return Checked.Refused(Refusal.NOT_AN_ADDRESS)
        if (u.scheme != "https") return Checked.Refused(Refusal.NOT_HTTPS)
        if (u.username.isNotEmpty() || u.password.isNotEmpty() || t.substringAfter("://").substringBefore('/').contains('@')) return Checked.Refused(Refusal.HAS_LOGIN)
        if (u.queryParameterNames.any { it.lowercase() in KEYISH }) return Checked.Refused(Refusal.HAS_KEY)
        if (!u.host.contains('.') && u.host != "localhost") return Checked.Refused(Refusal.NOT_AN_ADDRESS)
        val normal = u.newBuilder().fragment(null).build().toString().trimEnd('/')
        if (normal.length > MAX_ADDRESS) return Checked.Refused(Refusal.TOO_LONG)
        return Checked.Ok(normal)
    }

    /**
     * The redirect rule: at most one redirect, only to the same host over https. Pure, for tests.
     * [from] and [to] are full addresses; [hops] is how many redirects were already followed.
     */
    fun redirectAllowed(from: String, to: String?, hops: Int): Boolean {
        if (hops >= 1 || to == null) return false
        val a = from.toHttpUrlOrNull() ?: return false
        val b = to.toHttpUrlOrNull() ?: return false
        return b.scheme == "https" && b.host.equals(a.host, ignoreCase = true) && b.port == a.port
    }

    // ---- matching -----------------------------------------------------------------------------------------------
    /** Host of a service URL as written by a source (may be a bare hostname). */
    fun serviceHost(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val withScheme = if (url.contains("://")) url else "https://$url"
        return withScheme.toHttpUrlOrNull()?.host?.lowercase()
    }

    /**
     * The source service for an add-on host: exact host first; else a service on the same registrable host
     * (sub.example.com vs example.com) only when there is exactly one such service. Pure, for tests.
     */
    fun matchService(addonHost: String, services: List<SourceService>): SourceService? {
        val h = addonHost.lowercase()
        services.firstOrNull { it.host == h }?.let { return it }
        val base = registrable(h)
        val near = services.filter { s -> s.host != null && registrable(s.host) == base && (s.host.endsWith(".$h") || h.endsWith(".${s.host}")) }
        return near.singleOrNull()
    }

    private fun registrable(host: String): String = host.split('.').takeLast(2).joinToString(".")

    /** Whether an add-on resolves through TorBox, from its manifest address (path or query mention TorBox). */
    fun usesTorBox(baseUrl: String): Boolean {
        val l = baseUrl.lowercase()
        return "torbox" in l || "tb=" in l || "debridprovider=torbox" in l
    }

    /** The worst of several source states for one host (several instances on one host). */
    fun worst(states: List<SourceState>): SourceState? = states.maxByOrNull {
        when (it) { SourceState.DOWN -> 4; SourceState.DEGRADED -> 3; SourceState.MAINTENANCE -> 2; SourceState.UP -> 1; SourceState.UNKNOWN -> 0 }
    }

    /** Saved sources as one string: one per line, "1|address" or "0|address". Pure, for tests. */
    fun encodeSources(list: List<HealthSourceSetting>): String =
        list.take(MAX_SOURCES).joinToString("\n") { (if (it.enabled) "1|" else "0|") + it.address }

    fun decodeSources(raw: String?): List<HealthSourceSetting> =
        raw.orEmpty().split('\n').mapNotNull { line ->
            val on = line.startsWith("1|"); val off = line.startsWith("0|")
            if (!on && !off) return@mapNotNull null
            (checkSourceAddress(line.drop(2)) as? Checked.Ok)?.let { HealthSourceSetting(it.address, on) }
        }.distinctBy { it.address }.take(MAX_SOURCES).let { if (com.nuvio.tv.NuvioCFeatures.HEALTH_CHAIN_V68) it else oneActive(it) }

    // ---- [fork] round 68: hold of 5 minutes and check order --------------------------------------------------------
    const val HOLD_MS = 5 * 60_000L

    /** How long a finished check is still held (0 = a new check may run). Pure, for tests. */
    fun holdLeft(now: Long, checkedAt: Long?): Long {
        if (checkedAt == null) return 0L
        val left = checkedAt + HOLD_MS - now
        return if (left > 0 && left <= HOLD_MS) left else 0L // a clock that went back never holds longer than 5 min
    }

    /** Move [address] one place up ([up]) or down in the check order; ends stay put. Pure, for tests. */
    fun move(list: List<HealthSourceSetting>, address: String, up: Boolean): List<HealthSourceSetting> {
        val i = list.indexOfFirst { it.address == address }
        val j = if (up) i - 1 else i + 1
        if (i < 0 || j !in list.indices) return list
        return list.toMutableList().also { val t = it[i]; it[i] = it[j]; it[j] = t }
    }

    /** Switch one source on or off, leaving the others as they are (any number can be on). Pure, for tests. */
    fun toggle(list: List<HealthSourceSetting>, address: String, on: Boolean): List<HealthSourceSetting> =
        list.map { if (it.address == address) it.copy(enabled = on) else it }

    /** Position in the check order of each switched-on source (1, 2…), null for one that's off. Pure, for tests. */
    fun positions(list: List<HealthSourceSetting>): List<Int?> {
        var n = 0
        return list.map { if (it.enabled) ++n else null }
    }

    /** A reading that can decide anything this run: answered, readable, not stale, not rate-limited. Pure, for tests. */
    fun usable(r: SourceReading): Boolean = r.error == null && !r.stale && r.services.isNotEmpty()

    /** A clear state from a source (UNKNOWN hands the item to the next source). */
    fun decisive(state: SourceState?): Boolean = state != null && state != SourceState.UNKNOWN
}
