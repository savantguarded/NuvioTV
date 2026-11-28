package com.nuvio.tv.ui.screens.addon

// [fork] Nuvio C round 68 `HEALTH_V68` / `HEALTH_HOLD_5MIN`, boards 1 and 2: the Service Health summary page. Header
// "Service Health", then "Uptime Tracker" (bold) with "Checked 30 seconds ago" under it (no tracker or source names);
// Check Again greyed for 5 minutes after a check with "Available in N min" under it; capitalised counters; three
// columns that fill the page down to a 24 dp bottom margin and scroll inside; each row: name (16 sp) with its host
// under it (12 sp, never a path), the dot on the name line, the state word on the right.

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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.core.nuvioc.health.HealthTone
import com.nuvio.tv.core.nuvioc.health.HealthVerdict
import com.nuvio.tv.core.nuvioc.health.NuvioCHealthCombine
import com.nuvio.tv.core.nuvioc.health.NuvioCHealthRules
import com.nuvio.tv.core.nuvioc.health.ServiceGroup
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.delay

internal object NuvioCHealth68 {
    val nameStyle = TextStyle(fontSize = 16.sp, lineHeight = 22.sp)
    val hostStyle = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)
    val bottomMargin = 24.dp
}

@Composable
internal fun NuvioCHealthScreen68(onBack: () -> Unit, onOpenSources: () -> Unit, viewModel: NuvioCHealthViewModel) {
    val s by viewModel.state.collectAsState()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    // one tick a minute (and when a check lands), never per second
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(s.checkedAtMs, s.running) {
        now = System.currentTimeMillis()
        while (true) {
            val left = viewModel.holdLeft(now)
            delay(if (left in 1 until 60_000L) left + 50L else 60_000L) // ungreys right when the hold ends
            now = System.currentTimeMillis()
        }
    }
    val holdLeft = if (s.running) 0L else viewModel.holdLeft(now)
    val held = holdLeft > 0
    val status = com.nuvio.tv.ui.theme.NuvioCStatusFontFamily
    Column(
        Modifier.fillMaxSize().background(NuvioTheme.colors.Background)
            .padding(start = 36.dp, end = 36.dp, top = 28.dp, bottom = NuvioCHealth68.bottomMargin),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.nuvio_c_health_title_v68), style = MaterialTheme.typography.headlineMedium, color = NuvioTheme.colors.TextPrimary)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.nuvio_c_health_uptime_tracker), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = NuvioTheme.colors.TextPrimary, maxLines = 1)
                Text(
                    if (s.running && s.checkedAtMs == null) "Checking…" else NuvioCHealthText.checked(s.checkedAtMs, now),
                    style = MaterialTheme.typography.bodySmall, color = NuvioTheme.colors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    NuvioCHealthPill68(
                        stringResource(if (s.running) R.string.nuvio_c_health_checking else R.string.nuvio_c_health_check_again_v68),
                        greyed = held, modifier = Modifier.focusRequester(first)
                    ) { if (!held) viewModel.checkNow() } // greyed: still focusable so focus doesn't jump; OK does nothing
                    NuvioCHealthText.availableIn(holdLeft)?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = NuvioTheme.colors.TextTertiary, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                NuvioCHealthPill68(stringResource(R.string.nuvio_c_health_sources_v68), greyed = false, modifier = Modifier) { onOpenSources() }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Counter68(s.working, stringResource(R.string.nuvio_c_health_working_v68), HealthTone.GREEN, status)
            Counter68(s.problems, stringResource(if (s.problems == 1) R.string.nuvio_c_health_problem_v68 else R.string.nuvio_c_health_problems_v68), HealthTone.AMBER, status)
            Counter68(s.down, stringResource(R.string.nuvio_c_health_down_v68), HealthTone.RED, status)
        }
        // weight(1f): the columns take everything left down to the bottom margin, whatever the list length
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column68(stringResource(R.string.nuvio_c_health_col_addons_v68), Modifier.weight(1f)) {
                items(s.addons.filter { it.verdict != HealthVerdict.OFF }.sortedBy { NuvioCHealthCombine.order(it.verdict) }, key = { it.baseUrl }) { a ->
                    Row68(a.verdict, a.name, NuvioCHealthText.hostLine(a), status) {
                        NuvioCHealthNav.focusAddon = a.baseUrl
                        onBack()
                    }
                }
            }
            Column68(stringResource(R.string.nuvio_c_health_col_services_v68), Modifier.weight(1f)) {
                val list = s.services.filter { it.group != ServiceGroup.WEBSITE }.sortedBy { NuvioCHealthCombine.order(it.verdict) }
                items(list, key = { it.key }) { v -> Row68(v.verdict, v.name, NuvioCHealthText.hostLine(v), status, null) }
                if (list.isEmpty()) item(key = "none") {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)) {
                        Text(stringResource(R.string.nuvio_c_health_no_services), style = MaterialTheme.typography.bodySmall, color = NuvioTheme.colors.TextTertiary)
                    }
                }
            }
            Column68(stringResource(R.string.nuvio_c_health_col_websites), Modifier.weight(1f)) {
                items(s.services.filter { it.group == ServiceGroup.WEBSITE }.sortedBy { NuvioCHealthCombine.order(it.verdict) }, key = { it.key }) { v ->
                    Row68(v.verdict, v.name, NuvioCHealthText.hostLine(v), status, null)
                }
            }
        }
    }
}

/** A header pill; [greyed] = TextTertiary on a darker fill, still focusable. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun NuvioCHealthPill68(text: String, greyed: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        shape = ButtonDefaults.shape(RoundedCornerShape(50)),
        colors = if (greyed) ButtonDefaults.colors(
            containerColor = NuvioTheme.colors.Background,
            contentColor = NuvioTheme.colors.TextTertiary,
            focusedContainerColor = NuvioTheme.colors.BackgroundCard,
            focusedContentColor = NuvioTheme.colors.TextTertiary
        ) else ButtonDefaults.colors(
            containerColor = NuvioTheme.colors.BackgroundCard,
            contentColor = NuvioTheme.colors.TextPrimary,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black
        ),
        border = if (greyed) ButtonDefaults.border(
            border = Border(androidx.compose.foundation.BorderStroke(1.dp, NuvioTheme.colors.Border), shape = RoundedCornerShape(50)),
            focusedBorder = Border(NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs), shape = RoundedCornerShape(50))
        ) else ButtonDefaults.border()
    ) { Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1) }
}

@Composable
private fun Counter68(number: Int, label: String, tone: HealthTone, status: androidx.compose.ui.text.font.FontFamily) {
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

/** A full-height column: the panel fills its height, the list scrolls inside; rows stay clear of the rounded bottom. */
@Composable
private fun Column68(title: String, modifier: Modifier, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column(modifier.fillMaxHeight().background(NuvioTheme.colors.BackgroundElevated, RoundedCornerShape(18.dp)).padding(start = 14.dp, end = 14.dp, top = 14.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = NuvioTheme.colors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp))
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(top = 4.dp, bottom = 14.dp), content = content)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Row68(verdict: HealthVerdict, title: String, host: String?, status: androidx.compose.ui.text.font.FontFamily, onClick: (() -> Unit)?) {
    val tone = NuvioCHealthRules.tone(verdict)
    val problem = tone == HealthTone.AMBER || tone == HealthTone.RED
    val firstLine = with(LocalDensity.current) { NuvioCHealth68.nameStyle.lineHeight.toDp() }
    Surface(
        onClick = { onClick?.invoke() },
        modifier = Modifier.fillMaxWidth(),
        colors = ClickableSurfaceDefaults.colors(containerColor = if (problem) NuvioCHealthColors.wash(tone) else Color.Transparent, focusedContainerColor = NuvioTheme.colors.FocusBackground),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs), shape = RoundedCornerShape(12.dp))),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
            // the dot sits on the name line, centred on its height
            Box(Modifier.height(firstLine), contentAlignment = Alignment.Center) { NuvioCHealthDot(tone) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = NuvioCHealth68.nameStyle.copy(fontWeight = if (problem) FontWeight.Bold else FontWeight.SemiBold), color = NuvioTheme.colors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (host != null) Text(host, style = NuvioCHealth68.hostStyle, color = NuvioTheme.colors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.height(firstLine), contentAlignment = Alignment.Center) {
                Text(NuvioCHealthText.state(verdict), style = MaterialTheme.typography.labelMedium.copy(fontFamily = status, fontWeight = FontWeight.Bold),
                    color = NuvioCHealthColors.text(tone), maxLines = 1)
            }
        }
    }
}
