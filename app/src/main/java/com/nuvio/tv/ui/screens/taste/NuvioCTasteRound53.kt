package com.nuvio.tv.ui.screens.taste

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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

// [fork] Nuvio C round 53 `taste_tabs_v2` (53b rework after Charles's TV check):
// - Tabs are picked with OK, not by passing over them, so Right from Overview reaches the scope pills instead of
//   switching tab on the way (that is why the range could never be picked in round 53). The scope is All time /
//   Year ▾ / Month ▾ (dropdowns with every year and month that has history), shared by Overview, Discover, Stats.
//   Wrapped has its own Year ▾. Dropdowns scroll and keep room around the last item.
// - Pages with nothing to press take no focus; a held Right stops at the last item of a row.
// - Short, compact copy; colour per card (the profile accent plus teal, amber, rose, violet).

private val C_CARD = Color(0xFF15161B)
private val C_INNER = Color(0xFF202128)
private val C_MUTE = Color.White.copy(alpha = 0.64f)
private val C_TRACK = Color(0xFF2A2B32)
private val C_TEAL = Color(0xFF4FD1C5)
private val C_AMBER = Color(0xFFF6B44B)
private val C_ROSE = Color(0xFFF47C8C)
private val C_VIOLET = Color(0xFFA78BFA)
private const val STATS_MIN = 10

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

    LaunchedEffect(Unit) { viewModel.open() }
    val all = state.all[NuvioCTasteRange.ALL]
    LaunchedEffect(state.builtAt) { all?.let { viewModel.loadRows(it, state.builtAt) } }
    LaunchedEffect(state.builtAt, key) { if (state.builtAt > 0) viewModel.loadScope(key) }
    LaunchedEffect(state.builtAt, viewModel.tasteYear) { if (state.builtAt > 0) viewModel.loadYear(viewModel.tasteYear) }

    val accent = remember { viewModel.accent }
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
    val downTarget: FocusRequester = when {
        empty && current == Tab53.OVERVIEW -> newProfileFocus
        current == Tab53.DISCOVER -> spinFocus
        else -> FocusRequester.Cancel
    }
    LaunchedEffect(all != null) {
        if (all == null) return@LaunchedEffect
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
        if (all == null && com.nuvio.tv.NuvioCFeatures.TASTE_BUILD_ANIM && state.progressTotal > 0) {
            TasteBuilding(state, accent)
            return@Box
        }
        Column(Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, top = 18.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).background(accent, CircleShape), contentAlignment = Alignment.Center) {
                    Text(viewModel.profileName.trim().take(1).uppercase(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0B0B0D))
                }
                Column(Modifier.padding(start = 12.dp, end = 22.dp)) {
                    Text("TASTE", style = MaterialTheme.typography.labelSmall, color = accent, letterSpacing = 1.5.sp, maxLines = 1)
                    Text(viewModel.profileName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1)
                }
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
                        Pill53("All time", key == "all", accent, m, quiet = true) { viewModel.tasteScopeKey = "all"; menu = Menu53.NONE }
                        if (scopeYears.isNotEmpty()) Pill53(
                            if (key.startsWith("y:")) key.drop(2) + "  ▾" else "Year  ▾", key.startsWith("y:"), accent, m.focusRequester(yearPill), quiet = true
                        ) { menu = if (menu == Menu53.YEAR) Menu53.NONE else Menu53.YEAR }
                        if (months.isNotEmpty()) Pill53(
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
                            sc.overview.empty -> Center53("Nothing watched in ${scopeName(key)}")
                            else -> Overview53(sc.overview, accent)
                        }
                        tab == Tab53.STATS -> when {
                            all.titles < STATS_MIN -> Center53("Stats unlock at $STATS_MIN titles · ${STATS_MIN - all.titles} to go")
                            sc == null -> Center53("Reading your history")
                            sc.stats.empty -> Center53("Nothing watched in ${scopeName(key)}")
                            else -> Stats53(sc.stats, sc.facts, sc.strip, accent)
                        }
                        tab == Tab53.DISCOVER -> Discover53(
                            viewModel = viewModel,
                            all = all,
                            people = (sc?.stats ?: all).let { s -> s.people.ifEmpty { s.morePeople } }.take(7),
                            rows = rows,
                            accent = accent,
                            spinFocus = spinFocus,
                            upTarget = tabFocus.getValue(Tab53.DISCOVER),
                            onSpin = { spin(all.genres.firstOrNull()?.name, all.topDecade) },
                            onOpenTitle = onOpenTitle,
                            onOpenPerson = onOpenPerson
                        )
                        else -> yearData?.let { Wrapped53(it, accent) } ?: Center53("Reading your history")
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

private fun monthLabel(m: YearMonth) = m.month.getDisplayName(TextStyle.SHORT, Locale.US) + " " + m.year

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
            containerColor = if (selected) accent.copy(alpha = 0.22f) else if (quiet) Color.Transparent else Color(0xFF1F2027),
            contentColor = if (selected) Color.White else Color.White.copy(alpha = 0.86f),
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black
        ),
        border = ClickableSurfaceDefaults.border(
            border = if (selected) Border(BorderStroke(1.5.dp, accent), shape = RoundedCornerShape(50)) else Border.None,
            focusedBorder = Border(NuvioTheme.focusRing.border(2.dp), shape = RoundedCornerShape(50))
        )
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
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
                        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                        Spacer(Modifier.weight(1f))
                        if (sub != null) Text(sub, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun Center53(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text, style = MaterialTheme.typography.titleMedium, color = C_MUTE) }
}

@Composable
private fun Card53(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.background(C_CARD, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 12.dp), content = content)
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

private fun day53(d: LocalDate): String = "${d.dayOfMonth} " + d.month.getDisplayName(TextStyle.SHORT, Locale.US)

/** Section label: a colour dot and short caps. */
@Composable
private fun Label53(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = C_MUTE, letterSpacing = 1.1.sp, maxLines = 1, softWrap = false)
    }
}

// ---- Overview ------------------------------------------------------------------------------------------------

@Composable
private fun Overview53(o: NuvioCTasteOverview, accent: Color) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().height(84.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val delta = o.prevMinutes?.let { p ->
                val pct = ((o.minutes - p) * 100.0 / p).toInt()
                (if (pct >= 0) "+$pct%" else "$pct%") + " vs ${o.prevLabel?.take(4)}"
            }
            Tile53("Watch time", hm53(o.minutes), delta ?: "${o.daysWatched} active days", accent)
            Tile53("Titles", "%,d".format(o.titles), "${o.films} films · ${o.shows} shows", C_TEAL)
            Tile53("Episodes", "%,d".format(o.episodes), if (o.shows > 0) "${o.shows} shows" else "No shows", C_AMBER)
            Tile53("Active days", "%,d".format(o.daysWatched), "of ${o.daysInRange} · best run ${o.longestStreak}", C_ROSE)
        }
        Row(Modifier.fillMaxWidth().height(112.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val ms = o.mostShow
            Fact53("Top show", ms?.name ?: "–", ms?.let { "${it.episodes} eps · ${hm53(it.minutes)}" }, C_VIOLET)
            val b = o.biggestDay
            Fact53("Biggest day", b?.let { hm53(it.minutes) } ?: "–",
                b?.let { listOfNotNull(it.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US) + " " + day53(it.date), it.topTitle).joinToString(" · ") }, C_AMBER)
            val shows = o.finishedShows + o.inProgressShows + o.upToDateShows
            Fact53("Shows", if (shows == 0) "–" else "${o.finishedShows} finished",
                if (shows == 0) null else "${o.inProgressShows} ongoing · ${o.upToDateShows} caught up", C_TEAL)
            Fact53("New releases", o.freshPercent?.let { "$it%" } ?: "–", o.oldest?.let { "Oldest: ${it.first}, ${it.second}" }, C_ROSE)
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            List53(Modifier.weight(1f).fillMaxHeight(), "Recently finished", "Films & shows", o.recentFinished, "Nothing finished yet", accent)
            List53(Modifier.weight(1f).fillMaxHeight(), "Binges", "Most eps in a day", o.binges, "No binge days yet", C_AMBER)
        }
    }
}

@Composable
private fun RowScope.Tile53(label: String, value: String, sub: String, color: Color) {
    Column(Modifier.weight(1f).fillMaxHeight().background(C_CARD, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 11.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Label53(label, color)
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1, softWrap = false)
        Text(sub, style = MaterialTheme.typography.labelMedium, color = color.copy(alpha = 0.95f), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RowScope.Fact53(label: String, main: String, sub: String?, color: Color) {
    Column(Modifier.weight(1f).fillMaxHeight().background(C_CARD, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Label53(label, color)
        Text(main, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, lineHeight = 20.sp, overflow = TextOverflow.Ellipsis)
        if (sub != null) Text(sub, style = MaterialTheme.typography.labelMedium, color = C_MUTE, maxLines = 2, lineHeight = 15.sp, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun List53(modifier: Modifier, title: String, sub: String, lines: List<NuvioCTasteLine>, emptyText: String, color: Color) {
    Card53(modifier) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Label53(title, color)
            Spacer(Modifier.weight(1f))
            Text(sub, style = MaterialTheme.typography.labelSmall, color = C_MUTE, maxLines = 1, softWrap = false)
        }
        if (lines.isEmpty()) Text(emptyText, style = MaterialTheme.typography.bodySmall, color = C_MUTE)
        lines.forEach { l ->
            Row(Modifier.fillMaxWidth().height(26.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(l.name, style = MaterialTheme.typography.bodyMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(l.sub, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 10.dp))
                Text(day53(l.date), style = MaterialTheme.typography.labelMedium, color = C_MUTE, maxLines = 1, softWrap = false, textAlign = TextAlign.End, modifier = Modifier.width(52.dp))
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
                Title53("Viewing hours", C_TEAL, listOfNotNull(peakHour?.let { "Peak %02d:00".format(it) }, peakDay?.getDisplayName(TextStyle.SHORT, Locale.US)).joinToString(" · "))
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
                    Text("$pct%", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold, color = Color.White)
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
                top.forEachIndexed { i, g -> HBar53(g.name, "%,d".format(g.count), g.count.toFloat() / max, palette[i % palette.size]) }
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
                f.studios.forEach { (name, n) -> HBar53(name, n.toString(), n.toFloat() / max, C_ROSE) }
            }
        }
        Row(Modifier.fillMaxWidth().height(64.dp).background(C_CARD, RoundedCornerShape(14.dp)).padding(vertical = 10.dp)) {
            val lang = strip.languages
            StripFact53("Language", lang.firstOrNull()?.let { "${it.second}% ${it.first}" }, lang.drop(1).takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = "+ ") { it.first }, C_TEAL, first = true)
            StripFact53("Countries", strip.countries.takeIf { it > 0 }?.toString(), strip.topCountries.takeIf { it.isNotEmpty() }?.joinToString(" · "), C_AMBER)
            StripFact53("Avg film", strip.avgFilmMinutes?.let { hm53(it) }, strip.longestFilm?.let { "Longest ${hm53(it.second)}" }, C_ROSE)
            StripFact53("Avg episode", strip.avgEpisodeMinutes?.let { hm53(it) }, null, C_VIOLET)
            StripFact53("vs TMDB", s.vsCrowd?.let { "%+.1f".format(it) }, s.vsCrowd?.let { if (it >= 0) "You rate higher" else "You rate lower" } ?: "Needs 10 ratings", accent)
        }
    }
}

@Composable
private fun RowScope.StripFact53(label: String, value: String?, sub: String?, color: Color, first: Boolean = false) {
    Row(Modifier.weight(1f).fillMaxHeight()) {
        if (!first) Box(Modifier.width(1.dp).fillMaxHeight().background(C_TRACK))
        Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Label53(label, color)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value ?: "–", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, softWrap = false)
                if (sub != null) Text("  $sub", style = MaterialTheme.typography.labelSmall, color = C_MUTE, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun Title53(title: String, color: Color, line: String?) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Label53(title, color)
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
                if (values && c > 0) Text(if (c >= 1000) "${c / 1000}k" else "$c", style = MaterialTheme.typography.labelSmall, color = C_MUTE, maxLines = 1, softWrap = false, fontSize = 9.sp)
                Box(Modifier.fillMaxWidth().weight((c.toFloat() / max).coerceAtLeast(0.02f), fill = true)
                    .background(if (i == highlight) color else color.copy(alpha = 0.32f), RoundedCornerShape(3.dp)))
                if (c < max) Spacer(Modifier.weight((1f - c.toFloat() / max).coerceAtLeast(0.0001f)))
                if (labels != null) Text(labels[i], style = MaterialTheme.typography.labelSmall, color = C_MUTE, maxLines = 1, softWrap = false, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun HBar53(name: String, value: String, share: Float, color: Color) {
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(name, style = MaterialTheme.typography.bodySmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodySmall, color = C_MUTE, maxLines = 1, softWrap = false)
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
    val requesters = remember { HashMap<String, FocusRequester>() }
    fun req(key: String) = requesters.getOrPut(key) { if (key == "spin") spinFocus else FocusRequester() }
    LaunchedEffect(row?.id, people.size) {
        val key = viewModel.discoverFocusKey ?: return@LaunchedEffect
        val r = requesters[key]
        if (r != null && runCatching { r.requestFocusAfterFrames() }.getOrDefault(false)) viewModel.discoverFocusKey = null
    }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        var spinFocused by remember { mutableStateOf(false) }
        Card(
            onClick = { viewModel.discoverFocusKey = "spin"; onSpin() },
            modifier = Modifier.width(250.dp).fillMaxHeight().padding(4.dp)
                .shadow(if (spinFocused) 24.dp else 10.dp, RoundedCornerShape(20.dp), ambientColor = accent, spotColor = accent)
                .focusRequester(req("spin")).focusProperties { up = upTarget }
                .onFocusChanged { spinFocused = it.isFocused },
            shape = CardDefaults.shape(RoundedCornerShape(20.dp)),
            scale = CardDefaults.scale(focusedScale = 1f),
            colors = CardDefaults.colors(containerColor = Color(0xFF1C2233), focusedContainerColor = Color(0xFF243052)),
            border = CardDefaults.border(
                border = Border(BorderStroke(2.dp, accent.copy(alpha = 0.55f)), shape = RoundedCornerShape(20.dp)),
                focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = RoundedCornerShape(20.dp))
            )
        ) {
            Column(
                Modifier.fillMaxSize().background(Brush.radialGradient(listOf(accent.copy(alpha = 0.32f), C_VIOLET.copy(alpha = 0.10f), Color.Transparent), radius = 560f)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    all.spinPosters.take(3).forEach { url ->
                        val r = remember(url) { ImageRequest.Builder(context).data(url).size(96, 144).build() }
                        AsyncImage(r, null, contentScale = ContentScale.Crop, modifier = Modifier.width(44.dp).height(66.dp).clip(RoundedCornerShape(6.dp)).background(C_INNER))
                    }
                }
                Text("Spin my taste", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text("One unseen pick from what you rate and watch most.", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
                Spacer(Modifier.weight(1f))
                Text(listOfNotNull(all.genres.getOrNull(0)?.name, all.genres.getOrNull(1)?.name, all.topDecade?.let { "${it}s" }).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium, color = accent, maxLines = 2)
                Box(Modifier.background(Color.White, RoundedCornerShape(50)).padding(horizontal = 18.dp, vertical = 8.dp)) {
                    Text("Spin", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (row != null) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Label53(row.title, C_TEAL)
                LazyRow(state = viewModel.discoverListState, horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)) {
                    itemsIndexed(row.items, key = { _, it -> "t:" + it.id }) { i, item ->
                        val key = "t:" + item.id
                        val last = i == row.items.lastIndex
                        // a held Right stops on the last poster
                        Poster53(item, Modifier.focusRequester(req(key)).focusProperties { up = upTarget; if (last) right = FocusRequester.Cancel }) {
                            viewModel.discoverFocusKey = key
                            onOpenTitle(item.id, item.type)
                        }
                    }
                }
            }
            if (people.isNotEmpty()) Card53(Modifier.fillMaxWidth().weight(1f)) {
                Title53("Faces you watch most", C_ROSE, "Titles together")
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    people.forEachIndexed { i, p ->
                        val key = "p:" + p.person.id
                        val last = i == people.lastIndex
                        Face53(p, accent, Modifier.focusRequester(req(key)).focusProperties { if (row == null) up = upTarget; if (last) right = FocusRequester.Cancel }) {
                            viewModel.discoverFocusKey = key
                            onOpenPerson(p.person.id, p.person.name, p.director)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Poster53(item: com.nuvio.tv.data.nuvioc.NuvioCTasteShelfItem, modifier: Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(92.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Card(
            onClick = onClick,
            modifier = modifier.width(92.dp).height(138.dp).onFocusChanged { focused = it.isFocused },
            shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
            scale = CardDefaults.scale(focusedScale = 1f),
            border = CardDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(10.dp))),
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
        FocusMarqueeText(text = item.name, focused = focused, style = MaterialTheme.typography.labelMedium, color = if (focused) Color.White else C_MUTE)
    }
}

@Composable
private fun Face53(p: com.nuvio.tv.data.nuvioc.NuvioCTastePersonStat, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(92.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Surface(
            onClick = onClick,
            modifier = modifier.size(72.dp).onFocusChanged { focused = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
            colors = ClickableSurfaceDefaults.colors(containerColor = C_INNER, focusedContainerColor = C_INNER),
            border = ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(1.5.dp, accent.copy(alpha = 0.45f)), shape = CircleShape),
                focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = CircleShape)
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
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = Color.White, textAlign = TextAlign.Center
        )
        Text(if (p.director) "Director · ${p.count}" else "${p.count} titles", style = MaterialTheme.typography.labelSmall, color = C_MUTE, maxLines = 1, softWrap = false)
    }
}

// ---- Wrapped -------------------------------------------------------------------------------------------------

@Composable
private fun Wrapped53(y: NuvioCTasteYear, accent: Color) {
    val today = LocalDate.now()
    val soFar = y.year == today.year && today.monthValue < 12
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(
                Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(accent.copy(alpha = 0.55f), C_VIOLET.copy(alpha = 0.35f), C_CARD)), RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Your ${y.year}" + if (soFar) " so far" else "", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Row(Modifier.fillMaxWidth()) {
                    Num53("%,d".format(y.hours), "hours", Modifier.weight(1f))
                    Num53("%,d".format(y.titles), "titles", Modifier.weight(1f))
                    Num53("%,d".format(y.films), "films", Modifier.weight(1f))
                    Num53("%,d".format(y.episodes), "episodes", Modifier.weight(1f))
                }
            }
            Ranked53(Modifier.fillMaxWidth().weight(1f, fill = false), "Top shows", y.topShows, "No shows this year", C_TEAL)
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Ranked53(Modifier.fillMaxWidth(), "Top films", y.topFilms, "No films this year", C_AMBER)
            Card53(Modifier.fillMaxWidth().weight(1f)) {
                Title53("Highlights", C_ROSE, null)
                Line53("Top genre", y.topGenre)
                Line53("Most seen face", y.topFace?.let { "${it.first} · ${it.second}" })
                Line53("Biggest day", y.biggestDay?.let { day53(it) + " · " + hm53(y.biggestDayMinutes) })
                Line53("Longest streak", y.longestStreak.takeIf { it > 0 }?.let { "$it days" })
                Line53("Usually", when (y.peakPart) { 0 -> "Mornings"; 1 -> "Afternoons"; 2 -> "Evenings"; 3 -> "Late nights"; else -> null })
                Line53("Average rating", y.avgRating?.let { "%.1f · %d rated".format(it, y.rated) })
            }
        }
        Column(Modifier.weight(1.1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Card53(Modifier.fillMaxWidth().weight(1f)) {
                val peak = y.busiestMonth
                Title53("Hours by month", accent, peak?.let { java.time.Month.of(it + 1).getDisplayName(TextStyle.SHORT, Locale.US) + " peak" })
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
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1, softWrap = false)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f), maxLines = 1)
    }
}

@Composable
private fun Ranked53(modifier: Modifier, title: String, lines: List<NuvioCTasteLine>, emptyText: String, color: Color) {
    Card53(modifier) {
        Title53(title, color, null)
        if (lines.isEmpty()) Text(emptyText, style = MaterialTheme.typography.bodySmall, color = C_MUTE)
        lines.forEachIndexed { i, l ->
            // rank and title share one baseline; fixed 42 dp rows from the top, so a short list never spreads out
            Row(Modifier.fillMaxWidth().height(42.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = color,
                    textAlign = TextAlign.End, modifier = Modifier.width(20.dp).alignByBaseline())
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).alignByBaseline()) {
                    Text(l.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(l.sub, style = MaterialTheme.typography.labelSmall, color = C_MUTE, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

@Composable
private fun Line53(label: String, value: String?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = C_MUTE, maxLines = 1, softWrap = false)
        Spacer(Modifier.width(10.dp))
        Text(value ?: "–", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
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
