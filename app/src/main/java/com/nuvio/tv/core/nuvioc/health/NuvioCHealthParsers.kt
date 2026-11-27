package com.nuvio.tv.core.nuvioc.health

// [fork] Nuvio C round 67 `ADDON_HEALTH`: status source formats. Each answer is parsed strictly into fixed models
// (unknown keys ignored, nothing lenient); nothing in it is run, rendered as HTML or followed: links and images are
// ignored, text is cleaned to one short line and numbers are clamped. A hostile source can only change colours and
// text. Format detection tries each known API on the SAME host as the address you pasted.

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

enum class SourceFormat(val label: String) {
    IBBYLABS("IbbyLabs status"), GATUS("Gatus status page"), UPTIME_KUMA("Uptime Kuma status page"),
    BETTER_STACK("Better Stack status page"), STATUSPAGE("Atlassian Statuspage"), INSTATUS("Instatus page")
}

/** One address to try for a format; Uptime Kuma also needs its heartbeat address. */
data class FormatCandidate(val format: SourceFormat, val url: String, val extraUrl: String? = null)

object NuvioCHealthParsers {
    val json = Json { ignoreUnknownKeys = true; isLenient = false }

    /**
     * Candidates to try, in order, all on the pasted address's own host (never another host). Uptime Kuma's slug is
     * the last path segment after /status/. Pure, for tests.
     */
    fun candidates(address: String): List<FormatCandidate> {
        val u = address.toHttpUrlOrNull() ?: return emptyList()
        val root = "https://${u.host}" + (if (u.port != 443) ":${u.port}" else "")
        val segs = u.pathSegments.filter { it.isNotBlank() }
        val kumaSlug = segs.indexOf("status").takeIf { it >= 0 }?.let { segs.getOrNull(it + 1) }
        return buildList {
            add(FormatCandidate(SourceFormat.IBBYLABS, "$root/v1/status"))
            add(FormatCandidate(SourceFormat.GATUS, "$root/api/v1/endpoints/statuses?page=1&pageSize=1"))
            if (kumaSlug != null && kumaSlug.matches(Regex("[A-Za-z0-9_-]{1,64}")))
                add(FormatCandidate(SourceFormat.UPTIME_KUMA, "$root/api/status-page/$kumaSlug", "$root/api/status-page/heartbeat/$kumaSlug"))
            add(FormatCandidate(SourceFormat.BETTER_STACK, "$root/index.json"))
            add(FormatCandidate(SourceFormat.STATUSPAGE, "$root/api/v2/summary.json"))
            add(FormatCandidate(SourceFormat.INSTATUS, "$root/summary.json"))
        }
    }

    private fun txt(s: String?) = NuvioCHealthRules.cleanText(s)

    /** Parsed services and a name, or null when the body isn't this format. */
    data class Parsed(val name: String?, val services: List<SourceService>, val stale: Boolean = false)

    // ---- IbbyLabs /v1/status ------------------------------------------------------------------------------------
    @Serializable private data class IbbyRoot(val source: IbbySource? = null, val services: List<IbbyService>? = null, val freshness: IbbyFresh? = null)
    @Serializable private data class IbbySource(val name: String? = null)
    @Serializable private data class IbbyFresh(val stale: Boolean? = null)
    @Serializable private data class IbbyService(
        val name: String? = null, val url: String? = null, val group: String? = null, val displayState: String? = null,
        val knownIssue: JsonElement? = null, val maintenance: JsonElement? = null,
        val last: IbbyLast? = null, val uptimeWindows: IbbyUptime? = null
    )
    @Serializable private data class IbbyLast(val latency: Double? = null, val error: String? = null, val recoveryEtaAt: String? = null)
    @Serializable private data class IbbyUptime(val d7: Double? = null)

    fun parseIbbyLabs(body: String): Parsed? = runCatching {
        val r = json.decodeFromString(IbbyRoot.serializer(), body)
        val list = r.services ?: return null
        Parsed(txt(r.source?.name), list.mapNotNull { s ->
            val name = txt(s.name) ?: return@mapNotNull null
            val maint = s.maintenance != null && s.maintenance !is kotlinx.serialization.json.JsonNull
            val state = if (maint) SourceState.MAINTENANCE else when (s.displayState?.uppercase()) {
                "UP" -> SourceState.UP; "DEGRADED" -> SourceState.DEGRADED; "DOWN" -> SourceState.DOWN
                "MAINTENANCE" -> SourceState.MAINTENANCE; else -> SourceState.UNKNOWN
            }
            val issue = textOf(s.knownIssue)
            SourceService(name, NuvioCHealthRules.serviceHost(s.url), txt(s.group), state,
                NuvioCHealthRules.clampLatency(s.last?.latency), txt(issue ?: s.last?.error), NuvioCHealthRules.clampUptime(s.uptimeWindows?.d7))
        }, stale = r.freshness?.stale == true)
    }.getOrNull()

    /** A string, or an object's title / message / summary / reason. */
    private fun textOf(e: JsonElement?): String? = when (e) {
        is JsonPrimitive -> e.contentOrNull?.takeIf { e.isString }
        is JsonObject -> listOf("title", "message", "summary", "reason", "name").firstNotNullOfOrNull { (e[it] as? JsonPrimitive)?.contentOrNull }
        else -> null
    }

    // ---- Gatus /api/v1/endpoints/statuses -----------------------------------------------------------------------
    @Serializable private data class GatusEndpoint(val name: String? = null, val group: String? = null, val results: List<GatusResult>? = null)
    @Serializable private data class GatusResult(val hostname: String? = null, val success: Boolean? = null, val duration: Double? = null)

    fun parseGatus(body: String): Parsed? = runCatching {
        val list = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(GatusEndpoint.serializer()), body)
        Parsed(null, list.mapNotNull { e ->
            val name = txt(e.name) ?: return@mapNotNull null
            val last = e.results?.lastOrNull()
            val state = when (last?.success) { true -> SourceState.UP; false -> SourceState.DOWN; null -> SourceState.UNKNOWN }
            SourceService(name, last?.hostname?.lowercase()?.takeIf { it.contains('.') }, txt(e.group), state,
                NuvioCHealthRules.clampLatency(last?.duration?.div(1_000_000.0)))
        })
    }.getOrNull()

    // ---- Uptime Kuma /api/status-page/<slug> + heartbeat ---------------------------------------------------------
    @Serializable private data class KumaPage(val config: KumaConfig? = null, val publicGroupList: List<KumaGroup>? = null)
    @Serializable private data class KumaConfig(val title: String? = null)
    @Serializable private data class KumaGroup(val name: String? = null, val monitorList: List<KumaMonitor>? = null)
    @Serializable private data class KumaMonitor(val id: Long? = null, val name: String? = null, val url: String? = null)
    @Serializable private data class KumaBeats(val heartbeatList: Map<String, List<KumaBeat>>? = null, val uptimeList: Map<String, Double>? = null)
    @Serializable private data class KumaBeat(val status: Int? = null, val ping: Double? = null, val msg: String? = null)

    fun parseUptimeKuma(page: String, beats: String?): Parsed? = runCatching {
        val p = json.decodeFromString(KumaPage.serializer(), page)
        val groups = p.publicGroupList ?: return null
        val b = beats?.let { runCatching { json.decodeFromString(KumaBeats.serializer(), it) }.getOrNull() }
        Parsed(txt(p.config?.title), groups.flatMap { g ->
            g.monitorList.orEmpty().mapNotNull { m ->
                val name = txt(m.name) ?: return@mapNotNull null
                val last = m.id?.let { b?.heartbeatList?.get(it.toString())?.lastOrNull() }
                val state = when (last?.status) { 1 -> SourceState.UP; 0 -> SourceState.DOWN; 2 -> SourceState.DEGRADED; 3 -> SourceState.MAINTENANCE; else -> SourceState.UNKNOWN }
                val up = m.id?.let { b?.uptimeList?.get("${it}_24") }?.let { it * 100.0 }
                SourceService(name, NuvioCHealthRules.serviceHost(m.url), txt(g.name), state,
                    NuvioCHealthRules.clampLatency(last?.ping), if (state == SourceState.DOWN) txt(last?.msg) else null, NuvioCHealthRules.clampUptime(up))
            }
        })
    }.getOrNull()

    // ---- Better Stack /index.json -------------------------------------------------------------------------------
    @Serializable private data class BsRoot(val data: BsItem? = null, val included: List<BsItem>? = null)
    @Serializable private data class BsItem(val id: String? = null, val type: String? = null, val attributes: BsAttr? = null)
    @Serializable private data class BsAttr(
        @SerialName("company_name") val companyName: String? = null,
        @SerialName("aggregate_state") val aggregateState: String? = null,
        @SerialName("public_name") val publicName: String? = null,
        val status: String? = null,
        val availability: Double? = null,
        val title: String? = null,
        @SerialName("report_type") val reportType: String? = null,
        @SerialName("starts_at") val startsAt: String? = null,
        @SerialName("ends_at") val endsAt: String? = null,
        @SerialName("aggregate_state_name") val aggregateStateName: String? = null
    )

    /** Better Stack incident / maintenance, for TorBox's line. */
    data class BsReport(val title: String, val maintenance: Boolean, val startsAt: String?, val endsAt: String?)

    fun parseBetterStack(body: String): Parsed? = runCatching {
        val r = json.decodeFromString(BsRoot.serializer(), body)
        val d = r.data ?: return null
        if (d.type != "status_page") return null
        Parsed(txt(d.attributes?.companyName), r.included.orEmpty().filter { it.type == "status_page_resource" }.mapNotNull { i ->
            val a = i.attributes ?: return@mapNotNull null
            val name = txt(a.publicName) ?: return@mapNotNull null
            SourceService(name, null, null, betterStackState(a.status), uptime7d = NuvioCHealthRules.clampUptime(a.availability?.times(100.0)))
        })
    }.getOrNull()

    fun betterStackState(s: String?): SourceState = when (s?.lowercase()) {
        "operational" -> SourceState.UP; "degraded" -> SourceState.DEGRADED; "downtime" -> SourceState.DOWN
        "maintenance" -> SourceState.MAINTENANCE; else -> SourceState.UNKNOWN
    }

    /** Overall state of a Better Stack page plus its open reports (no end time, or ending after [nowIso]). */
    fun betterStackOverall(body: String, nowIso: String): Pair<SourceState, List<BsReport>>? = runCatching {
        val r = json.decodeFromString(BsRoot.serializer(), body)
        val d = r.data ?: return null
        if (d.type != "status_page") return null
        val reports = r.included.orEmpty().filter { it.type == "status_report" }.mapNotNull { i ->
            val a = i.attributes ?: return@mapNotNull null
            val open = a.endsAt == null || a.endsAt > nowIso
            val started = a.startsAt == null || a.startsAt <= nowIso
            if (!open || !started) return@mapNotNull null
            BsReport(txt(a.title) ?: return@mapNotNull null, a.reportType == "maintenance", a.startsAt?.take(16), a.endsAt?.take(16))
        }
        betterStackState(d.attributes?.aggregateState) to reports
    }.getOrNull()

    // ---- Atlassian Statuspage /api/v2/summary.json --------------------------------------------------------------
    @Serializable private data class SpRoot(val page: SpPage? = null, val components: List<SpComponent>? = null)
    @Serializable private data class SpPage(val name: String? = null)
    @Serializable private data class SpComponent(val name: String? = null, val status: String? = null, val group: Boolean? = null)

    fun parseStatuspage(body: String): Parsed? = runCatching {
        val r = json.decodeFromString(SpRoot.serializer(), body)
        val comps = r.components ?: return null
        val page = r.page ?: return null
        Parsed(txt(page.name), comps.filter { it.group != true }.mapNotNull { c ->
            val name = txt(c.name) ?: return@mapNotNull null
            SourceService(name, null, null, when (c.status) {
                "operational" -> SourceState.UP; "degraded_performance", "partial_outage" -> SourceState.DEGRADED
                "major_outage" -> SourceState.DOWN; "under_maintenance" -> SourceState.MAINTENANCE; else -> SourceState.UNKNOWN
            })
        })
    }.getOrNull()

    // ---- Instatus /summary.json ---------------------------------------------------------------------------------
    @Serializable private data class InRoot(val page: InPage? = null, val activeIncidents: List<InIncident>? = null, val activeMaintenances: List<InIncident>? = null)
    @Serializable private data class InPage(val name: String? = null, val status: String? = null)
    @Serializable private data class InIncident(val name: String? = null, val impact: String? = null)

    fun parseInstatus(body: String): Parsed? = runCatching {
        val r = json.decodeFromString(InRoot.serializer(), body)
        val page = r.page ?: return null
        val name = txt(page.name) ?: return null
        val state = when (page.status?.uppercase()) {
            "UP" -> SourceState.UP; "HASISSUES" -> SourceState.DEGRADED; "UNDERMAINTENANCE" -> SourceState.MAINTENANCE; else -> SourceState.UNKNOWN
        }
        val issue = (r.activeIncidents.orEmpty() + r.activeMaintenances.orEmpty()).firstNotNullOfOrNull { txt(it.name) }
        Parsed(name, listOf(SourceService(name, null, null, state, detail = issue)))
    }.getOrNull()

    /** Parse [body] as [format]. */
    fun parse(format: SourceFormat, body: String, extra: String? = null): Parsed? = when (format) {
        SourceFormat.IBBYLABS -> parseIbbyLabs(body)
        SourceFormat.GATUS -> parseGatus(body)
        SourceFormat.UPTIME_KUMA -> parseUptimeKuma(body, extra)
        SourceFormat.BETTER_STACK -> parseBetterStack(body)
        SourceFormat.STATUSPAGE -> parseStatuspage(body)
        SourceFormat.INSTATUS -> parseInstatus(body)
    }

    /** A source's display name: its own name, cleaned, else the host. Pure, for tests. */
    fun sourceName(parsed: Parsed?, address: String): String = parsed?.name ?: NuvioCHealthRules.hostOnly(address)

    // ---- TorBox /v1/api/user/me ---------------------------------------------------------------------------------
    @Serializable private data class TbRoot(val success: Boolean? = null, val data: TbUser? = null)
    @Serializable private data class TbUser(val plan: Int? = null, @SerialName("premium_expires_at") val premiumExpiresAt: String? = null)

    /** (plan name, expiry ISO) from TorBox's account answer; nothing else is read (no email, no ids). */
    fun parseTorBoxMe(body: String): Pair<String?, String?>? = runCatching {
        val r = json.decodeFromString(TbRoot.serializer(), body)
        val u = r.data ?: return null
        val plan = when (u.plan) { 0 -> "Free"; 1 -> "Essential"; 2 -> "Pro"; 3 -> "Standard"; else -> null }
        plan to u.premiumExpiresAt?.take(25)
    }.getOrNull()
}
