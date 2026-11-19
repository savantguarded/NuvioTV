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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import com.nuvio.tv.ui.screens.shuffle.NuvioCRoulettePreset
import com.nuvio.tv.ui.theme.NuvioTheme
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

// [fork] Nuvio C round 53 `taste_tabs_v2`: Taste reworked from Charles's round 53 list.
// - One header row: a large avatar + profile name, the tabs (Overview, Discover, Stats, Wrapped) and the range
//   (All time / This year / Month) in the same focus row, so a range can be picked from any tab; the range is shared
//   by Overview, Discover and Stats and kept for the visit. Wrapped puts a Year dropdown in the same place.
// - Pages with nothing to press (Overview, Stats, Wrapped) have no invisible focus stop: Down from the header does
//   nothing, so focus never "disappears" and Up never jumps to the far-right tab.
// - Discover remembers the item you opened and lands on it again after Back.
// - Each tab shows its own facts only: Overview = what you did, Stats = patterns, Discover = what's next + people,
//   Wrapped = the year's story. No rewatch counts anywhere (Nuvio can't tell a rewatch from a re-sync).

private val R53_CARD = Color(0xFF16171B)
private val R53_INNER = Color(0xFF202126)
private val R53_MUTE = Color.White.copy(alpha = 0.66f)
private val R53_BAR = Color(0xFF5D5F66)
private val R53_TRACK = Color(0xFF2A2B30)
private const val R53_STATS_MIN = 10

private enum class Tab53(val label: String) { OVERVIEW("Overview"), DISCOVER("Discover"), STATS("Stats"), WRAPPED("Wrapped") }

@Composable
internal fun NuvioCTasteTabsV2(
    viewModel: NuvioCTasteViewModel,
    onOpenTitle: (id: String, type: String) -> Unit,
    onOpenPerson: (id: Int, name: String, crew: Boolean) -> Unit,
    onOpenRoulette: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val rows by viewModel.rows.collectAsState()
    val monthData by viewModel.monthData.collectAsState()
    val overview by viewModel.overview.collectAsState()
    val strip by viewModel.strip.collectAsState()
    val facts by viewModel.facts.collectAsState()
    val yearData by viewModel.year.collectAsState()
    val years by viewModel.wrappedYears.collectAsState()
    val range = viewModel.tasteRange
    val month = viewModel.tasteMonth

    LaunchedEffect(Unit) { viewModel.open() }
    LaunchedEffect(month) { viewModel.selectMonth(month) }
    val all = state.all[NuvioCTasteRange.ALL]
    val stats: NuvioCTasteStats? = if (month != null) monthData?.first else state.all[range] ?: all
    LaunchedEffect(state.builtAt) { all?.let { viewModel.loadRows(it, state.builtAt) } }
    LaunchedEffect(state.builtAt, range, month) {
        if (state.builtAt > 0) {
            viewModel.loadRound53(range, month)
            viewModel.loadFacts(range, month) // hour / weekday / studios for Stats
        }
    }
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
    val yearPillFocus = remember { FocusRequester() }
    var yearMenu by remember { mutableStateOf(false) }
    val downTarget: FocusRequester = when {
        empty && current == Tab53.OVERVIEW -> newProfileFocus
        current == Tab53.DISCOVER -> spinFocus
        else -> FocusRequester.Cancel
    }
    LaunchedEffect(all != null) {
        if (all == null) return@LaunchedEffect
        // Back from a Discover title: Discover restores its own item, the header stays out of the way
        if (current == Tab53.DISCOVER && viewModel.discoverFocusKey != null) return@LaunchedEffect
        tabFocus.getValue(current).requestFocusAfterFrames()
    }

    fun spin(genre: String?, decade: Int?) {
        NuvioCRoulettePreset.set(NuvioCRoulettePreset.Preset(mood = NuvioCRoulettePreset.moodFor(genre), decade = NuvioCRoulettePreset.decadeFor(decade)))
        onOpenRoulette()
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B0D))) {
        if (all == null && com.nuvio.tv.NuvioCFeatures.TASTE_BUILD_ANIM && state.progressTotal > 0) {
            TasteBuilding(state, accent)
            return@Box
        }
        Column(Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, top = 18.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                // profile: avatar + large name
                Box(Modifier.size(42.dp).background(accent, CircleShape), contentAlignment = Alignment.Center) {
                    Text(viewModel.profileName.trim().take(1).uppercase(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0B0B0D))
                }
                Column(Modifier.padding(start = 12.dp, end = 22.dp)) {
                    Text("TASTE", style = MaterialTheme.typography.labelSmall, color = R53_MUTE, letterSpacing = 1.5.sp, maxLines = 1)
                    Text(viewModel.profileName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1)
                }
                tabs.forEachIndexed { i, tab ->
                    Pill53(
                        label = tab.label,
                        selected = tab == current,
                        modifier = Modifier.padding(end = 6.dp).focusRequester(tabFocus.getValue(tab)).focusProperties { down = downTarget },
                        onFocused = { if (viewModel.tasteTab != i) { viewModel.tasteTab = i; yearMenu = false } }
                    ) { viewModel.tasteTab = i }
                }
                Spacer(Modifier.weight(1f))
                if (current == Tab53.WRAPPED) {
                    val y = yearData?.year
                    if (y != null) Pill53(
                        label = "Year $y  ▾",
                        selected = true,
                        modifier = Modifier.focusRequester(yearPillFocus).focusProperties { down = FocusRequester.Cancel }
                    ) { yearMenu = !yearMenu }
                } else if (!empty) {
                    Row(Modifier.background(R53_CARD, RoundedCornerShape(50)).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val rangeMod = Modifier.focusProperties { down = downTarget }
                        Pill53("All time", month == null && range == NuvioCTasteRange.ALL, rangeMod, quiet = true) { viewModel.tasteRange = NuvioCTasteRange.ALL; viewModel.tasteMonth = null }
                        Pill53("This year", month == null && range == NuvioCTasteRange.YEAR, rangeMod, quiet = true) { viewModel.tasteRange = NuvioCTasteRange.YEAR; viewModel.tasteMonth = null }
                        val months = state.months.sortedDescending()
                        if (months.isNotEmpty()) {
                            val label = month?.let { "‹ " + it.month.getDisplayName(TextStyle.SHORT, Locale.US) + " " + it.year + " ›" } ?: "Month"
                            Pill53(label, month != null, rangeMod, quiet = true) {
                                // first press: the newest month; each press after steps one month back, then wraps
                                val i = month?.let { months.indexOf(it) } ?: -1
                                viewModel.tasteMonth = months[(i + 1) % months.size]
                            }
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                Crossfade(targetState = current, animationSpec = tween(150), label = "taste53") { tab ->
                    val o = overview
                    when {
                        all == null -> Center53("Reading your history")
                        empty && tab == Tab53.OVERVIEW -> NewProfile53(newProfileFocus) { spin(null, null) }
                        tab == Tab53.OVERVIEW -> when {
                            o == null -> Center53("Reading your history")
                            o.empty -> Center53("Nothing watched in " + (month?.let { it.month.getDisplayName(TextStyle.FULL, Locale.US) + " " + it.year } ?: if (range == NuvioCTasteRange.YEAR) "this year yet" else "this range"))
                            else -> Overview53(o, accent)
                        }
                        tab == Tab53.STATS -> when {
                            (all.titles) < R53_STATS_MIN -> Center53("Stats unlock at $R53_STATS_MIN titles · ${R53_STATS_MIN - all.titles} to go")
                            stats == null || facts == null || strip == null -> Center53("Reading your history")
                            stats.empty -> Center53("Nothing watched in this range")
                            else -> Stats53(stats, facts!!, strip!!, accent)
                        }
                        tab == Tab53.DISCOVER -> Discover53(
                            viewModel = viewModel,
                            all = all,
                            people = (stats ?: all).let { s -> s.people.ifEmpty { s.morePeople } }.take(7),
                            rows = rows,
                            accent = accent,
                            spinFocus = spinFocus,
                            upTarget = tabFocus.getValue(Tab53.DISCOVER),
                            onSpin = { spin(all.genres.firstOrNull()?.name, all.topDecade) },
                            onOpenTitle = onOpenTitle,
                            onOpenPerson = onOpenPerson
                        )
                        else -> yearData?.let { Wrapped53(it) } ?: Center53("Reading your history")
                    }
                }
                // Year dropdown, over the page under its pill
                val yd = yearData
                if (yearMenu && current == Tab53.WRAPPED && yd != null) {
                    YearMenu53(yd, Modifier.align(Alignment.TopEnd).zIndex(2f)) { picked ->
                        yearMenu = false
                        if (picked != null) viewModel.tasteYear = picked
                        runCatching { yearPillFocus.requestFocus() }
                    }
                }
            }
        }
    }
}

@Composable
private fun Pill53(label: String, selected: Boolean, modifier: Modifier = Modifier, quiet: Boolean = false, onFocused: () -> Unit = {}, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { if (it.isFocused) onFocused() },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White else if (quiet) Color.Transparent else Color(0xFF1F2025),
            contentColor = if (selected) Color.Black else Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(NuvioTheme.focusRing.border(2.dp), shape = RoundedCornerShape(50)))
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 7.dp), maxLines = 1)
    }
}

@Composable
private fun Center53(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text, style = MaterialTheme.typography.titleMedium, color = R53_MUTE) }
}

@Composable
private fun Card53(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.background(R53_CARD, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 12.dp), content = content)
}

private fun hm53(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h >= 100 -> "%,d h".format(h)
        h > 0 -> "$h h" + if (m > 0) " $m m" else ""
        else -> "$m m"
    }
}

private fun day53(d: LocalDate): String = "${d.dayOfMonth} " + d.month.getDisplayName(TextStyle.SHORT, Locale.US)

// ---- Overview ------------------------------------------------------------------------------------------------

@Composable
private fun Overview53(o: NuvioCTasteOverview, accent: Color) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().height(86.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val delta = o.prevMinutes?.let { p ->
                val pct = ((o.minutes - p) * 100.0 / p).toInt()
                when {
                    pct > 0 -> "$pct% more than ${o.prevLabel}"
                    pct < 0 -> "${-pct}% less than ${o.prevLabel}"
                    else -> "same as ${o.prevLabel}"
                }
            }
            Tile53("Time watched", hm53(o.minutes), delta ?: "${o.daysWatched} days with something on", if (delta != null) accent else R53_MUTE)
            Tile53("Titles", "%,d".format(o.titles), "${"%,d".format(o.films)} films · ${"%,d".format(o.shows)} shows")
            Tile53("Episodes", "%,d".format(o.episodes), if (o.shows > 0) "across ${o.shows} shows" else "no shows in this range")
            Tile53("Days you watched", "%,d".format(o.daysWatched), "of ${"%,d".format(o.daysInRange)} · longest streak ${o.longestStreak} " + if (o.longestStreak == 1) "day" else "days")
        }
        Row(Modifier.fillMaxWidth().height(108.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val ms = o.mostShow
            Fact53("Most watched show", ms?.name ?: "–", ms?.let { "${it.episodes} episodes · ${hm53(it.minutes)}" }, ms?.let { "Last watched ${day53(it.last)}" })
            val b = o.biggestDay
            Fact53("Biggest day", b?.let { hm53(it.minutes) } ?: "–",
                b?.let { it.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US) + " " + day53(it.date) + if (it.date.year != LocalDate.now().year) " ${it.date.year}" else "" },
                b?.topTitle?.let { t -> if (b.topIsShow && b.topCount > 1) "Mostly $t, ${b.topCount} episodes" else "Mostly $t" })
            val shows = o.finishedShows + o.inProgressShows + o.upToDateShows
            Fact53("Shows", if (shows == 0) "–" else "${o.finishedShows} finished",
                if (shows == 0) "No shows in this range" else "${o.inProgressShows} in progress",
                if (shows == 0) null else "${o.upToDateShows} up to date with new episodes")
            Fact53("Fresh vs catalogue", o.freshPercent?.let { "$it% new releases" } ?: "–", "watched within a year of release",
                o.oldest?.let { "Oldest: ${it.first} (${it.second})" })
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            List53(Modifier.weight(1f).fillMaxHeight(), "Recently finished", "films and whole shows", o.recentFinished, "Nothing finished in this range")
            List53(Modifier.weight(1f).fillMaxHeight(), "Binges", "most episodes of one show in a day", o.binges, "No binge days here yet")
        }
    }
}

@Composable
private fun RowScope.Tile53(label: String, value: String, sub: String, subColor: Color = R53_MUTE) {
    Column(Modifier.weight(1f).fillMaxHeight().background(R53_CARD, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = R53_MUTE, maxLines = 1)
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1)
        Text(sub, style = MaterialTheme.typography.labelMedium, color = subColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RowScope.Fact53(label: String, main: String, line: String?, note: String?) {
    Column(Modifier.weight(1f).fillMaxHeight().background(R53_CARD, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = R53_MUTE, maxLines = 1)
        Text(main, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (line != null) Text(line, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (note != null) Text(note, style = MaterialTheme.typography.labelMedium, color = R53_MUTE, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
    }
}

@Composable
private fun List53(modifier: Modifier, title: String, sub: String, lines: List<NuvioCTasteLine>, emptyText: String) {
    Card53(modifier) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
            Spacer(Modifier.weight(1f))
            Text(sub, style = MaterialTheme.typography.labelSmall, color = R53_MUTE, maxLines = 1)
        }
        if (lines.isEmpty()) Text(emptyText, style = MaterialTheme.typography.bodySmall, color = R53_MUTE)
        lines.forEach { l ->
            Row(Modifier.fillMaxWidth().height(25.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(l.name, style = MaterialTheme.typography.bodyMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Text("  " + l.sub, style = MaterialTheme.typography.bodySmall, color = R53_MUTE, maxLines = 1)
                Spacer(Modifier.weight(0.001f))
                Text(day53(l.date), style = MaterialTheme.typography.bodySmall, color = R53_MUTE, maxLines = 1, modifier = Modifier.padding(start = 10.dp))
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
                Title53("Viewing hours", listOfNotNull(
                    peakHour?.let { "Peak %02d:00".format(it) },
                    peakDay?.let { it.getDisplayName(TextStyle.FULL, Locale.US) + "s lead the week" }
                ).joinToString(" · "))
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(Modifier.weight(3f).fillMaxHeight()) {
                        Bars53(f.byHour, peakHour, null, Modifier.fillMaxWidth().weight(1f))
                        Row(Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            listOf("00", "06", "12", "18", "23").forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = R53_MUTE) }
                        }
                    }
                    Bars53(f.byWeekday, f.byWeekday.indices.maxByOrNull { f.byWeekday[it] }, listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"), Modifier.weight(2f).fillMaxHeight())
                }
            }
            Card53(Modifier.weight(1f).fillMaxHeight()) {
                val counts = s.ratingCounts.takeIf { it.size == 10 } ?: List(10) { 0 }
                val rated = counts.sum()
                if (rated == 0) {
                    Title53("Shows you finish", null)
                    val pct = s.finishedPercent ?: 0
                    Spacer(Modifier.weight(1f))
                    Text("$pct%", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text("of the shows you start", style = MaterialTheme.typography.bodySmall, color = R53_MUTE)
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(8.dp).background(R53_TRACK, RoundedCornerShape(4.dp))) {
                        Box(Modifier.fillMaxWidth(pct / 100f).height(8.dp).background(accent, RoundedCornerShape(4.dp)))
                    }
                    Spacer(Modifier.weight(1f))
                } else {
                    Title53("Rating distribution", "%,d rated".format(rated))
                    val top = counts.indices.maxByOrNull { counts[it] }
                    Bars53(counts, top, (1..10).map { it.toString() }, Modifier.fillMaxWidth().weight(1f), values = true)
                    val sorted = counts.flatMapIndexed { i, c -> List(c) { i + 1 } }
                    val median = sorted.getOrNull(sorted.size / 2)
                    Text(listOfNotNull(median?.let { "Median $it" }, "most often ${(top ?: 0) + 1}", "tens: ${counts[9]}").joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium, color = R53_MUTE, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Card53(Modifier.weight(1f).fillMaxHeight()) {
                Title53("Genres", "by titles watched")
                val top = s.genres.take(5)
                val max = top.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
                top.forEach { g -> HBar53(g.name, "%,d".format(g.count), g.count.toFloat() / max) }
            }
            Card53(Modifier.weight(1f).fillMaxHeight()) {
                val decades = s.decades.takeLast(9)
                Title53("Release decades", s.decades.firstOrNull()?.let { "since the ${it.first}s" })
                Bars53(decades.map { it.second }, decades.indices.maxByOrNull { decades[it].second },
                    decades.map { "${it.first % 100}".padStart(2, '0') + "s" }, Modifier.fillMaxWidth().weight(1f), values = true)
            }
            Card53(Modifier.weight(1f).fillMaxHeight()) {
                Title53("Studios and networks", null)
                val max = f.studios.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
                if (f.studios.isEmpty()) Text("Not enough details yet", style = MaterialTheme.typography.bodySmall, color = R53_MUTE)
                f.studios.forEach { (name, n) -> HBar53(name, n.toString(), n.toFloat() / max) }
            }
        }
        Row(Modifier.fillMaxWidth().height(66.dp).background(R53_CARD, RoundedCornerShape(14.dp)).padding(vertical = 10.dp)) {
            val lang = strip.languages
            StripFact53("Original language", lang.firstOrNull()?.let { "${it.second}% ${it.first}" },
                lang.drop(1).takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = "then ") { "${it.first} ${it.second}%" }, first = true)
            StripFact53("Countries", strip.countries.takeIf { it > 0 }?.toString(), strip.topCountries.takeIf { it.isNotEmpty() }?.joinToString(", ", postfix = " lead"))
            StripFact53("Average film", strip.avgFilmMinutes?.let { hm53(it) }, strip.longestFilm?.let { "longest: ${it.first}" })
            StripFact53("Average episode", strip.avgEpisodeMinutes?.let { hm53(it) }, null)
            StripFact53("You vs the crowd", s.vsCrowd?.let { "%+.1f".format(it) }, s.vsCrowd?.let { if (it >= 0) "you rate a little higher than TMDB" else "you rate a little lower than TMDB" } ?: "needs 10 rated titles")
        }
    }
}

@Composable
private fun RowScope.StripFact53(label: String, value: String?, sub: String?, first: Boolean = false) {
    Row(Modifier.weight(1f).fillMaxHeight()) {
        if (!first) Box(Modifier.width(1.dp).fillMaxHeight().background(R53_TRACK))
        Column(Modifier.padding(horizontal = 14.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = R53_MUTE, maxLines = 1)
            Text(value ?: "–", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = R53_MUTE, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Title53(title: String, line: String?) {
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
        if (!line.isNullOrBlank()) {
            Spacer(Modifier.width(10.dp))
            Text(line, style = MaterialTheme.typography.labelSmall, color = R53_MUTE, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun Bars53(counts: List<Int>, highlight: Int?, labels: List<String>?, modifier: Modifier, values: Boolean = false) {
    val max = counts.maxOrNull()?.coerceAtLeast(1) ?: 1
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(if (counts.size > 12) 3.dp else 6.dp), verticalAlignment = Alignment.Bottom) {
        counts.forEachIndexed { i, c ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                if (values && c > 0) Text("%,d".format(c), style = MaterialTheme.typography.labelSmall, color = R53_MUTE, maxLines = 1, fontSize = 9.sp)
                Box(Modifier.fillMaxWidth().weight((c.toFloat() / max).coerceAtLeast(0.02f), fill = true).background(if (i == highlight) Color.White else R53_BAR, RoundedCornerShape(3.dp)))
                if (c < max) Spacer(Modifier.weight((1f - c.toFloat() / max).coerceAtLeast(0.0001f)))
                if (labels != null) Text(labels[i], style = MaterialTheme.typography.labelSmall, color = R53_MUTE, maxLines = 1, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun HBar53(name: String, value: String, share: Float) {
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(name, style = MaterialTheme.typography.bodySmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodySmall, color = R53_MUTE)
        }
        Box(Modifier.fillMaxWidth().padding(top = 3.dp).height(3.dp).background(R53_TRACK, RoundedCornerShape(2.dp))) {
            Box(Modifier.fillMaxWidth(share.coerceIn(0.02f, 1f)).height(3.dp).background(Color(0xFFD9D9DC), RoundedCornerShape(2.dp)))
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
        // Spin my taste: its own card, apart from the rows
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
                Modifier.fillMaxSize().background(Brush.radialGradient(listOf(accent.copy(alpha = 0.28f), Color.Transparent), radius = 520f)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    all.spinPosters.take(3).forEach { url ->
                        val r = remember(url) { ImageRequest.Builder(context).data(url).size(96, 144).build() }
                        AsyncImage(r, null, contentScale = ContentScale.Crop, modifier = Modifier.width(44.dp).height(66.dp).clip(RoundedCornerShape(6.dp)).background(R53_INNER))
                    }
                }
                Text("Spin my taste", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text("One pick you haven't seen, from what you rate highest and watch most.", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
                Spacer(Modifier.weight(1f))
                Text(listOfNotNull(all.genres.getOrNull(0)?.name, all.genres.getOrNull(1)?.name, all.topDecade?.let { "${it}s" }).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium, color = R53_MUTE, maxLines = 2)
                Box(Modifier.background(Color.White, RoundedCornerShape(50)).padding(horizontal = 18.dp, vertical = 8.dp)) {
                    Text("Spin", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (row != null) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(row.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                LazyRow(state = viewModel.discoverListState, horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)) {
                    items(row.items, key = { "t:" + it.id }) { item ->
                        val key = "t:" + item.id
                        Poster53(item, Modifier.focusRequester(req(key)).focusProperties { up = upTarget }) {
                            viewModel.discoverFocusKey = key
                            onOpenTitle(item.id, item.type)
                        }
                    }
                }
            }
            if (people.isNotEmpty()) Card53(Modifier.fillMaxWidth().weight(1f)) {
                Title53("Faces you watch most", "titles together")
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    people.forEach { p ->
                        val key = "p:" + p.person.id
                        Face53(p, Modifier.focusRequester(req(key)).focusProperties { if (row == null) up = upTarget }) {
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
            colors = CardDefaults.colors(containerColor = R53_INNER),
            glow = com.nuvio.tv.ui.components.nuvioCPosterCardGlow() // [fork] round 53 poster_glow_everywhere
        ) {
            if (item.poster != null) {
                val r = remember(item.poster) { ImageRequest.Builder(context).data(item.poster).size(184, 276).build() }
                AsyncImage(r, item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
                Text(item.name, style = MaterialTheme.typography.labelSmall, color = R53_MUTE, maxLines = 4)
            }
        }
        com.nuvio.tv.ui.components.FocusMarqueeText(
            text = item.name, focused = focused,
            style = MaterialTheme.typography.labelMedium, color = if (focused) Color.White else R53_MUTE
        )
    }
}

@Composable
private fun Face53(p: com.nuvio.tv.data.nuvioc.NuvioCTastePersonStat, modifier: Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(92.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Surface(
            onClick = onClick,
            modifier = modifier.size(72.dp).onFocusChanged { focused = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
            colors = ClickableSurfaceDefaults.colors(containerColor = R53_INNER, focusedContainerColor = R53_INNER),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = CircleShape))
        ) {
            val photo = p.person.photo
            if (photo != null) {
                val r = remember(photo) { ImageRequest.Builder(context).data(photo).size(144, 144).build() }
                AsyncImage(r, p.person.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
            } else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(p.person.name.take(1), style = MaterialTheme.typography.titleLarge, color = R53_MUTE)
            }
        }
        if (focused) com.nuvio.tv.ui.components.FocusMarqueeText(
            text = p.person.name, focused = true,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), color = Color.White, textAlign = TextAlign.Center
        ) else Text(p.person.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = Color.White,
            maxLines = 2, textAlign = TextAlign.Center, lineHeight = 15.sp, overflow = TextOverflow.Ellipsis)
        Text(if (p.director) "director · ${p.count}" else "${p.count} titles", style = MaterialTheme.typography.labelSmall, color = R53_MUTE, maxLines = 1)
    }
}

// ---- Wrapped -------------------------------------------------------------------------------------------------

@Composable
private fun Wrapped53(y: NuvioCTasteYear) {
    val today = LocalDate.now()
    val soFar = y.year == today.year && today.monthValue < 12
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Card53(Modifier.fillMaxWidth()) {
                Text("Your ${y.year}" + if (soFar) " so far" else "", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text("${"%,d".format(y.hours)} hours · ${"%,d".format(y.titles)} titles", style = MaterialTheme.typography.bodyMedium, color = R53_MUTE)
            }
            Ranked53(Modifier.fillMaxWidth().weight(1f, fill = false), "Top shows", "by episodes", y.topShows, "No shows in ${y.year}")
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Ranked53(Modifier.fillMaxWidth(), "Top films", "your highest rated", y.topFilms, "No films in ${y.year}")
            Card53(Modifier.fillMaxWidth().weight(1f)) {
                Title53("Firsts and lasts", null)
                Line53("First of the year", y.firstTitle?.let { "${it.first} · ${day53(it.second)}" })
                Line53(if (soFar) "Latest" else "Last of the year", y.lastTitle?.let { "${it.first} · ${day53(it.second)}" })
                Line53("New shows started", "%,d".format(y.newShows))
                Line53("New directors", "%,d".format(y.newDirectors))
            }
        }
        Column(Modifier.weight(1.1f).fillMaxHeight()) {
            Card53(Modifier.fillMaxSize()) {
                val peak = y.byMonth.indices.maxByOrNull { y.byMonth[it] }?.takeIf { y.byMonth[it] > 0 }
                Title53("Hours by month", peak?.let { java.time.Month.of(it + 1).getDisplayName(TextStyle.FULL, Locale.US) + " was your biggest" })
                Bars53(y.byMonth.map { it / 60 }, peak, listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D"), Modifier.fillMaxWidth().weight(1f), values = true)
            }
        }
    }
}

@Composable
private fun Ranked53(modifier: Modifier, title: String, sub: String, lines: List<NuvioCTasteLine>, emptyText: String) {
    Card53(modifier) {
        Title53(title, sub)
        if (lines.isEmpty()) Text(emptyText, style = MaterialTheme.typography.bodySmall, color = R53_MUTE)
        lines.forEachIndexed { i, l ->
            // rank and title share one baseline; fixed 44 dp rows stacked from the top, so a short list never spreads out
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.End, modifier = Modifier.width(22.dp).alignByBaseline())
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).alignByBaseline()) {
                    Text(l.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(l.sub, style = MaterialTheme.typography.labelMedium, color = R53_MUTE, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun Line53(label: String, value: String?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = R53_MUTE, maxLines = 1)
        Spacer(Modifier.width(10.dp))
        Text(value ?: "–", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun YearMenu53(y: NuvioCTasteYear, modifier: Modifier, onDone: (Int?) -> Unit) {
    val first = remember { FocusRequester() }
    BackHandler { onDone(null) }
    LaunchedEffect(Unit) { first.requestFocusAfterFrames() }
    Column(
        modifier.width(200.dp).offset(y = (-6).dp).background(Color(0xFF24252B), RoundedCornerShape(14.dp))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp)).padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        y.hoursByYear.forEach { (year, hours) ->
            Surface(
                onClick = { onDone(year) },
                modifier = Modifier.fillMaxWidth().then(if (year == y.year) Modifier.focusRequester(first) else Modifier),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, contentColor = Color.White, focusedContainerColor = Color.White, focusedContentColor = Color.Black)
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(year.toString(), style = MaterialTheme.typography.titleSmall, fontWeight = if (year == y.year) FontWeight.Bold else FontWeight.Normal)
                    Spacer(Modifier.weight(1f))
                    Text("%,d h".format(hours), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun NewProfile53(focus: FocusRequester, onSpin: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Nothing watched yet", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(6.dp))
        Text("Your stats fill in as you watch on this profile.", style = MaterialTheme.typography.bodyMedium, color = R53_MUTE)
        Spacer(Modifier.height(14.dp))
        Pill53("Spin something", false, Modifier.focusRequester(focus)) { onSpin() }
    }
}
