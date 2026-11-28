package com.nuvio.tv.ui.screens.addon

// [fork] Nuvio C round 67 `ADDON_HEALTH`, board 5b: the Service health summary page. Header with Check again and
// Status sources (14 dp apart), when the TV checked and how old each source is; counters; three columns: your
// add-ons (problems first; OK opens the Add-ons page on that card), debrid / trackers / sync, and the websites
// you use. Rows are focusable only for scrolling: Up / Down in a column, Left / Right between columns.
// Round 67b: one source decides; third column = websites the app uses; counts and status words in the status font;
// no addresses and no response times.

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
import com.nuvio.tv.core.nuvioc.health.ServiceGroup
import com.nuvio.tv.ui.theme.NuvioTheme

@Composable
fun NuvioCHealthScreen(
    onBack: () -> Unit,
    onOpenSources: () -> Unit,
    viewModel: NuvioCHealthViewModel = hiltViewModel()
) {
    if (com.nuvio.tv.NuvioCFeatures.HEALTH_V68) { NuvioCHealthScreen68(onBack, onOpenSources, viewModel); return } // [fork] round 68
    val s by viewModel.state.collectAsState()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val now = remember(s.checkedAtMs, s.running) { System.currentTimeMillis() }
    val status = com.nuvio.tv.ui.theme.NuvioCStatusFontFamily
    Column(
        Modifier.fillMaxSize().background(NuvioTheme.colors.Background).padding(horizontal = 36.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
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
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NuvioCHealthCounter(s.working, stringResource(R.string.nuvio_c_health_working), HealthTone.GREEN, status)
            NuvioCHealthCounter(s.problems, stringResource(if (s.problems == 1) R.string.nuvio_c_health_problem else R.string.nuvio_c_health_problems), HealthTone.AMBER, status)
            NuvioCHealthCounter(s.down, stringResource(R.string.nuvio_c_health_down), HealthTone.RED, status)
        }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            // 1. your add-ons, problems first
            NuvioCHealthColumn(stringResource(R.string.nuvio_c_health_col_addons), Modifier.weight(1f)) {
                items(s.addons.filter { it.verdict != com.nuvio.tv.core.nuvioc.health.HealthVerdict.OFF }.sortedBy { NuvioCHealthCombine.order(it.verdict) }, key = { it.baseUrl }) { a ->
                    NuvioCHealthRow(a.verdict, a.name, NuvioCHealthText.sub(a), status) {
                        NuvioCHealthNav.focusAddon = a.baseUrl
                        onBack()
                    }
                }
            }
            // 2. debrid, trackers and sync
            NuvioCHealthColumn(stringResource(R.string.nuvio_c_health_col_services), Modifier.weight(1f)) {
                val list = s.services.filter { it.group != ServiceGroup.WEBSITE }.sortedBy { NuvioCHealthCombine.order(it.verdict) }
                items(list, key = { it.key }) { v -> NuvioCHealthRow(v.verdict, v.name, listOfNotNull(v.sub, v.reason).joinToString(" · ").ifBlank { null }, status, null) }
                if (list.isEmpty()) item(key = "none") { NuvioCHealthNote(stringResource(R.string.nuvio_c_health_no_services)) }
            }
            // 3. websites the app uses
            NuvioCHealthColumn(stringResource(R.string.nuvio_c_health_col_websites), Modifier.weight(1f)) {
                items(s.services.filter { it.group == ServiceGroup.WEBSITE }.sortedBy { NuvioCHealthCombine.order(it.verdict) }, key = { it.key }) { v ->
                    NuvioCHealthRow(v.verdict, v.name, v.reason, status, null)
                }
            }
        }
    }
}

/** "Checked just now", or with a status source: "IbbyLabs Uptime Tracker · checked 2 min ago". */
private fun headerLine(s: HealthState, now: Long): String {
    if (s.running && s.checkedAtMs == null) return "Checking…"
    val reading = s.sources.firstOrNull()
    if (reading != null) {
        val ago = "checked " + NuvioCHealthText.ago(reading.fetchedAtMs, now)
        return listOfNotNull(reading.name, reading.error ?: ago, if (reading.stale) "its data is old" else null).joinToString(" · ")
    }
    return "Checked " + NuvioCHealthText.ago(s.checkedAtMs, now)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun NuvioCHealthPill(text: String, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
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
private fun NuvioCHealthCounter(number: Int, label: String, tone: HealthTone, status: androidx.compose.ui.text.font.FontFamily) {
    val lit = number > 0
    Row(
        Modifier.background(if (lit && tone != HealthTone.GREEN) NuvioCHealthColors.wash(tone) else NuvioTheme.colors.BackgroundCard, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("$number", style = MaterialTheme.typography.headlineSmall.copy(fontFamily = status, fontWeight = FontWeight.Bold),
            color = if (lit) NuvioCHealthColors.text(tone) else NuvioTheme.colors.TextTertiary)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = if (lit) NuvioTheme.colors.TextSecondary else NuvioTheme.colors.TextTertiary)
    }
}

@Composable
private fun NuvioCHealthColumn(title: String, modifier: Modifier, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column(modifier.fillMaxHeight().background(NuvioTheme.colors.BackgroundElevated, RoundedCornerShape(18.dp)).padding(14.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = NuvioTheme.colors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp))
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 4.dp), content = content)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NuvioCHealthRow(verdict: com.nuvio.tv.core.nuvioc.health.HealthVerdict, title: String, line: String?, status: androidx.compose.ui.text.font.FontFamily, onClick: (() -> Unit)?) {
    val tone = NuvioCHealthRules.tone(verdict)
    val problem = tone == HealthTone.AMBER || tone == HealthTone.RED
    Surface(
        onClick = { onClick?.invoke() },
        modifier = Modifier.fillMaxWidth(),
        colors = ClickableSurfaceDefaults.colors(containerColor = if (problem) NuvioCHealthColors.wash(tone) else androidx.compose.ui.graphics.Color.Transparent, focusedContainerColor = NuvioTheme.colors.FocusBackground),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs), shape = RoundedCornerShape(12.dp))),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            NuvioCHealthDot(tone)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (problem) FontWeight.Bold else FontWeight.SemiBold), color = NuvioTheme.colors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (line != null) Text(line, style = MaterialTheme.typography.bodySmall, color = NuvioTheme.colors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Text(NuvioCHealthText.state(verdict), style = MaterialTheme.typography.labelMedium.copy(fontFamily = status, fontWeight = FontWeight.Bold),
                color = NuvioCHealthColors.text(tone), maxLines = 1)
        }
    }
}

@Composable
private fun NuvioCHealthNote(text: String) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = NuvioTheme.colors.TextTertiary)
    }
}
