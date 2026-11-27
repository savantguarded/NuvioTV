package com.nuvio.tv.ui.screens.addon

// [fork] Nuvio C round 67 `ADDON_HEALTH`: the Add-ons page parts of Service health (board 5a): the entry card after
// Refresh add-ons, and the status at the end of each add-on card's second line (dot, state, " · " detail). Plain
// text, never focusable; the space is held from the first frame ("Checking…"), so no card changes height.

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.core.nuvioc.health.AddonHealth
import com.nuvio.tv.core.nuvioc.health.HealthState
import com.nuvio.tv.core.nuvioc.health.HealthTone
import com.nuvio.tv.core.nuvioc.health.HealthVerdict
import com.nuvio.tv.core.nuvioc.health.NuvioCHealthChecker
import com.nuvio.tv.core.nuvioc.health.NuvioCHealthCombine
import com.nuvio.tv.core.nuvioc.health.NuvioCHealthRules
import com.nuvio.tv.core.nuvioc.health.TvCheck
import com.nuvio.tv.ui.theme.NuvioTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Thin view model over the shared checker (one per page; the checker and its result are shared). */
@HiltViewModel
class NuvioCHealthViewModel @Inject constructor(val checker: NuvioCHealthChecker) : ViewModel() {
    val state = checker.state
    fun checkNow() = checker.runNow()
}

/** Status per add-on address, for the cards. Null = health off (nothing drawn, no space). */
internal val LocalNuvioCAddonHealth = compositionLocalOf<Map<String, AddonHealth>?> { null }

/** Where the summary page asked the Add-ons page to put focus when it comes back (an add-on's address). */
internal object NuvioCHealthNav {
    @Volatile var focusAddon: String? = null
}

internal object NuvioCHealthColors {
    // [fork] round 67b: the app's own success / warning / error family; dots strong, words a light tint of the same hue
    val green = Color(0xFF4CAF50)
    val amber = Color(0xFFFFB74D)
    val red = Color(0xFFFF5252)
    val grey = Color(0xFF6F6F6F)
    fun of(t: HealthTone) = when (t) { HealthTone.GREEN -> green; HealthTone.AMBER -> amber; HealthTone.RED -> red; HealthTone.GREY -> grey }
    fun text(t: HealthTone) = when (t) { HealthTone.GREEN -> Color(0xFF7FD39A); HealthTone.AMBER -> Color(0xFFFFB74D); HealthTone.RED -> Color(0xFFFF8A80); HealthTone.GREY -> Color(0xFF9E9E9E) }
    /** Row background behind a problem in the summary. */
    fun wash(t: HealthTone) = when (t) { HealthTone.AMBER -> Color(0xFF2A2114); HealthTone.RED -> Color(0xFF2A1616); else -> Color.Transparent }
}

/** Texts of Service health, pure, for tests. Never an address and never a response time (round 67b). */
internal object NuvioCHealthText {
    fun state(v: HealthVerdict): String = when (v) {
        HealthVerdict.CHECKING -> "Checking…"
        HealthVerdict.WORKING, HealthVerdict.WORKING_SOURCE_DISAGREES -> "Working"
        HealthVerdict.CANT_REACH -> "Can't reach it from here"
        HealthVerdict.RATE_LIMITED -> "Rate-limited"
        HealthVerdict.BLOCKED -> "Blocked"
        HealthVerdict.DOWN -> "Down"
        HealthVerdict.TROUBLE -> "Having trouble"
        HealthVerdict.MAINTENANCE -> "Maintenance"
        HealthVerdict.NOT_TRACKED -> "Not tracked"
        HealthVerdict.OFF -> "Off"
    }

    fun state(a: AddonHealth): String = state(a.verdict)

    /** The short reason after " · " (Timed out, Blocked (403), TorBox is having trouble), or null. */
    fun detail(a: AddonHealth): String? = when (a.verdict) {
        HealthVerdict.CANT_REACH, HealthVerdict.RATE_LIMITED, HealthVerdict.BLOCKED, HealthVerdict.DOWN, HealthVerdict.TROUBLE -> a.tvReason
        HealthVerdict.NOT_TRACKED -> a.sourceName?.let { "not on $it" }
        else -> null
    }?.takeUnless { it == state(a) }

    fun line(a: AddonHealth): String = state(a) + (detail(a)?.let { " · $it" } ?: "")

    /** Summary row line under a name: the instance (e.g. ElfHosted) and the reason, never an address. */
    fun sub(a: AddonHealth): String? = listOfNotNull(NuvioCHealthRules.instanceName(a.host), detail(a)).joinToString(" · ").ifBlank { null }

    fun ago(ms: Long?, now: Long): String {
        if (ms == null) return "not checked yet"
        val s = ((now - ms) / 1000).coerceAtLeast(0)
        return when {
            s < 60 -> "just now"
            s < 3600 -> "${s / 60} min ago"
            else -> "${s / 3600} h ago"
        }
    }

    /** One piece of the summary line; [tone] set = a count, drawn bold in its colour. */
    data class Part(val text: String, val tone: HealthTone? = null)

    /** "1 PROBLEM · 9 WORKING · TorBox online, Pro · 23 days left · checked just now". */
    fun summaryParts(s: HealthState, now: Long): List<Part> {
        if (s.running && s.checkedAtMs == null) return listOf(Part("Checking…"))
        val parts = mutableListOf<Part>()
        if (s.down > 0) parts += Part("${s.down} DOWN", HealthTone.RED)
        if (s.problems > 0) parts += Part(if (s.problems == 1) "1 PROBLEM" else "${s.problems} PROBLEMS", HealthTone.AMBER)
        parts += Part("${s.working} WORKING", HealthTone.GREEN)
        s.torBox?.let { t ->
            parts += Part(buildString {
                append(if (t.unwell) "TorBox having trouble" else if (t.reachable) "TorBox online" else "TorBox key refused")
                t.plan?.let { append(", ").append(it) }
            })
            t.daysLeft?.let { parts += Part(if (it == 1) "1 day left" else "$it days left") }
        }
        parts += Part(if (s.running) "checking…" else "checked " + ago(s.checkedAtMs, now))
        return parts
    }

    fun summary(s: HealthState, now: Long): String = summaryParts(s, now).joinToString(" · ") { it.text }

    /** Up to five dots for the entry card, problems first. */
    fun dots(s: HealthState): List<HealthTone> =
        (s.addons.map { it.verdict } + s.services.map { it.verdict }).filter { it != HealthVerdict.OFF && it != HealthVerdict.NOT_TRACKED }
            .sortedBy { NuvioCHealthCombine.order(it) }.take(5).map { NuvioCHealthRules.tone(it) }
}

@Composable
internal fun NuvioCHealthDot(tone: HealthTone, size: androidx.compose.ui.unit.Dp = 8.dp) {
    Box(Modifier.size(size).background(NuvioCHealthColors.of(tone), CircleShape))
}

/** End of an add-on card's second line: dot, state, " · " detail. Holds its place from the first frame. */
@Composable
internal fun NuvioCAddonHealthStatus(baseUrl: String, enabled: Boolean, modifier: Modifier = Modifier) {
    val map = LocalNuvioCAddonHealth.current ?: return
    val h = map[baseUrl] ?: AddonHealth(baseUrl, "", NuvioCHealthRules.hostOnly(baseUrl), if (enabled) TvCheck.RUNNING else TvCheck.OFF,
        verdict = if (enabled) HealthVerdict.CHECKING else HealthVerdict.OFF)
    if (h.verdict == HealthVerdict.OFF) return // the official "Disabled" label already says it
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        NuvioCHealthDot(NuvioCHealthRules.tone(h.verdict), 8.dp)
        Text(
            text = androidx.compose.ui.text.buildAnnotatedString {
                withStyle(androidx.compose.ui.text.SpanStyle(fontFamily = com.nuvio.tv.ui.theme.NuvioCStatusFontFamily, fontWeight = FontWeight.Bold, color = NuvioCHealthColors.text(NuvioCHealthRules.tone(h.verdict)))) {
                    append(NuvioCHealthText.state(h))
                }
                NuvioCHealthText.detail(h)?.let { append("  ·  "); append(it) }
            },
            style = MaterialTheme.typography.bodySmall,
            color = NuvioTheme.colors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** The Service health entry card, styled like the other entry cards. OK opens the summary page. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun ServiceHealthEntryCard(state: HealthState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var isFocused by remember { mutableStateOf(false) }
    val now = remember(state.checkedAtMs, state.running) { System.currentTimeMillis() }
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().onFocusChanged { isFocused = it.isFocused }.semantics { role = Role.Button },
        colors = ClickableSurfaceDefaults.colors(containerColor = NuvioTheme.colors.BackgroundCard, focusedContainerColor = NuvioTheme.colors.FocusBackground),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs), shape = RoundedCornerShape(18.dp))),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f)
    ) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.MonitorHeart,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = if (isFocused) NuvioTheme.colors.Secondary else NuvioTheme.colors.TextSecondary
            )
            Spacer(Modifier.width(NuvioTheme.spacing.lg))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.nuvio_c_health_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = NuvioTheme.colors.TextPrimary
                )
                Text(
                    text = nuvioCSummaryText(NuvioCHealthText.summaryParts(state, now)),
                    style = MaterialTheme.typography.bodySmall,
                    color = NuvioTheme.colors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                NuvioCHealthText.dots(state).forEach { NuvioCHealthDot(it) }
            }
            Spacer(Modifier.width(10.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = NuvioTheme.colors.TextSecondary
            )
        }
    }
}

/** [fork] round 67b: the summary with its counts in the status font, bold and coloured. */
internal fun nuvioCSummaryText(parts: List<NuvioCHealthText.Part>): androidx.compose.ui.text.AnnotatedString =
    androidx.compose.ui.text.buildAnnotatedString {
        parts.forEachIndexed { i, p ->
            if (i > 0) withStyle(androidx.compose.ui.text.SpanStyle(color = Color(0xFF6F6F6F))) { append("  ·  ") }
            if (p.tone != null) withStyle(androidx.compose.ui.text.SpanStyle(fontFamily = com.nuvio.tv.ui.theme.NuvioCStatusFontFamily, fontWeight = FontWeight.Bold, color = NuvioCHealthColors.text(p.tone))) { append(p.text) }
            else append(p.text)
        }
    }
