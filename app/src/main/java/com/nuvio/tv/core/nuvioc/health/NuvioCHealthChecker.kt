package com.nuvio.tv.core.nuvioc.health

// [fork] Nuvio C round 67 `ADDON_HEALTH`: runs the checks each time the Add-ons page opens and on Check again; never
// in the background. Per add-on the TV GETs the manifest it is installed from (3 at a time, 6 s, timed); each
// enabled status source gets one GET per run, parsed on Dispatchers.Default and matched by hostname on the TV.
// TorBox: the account call (key only to api.torbox.app, in the Authorization header) and, when something says
// TorBox is unwell, its Better Stack page for the incident. Addresses are logged and shown as hostnames only.

import android.content.Context
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.data.local.DebridSettingsDataStore
import com.nuvio.tv.domain.repository.AddonRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Result of "Test and add": the line to show and, when it worked, the address to save. */
data class SourceTestResult(val ok: Boolean, val address: String?, val format: SourceFormat?, val services: Int, val matches: Int, val reason: String?)

@Singleton
class NuvioCHealthChecker @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val addons: AddonRepository,
    private val debrid: DebridSettingsDataStore,
    private val profiles: ProfileManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(HealthState())
    val state: StateFlow<HealthState> = _state.asStateFlow()

    private val profile: Int get() = profiles.activeProfileId.value

    fun sources(): List<HealthSourceSetting> = NuvioCHealthSources.load(context, profile)

    fun saveSources(list: List<HealthSourceSetting>) = NuvioCHealthSources.save(context, profile, list)

    /** Start a run unless one is going. Fire and forget; the state flow carries the result. */
    fun runNow() {
        if (!com.nuvio.tv.NuvioCFeatures.ADDON_HEALTH) return
        if (_state.value.running) return
        scope.launch { runCatching { run() }.onFailure { _state.update { it.copy(running = false) } } }
    }

    private suspend fun run() {
        val p = profile
        val installed = addons.getInstalledAddons().first()
        // the space for each card's status is held from the first frame: everything starts as Checking (or Off)
        val start = installed.map { a ->
            val host = NuvioCHealthRules.hostOnly(a.baseUrl)
            val tv = if (a.enabled) TvCheck.RUNNING else TvCheck.OFF
            AddonHealth(a.baseUrl, NuvioCHealthRules.cleanText(a.displayName) ?: host, host, tv, viaTorBox = NuvioCHealthRules.usesTorBox(a.baseUrl), verdict = NuvioCHealthRules.verdict(tv, null))
        }
        _state.update { it.copy(running = true, addons = start) }
        val gate = Semaphore(NuvioCHealthRules.PARALLEL)
        val key = runCatching { debrid.settings.first().torboxApiKey }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        val (readings, tv, account) = kotlinx.coroutines.coroutineScope {
            val src = sources().filter { it.enabled }.map { s -> async { readSource(s.address) } }
            val tvJobs = start.filter { it.tv == TvCheck.RUNNING }.map { a -> async { gate.withPermit { a.baseUrl to tvCheck(a.baseUrl) } } }
            val acc = async { key?.let { torBoxAccount(it) } }
            Triple(src.awaitAll(), tvJobs.awaitAll().toMap(), acc.await())
        }
        if (profile != p) { _state.update { it.copy(running = false) }; return } // profile switched mid-run: drop it
        _state.value = withContext(Dispatchers.Default) { combine(start, tv, readings, account, key != null) }
            .let { s -> val t = s.torBox; if (t != null && t.unwell) s.copy(torBox = torBoxIncident(t)) else s }
            .copy(running = false, checkedAtMs = System.currentTimeMillis())
    }

    /** The TV's own check: GET the add-on's manifest, timed. */
    private suspend fun tvCheck(baseUrl: String): Triple<TvCheck, Int?, String?> = withContext(Dispatchers.IO) {
        val r = NuvioCHealthHttp.get(NuvioCHealthRules.manifestUrl(baseUrl))
        val valid = r.code in 200..299 && r.body?.let { looksLikeManifest(it) } == true
        val check = NuvioCHealthRules.tvCheck(r.code, valid)
        val reason = when {
            check == TvCheck.OK -> null
            r.code in 200..299 && !valid -> "Not a manifest"
            else -> r.reason
        }
        Triple(check, r.latencyMs, reason)
    }

    private fun looksLikeManifest(body: String): Boolean = runCatching {
        val o = NuvioCHealthParsers.json.parseToJsonElement(body) as? kotlinx.serialization.json.JsonObject ?: return false
        o.containsKey("id") && (o.containsKey("resources") || o.containsKey("catalogs") || o.containsKey("name"))
    }.getOrDefault(false)

    /** One run's read of a source: its remembered format first (one GET), else detection on the same host. */
    private suspend fun readSource(address: String): SourceReading {
        val now = System.currentTimeMillis()
        val known = NuvioCHealthSources.format(context, address)
        val candidates = NuvioCHealthParsers.candidates(address).let { all -> if (known != null) all.filter { it.format == known } + all.filter { it.format != known } else all }
        for (c in candidates) { // the remembered format first: one GET; the others only if it stopped answering
            val got = fetchParsed(c) ?: continue
            if (known != c.format) NuvioCHealthSources.rememberFormat(context, address, c.format)
            return SourceReading(address, NuvioCHealthParsers.sourceName(got, address), c.format.label, got.services, now, got.stale)
        }
        return SourceReading(address, NuvioCHealthRules.hostOnly(address), "", emptyList(), now, error = "Not a readable status page")
    }

    private suspend fun fetchParsed(c: FormatCandidate): NuvioCHealthParsers.Parsed? {
        val r = withContext(Dispatchers.IO) { NuvioCHealthHttp.get(c.url) }
        val body = r.body?.takeIf { r.code in 200..299 } ?: return null
        val extra = c.extraUrl?.let { u -> withContext(Dispatchers.IO) { NuvioCHealthHttp.get(u) }.body }
        return withContext(Dispatchers.Default) { NuvioCHealthParsers.parse(c.format, body, extra) }?.takeIf { it.services.isNotEmpty() }
    }

    /** "Test and add": check the address, read it, count services and matches. Nothing is saved here. */
    suspend fun testSource(raw: String): SourceTestResult {
        val checked = NuvioCHealthRules.checkSourceAddress(raw)
        if (checked is NuvioCHealthRules.Checked.Refused) return SourceTestResult(false, null, null, 0, 0, refusalText(checked.why))
        val address = (checked as NuvioCHealthRules.Checked.Ok).address
        if (sources().any { it.address == address }) return SourceTestResult(false, null, null, 0, 0, "Already added")
        if (sources().size >= NuvioCHealthRules.MAX_SOURCES) return SourceTestResult(false, null, null, 0, 0, "Up to 4 sources")
        for (c in NuvioCHealthParsers.candidates(address)) {
            val got = fetchParsed(c) ?: continue
            NuvioCHealthSources.rememberFormat(context, address, c.format)
            val hosts = addons.getInstalledAddons().first().map { NuvioCHealthRules.hostOnly(it.baseUrl).lowercase() }
            val matches = hosts.count { h -> NuvioCHealthRules.matchService(h, got.services) != null }
            return SourceTestResult(true, address, c.format, got.services.size, matches, null)
        }
        return SourceTestResult(false, null, null, 0, 0, "Not a readable status page")
    }

    fun refusalText(r: NuvioCHealthRules.Refusal): String = when (r) {
        NuvioCHealthRules.Refusal.EMPTY -> "Type an address"
        NuvioCHealthRules.Refusal.TOO_LONG -> "Address too long"
        NuvioCHealthRules.Refusal.NOT_HTTPS -> "Only https addresses"
        NuvioCHealthRules.Refusal.NOT_AN_ADDRESS -> "Not a valid address"
        NuvioCHealthRules.Refusal.HAS_LOGIN -> "No user name or password in the address"
        NuvioCHealthRules.Refusal.HAS_KEY -> "No keys or tokens in the address"
    }

    // ---- TorBox --------------------------------------------------------------------------------------------------
    private suspend fun torBoxAccount(key: String): TorBoxHealth = withContext(Dispatchers.IO) {
        val r = NuvioCHealthHttp.get(NuvioCHealthRules.TORBOX_ME_URL, bearer = key)
        val parsed = r.body?.takeIf { r.code in 200..299 }?.let { NuvioCHealthParsers.parseTorBoxMe(it) }
        val days = parsed?.second?.let { iso -> runCatching { ChronoUnit.DAYS.between(OffsetDateTime.now(ZoneOffset.UTC), OffsetDateTime.parse(iso)).toInt() }.getOrNull() }
        TorBoxHealth(reachable = r.code in 200..299, plan = parsed?.first, daysLeft = days?.coerceAtLeast(0), latencyMs = r.latencyMs,
            unwell = r.code == null || (r.code ?: 0) >= 500)
    }

    private suspend fun torBoxIncident(t: TorBoxHealth): TorBoxHealth = withContext(Dispatchers.IO) {
        val r = NuvioCHealthHttp.get(NuvioCHealthRules.TORBOX_STATUS_URL)
        val nowIso = java.time.Instant.now().toString()
        val got = r.body?.takeIf { r.code in 200..299 }?.let { b -> withContext(Dispatchers.Default) { NuvioCHealthParsers.betterStackOverall(b, nowIso) } }
            ?: return@withContext t
        val incident = got.second.firstOrNull { !it.maintenance }
        val maint = got.second.firstOrNull { it.maintenance }
        t.copy(incident = incident?.title ?: maint?.title, incidentSince = incident?.startsAt, maintenanceUntil = maint?.endsAt)
    }

    // ---- combine -------------------------------------------------------------------------------------------------
    private fun combine(
        start: List<AddonHealth>, tv: Map<String, Triple<TvCheck, Int?, String?>>, readings: List<SourceReading>,
        account: TorBoxHealth?, hasKey: Boolean
    ): HealthState = NuvioCHealthCombine.combine(start, tv, readings, account, hasKey)
}

/** The merge of one run, pure, for tests. */
object NuvioCHealthCombine {
    private val TRACKERS = listOf("trakt", "simkl", "mdblist", "nuvio")

    fun isTracker(s: SourceService) = TRACKERS.any { s.name.contains(it, ignoreCase = true) || s.host?.contains(it) == true }
    fun isTorBox(s: SourceService) = s.name.contains("torbox", ignoreCase = true) && (s.host == null || s.host.endsWith("torbox.app"))

    fun combine(
        start: List<AddonHealth>, tv: Map<String, Triple<TvCheck, Int?, String?>>, readings: List<SourceReading>,
        account: TorBoxHealth?, hasKey: Boolean
    ): HealthState {
        val all = readings.flatMap { r -> r.services.map { r to it } }
        val torBoxService = all.map { it.second }.filter { isTorBox(it) && !it.name.contains("manager", true) }
        val torBoxSourceUnwell = torBoxService.any { it.state == SourceState.DOWN || it.state == SourceState.DEGRADED || it.state == SourceState.MAINTENANCE }
        val torBox = when {
            account != null -> account.copy(unwell = account.unwell || torBoxSourceUnwell)
            torBoxSourceUnwell || torBoxService.isNotEmpty() -> TorBoxHealth(reachable = !torBoxSourceUnwell, unwell = torBoxSourceUnwell)
            else -> null
        }.takeIf { hasKey || torBoxService.isNotEmpty() }
        val matched = HashSet<SourceService>()
        val addons = start.map { a ->
            val check = tv[a.baseUrl]
            val t = check?.first ?: a.tv
            val perSource = readings.mapNotNull { r -> NuvioCHealthRules.matchService(a.host, r.services)?.let { r to it } }
            perSource.forEach { matched += it.second }
            val src = NuvioCHealthRules.worst(perSource.map { it.second.state })
            val srcName = perSource.firstOrNull { it.second.state == src }?.first?.name
            val reason = when {
                torBox?.unwell == true && a.viaTorBox && t != TvCheck.OK && t != TvCheck.OFF -> "TorBox is having trouble"
                else -> check?.third
            }
            a.copy(tv = t, tvLatencyMs = check?.second, tvReason = reason, source = src, sourceName = srcName, verdict = NuvioCHealthRules.verdict(t, src))
        }
        val others = all.map { it.second }.filter { it !in matched && !isTracker(it) && !isTorBox(it) }.distinctBy { it.name to it.host }
        val problems = others.filter { it.state == SourceState.DOWN || it.state == SourceState.DEGRADED || it.state == SourceState.MAINTENANCE }
        return HealthState(
            addons = addons, // card order; the summary sorts problems first with [order]
            sources = readings,
            torBox = torBox,
            trackers = all.map { it.second }.filter { isTracker(it) }.distinctBy { it.name },
            elsewhere = problems.take(20),
            elsewhereWorking = others.size - problems.size
        )
    }

    /** Problems first in the summary. */
    fun order(v: HealthVerdict): Int = when (v) {
        HealthVerdict.DOWN -> 0; HealthVerdict.BLOCKED -> 1; HealthVerdict.RATE_LIMITED -> 2; HealthVerdict.CANT_REACH -> 3
        HealthVerdict.CHECKING -> 4; HealthVerdict.WORKING_SOURCE_DISAGREES -> 5; HealthVerdict.WORKING -> 6; HealthVerdict.OFF -> 7
    }
}
