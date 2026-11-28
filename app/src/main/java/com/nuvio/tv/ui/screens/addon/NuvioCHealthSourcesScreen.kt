package com.nuvio.tv.ui.screens.addon

// [fork] Nuvio C round 67 `ADDON_HEALTH`, board 5c; round 67b: Status sources. This TV (built in) plus up to 4 you add;
// each row is only the name, when it was checked and an on / off switch (Remove on the ones you added). Only one is on at a
// time: switching one on switches the others off; switching yours off hands back to This TV. "Add a status source" opens
// a field with Test and add, which reads the page once and shows what it found, or why it refused.

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.toggleableState
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
    if (com.nuvio.tv.NuvioCFeatures.HEALTH_CHAIN_V68) { NuvioCHealthSources68(viewModel); return } // [fork] round 68 check order
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
    val tvRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { tvRequester.requestFocus() } }
    LaunchedEffect(adding) { if (adding) runCatching { fieldRequester.requestFocus() } }
    val now = remember(state.checkedAtMs) { System.currentTimeMillis() }
    val tvOn = sources.none { it.enabled }

    fun save(list: List<HealthSourceSetting>) {
        sources = NuvioCHealthRules.oneActive(list)
        checker.saveSources(sources)
        viewModel.checkForced() // the newly chosen source decides from the next check (round 68: past the hold)
    }

    Column(
        Modifier.fillMaxSize().background(NuvioTheme.colors.Background).padding(horizontal = 36.dp, vertical = 28.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.nuvio_c_health_sources), style = MaterialTheme.typography.headlineMedium, color = NuvioTheme.colors.TextPrimary, modifier = Modifier.padding(bottom = 6.dp))

        // built in: this TV's own checks; on whenever none of yours is
        NuvioCSourceRow(
            on = tvOn,
            title = stringResource(R.string.nuvio_c_health_this_tv),
            line = if (tvOn) nuvioCCheckedLine(state.checkedAtMs.takeIf { state.sourceName == null }, now) else "Off"
        ) {
            NuvioCHealthSwitch(tvOn, Modifier.focusRequester(tvRequester)) { if (!tvOn) save(sources.map { it.copy(enabled = false) }) }
        }

        sources.forEach { src ->
            val reading = state.sources.firstOrNull { it.address == src.address }
            NuvioCSourceRow(
                on = src.enabled,
                title = reading?.name ?: NuvioCHealthRules.hostOnly(src.address),
                line = when {
                    !src.enabled -> "Off"
                    reading?.error != null -> reading.error
                    reading != null -> nuvioCCheckedLine(reading.fetchedAtMs, now)
                    else -> "Checking…"
                }
            ) {
                NuvioCHealthSwitch(src.enabled, Modifier) { save(NuvioCHealthRules.select(sources, src.address, !src.enabled)) }
                Spacer(Modifier.width(14.dp))
                NuvioCHealthPill(stringResource(R.string.nuvio_c_health_remove), Modifier) {
                    save(sources.filterNot { it.address == src.address })
                    runCatching { tvRequester.requestFocus() }
                }
            }
        }

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
                            if (text.isEmpty()) Text("https://", style = MaterialTheme.typography.bodyMedium, color = NuvioTheme.colors.TextTertiary)
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
                                save(sources + HealthSourceSetting(r.address, false)) // added off: switch it on to use it
                                text = ""
                                adding = false
                            }
                        }
                    }
                }
            }
        }
        result?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = NuvioTheme.colors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

/** "Checked 2 min ago" or "Not checked yet". Pure, for tests. */
internal fun nuvioCCheckedLine(at: Long?, now: Long): String = if (at == null) "Not checked yet" else "Checked " + NuvioCHealthText.ago(at, now)

@Composable
private fun NuvioCSourceRow(on: Boolean, title: String, line: String, actions: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(NuvioTheme.colors.BackgroundCard, RoundedCornerShape(12.dp)).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NuvioCHealthDot(if (on) HealthTone.GREEN else HealthTone.GREY)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = NuvioTheme.colors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(line, style = MaterialTheme.typography.bodySmall, color = NuvioTheme.colors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

/** [fork] round 67b: an on / off switch you can see focus on: accent ring, focus background, a slight lift, ON / OFF beside it. */
@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
internal fun NuvioCHealthSwitch(checked: Boolean, modifier: Modifier, onToggle: () -> Unit) {
    androidx.tv.material3.Surface(
        onClick = onToggle,
        modifier = modifier.semantics {
            role = androidx.compose.ui.semantics.Role.Switch
            toggleableState = if (checked) androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off
        },
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent, focusedContainerColor = NuvioTheme.colors.FocusBackground),
        border = androidx.tv.material3.ClickableSurfaceDefaults.border(focusedBorder = androidx.tv.material3.Border(border = NuvioTheme.focusRing.border(3.dp), shape = RoundedCornerShape(16.dp))),
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.06f)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (checked) "ON" else "OFF", style = MaterialTheme.typography.labelMedium.copy(fontFamily = com.nuvio.tv.ui.theme.NuvioCStatusFontFamily, fontWeight = FontWeight.Bold),
                color = if (checked) NuvioTheme.colors.TextPrimary else NuvioTheme.colors.TextTertiary)
            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = androidx.tv.material3.SwitchDefaults.colors(
                    checkedThumbColor = NuvioTheme.colors.Secondary,
                    checkedTrackColor = NuvioTheme.colors.Secondary.copy(alpha = 0.3f)
                )
            )
        }
    }
}
