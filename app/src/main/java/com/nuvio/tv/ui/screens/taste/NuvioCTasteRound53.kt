package com.nuvio.tv.ui.screens.taste

import com.nuvio.tv.data.nuvioc.nuvioC3
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.nuvio.tv.data.nuvioc.NuvioCTasteLine
import com.nuvio.tv.data.nuvioc.NuvioCTasteOverview
import com.nuvio.tv.data.nuvioc.NuvioCTasteRange
import com.nuvio.tv.data.nuvioc.NuvioCTasteStats
import com.nuvio.tv.data.nuvioc.NuvioCTasteStatsStrip
import com.nuvio.tv.data.nuvioc.NuvioCTasteYear
import com.nuvio.tv.ui.components.FocusMarqueeText
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import com.nuvio.tv.ui.screens.shuffle.NuvioCRoulettePreset
import com.nuvio.tv.ui.theme.NuvioTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

// [fork] Nuvio C round 53 `taste_tabs_v2` (53b rework after the TV check):
// - Tabs are picked with OK, not by passing over them, so Right from Overview reaches the scope pills instead of
//   switching tab on the way (that is why the range could never be picked in round 53). The scope is All time /
//   Year ▾ / Month ▾ (dropdowns with every year and month that has history), shared by Overview, Discover, Stats.
//   Wrapped has its own Year ▾. Dropdowns scroll and keep room around the last item.
// - Pages with nothing to press take no focus; a held Right stops at the last item of a row.
// - Short, compact copy; colour per card (the profile accent plus teal, amber, rose, violet).

private val C_CARD = if (com.nuvio.tv.NuvioCFeatures.TASTE_BACKDROP) Color(0xD915161B) else Color(0xFF15161B) // [fork] round 54: a touch see-through over the backdrop
private val C_INNER = Color(0xFF202128)
private val C_MUTE = Color.White.copy(alpha = 0.64f)
private val C_TRACK = Color(0xFF2A2B32)
private val C_TEAL = Color(0xFF4FD1C5)
private val C_AMBER = Color(0xFFF6B44B)
private val C_ROSE = Color(0xFFF47C8C)
private val C_VIOLET = Color(0xFFA78BFA)
private const val STATS_MIN = 10
private val C_EDGE = Color.White.copy(alpha = 0.07f) // [fork] round 57 taste_v3: hairline card edge

private enum class Tab53(val label: String) { OVERVIEW("Overview"), DISCOVER("Discover"), STATS("Stats"), WRAPPED("Wrapped") }

private enum class Menu53 { NONE, YEAR, MONTH, WRAPPED_YEAR }

@Composable
internal fun NuvioCTasteTabsV2(
    viewModel: NuvioCTasteViewModel,
    onOpenTitle: (id: String, type: String) -> Unit,
    onOpenPerson: (id: Int, name: String, crew: Boolean) -> Unit,
    onOpenRoulette: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val rows by viewModel.rows.collectAsState()
    val scope by viewModel.scope.collectAsState()
    val yearData by viewModel.year.collectAsState()
    val years by viewModel.wrappedYears.collectAsState()
    val key = viewModel.tasteScopeKey
    val thisMonthKey = remember { "m:" + YearMonth.now() }
    val earlyPill = remember { FocusRequester() } // [fork] round 58: "Nothing yet this month" jump

    LaunchedEffect(Unit) { viewModel.open() }
    val all = state.all[NuvioCTasteRange.ALL]
    LaunchedEffect(state.builtAt) { all?.let { viewModel.loadRows(it, state.builtAt) } }
    LaunchedEffect(state.builtAt, key) { if (state.builtAt > 0) viewModel.loadScope(key) }
    LaunchedEffect(state.builtAt, viewModel.tasteYear) { if (state.builtAt > 0) viewModel.loadYear(viewModel.tasteYear) }

    // [fork] round 57 taste_v3: the app theme's accent (focus colour), not the profile's avatar colour
    val accent = if (com.nuvio.tv.NuvioCFeatures.TASTE_V57) NuvioTheme.colors.FocusRing else remember { viewModel.accent }
    val yearsLoaded by viewModel.yearsLoaded.collectAsState()
    val empty = all == null || all.empty
    val tabs = buildList {
        add(Tab53.OVERVIEW)
        if (!empty) add(Tab53.DISCOVER)
        if (!empty) add(Tab53.STATS)
        if (years.isNotEmpty()) add(Tab53.WRAPPED)
    }
    val current = tabs.getOrNull(viewModel.tasteTab) ?: tabs.first()
    val tabFocus = remember { Tab53.entries.associateWith { FocusRequester() } }
    val spinFocus = remember { FocusRequester() }
    val newProfileFocus = remember { FocusRequester() }
    val yearPill = remember { FocusRequester() }
    val monthPill = remember { FocusRequester() }
    val wrappedPill = remember { FocusRequester() }
    var menu by remember { mutableStateOf(Menu53.NONE) }
    val overviewFirstRow = remember { FocusRequester() } // [fork] round 57 taste_v3: Overview rows open their title
    val wrappedFirstRow = remember { FocusRequester() }
    val downTarget: FocusRequester = when {
        empty && current == Tab53.OVERVIEW -> newProfileFocus
        current == Tab53.DISCOVER -> spinFocus
        com.nuvio.tv.NuvioCFeatures.TASTE_V58 && key == thisMonthKey && (current == Tab53.OVERVIEW && scope?.overview?.empty == true || current == Tab53.STATS && scope?.stats?.empty == true) -> earlyPill
        com.nuvio.tv.NuvioCFeatures.TASTE_V57 && current == Tab53.OVERVIEW && (scope?.overview?.recentFinished?.isNotEmpty() == true || scope?.overview?.binges?.isNotEmpty() == true) -> overviewFirstRow
        com.nuvio.tv.NuvioCFeatures.TASTE_V57 && current == Tab53.WRAPPED && (yearData?.topShows?.isNotEmpty() == true || yearData?.topFilms?.isNotEmpty() == true) -> wrappedFirstRow
        else -> FocusRequester.Cancel
    }
    // [fork] round 57 taste_v3: one loading screen until the tab list is final and the opening tab's data is in, then
    // the page fades in once (no Overview drawn first and swapped for the saved tab, no "Reading" text in between).
    var nuvioCShown by remember { mutableStateOf(!com.nuvio.tv.NuvioCFeatures.TASTE_V57) }
    var nuvioCGiveUp by remember { mutableStateOf(false) }
    LaunchedEffect(all != null) { if (all != null) { kotlinx.coroutines.delay(4_000); nuvioCGiveUp = true } }
    val nuvioCReady = all != null && (empty || nuvioCGiveUp || (yearsLoaded && when (current) {
        Tab53.OVERVIEW, Tab53.STATS -> scope != null
        Tab53.WRAPPED -> yearData != null
        Tab53.DISCOVER -> true
    }))
    LaunchedEffect(nuvioCReady) { if (nuvioCReady) nuvioCShown = true }
    val nuvioCFade = remember { androidx.compose.animation.core.Animatable(if (com.nuvio.tv.NuvioCFeatures.TASTE_V57) 0f else 1f) }
    LaunchedEffect(nuvioCShown) { if (nuvioCShown && nuvioCFade.value < 1f) nuvioCFade.animateTo(1f, tween(220)) }
    LaunchedEffect(all != null, nuvioCShown) {
        if (all == null || !nuvioCShown) return@LaunchedEffect
        if (current == Tab53.DISCOVER && viewModel.discoverFocusKey != null) return@LaunchedEffect
        tabFocus.getValue(current).requestFocusAfterFrames()
    }
    // years and months that have history, newest first
    val months = remember(state.months) { state.months.sortedDescending() }
    val scopeYears = remember(months) { months.map { it.year }.distinct() }

    fun spin(genre: String?, decade: Int?) {
        NuvioCRoulettePreset.set(NuvioCRoulettePreset.Preset(mood = NuvioCRoulettePreset.moodFor(genre), decade = NuvioCRoulettePreset.decadeFor(decade)))
        onOpenRoulette()
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF101119), Color(0xFF0B0B0D))))) {
        // [fork] round 54 taste_backdrop: the profile's top title, decoded small (soft when stretched) and dimmed, under
        // a dark wash that is heaviest behind the cards
        val backdropUrl = all?.backdrops?.firstOrNull()
        if (com.nuvio.tv.NuvioCFeatures.TASTE_BACKDROP && backdropUrl != null) {
            val bgContext = LocalContext.current
            // [fork] round 57b `BACKDROP_SMOOTH`: decoded large enough and smoothly scaled (with a soft blur on Android 12+);
            // the 320 x 180 decode stretched over the screen looked pixelated
            val smooth = com.nuvio.tv.NuvioCFeatures.BACKDROP_SMOOTH
            val req = remember(backdropUrl) { ImageRequest.Builder(bgContext).data(backdropUrl).size(if (smooth) 960 else 320, if (smooth) 540 else 180).build() }
            AsyncImage(req, null, contentScale = ContentScale.Crop, alpha = 0.42f,
                filterQuality = if (smooth) androidx.compose.ui.graphics.FilterQuality.High else androidx.compose.ui.graphics.drawscope.DrawScope.DefaultFilterQuality,
                modifier = Modifier.fillMaxSize().then(if (smooth && android.os.Build.VERSION.SDK_INT >= 31) Modifier.blur(10.dp, androidx.compose.ui.draw.BlurredEdgeTreatment.Rectangle) else Modifier))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xB30B0B0F), Color(0xE00B0B0F), Color(0xF20B0B0D)))))
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0x660B0B0F), Color.Transparent, Color(0x660B0B0F)))))
        }
        if (com.nuvio.tv.NuvioCFeatures.TASTE_V57 && !nuvioCShown) {
            NuvioCTasteLoading57(state, accent)
            return@Box
        }
        if (all == null && com.nuvio.tv.NuvioCFeatures.TASTE_BUILD_ANIM && state.progressTotal > 0) {
            TasteBuilding(state, accent)
            return@Box
        }
        Column(Modifier.fillMaxSize().graphicsLayer { alpha = nuvioCFade.value }.padding(start = 40.dp, end = 40.dp, top = 18.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                tabs.forEachIndexed { i, tab ->
                    // OK picks a tab; passing over it only moves focus, so Right reaches the scope pills
                    Pill53(
                        label = tab.label,
                        selected = tab == current,
                        accent = accent,
                        modifier = Modifier.padding(end = 6.dp).focusRequester(tabFocus.getValue(tab)).focusProperties { down = downTarget }
                    ) { viewModel.tasteTab = i; menu = Menu53.NONE }
                }
                Spacer(Modifier.weight(1f))
                if (current == Tab53.WRAPPED) {
                    val y = yearData?.year
                    if (y != null) Pill53("$y  ▾", menu == Menu53.WRAPPED_YEAR, accent,
                        Modifier.focusRequester(wrappedPill).focusProperties { down = FocusRequester.Cancel; right = FocusRequester.Cancel }) {
                        menu = if (menu == Menu53.WRAPPED_YEAR) Menu53.NONE else Menu53.WRAPPED_YEAR
                    }
                } else if (!empty) {
                    Row(Modifier.background(C_CARD, RoundedCornerShape(50)).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val m = Modifier.focusProperties { down = downTarget }
                        if (!com.nuvio.tv.NuvioCFeatures.TASTE_V58) Pill53("All time", key == "all", accent, m, quiet = true) { viewModel.tasteScopeKey = "all"; menu = Menu53.NONE }
                        if (com.nuvio.tv.NuvioCFeatures.TASTE_V58) Pill53( // [fork] round 58: Month first, "This month" when it is
                            if (key == thisMonthKey) "This month  ▾" else if (key.startsWith("m:")) runCatching { YearMonth.parse(key.drop(2)) }.getOrNull()?.let { monthLabel(it) + "  ▾" } ?: "Month  ▾" else "Month  ▾",
                            key.startsWith("m:"), accent, m.focusRequester(monthPill), quiet = true
                        ) { menu = if (menu == Menu53.MONTH) Menu53.NONE else Menu53.MONTH }
                        if (scopeYears.isNotEmpty()) Pill53(
                            if (key.startsWith("y:")) key.drop(2) + "  ▾" else "Year  ▾", key.startsWith("y:"), accent, m.focusRequester(yearPill), quiet = true
                        ) { menu = if (menu == Menu53.YEAR) Menu53.NONE else Menu53.YEAR }
                        if (com.nuvio.tv.NuvioCFeatures.TASTE_V58) Pill53("All time", key == "all", accent, m.focusProperties { right = FocusRequester.Cancel }, quiet = true) { viewModel.tasteScopeKey = "all"; menu = Menu53.NONE }
                        if (!com.nuvio.tv.NuvioCFeatures.TASTE_V58 && months.isNotEmpty()) Pill53(
                            if (key.startsWith("m:")) runCatching { YearMonth.parse(key.drop(2)) }.getOrNull()?.let { monthLabel(it) + "  ▾" } ?: "Month  ▾" else "Month  ▾",
                            key.startsWith("m:"), accent, m.focusRequester(monthPill).focusProperties { right = FocusRequester.Cancel }, quiet = true
                        ) { menu = if (menu == Menu53.MONTH) Menu53.NONE else Menu53.MONTH }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                val sc = scope
                Crossfade(targetState = current, animationSpec = tween(150), label = "taste53") { tab ->
                    when {
                        all == null -> Center53("Reading your history")
                        empty && tab == Tab53.OVERVIEW -> NewProfile53(newProfileFocus, accent) { spin(null, null) }
                        tab == Tab53.OVERVIEW -> when {
                            sc == null -> Center53("Reading your history")
                            sc.overview.empty && com.nuvio.tv.NuvioCFeatures.TASTE_V58 && key == thisMonthKey -> EarlyMonth58(accent, earlyPill, tabFocus.getValue(current)) { viewModel.tasteScopeKey = "m:" + YearMonth.now().minusMonths(1) }
                            sc.overview.empty -> Center53("Nothing watched in ${scopeName(key)}")
                            else -> Overview53(sc.overview, accent, overviewFirstRow, tabFocus.getValue(Tab53.OVERVIEW), onOpenTitle)
                        }
                        tab == Tab53.STATS -> when {
                            all.titles < STATS_MIN -> Center53("Stats unlock at $STATS_MIN titles · ${STATS_MIN - all.titles} to go")
                            sc == null -> Center53("Reading your history")
                            sc.stats.empty && com.nuvio.tv.NuvioCFeatures.TASTE_V58 && key == thisMonthKey -> EarlyMonth58(accent, earlyPill, tabFocus.getValue(current)) { viewModel.tasteScopeKey = "m:" + YearMonth.now().minusMonths(1) }
                            sc.stats.empty -> Center53("Nothing watched in ${scopeName(key)}")
                            else -> Stats53(sc.stats, sc.facts, sc.strip, accent)
                        }
                        tab == Tab53.DISCOVER -> Discover53(
                            viewModel = viewModel,
                            all = all,
                            people = (sc?.stats ?: all).let { s -> s.people.ifEmpty { s.morePeople } }.take(8),
                            rows = rows,
                            accent = accent,
                            spinFocus = spinFocus,
                            upTarget = tabFocus.getValue(Tab53.DISCOVER),
                            onSpin = { spin(all.genres.firstOrNull()?.name, all.topDecade) },
                            onOpenTitle = onOpenTitle,
                            onOpenPerson = onOpenPerson
                        )
                        else -> yearData?.let { Wrapped53(it, accent, wrappedFirstRow, tabFocus.getValue(Tab53.WRAPPED), onOpenTitle) } ?: Center53("Reading your history")
                    }
                }
            }
        }
        // dropdowns float over the page under the header, never clipped by it
        val closeTo: (FocusRequester) -> Unit = { r -> menu = Menu53.NONE; runCatching { r.requestFocus() } }
        when (menu) {
            Menu53.YEAR -> Dropdown53(
                items = scopeYears.map { it.toString() to (if (it == LocalDate.now().year) "so far" else null) },
                selected = scopeYears.indexOfFirst { key == "y:$it" },
                accent = accent,
                onPick = { i -> viewModel.tasteScopeKey = "y:${scopeYears[i]}"; closeTo(yearPill) },
                onClose = { closeTo(yearPill) }
            )
            Menu53.MONTH -> Dropdown53(
                items = months.map { monthLabel(it) to null },
                selected = months.indexOfFirst { key == "m:$it" },
                accent = accent,
                onPick = { i -> viewModel.tasteScopeKey = "m:${months[i]}"; closeTo(monthPill) },
                onClose = { closeTo(monthPill) }
            )
            Menu53.WRAPPED_YEAR -> yearData?.let { y ->
                Dropdown53(
                    items = y.hoursByYear.map { (yr, h) -> yr.toString() to "%,d h".format(h) },
                    selected = y.hoursByYear.indexOfFirst { it.first == y.year },
                    accent = accent,
                    onPick = { i -> viewModel.tasteYear = y.hoursByYear[i].first; closeTo(wrappedPill) },
                    onClose = { closeTo(wrappedPill) }
                )
            }
            Menu53.NONE -> Unit
        }
    }
}

private fun monthLabel(m: YearMonth) = m.month.nuvioC3() + " " + m.year

private fun scopeName(key: String): String = when {
    key.startsWith("y:") -> key.drop(2)
    key.startsWith("m:") -> runCatching { YearMonth.parse(key.drop(2)) }.getOrNull()?.let { it.month.getDisplayName(TextStyle.FULL, Locale.US) + " " + it.year } ?: "this month"
    else -> "this range"
}

@Composable
private fun Pill53(label: String, selected: Boolean, accent: Color, modifier: Modifier = Modifier, quiet: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) (if (com.nuvio.tv.NuvioCFeatures.TASTE_V57) Color.White.copy(alpha = 0.16f) else accent.copy(alpha = 0.22f)) else if (quiet) Color.Transparent else Color(0xFF1F2027),
            contentColor = if (selected) Color.White else Color.White.copy(alpha = 0.86f),
            focusedContainerColor = if (com.nuvio.tv.NuvioCFeatures.TASTE_V57) accent else Color.White,
            focusedContentColor = if (com.nuvio.tv.NuvioCFeatures.TASTE_V57) (if (accent.luminance() > 0.6f) Color.Black else Color.White) else Color.Black
        ),
        border = ClickableSurfaceDefaults.border(
            border = if (selected && !com.nuvio.tv.NuvioCFeatures.TASTE_V57) Border(BorderStroke(1.5.dp, accent), shape = RoundedCornerShape(50)) else Border.None,
            // [fork] round 57 taste_v3: white pill with an accent ring just outside it (same shape)
            focusedBorder = if (com.nuvio.tv.NuvioCFeatures.TASTE_V57) Border.None
                else Border(NuvioTheme.focusRing.border(2.dp), shape = RoundedCornerShape(50))
        )
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 7.dp), maxLines = 1, softWrap = false)
    }
}

/** A dropdown list under the header, right-aligned; scrolls, keeps room around the first and last item, Back closes. */
@Composable
private fun Dropdown53(items: List<Pair<String, String?>>, selected: Int, accent: Color, onPick: (Int) -> Unit, onClose: () -> Unit) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (selected - 2).coerceAtLeast(0))
    val first = remember { FocusRequester() }
    val target = selected.coerceAtLeast(0)
    BackHandler { onClose() }
    LaunchedEffect(Unit) { first.requestFocusAfterFrames() }
    Box(Modifier.fillMaxSize().zIndex(3f), contentAlignment = Alignment.TopEnd) {
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(top = 72.dp, end = 40.dp).width(210.dp).heightIn(max = 340.dp)
                .shadow(16.dp, RoundedCornerShape(14.dp))
                .background(Color(0xFF22232A), RoundedCornerShape(14.dp))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp)),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(items) { i, (label, sub) ->
                Surface(
                    onClick = { onPick(i) },
                    modifier = Modifier.fillMaxWidth().then(if (i == target) Modifier.focusRequester(first) else Modifier)
                        .focusProperties { left = FocusRequester.Cancel; right = FocusRequester.Cancel },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (i == selected) accent.copy(alpha = 0.2f) else Color.Transparent,
                        contentColor = Color.White, focusedContainerColor = Color.White, focusedContentColor = Color.Black
                    )
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = if (i == selected) FontWeight.Medium else FontWeight.Normal, maxLines = 1)
                        Spacer(Modifier.weight(1f))
                        if (sub != null) Text(sub, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** [fork] round 58: an empty "This month" offers last month instead of a blank page. */
@Composable
private fun EarlyMonth58(accent: Color, focus: FocusRequester, up: FocusRequester, onLastMonth: () -> Unit) {
    val last = YearMonth.now().minusMonths(1)
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Nothing yet this month", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Text("Your numbers fill in as you watch.", style = MaterialTheme.typography.bodySmall, color = C_MUTE, maxLines = 1)
        Spacer(Modifier.height(16.dp))
        Pill53("See " + last.month.getDisplayName(TextStyle.FULL, Locale.US), false, accent,
            Modifier.focusRequester(focus).focusProperties { this.up = up; left = FocusRequester.Cancel; right = FocusRequester.Cancel; down = FocusRequester.Cancel }) { onLastMonth() }
    }
}

@Composable
private fun Center53(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text, style = MaterialTheme.typography.titleMedium, color = C_MUTE) }
}

@Composable
private fun Card53(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    if (com.nuvio.tv.NuvioCFeatures.TASTE_V57) Column(modifier.background(C_CARD, RoundedCornerShape(14.dp)).border(1.dp, C_EDGE, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 14.dp), content = content)
    else Column(modifier.background(C_CARD, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 12.dp), content = content)
}

private fun hm53(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h >= 100 -> "%,dh".format(h)
        h > 0 -> "${h}h" + if (m > 0) " ${m}m" else ""
        else -> "${m}m"
    }
}

private fun day53(d: LocalDate): String = "${d.dayOfMonth} " + d.month.nuvioC3()

/**
 * [fork] round 53c: a column that shows only the children that fit whole (top to bottom), so a card never shows a
 * half-cut fifth row; what doesn't fit is left out instead of clipped.
 */
@Composable
private fun FitColumn53(modifier: Modifier = Modifier, gap: androidx.compose.ui.unit.Dp = 0.dp, content: @Composable () -> Unit) {
    androidx.compose.ui.layout.Layout(content = content, modifier = modifier) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val loose = constraints.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity)
        val placeables = ArrayList<androidx.compose.ui.layout.Placeable>()
        var used = 0
        for (m in measurables) {
            val p = m.measure(loose)
            val next = used + (if (placeables.isEmpty()) 0 else gapPx) + p.height
            if (constraints.hasBoundedHeight && next > constraints.maxHeight) break
            placeables += p
            used = next
        }
        val h = if (constraints.hasBoundedHeight) constraints.maxHeight.coerceAtLeast(used) else used
        layout(constraints.maxWidth, h.coerceAtLeast(constraints.minHeight)) {
            var y = 0
            placeables.forEach { it.place(0, y); y += it.height + gapPx }
        }
    }
}

/** Card heading: colour dot and a bold title. */
@Composable
private fun Heading53(text: String, color: Color) {
    if (com.nuvio.tv.NuvioCFeatures.TASTE_V57) { Label53(text, color); return } // [fork] round 57 taste_v3: one quiet caps label style on every card
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, softWrap = false)
    }
}

/** Section label: a colour dot and short caps. */
@Composable
private fun Label53(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(if (com.nuvio.tv.NuvioCFeatures.TASTE_V57) 7.dp else 6.dp).background(color, CircleShape))
        Spacer(Modifier.width(if (com.nuvio.tv.NuvioCFeatures.TASTE_V57) 8.dp else 6.dp))
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = if (com.nuvio.tv.NuvioCFeatures.TASTE_V57) FontWeight.Bold else null, color = C_MUTE, letterSpacing = 1.1.sp, maxLines = 1, softWrap = false)
    }
}

// ---- Overview ------------------------------------------------------------------------------------------------

@Composable
private fun Overview53(o: NuvioCTasteOverview, accent: Color, firstRow: FocusRequester? = null, tab: FocusRequester? = null, onOpen: (String, String) -> Unit = { _, _ -> }) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().height(if (com.nuvio.tv.NuvioCFeatures.TASTE_V57) 84.dp else 92.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val delta = o.prevMinutes?.let { p ->
                val pct = ((o.minutes - p) * 100.0 / p).toInt()
                (if (pct >= 0) "+$pct%" else "$pct%") + " vs ${o.prevLabel?.let { l -> if (l.all { it.isDigit() }) l else l.take(3) }}"
            }
            Tile53("Watch time", hm53(o.minutes), delta ?: "${o.daysWatched} active days", accent)
            Tile53("Titles", "%,d".format(o.titles), "${o.films} films · ${o.shows} shows", C_TEAL)
            Tile53("Episodes", "%,d".format(o.episodes), if (o.shows > 0) "${o.shows} shows" else "No shows", C_AMBER)
            Tile53("Active days", "%,d".format(o.daysWatched), "of ${o.daysInRange} · best run ${o.longestStreak}", C_ROSE)
        }
        Row(Modifier.fillMaxWidth().height(if (com.nuvio.tv.NuvioCFeatures.TASTE_V57) 84.dp else 116.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val ms = o.mostShow
            Fact53("Top show", ms?.name ?: "–", ms?.let { "${it.episodes} eps · ${hm53(it.minutes)}" }, C_VIOLET, thumb = ms?.poster, twoLines = true)
            val b = o.biggestDay
            Fact53("Biggest day", b?.let { hm53(it.minutes) } ?: "–",
                if (com.nuvio.tv.NuvioCFeatures.TASTE_V59) b?.topTitle else b?.let { listOfNotNull(it.date.dayOfWeek.nuvioC3() + " " + day53(it.date), it.topTitle).joinToString(" · ") }, C_AMBER,
                aside = b?.let { it.date.dayOfWeek.nuvioC3() + " " + day53(it.date) }, twoLines = true)
            val shows = o.finishedShows + o.inProgressShows + o.upToDateShows
            Fact53("Shows", if (shows == 0) "–" else "${o.finishedShows} finished",
                if (shows == 0) null else "${o.inProgressShows} ongoing · ${o.upToDateShows} caught up", C_TEAL)
            Fact53("New releases", o.freshPercent?.let { "$it%" } ?: "–", o.oldest?.let { if (com.nuvio.tv.NuvioCFeatures.TASTE_V59) it.first else "Oldest: ${it.first}, ${it.second}" }, C_ROSE, ring = if (com.nuvio.tv.NuvioCFeatures.TASTE_V58) null else o.freshPercent,
                aside = o.oldest?.let { "oldest from ${it.second}" }, twoLines = true) // [fork] round 58: no chart
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (com.nuvio.tv.NuvioCFeatures.TASTE_V57) {
                val rightFirst = remember { FocusRequester() }
                Rows57(Modifier.weight(1f).fillMaxHeight(), "Recently finished", "Films & shows", o.recentFinished, "Nothing finished yet", C_TEAL, accent,
                    first = if (o.recentFinished.isNotEmpty()) firstRow else null, up = tab, rightOut = rightFirst.takeIf { o.binges.isNotEmpty() }, onOpen = onOpen) { l -> l.sub + " · " + day53(l.date) }
                Rows57(Modifier.weight(1f).fillMaxHeight(), "Binges", "Most eps in a day", o.binges, "No binge days yet", C_AMBER, accent,
                    first = if (o.recentFinished.isEmpty()) firstRow else rightFirst, up = tab, lastColumn = true, onOpen = onOpen) { l -> l.sub + " · " + day53(l.date) }
            } else {
            List53(Modifier.weight(1f).fillMaxHeight(), "Recently finished", "Films & shows", o.recentFinished, "Nothing finished yet", accent)
            List53(Modifier.weight(1f).fillMaxHeight(), "Binges", "Most eps in a day", o.binges, "No binge days yet", C_AMBER)
            }
        }
    }
}

@Composable
private fun RowScope.Tile53(label: String, value: String, sub: String, color: Color) {
    if (com.nuvio.tv.NuvioCFeatures.TASTE_V57) { // [fork] round 57 taste_v3: label, big number, quiet note; even padding
        Column(Modifier.weight(1f).fillMaxHeight().background(C_CARD, RoundedCornerShape(14.dp)).border(1.dp, C_EDGE, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Label53(label, color)
            Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1, softWrap = false)
            Text(sub, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Normal, color = C_MUTE, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }
        return
    }
    Column(Modifier.weight(1f).fillMaxHeight().background(C_CARD, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 11.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Label53(label, color)
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 1, softWrap = false)
        Text(sub, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Normal, color = color, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RowScope.Fact53(label: String, main: String, sub: String?, color: Color, thumb: String? = null, ring: Int? = null, aside: String? = null, twoLines: Boolean = false) {
    if (com.nuvio.tv.NuvioCFeatures.TASTE_V59 && twoLines) { Fact59(label, main, sub, color, thumb, aside); return }
    if (com.nuvio.tv.NuvioCFeatures.TASTE_V57) { // [fork] round 57 taste_v3: one-line value, optional poster or ring on the right
        Row(Modifier.weight(1f).fillMaxHeight().background(C_CARD, RoundedCornerShape(14.dp)).border(1.dp, C_EDGE, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                Label53(label, color)
                Text(main, style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sub ?: "", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Normal, color = C_MUTE, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (thumb != null) {
                val context = LocalContext.current
                val r = remember(thumb) { ImageRequest.Builder(context).data(thumb).size(80, 120).build() }
                AsyncImage(r, null, contentScale = ContentScale.Crop, modifier = Modifier.width(40.dp).height(60.dp).clip(RoundedCornerShape(6.dp)).background(C_INNER))
            } else if (ring != null) {
                androidx.compose.foundation.Canvas(Modifier.size(46.dp)) {
                    val w = 5.dp.toPx()
                    drawArc(C_TRACK, 0f, 360f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(w), topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2), size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w))
                    drawArc(color, -90f, 360f * ring.coerceIn(0, 100) / 100f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round), topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2), size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w))
                }
            }
        }
        return
    }
    Column(Modifier.weight(1f).fillMaxHeight().background(C_CARD, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Label53(label, color)
        Text(main, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 2, lineHeight = 20.sp, overflow = TextOverflow.Ellipsis)
        if (sub != null) Text(sub, style = MaterialTheme.typography.labelMedium, color = C_MUTE, maxLines = 2, lineHeight = 15.sp, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * [fork] round 59: long names get two lines. With a poster (Top show) the name itself wraps to two lines and
 * the note stays one line; without one (Biggest day, New releases) the short aside (date / year) sits beside
 * the number and the note below gets two lines.
 */
@Composable
private fun RowScope.Fact59(label: String, main: String, sub: String?, color: Color, thumb: String?, aside: String?) {
    Row(Modifier.weight(1f).fillMaxHeight().background(C_CARD, RoundedCornerShape(14.dp)).border(1.dp, C_EDGE, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            Label53(label, color)
            if (thumb != null || aside == null && sub == null) {
                Text(main, style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, lineHeight = 16.sp), fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(sub ?: "", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), fontWeight = FontWeight.Normal, color = C_MUTE, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(main, style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1, softWrap = false, modifier = Modifier.alignByBaseline())
                    if (aside != null) Text("  $aside", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = C_MUTE, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.alignByBaseline())
                }
                Text(sub ?: "", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 13.sp), fontWeight = FontWeight.Normal, color = C_MUTE, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (thumb != null) {
            val context = LocalContext.current
            val r = remember(thumb) { ImageRequest.Builder(context).data(thumb).size(80, 120).build() }
            AsyncImage(r, null, contentScale = ContentScale.Crop, modifier = Modifier.width(40.dp).height(60.dp).clip(RoundedCornerShape(6.dp)).background(C_INNER))
        }
    }
}

@Composable
private fun List53(modifier: Modifier, title: String, sub: String, lines: List<NuvioCTasteLine>, emptyText: String, color: Color) {
    Card53(modifier) {
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Heading53(title, color)
            Spacer(Modifier.weight(1f))
            Text(sub, style = MaterialTheme.typography.labelSmall, color = C_MUTE, maxLines = 1, softWrap = false)
        }
        if (lines.isEmpty()) Text(emptyText, style = MaterialTheme.typography.bodySmall, color = C_MUTE)
        // whole titles (two lines when needed); only the rows that fit are shown
        FitColumn53(Modifier.fillMaxWidth().weight(1f), gap = 8.dp) {
            lines.forEach { l ->
                Row(Modifier.fillMaxWidth()) {
                    Text(l.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Normal, color = Color.White, maxLines = 2, lineHeight = 18.sp,
                        modifier = Modifier.weight(1f).alignByBaseline())
                    Text(l.sub, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Normal, color = color, maxLines = 1, softWrap = false,
                        modifier = Modifier.padding(start = 12.dp).alignByBaseline())
                    Text(day53(l.date), style = MaterialTheme.typography.labelMedium, color = C_MUTE, maxLines = 1, softWrap = false, textAlign = TextAlign.End,
                        modifier = Modifier.width(50.dp).alignByBaseline())
                }
            }
        }
    }
}

// ---- Stats ---------------------------------------------------------------------------------------------------

@Composable
private fun Stats53(s: NuvioCTasteStats, f: com.nuvio.tv.data.nuvioc.NuvioCTasteFacts, strip: NuvioCTasteStatsStrip, accent: Color) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().weight(1.1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Card53(Modifier.weight(2f).fillMaxHeight()) {
                val peakHour = f.byHour.indices.maxByOrNull { f.byHour[it] }?.takeIf { f.byHour[it] > 0 }
                val peakDay = com.nuvio.tv.data.nuvioc.tastePeakWeekday(f.byWeekday)
                Title53("Viewing hours", C_TEAL, listOfNotNull(peakHour?.let { "Peak %02d:00".format(it) }, peakDay?.nuvioC3()).joinToString(" · "))
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(Modifier.weight(3f).fillMaxHeight()) {
                        Bars53(f.byHour, peakHour, null, Modifier.fillMaxWidth().weight(1f), C_TEAL)
                        Row(Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            listOf("00", "06", "12", "18", "23").forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = C_MUTE) }
                        }
                    }
                    Bars53(f.byWeekday, f.byWeekday.indices.maxByOrNull { f.byWeekday[it] }, listOf("M", "T", "W", "T", "F", "S", "S"), Modifier.weight(2f).fillMaxHeight(), C_TEAL)
                }
            }
            Card53(Modifier.weight(1f).fillMaxHeight()) {
                val counts = s.ratingCounts.takeIf { it.size == 10 } ?: List(10) { 0 }
                val rated = counts.sum()
                if (rated == 0) {
                    Title53("Completion", accent, null)
                    val pct = s.finishedPercent ?: 0
                    Spacer(Modifier.weight(1f))
                    Text("$pct%", style = MaterialTheme.typography.displaySmall, fontWeight = if (com.nuvio.tv.NuvioCFeatures.TASTE_V57) FontWeight.ExtraBold else FontWeight.Medium, color = Color.White)
                    Text("of shows started", style = MaterialTheme.typography.labelMedium, color = C_MUTE)
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(8.dp).background(C_TRACK, RoundedCornerShape(4.dp))) {
                        Box(Modifier.fillMaxWidth(pct / 100f).height(8.dp).background(accent, RoundedCornerShape(4.dp)))
                    }
                    Spacer(Modifier.weight(1f))
                } else {
                    val sorted = counts.flatMapIndexed { i, c -> List(c) { i + 1 } }
                    Title53("Ratings", accent, "%,d · median %d".format(rated, sorted.getOrNull(sorted.size / 2) ?: 0))
                    Bars53(counts, counts.indices.maxByOrNull { counts[it] }, (1..10).map { it.toString() }, Modifier.fillMaxWidth().weight(1f), accent, values = true)
                }
            }
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Card53(Modifier.weight(1f).fillMaxHeight()) {
                Title53("Genres", C_VIOLET, "Titles")
                val top = s.genres.take(5)
                val max = top.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
                val palette = listOf(C_VIOLET, C_ROSE, C_AMBER, C_TEAL, accent)
                FitColumn53(Modifier.fillMaxWidth().weight(1f)) { top.forEachIndexed { i, g -> HBar53(g.name, "%,d".format(g.count), g.count.toFloat() / max, palette[i % palette.size]) } }
            }
            Card53(Modifier.weight(1f).fillMaxHeight()) {
                val decades = s.decades.takeLast(8)
                Title53("Decades", C_AMBER, null)
                Bars53(decades.map { it.second }, decades.indices.maxByOrNull { decades[it].second },
                    decades.map { "${it.first % 100}".padStart(2, '0') + "s" }, Modifier.fillMaxWidth().weight(1f), C_AMBER, values = true)
            }
            Card53(Modifier.weight(1f).fillMaxHeight()) {
                Title53("Studios & networks", C_ROSE, null)
                val max = f.studios.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
                if (f.studios.isEmpty()) Text("Not enough data yet", style = MaterialTheme.typography.bodySmall, color = C_MUTE)
                FitColumn53(Modifier.fillMaxWidth().weight(1f)) { f.studios.forEach { (name, n) -> HBar53(name, n.toString(), n.toFloat() / max, C_ROSE) } }
            }
            if (com.nuvio.tv.NuvioCFeatures.TASTE_V58) Card53(Modifier.weight(1f).fillMaxHeight()) { // [fork] round 58: Languages gets its own card
                Title53("Languages", C_TEAL, "Share of titles")
                val lang = strip.languages
                val max = lang.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
                if (lang.isEmpty()) Text("Not enough data yet", style = MaterialTheme.typography.bodySmall, color = C_MUTE)
                FitColumn53(Modifier.fillMaxWidth().weight(1f)) { lang.forEach { (name, pct) -> HBar53(name, "$pct%", pct.toFloat() / max, C_TEAL) } }
            }
        }
        if (com.nuvio.tv.NuvioCFeatures.TASTE_V59) Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { // [fork] round 59: four cards under the four above
            StripCard59("Countries", strip.countries.takeIf { it > 0 }?.toString(), strip.topCountries.takeIf { it.isNotEmpty() }?.joinToString(" · "), C_AMBER)
            StripCard59("Avg film", strip.avgFilmMinutes?.let { hm53(it) }, strip.longestFilm?.let { "Longest ${hm53(it.second)}" }, C_ROSE)
            StripCard59("Avg episode", strip.avgEpisodeMinutes?.let { hm53(it) }, strip.showCount.takeIf { it > 0 }?.let { "Across %,d shows".format(it) }, C_VIOLET)
            StripCard59("vs TMDB", s.vsCrowd?.let { "%+.1f".format(it) }, s.vsCrowd?.let { if (it >= 0) "You rate higher" else "You rate lower" } ?: "Needs 10 ratings", accent)
        } else
        Row(Modifier.fillMaxWidth().height(64.dp).background(C_CARD, RoundedCornerShape(14.dp)).padding(vertical = 10.dp)) {
            val lang = strip.languages
            if (!com.nuvio.tv.NuvioCFeatures.TASTE_V58) StripFact53("Language", lang.firstOrNull()?.let { "${it.second}% ${it.first}" }, lang.drop(1).takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = "+ ") { it.first }, C_TEAL, first = true)
            StripFact53("Countries", strip.countries.takeIf { it > 0 }?.toString(), strip.topCountries.takeIf { it.isNotEmpty() }?.joinToString(" · "), C_AMBER, first = com.nuvio.tv.NuvioCFeatures.TASTE_V58)
            StripFact53("Avg film", strip.avgFilmMinutes?.let { hm53(it) }, strip.longestFilm?.let { "Longest ${hm53(it.second)}" }, C_ROSE)
            StripFact53("Avg episode", strip.avgEpisodeMinutes?.let { hm53(it) }, null, C_VIOLET)
            StripFact53("vs TMDB", s.vsCrowd?.let { "%+.1f".format(it) }, s.vsCrowd?.let { if (it >= 0) "You rate higher" else "You rate lower" } ?: "Needs 10 ratings", accent)
        }
    }
}

@Composable
private fun RowScope.StripCard59(label: String, value: String?, sub: String?, color: Color) {
    Column(Modifier.weight(1f).fillMaxHeight().background(C_CARD, RoundedCornerShape(14.dp)).border(1.dp, C_EDGE, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
        Label53(label, color)
        Row {
            Text(value ?: "–", style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1, softWrap = false, modifier = Modifier.alignByBaseline())
            if (sub != null) Text("  $sub", style = MaterialTheme.typography.labelSmall, color = C_MUTE, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.alignByBaseline())
        }
    }
}

@Composable
private fun RowScope.StripFact53(label: String, value: String?, sub: String?, color: Color, first: Boolean = false) {
    Row(Modifier.weight(1f).fillMaxHeight()) {
        if (!first) Box(Modifier.width(1.dp).fillMaxHeight().background(C_TRACK))
        Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Label53(label, color)
            // value and note share one baseline
            Row {
                Text(value ?: "–", style = MaterialTheme.typography.titleMedium, fontWeight = if (com.nuvio.tv.NuvioCFeatures.TASTE_V57) FontWeight.ExtraBold else FontWeight.Medium, color = Color.White, maxLines = 1, softWrap = false, modifier = Modifier.alignByBaseline())
                if (sub != null) Text("  $sub", style = MaterialTheme.typography.labelSmall, color = C_MUTE, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.alignByBaseline())
            }
        }
    }
}

@Composable
private fun Title53(title: String, color: Color, line: String?) {
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Heading53(title, color)
        if (!line.isNullOrBlank()) {
            Spacer(Modifier.weight(1f))
            Text(line, style = MaterialTheme.typography.labelSmall, color = C_MUTE, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun Bars53(counts: List<Int>, highlight: Int?, labels: List<String>?, modifier: Modifier, color: Color, values: Boolean = false) {
    val max = counts.maxOrNull()?.coerceAtLeast(1) ?: 1
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(if (counts.size > 12) 3.dp else 5.dp), verticalAlignment = Alignment.Bottom) {
        counts.forEachIndexed { i, c ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                // [fork] round 59: a reserved line on top so the tallest bar's number is never clipped
                if (com.nuvio.tv.NuvioCFeatures.TASTE_V59 && values) Spacer(Modifier.height(13.dp))
                // bars stand on the baseline: empty space first, then the value, then the bar
                if (c < max) Spacer(Modifier.weight((1f - c.toFloat() / max).coerceAtLeast(0.0001f)))
                if (values && c > 0) Text(if (c >= 1000) "${c / 1000}k" else "$c", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Normal, color = C_MUTE, maxLines = 1, softWrap = false, fontSize = 9.sp)
                Box(Modifier.fillMaxWidth().weight((c.toFloat() / max).coerceAtLeast(0.02f), fill = true)
                    .background(if (i == highlight) color else color.copy(alpha = 0.32f), RoundedCornerShape(3.dp)))
                if (labels != null) Text(labels[i], style = MaterialTheme.typography.labelSmall, color = C_MUTE, maxLines = 1, softWrap = false, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun HBar53(name: String, value: String, share: Float, color: Color) {
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Normal, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).alignByBaseline())
            Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Normal, color = Color.White.copy(alpha = 0.8f), maxLines = 1, softWrap = false, modifier = Modifier.alignByBaseline())
        }
        Box(Modifier.fillMaxWidth().padding(top = 3.dp).height(4.dp).background(C_TRACK, RoundedCornerShape(2.dp))) {
            Box(Modifier.fillMaxWidth(share.coerceIn(0.02f, 1f)).height(4.dp).background(color, RoundedCornerShape(2.dp)))
        }
    }
}

// ---- Discover ------------------------------------------------------------------------------------------------

@Composable
private fun Discover53(
    viewModel: NuvioCTasteViewModel,
    all: NuvioCTasteStats,
    people: List<com.nuvio.tv.data.nuvioc.NuvioCTastePersonStat>,
    rows: List<com.nuvio.tv.data.nuvioc.NuvioCTasteRow>,
    accent: Color,
    spinFocus: FocusRequester,
    upTarget: FocusRequester,
    onSpin: () -> Unit,
    onOpenTitle: (String, String) -> Unit,
    onOpenPerson: (Int, String, Boolean) -> Unit
) {
    val context = LocalContext.current
    val row = rows.firstOrNull { it.items.isNotEmpty() }
    val leftScope = androidx.compose.runtime.rememberCoroutineScope()
    val requesters = remember { HashMap<String, FocusRequester>() }
    fun req(key: String) = requesters.getOrPut(key) { if (key == "spin") spinFocus else FocusRequester() }
    LaunchedEffect(row?.id, people.size) {
        val key = viewModel.discoverFocusKey ?: return@LaunchedEffect
        val r = requesters[key]
        if (r != null && runCatching { r.requestFocusAfterFrames() }.getOrDefault(false)) viewModel.discoverFocusKey = null
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // top: a compact Spin card beside the picks; bottom: the faces across the full width
        Row(Modifier.fillMaxWidth().height(if (com.nuvio.tv.NuvioCFeatures.TASTE_V58) 242.dp else if (com.nuvio.tv.NuvioCFeatures.TASTE_V57) 228.dp else 196.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            var spinFocused by remember { mutableStateOf(false) }
            Card(
                onClick = { viewModel.discoverFocusKey = "spin"; onSpin() },
                modifier = Modifier.width(if (com.nuvio.tv.NuvioCFeatures.TASTE_V58) 204.dp else 220.dp).fillMaxHeight().padding(4.dp)
                    .shadow(if (spinFocused) 22.dp else 8.dp, RoundedCornerShape(18.dp), ambientColor = accent, spotColor = accent)
                    .focusRequester(req("spin")).focusProperties {
                        up = upTarget
                        // [fork] round 54: nothing to the left inside the page, so Left never wanders up to the tabs; a held
                        // Left stops here and a fresh press opens the sidebar (MainActivity)
                        if (com.nuvio.tv.NuvioCFeatures.TASTE_SPIN_FLOURISH) left = FocusRequester.Cancel
                    }
                    .onFocusChanged { spinFocused = it.isFocused },
                shape = CardDefaults.shape(RoundedCornerShape(18.dp)),
                scale = CardDefaults.scale(focusedScale = 1f),
                colors = CardDefaults.colors(containerColor = Color(0xFF1C2233), focusedContainerColor = Color(0xFF243052)),
                border = CardDefaults.border(
                    border = Border(BorderStroke(2.dp, accent.copy(alpha = 0.55f)), shape = RoundedCornerShape(18.dp)),
                    focusedBorder = Border(BorderStroke(3.dp, if (com.nuvio.tv.NuvioCFeatures.TASTE_V57) accent else Color.White), shape = RoundedCornerShape(18.dp))
                )
            ) {
                Column(
                    Modifier.fillMaxSize().background(Brush.radialGradient(listOf(accent.copy(alpha = 0.34f), C_VIOLET.copy(alpha = 0.12f), Color.Transparent), radius = 480f)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (com.nuvio.tv.NuvioCFeatures.TASTE_SPIN_FLOURISH) SpinFlourish53(spinFocused, accent)
                    else Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        all.spinPosters.take(3).forEach { url ->
                            val r = remember(url) { ImageRequest.Builder(context).data(url).size(80, 120).build() }
                            AsyncImage(r, null, contentScale = ContentScale.Crop, modifier = Modifier.width(36.dp).height(54.dp).clip(RoundedCornerShape(5.dp)).background(C_INNER))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text("Spin my taste", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                    val spinLine = listOfNotNull(all.genres.getOrNull(0)?.name, all.genres.getOrNull(1)?.name, all.topDecade?.let { "${it}s" }).joinToString(" · ")
                    if (com.nuvio.tv.NuvioCFeatures.MARQUEE_MORE) com.nuvio.tv.ui.components.FocusMarqueeText(spinLine, spinFocused, MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal), color = accent) // [fork] round 60 marquee_more
                    else Text(spinLine, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Normal, color = accent, maxLines = 1)
                    Text("One unseen pick for you", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (row != null) {
                    Heading53(row.title, C_TEAL)
                    LazyRow(state = viewModel.discoverListState, horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)) {
                        itemsIndexed(row.items, key = { _, it -> "t:" + it.id }) { i, item ->
                            val key = "t:" + item.id
                            val last = i == row.items.lastIndex
                            // a held Right stops on the last poster
                            Poster53(item, accent, Modifier.focusRequester(req(key)).focusProperties { up = upTarget; if (last) right = FocusRequester.Cancel }
                                .onPreviewKeyEvent { e ->
                                    // held Left walks the posters one by one (scrolling first) and stops on Spin my taste,
                                    // never on a face below or the sidebar
                                    if (e.nativeKeyEvent.keyCode != android.view.KeyEvent.KEYCODE_DPAD_LEFT) return@onPreviewKeyEvent false
                                    if (e.nativeKeyEvent.action != android.view.KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent true
                                    if (i == 0) runCatching { spinFocus.requestFocus() }
                                    else {
                                        val prev = req("t:" + row.items[i - 1].id)
                                        leftScope.launch {
                                            if (viewModel.discoverListState.layoutInfo.visibleItemsInfo.none { it.index == i - 1 }) viewModel.discoverListState.scrollToItem(i - 1)
                                            prev.requestFocusAfterFrames(1)
                                        }
                                    }
                                    true
                                }) {
                                viewModel.discoverFocusKey = key
                                onOpenTitle(item.id, item.type)
                            }
                        }
                    }
                } else Center53("Picks appear after a few more titles")
            }
        }
        if (people.isNotEmpty()) Card53(Modifier.fillMaxWidth().weight(1f)) {
            Title53("Faces you watch most", C_ROSE, "Titles together")
            // even spacing across the full width, room around every circle so none is cut
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val fits = (maxWidth / 108.dp).toInt().coerceAtLeast(1)
            val shown = people.take(fits) // only whole faces, never a squeezed one
            // [fork] round 61 taste_top_stack: a row that isn't full starts at the first position
            val spread = !com.nuvio.tv.NuvioCFeatures.TASTE_TOP_STACK || shown.size >= fits
            Row(Modifier.fillMaxSize(), horizontalArrangement = if (spread) Arrangement.SpaceEvenly else Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                shown.forEachIndexed { i, p ->
                    val key = "p:" + p.person.id
                    val last = i == shown.lastIndex
                    Face53(p, accent, Modifier.focusRequester(req(key)).focusProperties { if (row == null) up = upTarget; if (last) right = FocusRequester.Cancel; if (i == 0 && com.nuvio.tv.NuvioCFeatures.TASTE_SPIN_FLOURISH) left = FocusRequester.Cancel }) {
                        viewModel.discoverFocusKey = key
                        onOpenPerson(p.person.id, p.person.name, p.director)
                    }
                }
            }
            }
        }
    }
}

/**
 * [fork] round 54 taste_spin_flourish: two accent rings in the card's top-right corner (clipped by the card) and a dice
 * in the top-left. Focused, the rings turn slowly and the dice tilts; unfocused, everything is still.
 */
@Composable
private fun SpinFlourish53(focused: Boolean, accent: Color) {
    val turn = if (focused) {
        val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "spin53")
        t.animateFloat(initialValue = 0f, targetValue = 360f, animationSpec = androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(9000, easing = androidx.compose.animation.core.LinearEasing)), label = "spin53r").value
    } else 0f
    val tilt by androidx.compose.animation.core.animateFloatAsState(if (focused) -14f else 0f, androidx.compose.animation.core.tween(260), label = "dice53")
    Box(Modifier.fillMaxWidth().height(54.dp)) {
        androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
            val big = 74.dp.toPx(); val small = 44.dp.toPx()
            val c = androidx.compose.ui.geometry.Offset(size.width + 6.dp.toPx(), -4.dp.toPx())
            rotate(turn, c) {
                drawCircle(accent.copy(alpha = 0.30f), big, c, style = androidx.compose.ui.graphics.drawscope.Stroke(10.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(60f, 18f))))
            }
            rotate(-turn * 1.6f, c) {
                drawCircle(C_AMBER.copy(alpha = 0.42f), small, c, style = androidx.compose.ui.graphics.drawscope.Stroke(5.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(34f, 14f))))
            }
        }
        androidx.compose.material3.Icon(
            painter = androidx.compose.ui.res.painterResource(com.nuvio.tv.R.drawable.nuvio_c_ic_dice),
            contentDescription = null,
            tint = C_AMBER,
            modifier = Modifier.size(28.dp).graphicsLayer { rotationZ = tilt }
        )
    }
}

@Composable
private fun Poster53(item: com.nuvio.tv.data.nuvioc.NuvioCTasteShelfItem, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    val v3 = com.nuvio.tv.NuvioCFeatures.TASTE_V57 // [fork] round 57 taste_v3: larger posters, accent ring
    val w = if (com.nuvio.tv.NuvioCFeatures.TASTE_V58) 120.dp else if (v3) 104.dp else 92.dp // [fork] round 58: wider posters
    Column(Modifier.width(w), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Card(
            onClick = onClick,
            modifier = modifier.width(w).height(if (com.nuvio.tv.NuvioCFeatures.TASTE_V58) 170.dp else if (v3) 156.dp else 138.dp).onFocusChanged { focused = it.isFocused },
            shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
            scale = CardDefaults.scale(focusedScale = 1f),
            border = CardDefaults.border(focusedBorder = Border(BorderStroke(if (v3) 3.dp else 2.dp, if (v3) accent else Color.White), shape = RoundedCornerShape(10.dp))),
            colors = CardDefaults.colors(containerColor = C_INNER),
            glow = com.nuvio.tv.ui.components.nuvioCPosterCardGlow() // [fork] round 53 poster_glow_everywhere
        ) {
            if (item.poster != null) {
                val r = remember(item.poster) { ImageRequest.Builder(context).data(item.poster).size(184, 276).build() }
                AsyncImage(r, item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
                Text(item.name, style = MaterialTheme.typography.labelSmall, color = C_MUTE, maxLines = 4)
            }
        }
        FocusMarqueeText(text = item.name, focused = focused, style = if (com.nuvio.tv.NuvioCFeatures.TASTE_V57) MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp) else MaterialTheme.typography.labelMedium, color = if (focused) Color.White else C_MUTE)
    }
}

@Composable
private fun Face53(p: com.nuvio.tv.data.nuvioc.NuvioCTastePersonStat, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(104.dp).padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            onClick = onClick,
            modifier = modifier.size(if (com.nuvio.tv.NuvioCFeatures.TASTE_V57) 84.dp else 76.dp).onFocusChanged { focused = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            colors = ClickableSurfaceDefaults.colors(containerColor = C_INNER, focusedContainerColor = C_INNER),
            border = ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(1.5.dp, accent.copy(alpha = 0.45f)), shape = CircleShape),
                focusedBorder = Border(BorderStroke(3.dp, if (com.nuvio.tv.NuvioCFeatures.TASTE_V57) accent else Color.White), shape = CircleShape)
            )
        ) {
            val photo = p.person.photo
            if (photo != null) {
                val r = remember(photo) { ImageRequest.Builder(context).data(photo).size(144, 144).build() }
                AsyncImage(r, p.person.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
            } else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(p.person.name.take(1), style = MaterialTheme.typography.titleLarge, color = C_MUTE)
            }
        }
        // one line for every name; it scrolls when focused
        FocusMarqueeText(
            text = p.person.name, focused = focused,
            style = if (com.nuvio.tv.NuvioCFeatures.TASTE_V57) MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal) else MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium), color = Color.White, textAlign = TextAlign.Center
        )
        val roleLine = if (p.director) "Director · ${p.count}" else "${p.count} titles"
        if (com.nuvio.tv.NuvioCFeatures.MARQUEE_MORE) com.nuvio.tv.ui.components.FocusMarqueeText(roleLine, focused, MaterialTheme.typography.labelSmall, color = C_MUTE, textAlign = TextAlign.Center) // [fork] round 60 marquee_more
        else Text(roleLine, style = MaterialTheme.typography.labelSmall, color = C_MUTE, maxLines = 1, softWrap = false)
    }
}

// ---- Wrapped -------------------------------------------------------------------------------------------------

@Composable
private fun Wrapped53(y: NuvioCTasteYear, accent: Color, firstRow: FocusRequester? = null, tab: FocusRequester? = null, onOpen: (String, String) -> Unit = { _, _ -> }) {
    if (com.nuvio.tv.NuvioCFeatures.TASTE_V57) { Wrapped57(y, accent, firstRow, tab, onOpen); return }
    val today = LocalDate.now()
    val soFar = y.year == today.year && today.monthValue < 12
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(
                Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(accent.copy(alpha = 0.55f), C_VIOLET.copy(alpha = 0.35f), C_CARD)), RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Your ${y.year}" + if (soFar) " so far" else "", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                Row(Modifier.fillMaxWidth()) {
                    Num53("%,d".format(y.hours), "hours", Modifier.weight(1f))
                    Num53("%,d".format(y.titles), "titles", Modifier.weight(1f))
                    Num53("%,d".format(y.films), "films", Modifier.weight(1f))
                    Num53("%,d".format(y.episodes), "episodes", Modifier.weight(1f))
                }
            }
            Ranked53(Modifier.fillMaxWidth().weight(1f), "Top shows", y.topShows, "No shows this year", C_TEAL)
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Ranked53(Modifier.fillMaxWidth().weight(1.15f), "Top films", y.topFilms, "No films this year", C_AMBER)
            Card53(Modifier.fillMaxWidth().weight(1f)) {
                Title53("Highlights", C_ROSE, null)
                FitColumn53(Modifier.fillMaxWidth().weight(1f)) {
                Line53("Top genre", y.topGenre)
                Line53("Most seen face", y.topFace?.let { "${it.first} · ${it.second}" })
                Line53("Biggest day", y.biggestDay?.let { day53(it) + " · " + hm53(y.biggestDayMinutes) })
                Line53("Longest streak", y.longestStreak.takeIf { it > 0 }?.let { "$it days" })
                Line53("Usually", when (y.peakPart) { 0 -> "Mornings"; 1 -> "Afternoons"; 2 -> "Evenings"; 3 -> "Late nights"; else -> null })
                Line53("Average rating", y.avgRating?.let { "%.1f · %d rated".format(it, y.rated) })
                }
            }
        }
        Column(Modifier.weight(1.1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Card53(Modifier.fillMaxWidth().weight(1f)) {
                val peak = y.busiestMonth
                Title53("Hours by month", accent, peak?.let { java.time.Month.of(it + 1).nuvioC3() + " peak" })
                Bars53(y.byMonth.map { it / 60 }, peak, listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D"), Modifier.fillMaxWidth().weight(1f), accent, values = true)
            }
            Card53(Modifier.fillMaxWidth()) {
                Title53("Firsts", C_VIOLET, null)
                Line53("First watch", y.firstTitle?.let { "${it.first} · ${day53(it.second)}" })
                Line53(if (soFar) "Latest" else "Last watch", y.lastTitle?.let { "${it.first} · ${day53(it.second)}" })
                Line53("New shows", "%,d".format(y.newShows))
                Line53("New directors", "%,d".format(y.newDirectors))
            }
        }
    }
}

@Composable
private fun Num53(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 1, softWrap = false)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f), maxLines = 1)
    }
}

@Composable
private fun Ranked53(modifier: Modifier, title: String, lines: List<NuvioCTasteLine>, emptyText: String, color: Color) {
    Card53(modifier) {
        Title53(title, color, null)
        if (lines.isEmpty()) Text(emptyText, style = MaterialTheme.typography.bodySmall, color = C_MUTE)
        // rank and title share one baseline; rows stack from the top; whole titles (two lines when needed); only rows that fit
        FitColumn53(Modifier.fillMaxWidth().weight(1f, fill = false), gap = 8.dp) {
            lines.forEachIndexed { i, l ->
                Column(Modifier.fillMaxWidth()) {
                    // the number sits on the title's first baseline; the note lines up under the title
                    Row(Modifier.fillMaxWidth()) {
                        // same size as the title, fixed-width digits, left aligned: number and title read as one line
                        Text("${i + 1}", style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Medium, color = color,
                            maxLines = 1, softWrap = false, modifier = Modifier.width(18.dp).alignByBaseline())
                        Spacer(Modifier.width(8.dp))
                        Text(l.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 2, lineHeight = 18.sp,
                            modifier = Modifier.weight(1f).alignByBaseline())
                    }
                    Text(l.sub, style = MaterialTheme.typography.labelSmall, color = C_MUTE, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 26.dp))
                }
            }
        }
    }
}

@Composable
private fun Line53(label: String, value: String?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = C_MUTE, maxLines = 1, softWrap = false, modifier = Modifier.alignByBaseline())
        Spacer(Modifier.width(10.dp))
        Text(value ?: "–", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Normal, color = Color.White, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.weight(1f).alignByBaseline())
    }
}

@Composable
private fun NewProfile53(focus: FocusRequester, accent: Color, onSpin: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Nothing watched yet", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(6.dp))
        Text("Stats appear as you watch on this profile.", style = MaterialTheme.typography.bodyMedium, color = C_MUTE)
        Spacer(Modifier.height(14.dp))
        Pill53("Spin something", false, accent, Modifier.focusRequester(focus)) { onSpin() }
    }
}


// ---- [fork] round 57 `taste_v3` ---------------------------------------------------------------------------------
// Flat cards with a quiet caps label, big tabular numbers, poster thumbnails on list rows. Rows open their title and
// take an accent focus ring (same corner shape as the row, drawn inside the card so it never clips). Rows spread to
// fill their card; only rows that fit whole are shown; long names are cut to one line. A held Down stops on the last
// row, a held Right on the right-hand column.

@Composable
private fun Rows57(
    modifier: Modifier,
    title: String,
    sub: String?,
    lines: List<NuvioCTasteLine>,
    emptyText: String,
    color: Color,
    accent: Color,
    first: FocusRequester?,
    up: FocusRequester?,
    rightOut: FocusRequester? = null,
    lastColumn: Boolean = false,
    numbered: Boolean = false,
    onOpen: (String, String) -> Unit,
    meta: (NuvioCTasteLine) -> String
) {
    Column(modifier.background(C_CARD, RoundedCornerShape(14.dp)).border(1.dp, C_EDGE, RoundedCornerShape(14.dp)).padding(horizontal = 8.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Label53(title, color)
            Spacer(Modifier.weight(1f))
            if (!sub.isNullOrBlank()) Text(sub, style = MaterialTheme.typography.labelSmall, color = C_MUTE, maxLines = 1, softWrap = false)
        }
        if (lines.isEmpty()) {
            Text(emptyText, style = MaterialTheme.typography.bodySmall, color = C_MUTE, modifier = Modifier.padding(horizontal = 8.dp))
            return@Column
        }
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val fit = (maxHeight / 40.dp).toInt().coerceAtLeast(1)
            val shown = lines.take(fit)
            Column(Modifier.fillMaxSize()) {
                shown.forEachIndexed { i, l ->
                    Row57(
                        line = l, index = if (numbered) i + 1 else null, meta = meta(l), accent = accent, rankColor = color,
                        // [fork] round 61 taste_top_stack: fixed rows from the top, never stretched apart
                        modifier = Modifier.fillMaxWidth().then(if (com.nuvio.tv.NuvioCFeatures.TASTE_TOP_STACK) Modifier.height(40.dp) else Modifier.weight(1f))
                            .then(if (i == 0 && first != null) Modifier.focusRequester(first) else Modifier)
                            .focusProperties {
                                if (i == 0 && up != null) this.up = up
                                if (i == shown.lastIndex) down = FocusRequester.Cancel
                                if (lastColumn) right = FocusRequester.Cancel
                                else if (rightOut != null && i == 0) right = rightOut
                            },
                        onOpen = onOpen
                    )
                }
            }
        }
    }
}

@Composable
private fun Row57(line: NuvioCTasteLine, index: Int?, meta: String, accent: Color, rankColor: Color, modifier: Modifier, onOpen: (String, String) -> Unit) {
    val context = LocalContext.current
    var rowFocused by remember { mutableStateOf(false) } // [fork] round 60 marquee_more
    Surface(
        onClick = { val id = line.id; val type = line.type; if (id != null && type != null) onOpen(id, type) },
        modifier = modifier.onFocusChanged { rowFocused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(9.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent, contentColor = Color.White,
            focusedContainerColor = Color.White.copy(alpha = 0.08f), focusedContentColor = Color.White
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, accent), shape = RoundedCornerShape(9.dp)))
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (index != null) Text("$index", style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Normal, color = rankColor, maxLines = 1, modifier = Modifier.width(12.dp))
            val p = line.poster
            if (p != null) {
                val r = remember(p) { ImageRequest.Builder(context).data(p).size(48, 72).build() }
                AsyncImage(r, null, contentScale = ContentScale.Crop, modifier = Modifier.width(20.dp).height(30.dp).clip(RoundedCornerShape(4.dp)).background(C_INNER))
            } else Box(Modifier.width(20.dp).height(30.dp).clip(RoundedCornerShape(4.dp)).background(C_INNER))
            if (com.nuvio.tv.NuvioCFeatures.TASTE_V59) Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) { // [fork] round 59: title line + details line
                if (com.nuvio.tv.NuvioCFeatures.MARQUEE_MORE) com.nuvio.tv.ui.components.NuvioCTurnMarquee( // [fork] round 60 marquee_more: both lines scroll in turn when cut
                    first = com.nuvio.tv.ui.components.NuvioCMarqueeSpec(line.name, MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Normal), Color.White),
                    second = com.nuvio.tv.ui.components.NuvioCMarqueeSpec(meta, MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 13.sp, fontFeatureSettings = "tnum", fontWeight = FontWeight.Normal), C_MUTE),
                    focused = rowFocused
                ) else {
                Text(line.name, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 15.sp), fontWeight = FontWeight.Normal, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(meta, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 13.sp, fontFeatureSettings = "tnum"), fontWeight = FontWeight.Normal, color = C_MUTE, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
            Text(line.name, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp), fontWeight = FontWeight.Normal, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(meta, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontFeatureSettings = "tnum"), fontWeight = FontWeight.Normal, color = C_MUTE, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** Label / value lines spread down the card, hairlines between, values right-aligned and cut to one line. */
@Composable
private fun Ledger57(modifier: Modifier, title: String, color: Color, lines: List<Pair<String, String?>>) {
    Card53(modifier) {
        Label53(title, color)
        Spacer(Modifier.height(6.dp))
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val shown = lines.take((maxHeight / 26.dp).toInt().coerceAtLeast(1))
            Column(Modifier.fillMaxSize()) {
                shown.forEachIndexed { i, (label, value) ->
                    Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Normal, color = C_MUTE, maxLines = 1, softWrap = false)
                        Spacer(Modifier.width(12.dp))
                        Text(value ?: "–", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontFeatureSettings = "tnum"), fontWeight = FontWeight.Normal, color = Color.White,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                    }
                    if (i < shown.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(C_EDGE))
                }
            }
        }
    }
}

/** [fork] round 58: top row totals / hours by month / Firsts; bottom row Top shows / Top films (top five) / Highlights. */
@Composable
private fun Wrapped58(y: NuvioCTasteYear, accent: Color, firstRow: FocusRequester?, tab: FocusRequester?, onOpen: (String, String) -> Unit, soFar: Boolean) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().weight(0.42f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Card53(Modifier.weight(1f).fillMaxHeight()) {
                Label53("Your ${y.year}" + if (soFar) " so far" else "", C_VIOLET)
                Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.SpaceEvenly) {
                    Row(Modifier.fillMaxWidth()) { Num57("%,d".format(y.hours), "hours watched", Modifier.weight(1f)); Num57("%,d".format(y.titles), "titles", Modifier.weight(1f)) }
                    Row(Modifier.fillMaxWidth()) { Num57("%,d".format(y.films), "films", Modifier.weight(1f)); Num57("%,d".format(y.episodes), "episodes", Modifier.weight(1f)) }
                }
            }
            Card53(Modifier.weight(1f).fillMaxHeight()) {
                val peak = y.busiestMonth
                Title53("Hours by month", accent, peak?.let { java.time.Month.of(it + 1).nuvioC3() + " peak" })
                Bars53(y.byMonth.map { it / 60 }, peak, listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D"), Modifier.fillMaxWidth().weight(1f), accent, values = true)
            }
            Ledger57(Modifier.weight(1f).fillMaxHeight(), "Firsts", C_VIOLET, listOf(
                "First watch" to y.firstTitle?.let { "${it.first} · ${day53(it.second)}" },
                (if (soFar) "Latest" else "Last watch") to y.lastTitle?.let { "${it.first} · ${day53(it.second)}" },
                "New shows" to "%,d".format(y.newShows),
                "New directors" to "%,d".format(y.newDirectors)
            ))
        }
        Row(Modifier.fillMaxWidth().weight(0.58f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val showsFirst = y.topShows.isNotEmpty()
            Rows57(Modifier.weight(1f).fillMaxHeight(), "Top shows", "By episodes", y.topShows, "No shows this year", C_TEAL, accent,
                first = if (showsFirst) firstRow else null, up = tab, numbered = true, onOpen = onOpen) { it.sub }
            Rows57(Modifier.weight(1f).fillMaxHeight(), "Top films", "By rating", y.topFilms, "No films this year", C_AMBER, accent,
                first = if (!showsFirst && y.topFilms.isNotEmpty()) firstRow else null, up = tab, numbered = true, onOpen = onOpen) { it.sub }
            Ledger57(Modifier.weight(1f).fillMaxHeight(), "Highlights", C_ROSE, listOf(
                "Top genre" to y.topGenre,
                "Most seen face" to y.topFace?.let { "${it.first} · ${it.second}" },
                "Biggest day" to y.biggestDay?.let { day53(it) + " · " + hm53(y.biggestDayMinutes) },
                "Longest streak" to y.longestStreak.takeIf { it > 0 }?.let { "$it days" },
                "Usually" to when (y.peakPart) { 0 -> "Mornings"; 1 -> "Afternoons"; 2 -> "Evenings"; 3 -> "Late nights"; else -> null },
                "Average rating" to y.avgRating?.let { "%.1f · %d rated".format(it, y.rated) }
            ))
        }
    }
}

@Composable
private fun Wrapped57(y: NuvioCTasteYear, accent: Color, firstRow: FocusRequester?, tab: FocusRequester?, onOpen: (String, String) -> Unit) {
    val today = LocalDate.now()
    val soFar = y.year == today.year && today.monthValue < 12
    if (com.nuvio.tv.NuvioCFeatures.TASTE_V58) { Wrapped58(y, accent, firstRow, tab, onOpen, soFar); return }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().weight(0.42f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Card53(Modifier.weight(1f).fillMaxHeight()) {
                Label53("Your ${y.year}" + if (soFar) " so far" else "", C_VIOLET)
                Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.SpaceEvenly) {
                    Row(Modifier.fillMaxWidth()) { Num57("%,d".format(y.hours), "hours watched", Modifier.weight(1f)); Num57("%,d".format(y.titles), "titles", Modifier.weight(1f)) }
                    Row(Modifier.fillMaxWidth()) { Num57("%,d".format(y.films), "films", Modifier.weight(1f)); Num57("%,d".format(y.episodes), "episodes", Modifier.weight(1f)) }
                }
            }
            Rows57(Modifier.weight(1f).fillMaxHeight(), "Top films", null, y.topFilms, "No films this year", C_AMBER, accent,
                first = if (y.topFilms.isNotEmpty()) firstRow else null, up = tab, onOpen = onOpen) { it.sub }
            Card53(Modifier.weight(1f).fillMaxHeight()) {
                val peak = y.busiestMonth
                Title53("Hours by month", accent, peak?.let { java.time.Month.of(it + 1).nuvioC3() + " peak" })
                Bars53(y.byMonth.map { it / 60 }, peak, listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D"), Modifier.fillMaxWidth().weight(1f), accent, values = true)
            }
        }
        Row(Modifier.fillMaxWidth().weight(0.58f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Rows57(Modifier.weight(1f).fillMaxHeight(), "Top shows", "By episodes", y.topShows, "No shows this year", C_TEAL, accent,
                first = if (y.topFilms.isEmpty()) firstRow else null, up = if (y.topFilms.isNotEmpty()) firstRow else tab, numbered = true, onOpen = onOpen) { it.sub }
            Ledger57(Modifier.weight(1f).fillMaxHeight(), "Highlights", C_ROSE, listOf(
                "Top genre" to y.topGenre,
                "Most seen face" to y.topFace?.let { "${it.first} · ${it.second}" },
                "Biggest day" to y.biggestDay?.let { day53(it) + " · " + hm53(y.biggestDayMinutes) },
                "Longest streak" to y.longestStreak.takeIf { it > 0 }?.let { "$it days" },
                "Usually" to when (y.peakPart) { 0 -> "Mornings"; 1 -> "Afternoons"; 2 -> "Evenings"; 3 -> "Late nights"; else -> null },
                "Average rating" to y.avgRating?.let { "%.1f · %d rated".format(it, y.rated) }
            ))
            Ledger57(Modifier.weight(1f).fillMaxHeight(), "Firsts", C_VIOLET, listOf(
                "First watch" to y.firstTitle?.let { "${it.first} · ${day53(it.second)}" },
                (if (soFar) "Latest" else "Last watch") to y.lastTitle?.let { "${it.first} · ${day53(it.second)}" },
                "New shows" to "%,d".format(y.newShows),
                "New directors" to "%,d".format(y.newDirectors)
            ))
        }
    }
}

@Composable
private fun Num57(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1, softWrap = false)
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Normal, color = C_MUTE, maxLines = 1)
    }
}

/** One loading screen: grey placeholders where the Overview cards go, and a small progress card in the middle. */
@Composable
internal fun NuvioCTasteLoading57(state: com.nuvio.tv.data.nuvioc.NuvioCTasteState, accent: Color) {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "taste57")
    val pulse by t.animateFloat(0.5f, 1f, androidx.compose.animation.core.infiniteRepeatable(tween(900), androidx.compose.animation.core.RepeatMode.Reverse), label = "taste57p")
    val sk = Color(0xFF1F2026)
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, top = 18.dp, bottom = 20.dp).graphicsLayer { alpha = pulse }, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(92, 92, 72, 90).forEach { w -> Box(Modifier.width(w.dp).height(34.dp).background(sk, RoundedCornerShape(50))) }
            }
            Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(2) {
                    Row(Modifier.fillMaxWidth().height(84.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        repeat(4) { Box(Modifier.weight(1f).fillMaxHeight().background(C_CARD, RoundedCornerShape(14.dp)).border(1.dp, C_EDGE, RoundedCornerShape(14.dp))) }
                    }
                }
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(2) { Box(Modifier.weight(1f).fillMaxHeight().background(C_CARD, RoundedCornerShape(14.dp)).border(1.dp, C_EDGE, RoundedCornerShape(14.dp))) }
                }
            }
        }
        val total = state.progressTotal
        Column(
            Modifier.align(Alignment.Center).width(300.dp).background(Color(0xF20B0B0D), RoundedCornerShape(16.dp)).border(1.dp, C_EDGE, RoundedCornerShape(16.dp)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Reading your history", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
            if (total > 0) {
                val done = (total - state.pending).coerceIn(0, total)
                Text("%,d of %,d titles".format(done, total), style = MaterialTheme.typography.labelMedium, color = C_MUTE, maxLines = 1)
                Box(Modifier.fillMaxWidth().height(4.dp).background(C_TRACK, RoundedCornerShape(2.dp))) {
                    Box(Modifier.fillMaxWidth(done.toFloat() / total).height(4.dp).background(accent, RoundedCornerShape(2.dp)))
                }
            } else {
                Box(Modifier.fillMaxWidth().height(4.dp).background(C_TRACK, RoundedCornerShape(2.dp))) {
                    Box(Modifier.fillMaxWidth(pulse).height(4.dp).background(accent.copy(alpha = 0.8f), RoundedCornerShape(2.dp)))
                }
            }
        }
    }
}
