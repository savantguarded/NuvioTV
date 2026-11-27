package com.nuvio.tv.ui.screens.addon

// [fork] Nuvio C round 67 `ADDON_HEALTH`, board 5c: Status sources. Up to 4; each row shows the source's own name,
// host · services · matches · age, an on / off switch and Remove. The TorBox status page is built in. "Add a status
// source" opens a field with Test and add, which reads the page once and shows what it found, or why it refused.

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.core.nuvioc.health.HealthSourceSetting
import com.nuvio.tv.core.nuvioc.health.HealthTone
import com.nuvio.tv.core.nuvioc.health.NuvioCHealthRules
import com.nuvio.tv.core.nuvioc.health.SourceTestResult
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.launch

/** "Read it: Uptime Kuma status page · 31 services · 4 match your add-ons", or the refusal. Pure, for tests. */
internal fun nuvioCHealthTestLine(r: SourceTestResult): String =
    if (!r.ok) r.reason ?: "Not a readable status page"
    else "Read it: ${r.format?.label ?: "status page"} · ${r.services} services · " + when (r.matches) { 0 -> "none match your add-ons"; 1 -> "1 matches your add-ons"; else -> "${r.matches} match your add-ons" }

@Composable
fun NuvioCHealthSourcesScreen(viewModel: NuvioCHealthViewModel = hiltViewModel()) {
    val checker = viewModel.checker
    val state by viewModel.state.collectAsState()
    var sources by remember { mutableStateOf(checker.sources()) }
    var adding by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val fieldRequester = remember { FocusRequester() }
    val addRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { addRequester.requestFocus() } }
    LaunchedEffect(adding) { if (adding) runCatching { fieldRequester.requestFocus() } }
    val hosts = remember(state.addons) { state.addons.map { it.host.lowercase() } }
    val now = remember(state.checkedAtMs) { System.currentTimeMillis() }

    fun save(list: List<HealthSourceSetting>) { sources = list; checker.saveSources(list) }

    Column(
        Modifier.fillMaxSize().background(NuvioTheme.colors.Background).padding(horizontal = 40.dp, vertical = 28.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.nuvio_c_health_sources), style = MaterialTheme.typography.headlineMedium, color = NuvioTheme.colors.TextPrimary)
        Text(stringResource(R.string.nuvio_c_health_sources_hint), style = MaterialTheme.typography.bodySmall, color = NuvioTheme.colors.TextSecondary)

        sources.forEach { src ->
            val reading = state.sources.firstOrNull { it.address == src.address }
            val matches = reading?.let { r -> hosts.count { h -> NuvioCHealthRules.matchService(h, r.services) != null } }
            val line = listOfNotNull(
                NuvioCHealthRules.hostOnly(src.address),
                reading?.takeIf { it.error == null }?.let { "${it.services.size} services" },
                matches?.takeIf { reading?.error == null }?.let { if (it == 1) "1 match" else "$it matches" },
                reading?.error,
                reading?.let { NuvioCHealthText.ago(it.fetchedAtMs, now) }
            ).joinToString(" · ")
            NuvioCSourceRow(
                tone = when { !src.enabled -> HealthTone.GREY; reading == null -> HealthTone.GREY; reading.error != null -> HealthTone.AMBER; else -> HealthTone.GREEN },
                title = reading?.name ?: NuvioCHealthRules.hostOnly(src.address),
                line = line
            ) {
                Switch(checked = src.enabled, onCheckedChange = { on -> save(sources.map { if (it.address == src.address) it.copy(enabled = on) else it }) })
                Spacer(Modifier.width(14.dp))
                NuvioCHealthPill(stringResource(R.string.nuvio_c_health_remove), Modifier) {
                    save(sources.filterNot { it.address == src.address })
                    runCatching { addRequester.requestFocus() }
                }
            }
        }

        // built in: TorBox's own status page, read only when TorBox seems unwell
        NuvioCSourceRow(HealthTone.GREEN, stringResource(R.string.nuvio_c_health_torbox_page), NuvioCHealthRules.TORBOX_STATUS_HOST + " · " + stringResource(R.string.nuvio_c_health_built_in)) {}

        if (sources.size < NuvioCHealthRules.MAX_SOURCES) {
            if (!adding) {
                NuvioCHealthPill(stringResource(R.string.nuvio_c_health_add_source), Modifier.focusRequester(addRequester)) { adding = true; result = null }
            } else {
                var fieldFocused by remember { mutableStateOf(false) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    BasicTextField(
                        value = text,
                        onValueChange = { v -> text = v.take(NuvioCHealthRules.MAX_ADDRESS).filterNot { it == '\n' } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = NuvioTheme.colors.TextPrimary),
                        cursorBrush = SolidColor(NuvioTheme.colors.Primary),
                        modifier = Modifier.weight(1f).focusRequester(fieldRequester).onFocusChanged { fieldFocused = it.isFocused; if (it.isFocused) keyboard?.show() }
                            .background(NuvioTheme.colors.BackgroundCard, RoundedCornerShape(12.dp))
                            .border(if (fieldFocused) 2.dp else 1.dp, if (fieldFocused) NuvioTheme.colors.FocusRing else NuvioTheme.colors.Border, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        decorationBox = { inner ->
                            if (text.isEmpty()) Text("https://uptime.example.com", style = MaterialTheme.typography.bodyMedium, color = NuvioTheme.colors.TextTertiary)
                            inner()
                        }
                    )
                    NuvioCHealthPill(stringResource(if (testing) R.string.nuvio_c_health_testing else R.string.nuvio_c_health_test_add), Modifier.focusRequester(addRequester)) {
                        if (testing) return@NuvioCHealthPill
                        testing = true; result = null
                        scope.launch {
                            val r = runCatching { checker.testSource(text) }.getOrElse { SourceTestResult(false, null, null, 0, 0, "Not a readable status page") }
                            testing = false
                            result = nuvioCHealthTestLine(r)
                            if (r.ok && r.address != null) {
                                save(sources + HealthSourceSetting(r.address, true))
                                text = ""
                                adding = false
                                viewModel.checkNow()
                            }
                        }
                    }
                }
            }
        }
        result?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = NuvioTheme.colors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun NuvioCSourceRow(tone: HealthTone, title: String, line: String, actions: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(NuvioTheme.colors.BackgroundCard, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NuvioCHealthDot(tone)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = NuvioTheme.colors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(line, style = MaterialTheme.typography.bodySmall, color = NuvioTheme.colors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}
