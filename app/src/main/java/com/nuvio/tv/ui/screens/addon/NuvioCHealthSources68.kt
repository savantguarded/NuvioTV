package com.nuvio.tv.ui.screens.addon

// [fork] Nuvio C round 68 `HEALTH_CHAIN_V68`, board 3: the Status Sources page as a check order. Any number of
// sources can be on; list order is check order. Each row: position (1, 2… for sources that are on, "–" for off),
// name, "Checked N minutes ago" / "Off", Move Up / Move Down (shown while the row has focus), switch, Remove. This TV
// is the last row: always on, a lock icon, no switch and no move. New sources start off at the end; Remove keeps the
// others' order. Every change saves (per profile, synced as before) and checks again at once.

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.core.nuvioc.health.HealthSourceSetting
import com.nuvio.tv.core.nuvioc.health.HealthTone
import com.nuvio.tv.core.nuvioc.health.NuvioCHealthRules
import com.nuvio.tv.core.nuvioc.health.SourceTestResult
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.launch

@Composable
internal fun NuvioCHealthSources68(viewModel: NuvioCHealthViewModel) {
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
    val firstRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstRequester.requestFocus() } }
    LaunchedEffect(adding) { if (adding) runCatching { fieldRequester.requestFocus() } }
    val now = remember(state.checkedAtMs) { System.currentTimeMillis() }
    val positions = NuvioCHealthRules.positions(sources)

    fun save(list: List<HealthSourceSetting>) {
        sources = list
        checker.saveSources(list)
        viewModel.checkForced() // the new order or switch counts from this check
    }

    Column(
        Modifier.fillMaxSize().background(NuvioTheme.colors.Background).padding(horizontal = 36.dp, vertical = 28.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.nuvio_c_health_sources_v68), style = MaterialTheme.typography.headlineMedium, color = NuvioTheme.colors.TextPrimary)
        Text(stringResource(R.string.nuvio_c_health_check_order), style = MaterialTheme.typography.titleSmall, color = NuvioTheme.colors.TextSecondary, modifier = Modifier.padding(top = 2.dp, bottom = 2.dp))

        sources.forEachIndexed { i, src ->
            key(src.address) { // the row (and the focused button) moves with its source
                val reading = state.sources.firstOrNull { it.address == src.address }
                var rowFocused by remember { mutableStateOf(false) }
                OrderRow(
                    position = positions[i]?.toString() ?: "–",
                    on = src.enabled,
                    title = reading?.name ?: NuvioCHealthRules.hostOnly(src.address),
                    line = when {
                        !src.enabled -> "Off"
                        reading?.error != null -> reading.error
                        reading?.stale == true -> "Its data is old"
                        reading != null -> NuvioCHealthText.checked(reading.fetchedAtMs, now)
                        else -> "Checking…"
                    },
                    modifier = Modifier.onFocusChanged { rowFocused = it.hasFocus }
                ) {
                    // Move Up / Move Down only while the row has focus; their room is kept so nothing shifts
                    val canUp = i > 0
                    val canDown = i < sources.lastIndex
                    Row(Modifier.alpha(if (rowFocused) 1f else 0f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NuvioCHealthPill68(stringResource(R.string.nuvio_c_health_move_up), greyed = !canUp,
                            modifier = Modifier.focusProperties { canFocus = rowFocused }) { if (canUp) save(NuvioCHealthRules.move(sources, src.address, up = true)) }
                        NuvioCHealthPill68(stringResource(R.string.nuvio_c_health_move_down), greyed = !canDown,
                            modifier = Modifier.focusProperties { canFocus = rowFocused }) { if (canDown) save(NuvioCHealthRules.move(sources, src.address, up = false)) }
                    }
                    Spacer(Modifier.width(14.dp))
                    NuvioCHealthSwitch(src.enabled, if (i == 0) Modifier.focusRequester(firstRequester) else Modifier) {
                        save(NuvioCHealthRules.toggle(sources, src.address, !src.enabled))
                    }
                    Spacer(Modifier.width(14.dp))
                    NuvioCHealthPill(stringResource(R.string.nuvio_c_health_remove), Modifier) {
                        save(sources.filterNot { it.address == src.address })
                        runCatching { (if (sources.isEmpty()) addRequester else firstRequester).requestFocus() }
                    }
                }
            }
        }

        // This TV: always on, always last; checks whatever the sources above didn't answer
        OrderRow(
            position = ((positions.filterNotNull().maxOrNull() ?: 0) + 1).toString(),
            on = true,
            title = stringResource(R.string.nuvio_c_health_this_tv),
            line = NuvioCHealthText.checked(state.checkedAtMs, now),
            modifier = Modifier
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = NuvioTheme.colors.TextTertiary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
        }

        if (sources.size < NuvioCHealthRules.MAX_SOURCES) {
            if (!adding) {
                NuvioCHealthPill(stringResource(R.string.nuvio_c_health_add_source_v68),
                    Modifier.focusRequester(if (sources.isEmpty()) firstRequester else addRequester)) { adding = true; result = null }
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
                    NuvioCHealthPill(stringResource(if (testing) R.string.nuvio_c_health_testing else R.string.nuvio_c_health_test_add_v68), Modifier.focusRequester(addRequester)) {
                        if (testing) return@NuvioCHealthPill
                        testing = true; result = null
                        scope.launch {
                            val r = runCatching { checker.testSource(text) }.getOrElse { SourceTestResult(false, null, null, 0, 0, "Not a readable status page") }
                            testing = false
                            result = nuvioCHealthTestLine(r)
                            if (r.ok && r.address != null) {
                                save(sources + HealthSourceSetting(r.address, false)) // new ones start off, at the end of the order
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

@Composable
private fun OrderRow(position: String, on: Boolean, title: String, line: String, modifier: Modifier, actions: @Composable () -> Unit) {
    Row(
        modifier.fillMaxWidth().background(NuvioTheme.colors.BackgroundCard, RoundedCornerShape(12.dp)).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            position,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = com.nuvio.tv.ui.theme.NuvioCStatusFontFamily, fontWeight = FontWeight.Bold),
            color = if (on) NuvioCHealthColors.text(HealthTone.GREEN) else NuvioTheme.colors.TextTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 24.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = NuvioTheme.colors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(line, style = MaterialTheme.typography.bodySmall, color = NuvioTheme.colors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}
