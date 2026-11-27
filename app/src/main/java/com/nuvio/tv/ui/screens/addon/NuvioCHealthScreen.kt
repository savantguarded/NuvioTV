package com.nuvio.tv.ui.screens.addon

// [fork] Nuvio C round 67 `ADDON_HEALTH`, board 5b: the Service health summary page. Header with Check again and
// Status sources (14 dp apart), when the TV checked and how old each source is; counters; three columns: your
// add-ons (problems first; OK opens the Add-ons page on that card), debrid / trackers / sync, and problems elsewhere
// on your sources. Rows are focusable only for scrolling: Up / Down in a column, Left / Right between columns.

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.core.nuvioc.health.HealthState
import com.nuvio.tv.core.nuvioc.health.HealthTone
import com.nuvio.tv.core.nuvioc.health.NuvioCHealthCombine
import com.nuvio.tv.core.nuvioc.health.NuvioCHealthRules
import com.nuvio.tv.core.nuvioc.health.SourceService
import com.nuvio.tv.core.nuvioc.health.SourceState
import com.nuvio.tv.ui.theme.NuvioTheme
import java.text.DateFormat
import java.util.Date

@Composable
fun NuvioCHealthScreen(
    onBack: () -> Unit,
    onOpenSources: () -> Unit,
    viewModel: NuvioCHealthViewModel = hiltViewModel()
) {
    val s by viewModel.state.collectAsState()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val now = remember(s.checkedAtMs, s.running) { System.currentTimeMillis() }
    Column(
        Modifier.fillMaxSize().background(NuvioTheme.colors.Background).padding(horizontal = 40.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.nuvio_c_health_title), style = MaterialTheme.typography.headlineMedium, color = NuvioTheme.colors.TextPrimary)
                Text(headerLine(s, now), style = MaterialTheme.typography.bodySmall, color = NuvioTheme.colors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                NuvioCHealthPill(stringResource(if (s.running) R.string.nuvio_c_health_checking else R.string.nuvio_c_health_check_again), Modifier.focusRequester(first)) { viewModel.checkNow() }
                NuvioCHealthPill(stringResource(R.string.nuvio_c_health_sources), Modifier) { onOpenSources() }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NuvioCHealthCounter("${s.working}", stringResource(R.string.nuvio_c_health_working), HealthTone.GREEN)
            NuvioCHealthCounter("${s.problems}", stringResource(if (s.problems == 1) R.string.nuvio_c_health_problem else R.string.nuvio_c_health_problems), HealthTone.AMBER)
            NuvioCHealthCounter("${s.down}", stringResource(R.string.nuvio_c_health_down), HealthTone.RED)
        }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            // 1. your add-ons, problems first
            NuvioCHealthColumn(stringResource(R.string.nuvio_c_health_col_addons), Modifier.weight(1f)) {
                items(s.addons.sortedBy { NuvioCHealthCombine.order(it.verdict) }, key = { it.baseUrl }) { a ->
                    NuvioCHealthRow(NuvioCHealthRules.tone(a.verdict), a.name, NuvioCHealthText.line(a) + " · " + a.host) {
                        NuvioCHealthNav.focusAddon = a.baseUrl
                        onBack()
                    }
                }
            }
            // 2. debrid, trackers and sync
            NuvioCHealthColumn(stringResource(R.string.nuvio_c_health_col_services), Modifier.weight(1f)) {
                s.torBox?.let { t ->
                    item(key = "torbox") {
                        val line = buildList {
                            add(if (t.unwell) "Having trouble" else if (t.reachable) "Online" else "Not checked")
                            t.plan?.let { add(it) }
                            t.daysLeft?.let { add(if (it == 1) "1 day left" else "$it days left") }
                            t.latencyMs?.takeIf { t.reachable }?.let { add(NuvioCHealthText.latency(it)) }
                        }.joinToString(" · ")
                        NuvioCHealthRow(if (t.unwell) HealthTone.AMBER else HealthTone.GREEN, "TorBox", line, null)
                    }
                    if (t.incident != null) item(key = "torbox_incident") {
                        val detail = listOfNotNull(t.incidentSince?.let { "since ${it.replace('T', ' ')} UTC" }, t.maintenanceUntil?.let { "until ${it.replace('T', ' ')} UTC" }).joinToString(" · ")
                        NuvioCHealthRow(HealthTone.AMBER, t.incident, detail.ifBlank { "status.torbox.app" }, null)
                    }
                }
                items(s.trackers, key = { "t_" + it.name }) { svc -> NuvioCHealthRow(toneOf(svc.state), svc.name, serviceLine(svc), null) }
                if (s.torBox == null && s.trackers.isEmpty()) item(key = "none") {
                    NuvioCHealthNote(stringResource(R.string.nuvio_c_health_no_services))
                }
            }
            // 3. elsewhere on your sources: problems only
            NuvioCHealthColumn(stringResource(R.string.nuvio_c_health_col_elsewhere), Modifier.weight(1f)) {
                items(s.elsewhere, key = { "e_" + it.name + (it.host ?: "") }) { svc -> NuvioCHealthRow(toneOf(svc.state), svc.name, serviceLine(svc), null) }
                item(key = "others") {
                    NuvioCHealthNote(
                        if (s.sources.isEmpty()) stringResource(R.string.nuvio_c_health_no_sources)
                        else "${s.elsewhereWorking} other services working"
                    )
                }
            }
        }
    }
}

private fun toneOf(st: SourceState) = when (st) {
    SourceState.UP -> HealthTone.GREEN
    SourceState.DEGRADED, SourceState.MAINTENANCE -> HealthTone.AMBER
    SourceState.DOWN -> HealthTone.RED
    SourceState.UNKNOWN -> HealthTone.GREY
}

private fun serviceLine(svc: SourceService): String = listOfNotNull(
    when (svc.state) { SourceState.UP -> "Up"; SourceState.DEGRADED -> "Degraded"; SourceState.DOWN -> "Down"; SourceState.MAINTENANCE -> "Maintenance"; SourceState.UNKNOWN -> "Unknown" },
    svc.detail,
    svc.latencyMs?.let { NuvioCHealthText.latency(it) },
    svc.group
).joinToString(" · ")

/** "Checked on this TV 18:04 · IbbyLabs Uptime Tracker 2 min old". */
private fun headerLine(s: HealthState, now: Long): String {
    val tv = s.checkedAtMs?.let { "Checked on this TV " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)) } ?: if (s.running) "Checking…" else "Not checked yet"
    val ages = s.sources.map { r ->
        r.name + if (r.error != null) " (${r.error})" else " " + NuvioCHealthText.ago(r.fetchedAtMs, now).let { if (it == "just now") "fresh" else it.removeSuffix(" ago") + " old" } + if (r.stale) ", stale" else ""
    }
    return (listOf(tv) + ages).joinToString(" · ")
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun NuvioCHealthPill(text: String, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        shape = ButtonDefaults.shape(RoundedCornerShape(50)),
        colors = ButtonDefaults.colors(
            containerColor = NuvioTheme.colors.BackgroundCard,
            contentColor = NuvioTheme.colors.TextPrimary,
            focusedContainerColor = androidx.compose.ui.graphics.Color.White,
            focusedContentColor = androidx.compose.ui.graphics.Color.Black
        )
    ) { Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1) }
}

@Composable
private fun NuvioCHealthCounter(number: String, label: String, tone: HealthTone) {
    Row(
        Modifier.background(NuvioTheme.colors.BackgroundCard, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        NuvioCHealthDot(tone)
        Text(number, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = NuvioTheme.colors.TextPrimary)
        Text(label, style = MaterialTheme.typography.bodySmall, color = NuvioTheme.colors.TextSecondary)
    }
}

@Composable
private fun NuvioCHealthColumn(title: String, modifier: Modifier, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column(modifier.fillMaxHeight()) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = NuvioTheme.colors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 4.dp), content = content)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NuvioCHealthRow(tone: HealthTone, title: String, line: String, onClick: (() -> Unit)?) {
    Surface(
        onClick = { onClick?.invoke() },
        modifier = Modifier.fillMaxWidth(),
        colors = ClickableSurfaceDefaults.colors(containerColor = NuvioTheme.colors.BackgroundCard, focusedContainerColor = NuvioTheme.colors.FocusBackground),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs), shape = RoundedCornerShape(12.dp))),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            NuvioCHealthDot(tone)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = NuvioTheme.colors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(line, style = MaterialTheme.typography.bodySmall, color = NuvioTheme.colors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun NuvioCHealthNote(text: String) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = NuvioTheme.colors.TextTertiary)
    }
}
