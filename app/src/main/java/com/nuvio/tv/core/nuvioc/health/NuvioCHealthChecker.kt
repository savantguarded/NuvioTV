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
    private val profiles: ProfileManager,
    // [fork] round 67b: what this profile uses
    private val trakt: com.nuvio.tv.data.local.TraktAuthDataStore,
    private val simkl: com.nuvio.tv.data.simkl.SimklAuthRepository,
    private val mdblist: com.nuvio.tv.data.local.MDBListSettingsDataStore,
    private val auth: com.nuvio.tv.core.auth.AuthManager,
    private val server: com.nuvio.tv.domain.model.ServerConfiguration
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(HealthState())
    val state: StateFlow<HealthState> = _state.asStateFlow()

    private val profile: Int get() = profiles.activeProfileId.value

    fun sources(): List<HealthSourceSetting> = NuvioCHealthSources.load(context, profile)

    fun saveSources(list: List<HealthSourceSetting>) = NuvioCHealthSources.save(context, profile, list)

    /** [fork] round 68 HEALTH_HOLD_5MIN: the profile and time of the last finished check (memory only). */
    @Volatile private var heldProfile: Int? = null
    @Volatile private var heldAt: Long? = null

    /** Milliseconds this profile's last check is still held for (0 = Check Again works). */
    fun holdLeft(now: Long = System.currentTimeMillis()): Long {
        if (!com.nuvio.tv.NuvioCFeatures.HEALTH_HOLD_5MIN || heldProfile != profile) return 0L
        return NuvioCHealthRules.holdLeft(now, heldAt)
    }

    /** When a new check may run (now or later), for the greyed Check Again. */
    fun canRunAt(now: Long = System.currentTimeMillis()): Long = now + holdLeft(now)

    /**
     * Start a run unless one is going. Fire and forget; the state flow carries the result. Round 68: a page open or
     * Check Again inside the 5-minute hold does nothing; [force] (a status source switched, moved, added or removed)
     * always runs.
     */
    fun runNow(force: Boolean = false) {
        if (!com.nuvio.tv.NuvioCFeatures.ADDON_HEALTH) return
        if (_state.value.running) return
        if (!force && holdLeft() > 0) return
        scope.launch { runCatching { if (com.nuvio.tv.NuvioCFeatures.HEALTH_CHAIN_V68) runChain() else run() }.onFailure { _state.update { it.copy(running = false) } } }
    }

    private fun hold(p: Int, at: Long) { heldProfile = p; heldAt = at }

    /** [fork] round 68 requests audit: GETs made in the current run, logged once per run (debug log only). */
    private val calls = java.util.concurrent.atomic.AtomicInteger()
    private fun get(address: String, bearer: String? = null, readBody: Boolean = true): HealthResponse {
        calls.incrementAndGet()
        return NuvioCHealthHttp.get(address, bearer, readBody)
    }

    /**
     * [fork] round 68 HEALTH_CHAIN_V68: every switched-on source is read once, in parallel; for each item the first
     * usable source in order with a clear state decides; whatever is left is checked by This TV (3 at a time, 6 s).
     * TorBox's plan / days always come from its own account call.
     */
    private suspend fun runChain() {
        val p = profile
        calls.set(0)
        val installed = addons.getInstalledAddons().first()
        val on = sources().filter { it.enabled }
        val usedServices = usedServices()
        val start = installed.map { a ->
            val host = NuvioCHealthRules.hostOnly(a.baseUrl)
            val tv = if (a.enabled) TvCheck.RUNNING else TvCheck.OFF
            AddonHealth(a.baseUrl, NuvioCHealthRules.cleanText(a.displayName) ?: host, host, tv, viaTorBox = NuvioCHealthRules.usesTorBox(a.baseUrl), verdict = NuvioCHealthRules.verdictFromTv(tv))
        }
        _state.update { it.copy(running = true, addons = start, services = usedServices.map { u -> ServiceHealth(u.key, u.group, u.name, host = NuvioCHealthCombine.hostOf(u)) }) }
        val key = runCatching { debrid.settings.first().torboxApiKey }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        val (readings, account) = kotlinx.coroutines.coroutineScope {
            val src = on.map { s -> async { readSource(s.address) } }
            val acc = async { key?.let { torBoxAccount(it) } }
            src.awaitAll() to acc.await()
        }
        val usable = readings.filter { NuvioCHealthRules.usable(it) }
        val (addonsLeft, servicesLeft) = NuvioCHealthCombine.leftovers(start, usedServices, usable)
        val gate = Semaphore(NuvioCHealthRules.PARALLEL)
        val tv = kotlinx.coroutines.coroutineScope {
            (addonsLeft.map { a -> async { gate.withPermit { a.baseUrl to tvCheck(a.baseUrl) } } } +
                servicesLeft.filter { it.probe != null }.map { u -> async { gate.withPermit { "svc:" + u.key to serviceCheck(u.probe!!) } } }).awaitAll().toMap()
        }
        if (profile != p) { _state.update { it.copy(running = false) }; return }
        val at = System.currentTimeMillis()
        _state.value = withContext(Dispatchers.Default) { NuvioCHealthCombine.combineChain(start, usedServices, readings, tv, account) }
            .copy(running = false, checkedAtMs = at)
        hold(p, at)
        android.util.Log.d("NuvioCHealth", "run: ${on.size} sources (${usable.size} usable), ${calls.get()} GETs, TV checked ${addonsLeft.size} add-ons + ${servicesLeft.count { it.probe != null }} services")
    }

    private suspend fun run() {
        val p = profile
        val installed = addons.getInstalledAddons().first()
        // [fork] round 67b: one source decides per run: the active status source, or (none on) this TV's own checks
        val active = sources().firstOrNull { it.enabled }
        val usedServices = usedServices()
        // the space for each card's status is held from the first frame: everything starts as Checking (or Off)
        val start = installed.map { a ->
            val host = NuvioCHealthRules.hostOnly(a.baseUrl)
            val tv = if (a.enabled) TvCheck.RUNNING else TvCheck.OFF
            AddonHealth(a.baseUrl, NuvioCHealthRules.cleanText(a.displayName) ?: host, host, tv, viaTorBox = NuvioCHealthRules.usesTorBox(a.baseUrl), verdict = NuvioCHealthRules.verdictFromTv(tv))
        }
        _state.update { it.copy(running = true, addons = start, services = usedServices.map { u -> ServiceHealth(u.key, u.group, u.name) }) }
        val gate = Semaphore(NuvioCHealthRules.PARALLEL)
        val key = runCatching { debrid.settings.first().torboxApiKey }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        val (reading, tv, account) = kotlinx.coroutines.coroutineScope {
            val src = active?.let { a -> async { readSource(a.address) } }
            val tvJobs = if (active != null) emptyList() else
                start.filter { it.tv == TvCheck.RUNNING }.map { a -> async { gate.withPermit { a.baseUrl to tvCheck(a.baseUrl) } } } +
                usedServices.filter { it.probe != null }.map { u -> async { gate.withPermit { "svc:" + u.key to serviceCheck(u.probe!!) } } }
            val acc = async { key?.let { torBoxAccount(it) } }
            Triple(src?.await(), tvJobs.awaitAll().toMap(), acc.await())
        }
        if (profile != p) { _state.update { it.copy(running = false) }; return } // profile switched mid-run: drop it
        val at = System.currentTimeMillis()
        _state.value = withContext(Dispatchers.Default) { NuvioCHealthCombine.combine(start, usedServices, tv, reading, account) }
            .copy(running = false, checkedAtMs = at)
        hold(p, at)
    }

    /** [fork] round 67b: what this profile uses besides add-ons: TorBox (key saved), signed-in trackers and sync, the sites the app calls. */
    private suspend fun usedServices(): List<UsedService> = buildList {
        val tb = runCatching { debrid.settings.first().torboxApiKey }.getOrNull()?.isNotBlank() == true
        if (tb) add(UsedService("torbox", ServiceGroup.DEBRID, "TorBox", null, listOf("torbox.app")))
        if (runCatching { trakt.isAuthenticated.first() }.getOrDefault(false))
            add(UsedService("trakt", ServiceGroup.TRACKING, "Trakt", "https://api.trakt.tv/", listOf("trakt.tv")))
        if (runCatching { simkl.state.value.isAuthenticated }.getOrDefault(false))
            add(UsedService("simkl", ServiceGroup.TRACKING, "Simkl", "https://api.simkl.com/", listOf("simkl.com")))
        if (runCatching { mdblist.settings.first().let { it.enabled && it.apiKey.isNotBlank() } }.getOrDefault(false))
            add(UsedService("mdblist", ServiceGroup.TRACKING, "MDBList", "https://api.mdblist.com/", listOf("mdblist.com")))
        if (runCatching { auth.isAuthenticated }.getOrDefault(false)) {
            val backend = runCatching { server.backendUrl }.getOrNull()?.takeIf { it.startsWith("https://") }
            add(UsedService("nuvio", ServiceGroup.TRACKING, "Nuvio sync", backend?.trimEnd('/')?.plus("/"), listOfNotNull(backend?.let { NuvioCHealthRules.hostOnly(it) }, "nuvio")))
        }
        add(UsedService("tmdb", ServiceGroup.WEBSITE, "TMDB", "https://api.themoviedb.org/3/", listOf("themoviedb.org", "tmdb")))
        if (com.nuvio.tv.NuvioCFeatures.LIBRARY_CALENDAR)
            add(UsedService("tvmaze", ServiceGroup.WEBSITE, "TVmaze", "https://api.tvmaze.com/", listOf("tvmaze.com")))
    }

    private suspend fun serviceCheck(url: String): Triple<TvCheck, Int?, String?> = withContext(Dispatchers.IO) {
        val r = get(url, readBody = false)
        val check = NuvioCHealthRules.tvCheckService(r.code)
        Triple(check, r.latencyMs, if (check == TvCheck.OK) null else r.reason)
    }

    /** The TV's own check: GET the add-on's manifest, timed. */
    private suspend fun tvCheck(baseUrl: String): Triple<TvCheck, Int?, String?> = withContext(Dispatchers.IO) {
        val r = get(NuvioCHealthRules.manifestUrl(baseUrl))
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
            val got = fetchParsed(c)
            if (got === RATE_LIMITED) // [fork] round 68: a 429 ends this source for the run, no other format is tried
                return SourceReading(address, NuvioCHealthRules.hostOnly(address), "", emptyList(), now, error = "Rate-limited")
            if (got == null) continue
            if (known != c.format) NuvioCHealthSources.rememberFormat(context, address, c.format)
            return SourceReading(address, NuvioCHealthParsers.sourceName(got, address), c.format.label, got.services, now, got.stale)
        }
        return SourceReading(address, NuvioCHealthRules.hostOnly(address), "", emptyList(), now, error = "Not a readable status page")
    }

    private val RATE_LIMITED = NuvioCHealthParsers.Parsed(null, emptyList())

    private suspend fun fetchParsed(c: FormatCandidate): NuvioCHealthParsers.Parsed? {
        val r = withContext(Dispatchers.IO) { get(c.url) }
        if (r.code == 429 && com.nuvio.tv.NuvioCFeatures.HEALTH_CHAIN_V68) return RATE_LIMITED
        val body = r.body?.takeIf { r.code in 200..299 } ?: return null
        val extra = c.extraUrl?.let { u -> withContext(Dispatchers.IO) { get(u) }.body }
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
            val got = fetchParsed(c)
            if (got === RATE_LIMITED) return SourceTestResult(false, null, null, 0, 0, "Rate-limited, try again later")
            if (got == null) continue
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
        val r = get(NuvioCHealthRules.TORBOX_ME_URL, bearer = key)
        val parsed = r.body?.takeIf { r.code in 200..299 }?.let { NuvioCHealthParsers.parseTorBoxMe(it) }
        val days = parsed?.second?.let { iso -> runCatching { ChronoUnit.DAYS.between(OffsetDateTime.now(ZoneOffset.UTC), OffsetDateTime.parse(iso)).toInt() }.getOrNull() }
        TorBoxHealth(reachable = r.code in 200..299, plan = parsed?.first, daysLeft = days?.coerceAtLeast(0), latencyMs = r.latencyMs,
            unwell = r.code == null || (r.code ?: 0) >= 500)
    }

}

/** [fork] round 67b: something this profile uses besides add-ons. [probe] = address this TV checks; [match] = host or name hints on a status source. */
data class UsedService(val key: String, val group: ServiceGroup, val name: String, val probe: String?, val match: List<String>)

/** The merge of one run, pure, for tests. One source decides: [reading] (a status source) or, when null, [tv]. */
object NuvioCHealthCombine {
    fun combine(
        start: List<AddonHealth>, used: List<UsedService>, tv: Map<String, Triple<TvCheck, Int?, String?>>,
        reading: SourceReading?, account: TorBoxHealth?
    ): HealthState {
        val services = reading?.services.orEmpty()
        val torBoxTrouble: Boolean
        val usedHealth = used.map { u ->
            val (verdict, reason) = if (reading != null) {
                NuvioCHealthRules.verdictFromSource(matchUsed(u, services)?.state) to null
            } else if (u.key == "torbox") {
                when {
                    account == null -> HealthVerdict.NOT_TRACKED to null
                    account.reachable -> HealthVerdict.WORKING to null
                    account.unwell -> HealthVerdict.CANT_REACH to "No answer"
                    else -> HealthVerdict.BLOCKED to "Key refused"
                }
            } else {
                val c = tv["svc:" + u.key]
                NuvioCHealthRules.verdictFromTv(c?.first ?: TvCheck.RUNNING) to c?.third
            }
            val sub = if (u.key == "torbox") account?.let { a -> listOfNotNull(a.plan, a.daysLeft?.let { if (it == 1) "1 day left" else "$it days left" }).joinToString(" · ").ifBlank { null } } else null
            ServiceHealth(u.key, u.group, u.name, sub, verdict, reason)
        }
        torBoxTrouble = usedHealth.any { it.key == "torbox" && NuvioCHealthRules.tone(it.verdict).let { t -> t == HealthTone.AMBER || t == HealthTone.RED } }
        val addons = start.map { a ->
            if (a.tv == TvCheck.OFF) return@map a.copy(verdict = HealthVerdict.OFF)
            if (reading != null) {
                val svc = NuvioCHealthRules.matchService(a.host, services)
                a.copy(tv = TvCheck.OK, source = svc?.state, sourceName = reading.name, tvReason = if (torBoxTrouble && a.viaTorBox) "TorBox is having trouble" else null,
                    verdict = NuvioCHealthRules.verdictFromSource(svc?.state))
            } else {
                val check = tv[a.baseUrl]
                val t = check?.first ?: a.tv
                val reason = if (torBoxTrouble && a.viaTorBox && t != TvCheck.OK) "TorBox is having trouble" else check?.third
                a.copy(tv = t, tvLatencyMs = check?.second, tvReason = reason, verdict = NuvioCHealthRules.verdictFromTv(t))
            }
        }
        return HealthState(
            addons = addons, // card order; the summary sorts problems first with [order]
            sources = listOfNotNull(reading),
            torBox = account,
            services = usedHealth,
            sourceName = reading?.name
        )
    }

    // ---- [fork] round 68 HEALTH_CHAIN_V68 ----------------------------------------------------------------------------
    /** Hostname shown under a used service's name, never a path. */
    fun hostOf(u: UsedService): String? = when {
        u.key == "torbox" -> NuvioCHealthRules.TORBOX_API_HOST
        u.probe != null -> NuvioCHealthRules.hostOnly(u.probe).takeIf { it != "?" }
        else -> null
    }

    /** The first usable source, in order, with a clear state for an add-on host; null = This TV checks it. */
    fun pickAddon(host: String, usable: List<SourceReading>): Pair<SourceService, SourceReading>? =
        usable.firstNotNullOfOrNull { r -> NuvioCHealthRules.matchService(host, r.services)?.takeIf { NuvioCHealthRules.decisive(it.state) }?.let { it to r } }

    /** The same for a used service (TorBox, trackers, sync, websites). */
    fun pickUsed(u: UsedService, usable: List<SourceReading>): Pair<SourceService, SourceReading>? =
        usable.firstNotNullOfOrNull { r -> matchUsed(u, r.services)?.takeIf { NuvioCHealthRules.decisive(it.state) }?.let { it to r } }

    /** What This TV still has to check after the sources: enabled add-ons and used services no source decided. */
    fun leftovers(start: List<AddonHealth>, used: List<UsedService>, usable: List<SourceReading>): Pair<List<AddonHealth>, List<UsedService>> =
        start.filter { it.tv == TvCheck.RUNNING && pickAddon(it.host, usable) == null } to used.filter { pickUsed(it, usable) == null }

    /** The merge of a chained run, pure, for tests. Unusable readings ([NuvioCHealthRules.usable]) decide nothing. */
    fun combineChain(
        start: List<AddonHealth>, used: List<UsedService>, readings: List<SourceReading>,
        tv: Map<String, Triple<TvCheck, Int?, String?>>, account: TorBoxHealth?
    ): HealthState {
        val usable = readings.filter { NuvioCHealthRules.usable(it) }
        val usedHealth = used.map { u ->
            val picked = pickUsed(u, usable)
            val (verdict, reason) = when {
                picked != null -> NuvioCHealthRules.verdictFromSource(picked.first.state) to null
                u.key == "torbox" -> when {
                    account == null -> HealthVerdict.NOT_TRACKED to null
                    account.reachable -> HealthVerdict.WORKING to null
                    account.unwell -> HealthVerdict.CANT_REACH to "No answer"
                    else -> HealthVerdict.BLOCKED to "Key refused"
                }
                else -> tv["svc:" + u.key].let { c -> NuvioCHealthRules.verdictFromTv(c?.first ?: TvCheck.RUNNING) to c?.third }
            }
            val sub = if (u.key == "torbox") account?.let { a -> listOfNotNull(a.plan, a.daysLeft?.let { if (it == 1) "1 day left" else "$it days left" }).joinToString(" · ").ifBlank { null } } else null
            ServiceHealth(u.key, u.group, u.name, sub, verdict, reason, host = hostOf(u), decidedBy = picked?.second?.name)
        }
        val torBoxTrouble = usedHealth.any { it.key == "torbox" && NuvioCHealthRules.tone(it.verdict).let { t -> t == HealthTone.AMBER || t == HealthTone.RED } }
        val addons = start.map { a ->
            if (a.tv == TvCheck.OFF) return@map a.copy(verdict = HealthVerdict.OFF)
            val picked = pickAddon(a.host, usable)
            if (picked != null) {
                val v = NuvioCHealthRules.verdictFromSource(picked.first.state)
                a.copy(tv = TvCheck.OK, source = picked.first.state, sourceName = picked.second.name, verdict = v,
                    tvReason = if (torBoxTrouble && a.viaTorBox && v != HealthVerdict.WORKING) "TorBox is having trouble" else null)
            } else {
                val check = tv[a.baseUrl]
                val t = check?.first ?: a.tv
                val reason = if (torBoxTrouble && a.viaTorBox && t != TvCheck.OK) "TorBox is having trouble" else check?.third
                a.copy(tv = t, tvLatencyMs = check?.second, tvReason = reason, verdict = NuvioCHealthRules.verdictFromTv(t), sourceName = null)
            }
        }
        return HealthState(addons = addons, sources = readings, torBox = account, services = usedHealth, sourceName = null)
    }

    /** A used service on a status source: by host first, then by name. */
    fun matchUsed(u: UsedService, services: List<SourceService>): SourceService? =
        services.firstOrNull { s -> s.host != null && u.match.any { m -> s.host == m || s.host.endsWith(".$m") } }
            ?: services.firstOrNull { s -> u.match.any { m -> s.name.contains(m.substringBefore('.'), ignoreCase = true) } || s.name.equals(u.name, ignoreCase = true) }

    /** Problems first in the summary. */
    fun order(v: HealthVerdict): Int = when (v) {
        HealthVerdict.DOWN -> 0; HealthVerdict.BLOCKED -> 1; HealthVerdict.RATE_LIMITED -> 2; HealthVerdict.CANT_REACH -> 3
        HealthVerdict.TROUBLE -> 3; HealthVerdict.MAINTENANCE -> 3
        HealthVerdict.CHECKING -> 4; HealthVerdict.WORKING_SOURCE_DISAGREES -> 5; HealthVerdict.WORKING -> 6
        HealthVerdict.NOT_TRACKED -> 7; HealthVerdict.OFF -> 8
    }
}
