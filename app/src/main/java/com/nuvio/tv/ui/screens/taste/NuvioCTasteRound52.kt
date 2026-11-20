package com.nuvio.tv.ui.screens.taste

import com.nuvio.tv.data.nuvioc.nuvioC3
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.nuvio.tv.data.nuvioc.NuvioCTasteFacts
import com.nuvio.tv.data.nuvioc.NuvioCTasteRange
import com.nuvio.tv.data.nuvioc.NuvioCTasteStats
import com.nuvio.tv.data.nuvioc.NuvioCTasteWrapped
import com.nuvio.tv.ui.components.FocusMarqueeText
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.screens.shuffle.NuvioCRoulettePreset
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

// [fork] Nuvio C round 52 `taste_tabs`: Taste as four tabs, each one screen that never scrolls: Overview (totals,
// split, streak, facts), Charts (when you watch, ratings, genres, decades, studios), Discover (Spin my taste, two rows
// picked from your taste, the faces you watch most) and Wrapped (a year, Left / Right for past years). Overview and
// Charts share the range: All time / This year / a month. Strictly the active profile's own data. A new profile sees
// Overview only; a light profile gets the parts it has data for and a line saying what unlocks the rest.

private val PAD_H = 40.dp
private val CARD = Color(0xFF16171B)
private val INNER = Color(0xFF202126)
private val MUTE = Color.White.copy(alpha = 0.62f)
private val BAR = Color(0xFF5D5F66)
private val TRACK = Color(0xFF2A2B30)
private const val CHART_MIN_TITLES = 10

@Composable
internal fun NuvioCTasteTabs(
    viewModel: NuvioCTasteViewModel,
    onOpenTitle: (id: String, type: String) -> Unit,
    onOpenPerson: (id: Int, name: String, crew: Boolean) -> Unit,
    onOpenRoulette: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val rows by viewModel.rows.collectAsState()
    val monthData by viewModel.monthData.collectAsState()
    val facts by viewModel.facts.collectAsState()
    val wrapped by viewModel.wrapped.collectAsState()
    val years by viewModel.wrappedYears.collectAsState()
    var range by rememberSaveable { mutableStateOf(NuvioCTasteRange.ALL) }
    var monthKey by rememberSaveable { mutableStateOf<String?>(null) }
    var yearShown by rememberSaveable { mutableStateOf<Int?>(null) }
    val month = monthKey?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
    LaunchedEffect(Unit) { viewModel.open() }
    LaunchedEffect(month) { viewModel.selectMonth(month) }

    val all = state.all[NuvioCTasteRange.ALL]
    val stats: NuvioCTasteStats? = if (month != null) monthData?.first else state.all[range] ?: all
    LaunchedEffect(state.builtAt) {
        all?.let { viewModel.loadRows(it, state.builtAt) }
        viewModel.loadWrapped(yearShown)
    }
    LaunchedEffect(state.builtAt, range, month) { if (state.builtAt > 0) viewModel.loadFacts(range, month) }
    LaunchedEffect(yearShown) { if (state.builtAt > 0) viewModel.loadWrapped(yearShown) }

    val accent = remember { viewModel.accent }
    val empty = all == null || all.empty
    val chartsOk = (all?.titles ?: 0) >= CHART_MIN_TITLES
    val tabs = buildList {
        add(TasteTab.OVERVIEW)
        if (!empty) add(TasteTab.CHARTS)
        if (!empty) add(TasteTab.DISCOVER)
        if (years.isNotEmpty()) add(TasteTab.WRAPPED)
    }
    val current = tabs.getOrNull(viewModel.tasteTab) ?: tabs.first()
    val tabFocus = remember { TasteTab.entries.associateWith { FocusRequester() } }
    val contentFocus = remember { FocusRequester() }
    LaunchedEffect(all != null) { if (all != null) tabFocus.getValue(current).requestFocusAfterFrames() }

    fun spin(genre: String?, decade: Int?) {
        NuvioCRoulettePreset.set(NuvioCRoulettePreset.Preset(mood = NuvioCRoulettePreset.moodFor(genre), decade = NuvioCRoulettePreset.decadeFor(decade)))
        onOpenRoulette()
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B0D))) {
        val building = all == null && com.nuvio.tv.NuvioCFeatures.TASTE_BUILD_ANIM && state.progressTotal > 0
        if (building) {
            TasteBuilding(state, accent)
            return@Box
        }
        Column(Modifier.fillMaxSize().padding(start = PAD_H, end = PAD_H, top = 20.dp, bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // header: name, tabs, range
            Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.padding(end = 18.dp)) {
                    Text("TASTE", style = MaterialTheme.typography.labelSmall, color = MUTE, letterSpacing = 1.4.sp)
                    Text(viewModel.profileName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                }
                tabs.forEachIndexed { i, tab ->
                    TastePillButton(
                        label = tab.label,
                        selected = tab == current,
                        modifier = Modifier.padding(end = 6.dp).focusRequester(tabFocus.getValue(tab)).focusProperties { down = contentFocus },
                        onFocused = { viewModel.tasteTab = i }
                    ) { viewModel.tasteTab = i }
                }
                Spacer(Modifier.weight(1f))
                when (current) {
                    TasteTab.OVERVIEW, TasteTab.CHARTS -> if (!empty) {
                        TastePillButton("All time", month == null && range == NuvioCTasteRange.ALL, Modifier.padding(start = 6.dp)) { range = NuvioCTasteRange.ALL; monthKey = null }
                        TastePillButton("This year", month == null && range == NuvioCTasteRange.YEAR, Modifier.padding(start = 6.dp)) { range = NuvioCTasteRange.YEAR; monthKey = null }
                        val months = state.months.sortedDescending()
                        val label = month?.let { "‹ " + it.month.nuvioC3() + " " + it.year } ?: "Month"
                        if (months.isNotEmpty()) TastePillButton(label, month != null, Modifier.padding(start = 6.dp)) {
                            // first press: the newest month; each press after steps one month back, then wraps
                            val i = month?.let { months.indexOf(it) } ?: -1
                            monthKey = months[(i + 1) % months.size].toString()
                        }
                    }
                    TasteTab.WRAPPED -> {
                        val y = wrapped?.year
                        val i = years.indexOf(y)
                        if (i >= 0 && i + 1 < years.size) TastePillButton("‹ ${years[i + 1]}", false, Modifier.padding(start = 6.dp)) { yearShown = years[i + 1] }
                        if (y != null) TastePillButton(y.toString(), true, Modifier.padding(start = 6.dp)) {}
                        if (i > 0) TastePillButton("${years[i - 1]} ›", false, Modifier.padding(start = 6.dp)) { yearShown = years[i - 1] }
                    }
                    TasteTab.DISCOVER -> Text("Fresh picks each visit", style = MaterialTheme.typography.bodySmall, color = MUTE)
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                val s = stats
                Crossfade(targetState = current, animationSpec = tween(150), label = "tasteTab") { tab ->
                    when {
                        all == null -> TasteCenterLine("Reading your history")
                        empty && tab == TasteTab.OVERVIEW -> TasteNewProfile(contentFocus) { spin(null, null) }
                        tab == TasteTab.OVERVIEW -> if (s == null || facts == null) TasteCenterLine("Reading your history")
                            else if (s.empty) TasteCenterLine("Nothing watched in " + (month?.let { it.month.getDisplayName(TextStyle.FULL, Locale.US) } ?: "this range"))
                            else TasteOverview(s, facts!!, accent, contentFocus)
                        tab == TasteTab.CHARTS -> if (!chartsOk) TasteCenterLine("Charts unlock at $CHART_MIN_TITLES titles · ${CHART_MIN_TITLES - (all?.titles ?: 0)} to go")
                            else if (s == null || facts == null) TasteCenterLine("Reading your history")
                            else TasteCharts(s, facts!!, accent, contentFocus, onGenre = { spin(it, null) }, onDecade = { spin(s.genres.firstOrNull()?.name, it) })
                        tab == TasteTab.DISCOVER -> TasteDiscover(all!!, rows, accent, contentFocus, onSpin = { spin(all.genres.firstOrNull()?.name, all.topDecade) }, onOpenTitle = onOpenTitle, onOpenPerson = onOpenPerson)
                        else -> wrapped?.let { TasteWrappedPage(it, accent, contentFocus) } ?: TasteCenterLine("Reading your history")
                    }
                }
            }
        }
    }
}

private enum class TasteTab(val label: String) { OVERVIEW("Overview"), CHARTS("Charts"), DISCOVER("Discover"), WRAPPED("Wrapped") }

@Composable
private fun TastePillButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onFocused: () -> Unit = {}, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { if (it.isFocused) onFocused() },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White else Color(0xFF1F2025),
            contentColor = if (selected) Color.Black else Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(NuvioTheme.focusRing.border(2.dp), shape = RoundedCornerShape(50)))
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp), maxLines = 1)
    }
}

@Composable
private fun TasteCenterLine(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = MUTE)
    }
}

@Composable
private fun TasteCard(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.background(CARD, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 12.dp), content = content)
}

/** A focus stop for pages with nothing else to focus: Down from the tabs lands here (no ring, nothing happens). */
private fun Modifier.tasteStop(focus: FocusRequester): Modifier = this.focusRequester(focus).focusable()

private fun hm(minutes: Int): String {
    val d = minutes / (60 * 24)
    val h = (minutes / 60) % 24
    val m = minutes % 60
    return when {
        d > 0 -> "${d}d ${h}h"
        minutes >= 60 -> "${minutes / 60}h" + (if (m > 0 && minutes < 600) " ${m}m" else "")
        else -> "${m}m"
    }
}

private fun change(now: Int, before: Int): String? {
    if (before <= 0 || now <= 0) return null
    val pct = ((now - before) * 100.0 / before).toInt()
    return if (pct >= 0) "↑ $pct%" else "↓ ${-pct}%"
}

// ---- Overview ---------------------------------------------------------------------------------------------

@Composable
private fun TasteOverview(s: NuvioCTasteStats, f: NuvioCTasteFacts, accent: Color, focus: FocusRequester) {
    Column(Modifier.fillMaxSize().tasteStop(focus), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().height(72.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TasteTile("Episodes", "%,d".format(f.episodes), "${f.shows} shows")
            TasteTile("Show time", hm(f.showMinutes), if (f.showMinutes + f.filmMinutes > 0) "${f.showMinutes * 100 / (f.showMinutes + f.filmMinutes)}% of your time" else null)
            TasteTile("Films", "%,d".format(f.films), if (f.rewatches > 0) "${f.rewatches} rewatches" else null)
            TasteTile("Film time", hm(f.filmMinutes), if (f.showMinutes + f.filmMinutes > 0) "${f.filmMinutes * 100 / (f.showMinutes + f.filmMinutes)}% of your time" else null)
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TasteCard(Modifier.weight(1.15f).fillMaxHeight()) {
                Text("Total watch time", style = MaterialTheme.typography.bodySmall, color = MUTE)
                val total = f.showMinutes + f.filmMinutes
                Text(
                    if (total >= 60 * 24) "${total / (60 * 24)} days, ${(total / 60) % 24} hours" else hm(total),
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1
                )
                Text("${"%,d".format(f.films)} films · ${"%,d".format(f.episodes)} episodes · ${f.shows} shows", style = MaterialTheme.typography.bodySmall, color = MUTE, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TasteSmall("Last 7 days", hm(f.last7Minutes), change(f.last7Minutes, f.prev7Minutes), Modifier.weight(1f))
                    TasteSmall("Last 30 days", hm(f.last30Minutes), change(f.last30Minutes, f.prev30Minutes), Modifier.weight(1f))
                }
            }
            TasteCard(Modifier.weight(1f).fillMaxHeight()) {
                val total = (f.filmMinutes + f.showMinutes).coerceAtLeast(1)
                val films = f.filmMinutes.toFloat() / total
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val stroke = 16.dp.toPx()
                            val inset = stroke / 2
                            val sz = Size(size.width - stroke, size.height - stroke)
                            drawArc(Color(0xFFE5484D), -90f, 360f, false, Offset(inset, inset), sz, style = Stroke(stroke))
                            drawArc(Color(0xFF6EA8FF), -90f, 360f * films, false, Offset(inset, inset), sz, style = Stroke(stroke))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${(films * 100).toInt()}%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            Text("Films", style = MaterialTheme.typography.labelSmall, color = MUTE)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Watch time split", style = MaterialTheme.typography.bodySmall, color = MUTE)
                        TasteLegendLine(Color(0xFF6EA8FF), "Films ${hm(f.filmMinutes)}")
                        TasteLegendLine(Color(0xFFE5484D), "Shows ${hm(f.showMinutes)}")
                    }
                }
            }
            TasteCard(Modifier.weight(1f).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${f.currentStreak}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("day\nstreak", style = MaterialTheme.typography.bodySmall, color = MUTE, lineHeight = 14.sp)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    val lit = f.currentStreak.coerceAtMost(7)
                    repeat(7) { i ->
                        Box(Modifier.width(14.dp).height(30.dp).background(
                            when { i < lit - 1 -> Color(0xFF9A9BA0); i == lit - 1 -> accent; else -> TRACK }, RoundedCornerShape(7.dp)))
                    }
                }
                Spacer(Modifier.weight(1f))
                Text("Best: ${f.longestStreak} days" + (f.biggestDay?.let { (d, m) -> "\nBiggest day " + d.month.nuvioC3() + " ${d.dayOfMonth}, ${d.year} · ${hm(m)}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = MUTE, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(Modifier.fillMaxWidth().height(74.dp).background(CARD, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TasteFact("Avg runtime", f.avgFilmMinutes?.let { "$it min" }, "films")
            TasteFact("Longest film", f.longestFilm?.let { "${it.second} min" }, f.longestFilm?.first)
            TasteFact("Rewatches", "%,d".format(f.rewatches), if (f.plays > 0) "${f.rewatches * 100 / f.plays}% of ${"%,d".format(f.plays)} plays" else null)
            TasteFact("First watch", f.firstWatch?.second?.let { it.month.nuvioC3() + " " + it.year }, f.firstWatch?.first)
            TasteFact("Latest watch", f.latestWatch?.second?.let { it.month.nuvioC3() + " " + it.year }, f.latestWatch?.first)
        }
    }
}

@Composable
private fun RowScope.TasteTile(label: String, value: String, sub: String?) {
    Column(Modifier.weight(1f).fillMaxHeight().background(CARD, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MUTE, maxLines = 1)
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1)
        if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = MUTE, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TasteSmall(label: String, value: String, delta: String?, modifier: Modifier) {
    Column(modifier.background(INNER, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MUTE)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
            if (delta != null) Text(" $delta", style = MaterialTheme.typography.labelSmall, color = if (delta.startsWith("↑")) Color(0xFF3ECF8E) else Color(0xFFFFB27A))
        }
    }
}

@Composable
private fun TasteLegendLine(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Color.White, maxLines = 1)
    }
}

@Composable
private fun RowScope.TasteFact(label: String, value: String?, sub: String?) {
    Column(Modifier.weight(1f)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MUTE, maxLines = 1)
        Text(value ?: "–", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
        if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = MUTE, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---- Charts -----------------------------------------------------------------------------------------------

@Composable
private fun TasteCharts(s: NuvioCTasteStats, f: NuvioCTasteFacts, accent: Color, focus: FocusRequester, onGenre: (String) -> Unit, onDecade: (Int) -> Unit) {
    Column(Modifier.fillMaxSize().tasteStop(focus), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().weight(1.05f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TasteCard(Modifier.weight(1f).fillMaxHeight()) {
                val peakHour = f.byHour.indices.maxByOrNull { f.byHour[it] }
                val peakDay = com.nuvio.tv.data.nuvioc.tastePeakWeekday(f.byWeekday)
                TasteChartTitle("When you watch", listOfNotNull(
                    peakHour?.let { when (it) { in 0..4 -> "Mostly after midnight"; in 5..11 -> "Mostly mornings"; in 12..16 -> "Mostly afternoons"; in 17..21 -> "Mostly evenings"; else -> "Mostly late at night" } },
                    peakDay?.let { it.getDisplayName(TextStyle.FULL, Locale.US) + "s most of all" }
                ).joinToString(", "))
                TasteBars(f.byHour, highlight = peakHour, labels = null, modifier = Modifier.fillMaxWidth().weight(1f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("00", "06", "12", "18", "23").forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = MUTE) }
                }
                Spacer(Modifier.height(6.dp))
                TasteBars(f.byWeekday, highlight = f.byWeekday.indices.maxByOrNull { f.byWeekday[it] }, labels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"), modifier = Modifier.fillMaxWidth().weight(0.8f))
            }
            TasteCard(Modifier.weight(1f).fillMaxHeight()) {
                val counts = s.ratingCounts.takeIf { it.size == 10 } ?: List(10) { 0 }
                val rated = counts.sum()
                if (rated == 0) {
                    // no ratings (e.g. a Nuvio Sync-only profile): what you finish instead
                    TasteChartTitle("Finished vs started", null)
                    val pct = s.finishedPercent ?: 0
                    Spacer(Modifier.weight(1f))
                    Text("$pct%", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text("of the shows you start, you finish", style = MaterialTheme.typography.bodySmall, color = MUTE)
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(8.dp).background(TRACK, RoundedCornerShape(4.dp))) {
                        Box(Modifier.fillMaxWidth(pct / 100f).height(8.dp).background(accent, RoundedCornerShape(4.dp)))
                    }
                    Spacer(Modifier.weight(1f))
                } else {
                    val avg = counts.mapIndexed { i, c -> (i + 1) * c }.sum().toDouble() / rated
                    TasteChartTitle("Your ratings", "%,d titles · avg %.1f".format(rated, avg) + (s.crowdAverage?.let { " · crowd %.1f".format(it) } ?: ""))
                    val top = counts.indices.maxByOrNull { counts[it] }
                    TasteBars(counts, highlight = top, labels = (1..10).map { it.toString() }, values = true, modifier = Modifier.fillMaxWidth().weight(1f))
                    Text(
                        "A ${(top ?: 0) + 1} is your usual verdict. Tens: ${counts[9]}.",
                        style = MaterialTheme.typography.bodySmall, color = MUTE, maxLines = 1
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TasteCard(Modifier.weight(1f).fillMaxHeight()) {
                TasteChartTitle("Genres", "Most watched")
                val top = s.genres.take(5)
                val max = top.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
                top.forEach { g -> TasteHBar(g.name, g.count.toString(), g.count.toFloat() / max) }
            }
            TasteCard(Modifier.weight(1f).fillMaxHeight()) {
                TasteChartTitle("Through the decades", null)
                val decades = s.decades.takeLast(8)
                TasteBars(decades.map { it.second }, highlight = decades.indices.maxByOrNull { decades[it].second }, labels = decades.map { "${it.first % 100}".padStart(2, '0') + "s" }, values = true, modifier = Modifier.fillMaxWidth().weight(1f))
                val first = f.firstWatch
                if (s.decades.size >= 2) Text("${s.decades.first().first}s to ${s.decades.last().first}s", style = MaterialTheme.typography.labelSmall, color = MUTE, maxLines = 1)
            }
            TasteCard(Modifier.weight(1f).fillMaxHeight()) {
                TasteChartTitle("Studios", null)
                val max = f.studios.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
                if (f.studios.isEmpty()) Text("Not enough details yet", style = MaterialTheme.typography.bodySmall, color = MUTE)
                f.studios.forEach { (name, n) -> TasteHBar(name, n.toString(), n.toFloat() / max) }
            }
        }
    }
}

@Composable
private fun TasteChartTitle(title: String, line: String?) {
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
        if (!line.isNullOrBlank()) {
            Spacer(Modifier.width(10.dp))
            Text(line, style = MaterialTheme.typography.labelSmall, color = MUTE, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
    }
}

@Composable
private fun TasteBars(counts: List<Int>, highlight: Int?, labels: List<String>?, modifier: Modifier, values: Boolean = false) {
    val max = counts.maxOrNull()?.coerceAtLeast(1) ?: 1
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(if (counts.size > 12) 3.dp else 6.dp), verticalAlignment = Alignment.Bottom) {
        counts.forEachIndexed { i, c ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                if (values && c > 0) Text("$c", style = MaterialTheme.typography.labelSmall, color = MUTE, maxLines = 1, fontSize = 9.sp)
                Box(Modifier.fillMaxWidth().weight((c.toFloat() / max).coerceAtLeast(0.02f), fill = true).background(if (i == highlight) Color.White else BAR, RoundedCornerShape(3.dp)))
                if (c < max) Spacer(Modifier.weight((1f - c.toFloat() / max).coerceAtLeast(0.0001f)))
                if (labels != null) Text(labels[i], style = MaterialTheme.typography.labelSmall, color = MUTE, maxLines = 1, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun TasteHBar(name: String, value: String, share: Float) {
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(name, style = MaterialTheme.typography.bodySmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodySmall, color = MUTE)
        }
        Box(Modifier.fillMaxWidth().padding(top = 3.dp).height(3.dp).background(TRACK, RoundedCornerShape(2.dp))) {
            Box(Modifier.fillMaxWidth(share.coerceIn(0.02f, 1f)).height(3.dp).background(Color(0xFFD9D9DC), RoundedCornerShape(2.dp)))
        }
    }
}

// ---- Discover ---------------------------------------------------------------------------------------------

@Composable
private fun TasteDiscover(
    all: NuvioCTasteStats,
    rows: List<com.nuvio.tv.data.nuvioc.NuvioCTasteRow>,
    accent: Color,
    focus: FocusRequester,
    onSpin: () -> Unit,
    onOpenTitle: (String, String) -> Unit,
    onOpenPerson: (Int, String, Boolean) -> Unit
) {
    val context = LocalContext.current
    val shown = rows.filter { it.items.isNotEmpty() }.take(2)
    val people = all.people.ifEmpty { all.morePeople }.take(6)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(shown.firstOrNull()?.title ?: "Picked from your taste", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)) {
                item(key = "spin") {
                    Card(
                        onClick = onSpin,
                        modifier = Modifier.width(176.dp).height(130.dp).focusRequester(focus),
                        shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
                        scale = CardDefaults.scale(focusedScale = 1f),
                        colors = CardDefaults.colors(containerColor = Color(0xFF231F38), focusedContainerColor = Color(0xFF2C2648)),
                        border = CardDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(12.dp)))
                    ) {
                        Column(Modifier.fillMaxSize().padding(12.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                all.spinPosters.take(3).forEach { url ->
                                    val r = remember(url) { ImageRequest.Builder(context).data(url).size(80, 120).build() }
                                    AsyncImage(r, null, contentScale = ContentScale.Crop, modifier = Modifier.width(28.dp).height(42.dp).background(INNER, RoundedCornerShape(4.dp)))
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            Text("Spin my taste", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(listOfNotNull(all.genres.getOrNull(0)?.name, all.genres.getOrNull(1)?.name, all.topDecade?.let { "${it}s" }).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall, color = MUTE, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                items(shown.firstOrNull()?.items.orEmpty(), key = { "a:" + it.id }) { item -> TastePoster(item, onOpenTitle) }
            }
        }
        shown.getOrNull(1)?.let { row ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(row.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)) {
                    items(row.items, key = { "b:" + it.id }) { item -> TastePoster(item, onOpenTitle) }
                }
            }
        }
        if (people.isNotEmpty()) Column(Modifier.fillMaxWidth().weight(1f, fill = false).background(CARD, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Faces you watch most", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                people.forEach { p ->
                    Surface(
                        onClick = { onOpenPerson(p.person.id, p.person.name, p.director) },
                        modifier = Modifier.weight(1f),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(30.dp)),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = INNER),
                        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(30.dp)))
                    ) {
                        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            val photo = p.person.photo
                            Box(Modifier.size(40.dp).background(INNER, CircleShape), contentAlignment = Alignment.Center) {
                                if (photo != null) {
                                    val r = remember(photo) { ImageRequest.Builder(context).data(photo).size(96, 96).build() }
                                    AsyncImage(r, p.person.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().background(INNER, CircleShape).then(Modifier.padding(0.dp)))
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.person.name, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (p.director) "director · ${p.count}" else "${p.count} titles", style = MaterialTheme.typography.labelSmall, color = MUTE, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TastePoster(item: com.nuvio.tv.data.nuvioc.NuvioCTasteShelfItem, onOpenTitle: (String, String) -> Unit) {
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    Card(
        onClick = { onOpenTitle(item.id, item.type) },
        modifier = Modifier.width(87.dp).height(130.dp).onFocusChanged { focused = it.isFocused },
        shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
        scale = CardDefaults.scale(focusedScale = 1f),
        border = CardDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(10.dp))),
        colors = CardDefaults.colors(containerColor = INNER)
    ) {
        if (item.poster != null) {
            val r = remember(item.poster) { ImageRequest.Builder(context).data(item.poster).size(174, 260).build() }
            AsyncImage(r, item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
            Text(item.name, style = MaterialTheme.typography.labelSmall, color = MUTE, maxLines = 4)
        }
    }
}

// ---- Wrapped ----------------------------------------------------------------------------------------------

@Composable
private fun TasteWrappedPage(w: NuvioCTasteWrapped, accent: Color, focus: FocusRequester) {
    val today = java.time.LocalDate.now()
    val soFar = w.year == today.year && today.monthValue < 12
    Column(
        Modifier.fillMaxSize().tasteStop(focus)
            .background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color(0xFF3A2A5A), Color(0xFF1D1A33), Color(0xFF121218)), radius = 1400f), RoundedCornerShape(18.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column {
                Text("NUVIO WRAPPED", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f), letterSpacing = 1.6.sp)
                Text("Your ${w.year}" + if (soFar) " so far" else "", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("%,d".format(w.hours) to "HOURS", "%,d".format(w.episodes) to "EPISODES", "%,d".format(w.films) to "FILMS", "%,d".format(w.shows) to "SHOWS").forEach { (v, l) ->
                Column(Modifier.weight(1f).background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(v, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text(l, style = MaterialTheme.typography.labelSmall, color = MUTE, letterSpacing = 1.2.sp)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            fun d(x: java.time.LocalDate?) = x?.let { it.month.nuvioC3() + " ${it.dayOfMonth}" }
            TasteWrapFact("BIGGEST DAY", d(w.biggestDay), "${w.biggestDayTitles} titles · ${hm(w.biggestDayMinutes)}")
            TasteWrapFact("LONGEST STREAK", "${w.longestStreak} days", listOfNotNull(d(w.streakFrom), d(w.streakTo)).joinToString(" – "))
            TasteWrapFact("YOU WATCH MOST", when (w.peakPart) { 0 -> "Mornings"; 1 -> "Afternoons"; 2 -> "Evenings"; 3 -> "Late at night"; else -> null },
                when (w.peakPart) { 0 -> "5 am to noon"; 1 -> "noon to 5 pm"; 2 -> "5 to 10 pm"; 3 -> "after 10 pm"; else -> null })
            TasteWrapFact("FIRST OF THE YEAR", w.firstTitle, d(w.firstDate))
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1.15f).fillMaxHeight().background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text("TOP SHOWS", style = MaterialTheme.typography.labelSmall, color = MUTE, letterSpacing = 1.2.sp)
                if (w.topShows.isEmpty()) Text("No shows this year", style = MaterialTheme.typography.bodySmall, color = MUTE)
                w.topShows.forEachIndexed { i, (name, eps, min) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", style = MaterialTheme.typography.titleSmall, color = MUTE, modifier = Modifier.width(18.dp))
                        Column {
                            Text(name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("$eps episodes · ${hm(min)}", style = MaterialTheme.typography.labelSmall, color = MUTE, maxLines = 1)
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight().background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text("TOP GENRES", style = MaterialTheme.typography.labelSmall, color = MUTE, letterSpacing = 1.2.sp)
                val max = w.topGenres.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
                w.topGenres.forEach { (g, m) -> TasteHBar(g, "${m / 60} h", m.toFloat() / max) }
            }
            Column(Modifier.weight(1f).fillMaxHeight().background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp)).padding(12.dp)) {
                Text("HOURS BY MONTH", style = MaterialTheme.typography.labelSmall, color = MUTE, letterSpacing = 1.2.sp)
                val peak = w.byMonth.indices.maxByOrNull { w.byMonth[it] }
                if (peak != null) Text(java.time.Month.of(peak + 1).getDisplayName(TextStyle.FULL, Locale.US) + " · ${w.byMonth[peak] / 60} hours", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(6.dp))
                TasteBars(w.byMonth, highlight = peak, labels = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D"), modifier = Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

@Composable
private fun RowScope.TasteWrapFact(label: String, value: String?, sub: String?) {
    Column(Modifier.weight(1f)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MUTE, letterSpacing = 1.2.sp, maxLines = 1)
        Text(value ?: "–", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!sub.isNullOrBlank()) Text(sub, style = MaterialTheme.typography.labelSmall, color = MUTE, maxLines = 1)
    }
}

// ---- edge: a new profile -----------------------------------------------------------------------------------

@Composable
private fun TasteNewProfile(focus: FocusRequester, onSpin: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Nothing watched yet", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(6.dp))
        Text("Your stats fill in as you watch on this profile.", style = MaterialTheme.typography.bodyMedium, color = MUTE)
        Spacer(Modifier.height(14.dp))
        TastePillButton("Spin something", false, Modifier.focusRequester(focus)) { onSpin() }
    }
}
