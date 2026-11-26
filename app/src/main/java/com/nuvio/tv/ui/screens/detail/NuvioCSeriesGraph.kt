package com.nuvio.tv.ui.screens.detail

// [fork] Nuvio C round 65 SERIES_GRAPH: the details Ratings row becomes a full-screen "ratings graph" layer, opened from
// a round hero button (series only), the same way the expanded synopsis opens. One layout for every show:
//  - logo (or name), a summary line, an IMDb / Mine toggle (Mine hidden when the profile has no own episode ratings);
//  - a grid of seasons (columns, 56 dp) by episodes (rows, 22 dp), the rating in every cell, the AVG row under it;
//    a watched dot, a dashed ring on the next episode due, a solid ring on focus;
//  - right: the focused episode, best / lowest, the season averages of ALL seasons as a map, the colour legend.
// Large shows scroll one season sideways / one episode down at a time; the episode labels and AVG row stay put and
// nothing changes size. The whole layer is one focus target with its own key handling (no per-cell focus nodes).

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.Video
import java.util.Locale

/** Seasons and episodes visible at once (fixed: nothing changes size on a large show). */
internal const val NUVIO_C_GRAPH_SEASONS = 7
internal const val NUVIO_C_GRAPH_EPISODES = 13
/** [fork] round 66 graph_v66: grid right edge for the full season window, so the panel never overlaps (dp). */
internal fun nuvioCGraphGridRight(seasons: Int): Int = 40 + 28 + seasons * (6 + 56)
/** [fork] round 66: the side panel's x and width on a 960 dp screen, 24 dp after the widest grid. */
internal fun nuvioCGraphPanel(): Pair<Int, Int> { val x = nuvioCGraphGridRight(NUVIO_C_GRAPH_SEASONS) + 24; return x to (960 - 40 - x) }

@Immutable
internal data class NuvioCGraphCell(val season: Int, val episode: Int, val title: String?, val year: String?, val rating: Double?)

@Immutable
internal data class NuvioCGraphSeason(val number: Int, val year: String?, val cells: List<NuvioCGraphCell>, val average: Double?)

@Immutable
internal data class NuvioCGraphModel(
    val seasons: List<NuvioCGraphSeason>,
    val episodeCount: Int,
    val average: Double?,
    val best: NuvioCGraphCell?,
    val lowest: NuvioCGraphCell?
) {
    val maxEpisodes: Int get() = seasons.maxOfOrNull { it.cells.size } ?: 0
}

private fun yearOf(released: String?): String? = released?.takeIf { it.length >= 4 }?.substring(0, 4)?.takeIf { y -> y.all(Char::isDigit) }

/** Pure: the grid for [videos] (specials left out) with [ratings]; averages are plain means of rated episodes. */
internal fun nuvioCGraphModel(videos: List<Video>, ratings: Map<Pair<Int, Int>, Double>): NuvioCGraphModel {
    val bySeason = videos.filter { (it.season ?: 0) >= 1 && it.episode != null }
        .distinctBy { it.season to it.episode }
        .groupBy { it.season!! }
        .toSortedMap()
    val seasons = bySeason.map { (s, eps) ->
        val cells = eps.sortedBy { it.episode }.map { v ->
            NuvioCGraphCell(s, v.episode!!, v.title.takeIf { it.isNotBlank() }, yearOf(v.released), ratings[s to v.episode])
        }
        val rated = cells.mapNotNull { it.rating }
        NuvioCGraphSeason(s, cells.firstNotNullOfOrNull { it.year }, cells, rated.takeIf { it.isNotEmpty() }?.average())
    }
    val all = seasons.flatMap { it.cells }
    val rated = all.filter { it.rating != null }
    return NuvioCGraphModel(
        seasons = seasons,
        episodeCount = all.size,
        average = rated.takeIf { it.isNotEmpty() }?.map { it.rating!! }?.average(),
        best = rated.maxByOrNull { it.rating!! },
        lowest = rated.minByOrNull { it.rating!! }
    )
}

/** Pure: the first visible index so that [focus] stays inside a window of [size] over [count] items. */
internal fun nuvioCGraphWindow(first: Int, focus: Int, size: Int, count: Int): Int {
    val maxFirst = (count - size).coerceAtLeast(0)
    return when {
        focus < first -> focus
        focus >= first + size -> focus - size + 1
        else -> first
    }.coerceIn(0, maxFirst)
}

internal fun nuvioCGraphColor(value: Double): Color = when {
    value >= 9.7 -> Color(0xFF1DA1F3)
    value >= 9.0 -> Color(0xFF186A3B)
    value >= 8.0 -> Color(0xFF28B463)
    value >= 7.5 -> Color(0xFFF4D03F)
    value >= 7.0 -> Color(0xFFF39C12)
    value >= 6.0 -> Color(0xFFE74C3C)
    else -> Color(0xFF633974)
}

internal fun nuvioCGraphTextColor(value: Double): Color = if (value >= 7.0 && value < 8.0) Color(0xFF1D1D1F) else Color.White

private fun fmt(v: Double) = String.format(Locale.US, "%.1f", v)

private val GraphBg = Color(0xFF0B0B0E)
private val Dim = Color(0xFF7D7D86)
private val Mid = Color(0xFF9A9AA2)
private val Light = Color(0xFFC9C9CF)

@Composable
internal fun NuvioCSeriesGraphOverlay(
    name: String,
    logo: String?,
    videos: List<Video>,
    imdb: Map<Pair<Int, Int>, Double>,
    loading: Boolean,
    mine: Map<Pair<Int, Int>, Int>,
    isWatched: (Pair<Int, Int>) -> Boolean,
    next: Pair<Int, Int>?,
    onDismiss: () -> Unit
) {
    var showMine by remember { mutableStateOf(false) }
    val mineOn = showMine && mine.isNotEmpty()
    val ratings = remember(imdb, mine, mineOn) { if (mineOn) mine.mapValues { it.value.toDouble() } else imdb }
    val model = remember(videos, ratings) { nuvioCGraphModel(videos, ratings) }
    val seasons = model.seasons
    var si by remember { mutableIntStateOf(-1) }
    var ei by remember { mutableIntStateOf(0) }
    var firstSeason by remember { mutableIntStateOf(0) }
    var firstEpisode by remember { mutableIntStateOf(0) }
    var onToggle by remember { mutableStateOf(false) }
    // Open on the next episode due (or the first one), once the seasons are known.
    LaunchedEffect(seasons.size) {
        if (si >= 0 || seasons.isEmpty()) return@LaunchedEffect
        val start = next?.let { n -> seasons.indexOfFirst { it.number == n.first }.takeIf { it >= 0 } } ?: 0
        si = start
        ei = next?.takeIf { seasons[start].number == it.first }?.let { n -> seasons[start].cells.indexOfFirst { it.episode == n.second } }?.coerceAtLeast(0) ?: 0
        firstSeason = nuvioCGraphWindow(0, si, NUVIO_C_GRAPH_SEASONS, seasons.size)
        firstEpisode = nuvioCGraphWindow(0, ei, NUVIO_C_GRAPH_EPISODES, model.maxEpisodes)
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { repeat(2) { androidx.compose.runtime.withFrameNanos { } }; runCatching { focusRequester.requestFocus() } }
    val focused = seasons.getOrNull(si)?.cells?.getOrNull(ei)

    fun move(ds: Int, de: Int) {
        if (seasons.isEmpty()) return
        if (onToggle) {
            when {
                de > 0 -> onToggle = false
                ds != 0 -> showMine = !showMine
            }
            return
        }
        if (de < 0 && ei == 0) { if (mine.isNotEmpty()) onToggle = true; return }
        val newSi = (si + ds).coerceIn(0, seasons.lastIndex)
        val size = seasons[newSi].cells.size
        val newEi = (ei + de).coerceIn(0, (size - 1).coerceAtLeast(0))
        si = newSi; ei = newEi
        firstSeason = nuvioCGraphWindow(firstSeason, si, NUVIO_C_GRAPH_SEASONS, seasons.size)
        firstEpisode = nuvioCGraphWindow(firstEpisode, ei, NUVIO_C_GRAPH_EPISODES, model.maxEpisodes)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(GraphBg)
                .background(Brush.linearGradient(listOf(Color(0xFF1E2B2A), GraphBg), end = Offset(900f, 900f)))
                .background(Brush.horizontalGradient(0.35f to GraphBg, 1f to GraphBg.copy(alpha = 0.75f)))
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (e.key) {
                        Key.DirectionLeft -> { move(-1, 0); true }
                        Key.DirectionRight -> { move(1, 0); true }
                        Key.DirectionUp -> { move(0, -1); true }
                        Key.DirectionDown -> { move(0, 1); true }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            if (onToggle) showMine = !showMine // OK on an episode does nothing
                            true
                        }
                        else -> false
                    }
                }
        ) {
            // ---- title block ----
            Column(Modifier.padding(start = 40.dp, top = 26.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.height(40.dp).widthIn(max = 380.dp), contentAlignment = Alignment.CenterStart) {
                    var logoFailed by remember(logo) { mutableStateOf(false) }
                    if (!logo.isNullOrBlank() && !logoFailed) {
                        AsyncImage(model = logo, contentDescription = name, contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
                            onError = { logoFailed = true }, modifier = Modifier.fillMaxHeight())
                    } else {
                        Text(name, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF4F1EA), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.nuvio_c_graph_summary, seasons.size, model.episodeCount, model.average?.let(::fmt) ?: "—"),
                        fontSize = 12.sp, color = Mid, maxLines = 1
                    )
                    if (mine.isNotEmpty()) {
                        Row(Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f))
                            .then(if (onToggle) Modifier.border(2.dp, Color.White, RoundedCornerShape(10.dp)) else Modifier)) {
                            listOf(false to "IMDb", true to stringResource(R.string.nuvio_c_graph_mine)).forEach { (m, label) ->
                                val on = m == mineOn
                                Text(label, fontSize = 11.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (on) Color.White else Mid,
                                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (on) Color.White.copy(alpha = 0.18f) else Color.Transparent)
                                        .padding(horizontal = 9.dp, vertical = 2.dp))
                            }
                        }
                    }
                }
            }

            // ---- grid ----
            val v66 = com.nuvio.tv.NuvioCFeatures.GRAPH_V66
            val noGrid = seasons.isEmpty() || (loading && imdb.isEmpty() && !mineOn) || (v66 && !loading && model.average == null)
            if (noGrid && v66) { // [fork] round 66: one centred line, nothing else
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(if (loading && seasons.isNotEmpty()) R.string.nuvio_c_graph_loading else R.string.ratings_unavailable), fontSize = 14.sp, color = Mid)
                }
            } else if (noGrid) {
                Text(
                    stringResource(if (seasons.isEmpty() || !loading) R.string.ratings_unavailable else R.string.nuvio_c_graph_loading),
                    fontSize = 13.sp, color = Mid, modifier = Modifier.padding(start = 40.dp, top = 120.dp)
                )
            } else {
                val visSeasons = seasons.drop(firstSeason).take(NUVIO_C_GRAPH_SEASONS)
                val epRows = (firstEpisode until (firstEpisode + NUVIO_C_GRAPH_EPISODES).coerceAtMost(model.maxEpisodes)).toList()
                Row(Modifier.offset(x = 40.dp, y = 104.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Column(Modifier.padding(top = if (v66) 29.dp else 22.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        epRows.forEach { e ->
                            Box(Modifier.height(22.dp).width(28.dp), contentAlignment = Alignment.CenterStart) { Text("E${e + 1}", fontSize = 10.sp, color = Dim, maxLines = 1) }
                        }
                        Box(Modifier.padding(top = 6.dp).height(26.dp).width(28.dp), contentAlignment = Alignment.CenterStart) {
                            Text("AVG", fontSize = 10.sp, color = Light, fontWeight = FontWeight.Bold)
                        }
                    }
                    visSeasons.forEachIndexed { vi, s ->
                        val sIndex = firstSeason + vi
                        Column(Modifier.width(56.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Column(Modifier.height(if (v66) 26.dp else 19.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = if (v66) Arrangement.spacedBy(2.dp) else Arrangement.Top) { // [fork] round 66: room for the year
                                Text("S${s.number}", fontSize = 11.sp, lineHeight = if (v66) 13.sp else 11.sp, color = Light, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text(s.year.orEmpty(), fontSize = 8.sp, lineHeight = if (v66) 10.sp else 8.sp, color = Dim, maxLines = 1)
                            }
                            epRows.forEach { e ->
                                val cell = s.cells.getOrNull(e)
                                GraphCell(
                                    cell = cell,
                                    mine = mineOn,
                                    focused = !onToggle && sIndex == si && e == ei,
                                    next = cell != null && next == (cell.season to cell.episode),
                                    watched = cell != null && isWatched(cell.season to cell.episode)
                                )
                            }
                            Box(
                                Modifier.padding(top = 6.dp).height(26.dp).fillMaxWidth().clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF1D1D24)).border(1.dp, Color(0xFF34343E), RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center
                            ) { Text(s.average?.let(::fmt) ?: "—", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFEDEDED)) }
                        }
                    }
                }
                if (!v66 && (seasons.size > NUVIO_C_GRAPH_SEASONS || model.maxEpisodes > NUVIO_C_GRAPH_EPISODES)) {
                    val lastS = (firstSeason + NUVIO_C_GRAPH_SEASONS).coerceAtMost(seasons.size)
                    val lastE = (firstEpisode + NUVIO_C_GRAPH_EPISODES).coerceAtMost(model.maxEpisodes)
                    Text(
                        stringResource(
                            R.string.nuvio_c_graph_window,
                            seasons[firstSeason].number, seasons[lastS - 1].number, seasons.size, firstEpisode + 1, lastE, model.maxEpisodes
                        ),
                        fontSize = 10.sp, color = Mid, maxLines = 1, modifier = Modifier.offset(x = 40.dp, y = 500.dp)
                    )
                }
            }

            // ---- right panel ----
            val (panelX, panelW) = if (com.nuvio.tv.NuvioCFeatures.GRAPH_V66) nuvioCGraphPanel() else 480 to 440 // [fork] round 66: after the grid's real edge
            val panelHidden = com.nuvio.tv.NuvioCFeatures.GRAPH_V66 && (seasons.isEmpty() || (loading && imdb.isEmpty() && !mineOn) || (!loading && model.average == null))
            if (!panelHidden) Column(Modifier.offset(x = panelX.dp, y = 104.dp).width(panelW.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                val panel = RoundedCornerShape(12.dp)
                Column(
                    Modifier.fillMaxWidth().clip(panel).background(Color.White.copy(alpha = 0.06f)).border(1.dp, Color.White.copy(alpha = 0.10f), panel)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val c = focused
                    Text(
                        if (c == null) " " else listOfNotNull("S${c.season}", "E${c.episode}", c.year?.let { stringResource(R.string.nuvio_c_graph_aired, it) }).joinToString(" · ").uppercase(),
                        fontSize = 11.sp, letterSpacing = 1.5.sp, color = Mid, fontWeight = FontWeight.SemiBold, maxLines = 1
                    )
                    Text(c?.title ?: " ", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFEDEDED), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        val r = c?.rating
                        Box(Modifier.clip(RoundedCornerShape(5.dp)).background(r?.let(::nuvioCGraphColor) ?: Color(0xFF1D1D24)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                            Text(r?.let(::fmt) ?: "—", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = r?.let(::nuvioCGraphTextColor) ?: Mid)
                        }
                        Text(c?.let { compareLine(it, seasons) } ?: "", fontSize = 12.sp, color = Mid, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (com.nuvio.tv.NuvioCFeatures.GRAPH_V66) Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) { // [fork] round 66: no separators, no marks legend
                        model.best?.let { Text(stringResource(R.string.nuvio_c_graph_best, "S${it.season} E${it.episode} · ${fmt(it.rating!!)}"), fontSize = 11.sp, color = Light, maxLines = 1) }
                        model.lowest?.let { Text(stringResource(R.string.nuvio_c_graph_lowest, "S${it.season} E${it.episode} · ${fmt(it.rating!!)}"), fontSize = 11.sp, color = Light, maxLines = 1) }
                    } else Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val best = model.best?.let { stringResource(R.string.nuvio_c_graph_best, "S${it.season} E${it.episode} · ${fmt(it.rating!!)}") } ?: ""
                        val low = model.lowest?.let { stringResource(R.string.nuvio_c_graph_lowest, "S${it.season} E${it.episode} · ${fmt(it.rating!!)}") } ?: ""
                        Text(best, fontSize = 11.sp, color = Light, maxLines = 1)
                        Text("|", fontSize = 11.sp, color = Color(0xFF555555))
                        Text(low, fontSize = 11.sp, color = Light, maxLines = 1)
                        Text("|", fontSize = 11.sp, color = Color(0xFF555555))
                        Text(stringResource(R.string.nuvio_c_graph_marks), fontSize = 11.sp, color = Light, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Column(
                    Modifier.fillMaxWidth().clip(panel).background(Color.White.copy(alpha = 0.04f)).border(1.dp, Color.White.copy(alpha = 0.08f), panel)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        if (seasons.size > NUVIO_C_GRAPH_SEASONS && !com.nuvio.tv.NuvioCFeatures.GRAPH_AVG_PLAIN) stringResource(R.string.nuvio_c_graph_averages_all, seasons.size)
                        else stringResource(R.string.nuvio_c_graph_averages),
                        fontSize = 11.sp, letterSpacing = 1.5.sp, color = Mid, fontWeight = FontWeight.SemiBold, maxLines = 1
                    )
                    SeasonBars(seasons, firstSeason, focusedSeason = si)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("9.7+" to 9.8, "9.0" to 9.0, "8.0" to 8.0, "7.5" to 7.5, "7.0" to 7.0, "6.0" to 6.0, "<6" to 5.0).forEach { (t, v) ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(nuvioCGraphColor(v)))
                            Text(t, fontSize = 10.sp, color = Mid)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun compareLine(c: NuvioCGraphCell, seasons: List<NuvioCGraphSeason>): String {
    val r = c.rating ?: return ""
    val s = seasons.firstOrNull { it.number == c.season } ?: return ""
    val avg = s.average ?: return ""
    val rated = s.cells.mapNotNull { it.rating }
    val diff = r - avg
    val rel = when {
        kotlin.math.abs(diff) < 0.05 -> stringResource(R.string.nuvio_c_graph_on_par)
        diff > 0 -> stringResource(R.string.nuvio_c_graph_above, fmt(diff))
        else -> stringResource(R.string.nuvio_c_graph_below, fmt(-diff))
    }
    return when {
        rated.size > 1 && r >= rated.max() -> stringResource(R.string.nuvio_c_graph_best_of, c.season) + " · " + rel
        rated.size > 1 && r <= rated.min() -> stringResource(R.string.nuvio_c_graph_lowest_of, c.season) + " · " + rel
        else -> rel
    }
}

@Composable
private fun GraphCell(cell: NuvioCGraphCell?, mine: Boolean, focused: Boolean, next: Boolean, watched: Boolean) {
    val shape = RoundedCornerShape(4.dp)
    if (cell == null) { Spacer(Modifier.height(22.dp).fillMaxWidth()); return }
    val r = cell.rating
    Box(
        Modifier
            .height(22.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(r?.let(::nuvioCGraphColor) ?: Color(0xFF1D1D24))
            .drawWithContent {
                drawContent()
                if (focused || next) {
                    val w = 2.dp.toPx()
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(w / 2, w / 2),
                        size = Size(size.width - w, size.height - w),
                        cornerRadius = CornerRadius(4.dp.toPx()),
                        style = Stroke(width = w, pathEffect = if (focused) null else PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
                    )
                }
                if (watched) drawCircle(Color.White.copy(alpha = 0.9f), radius = 2.dp.toPx(), center = Offset(size.width - 5.dp.toPx(), 5.dp.toPx()))
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            r?.let { if (mine) it.toInt().toString() else fmt(it) } ?: "—",
            fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = r?.let(::nuvioCGraphTextColor) ?: Dim, maxLines = 1
        )
    }
}

@Composable
private fun SeasonBars(seasons: List<NuvioCGraphSeason>, firstVisible: Int, focusedSeason: Int = -1) {
    val v66 = com.nuvio.tv.NuvioCFeatures.GRAPH_V66
    val avgs = seasons.mapNotNull { it.average }
    val lo = ((avgs.minOrNull() ?: 5.0) - 0.5).coerceAtMost(9.0)
    val few = seasons.size <= 10
    val tickEvery = if (few) 1 else ((seasons.size + 9) / 10)
    Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(if (few) 14.dp else 2.dp), verticalAlignment = Alignment.Bottom) {
        seasons.forEachIndexed { i, s ->
            val onScreen = if (v66) i == focusedSeason else i in firstVisible until firstVisible + NUVIO_C_GRAPH_SEASONS // [fork] round 66: only the focused season
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (few) Text(s.average?.let(::fmt) ?: "—", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEDEDED), maxLines = 1)
                val frac = s.average?.let { ((it - lo) / (10.0 - lo)).toFloat().coerceIn(0.05f, 1f) } ?: 0.03f
                Box(
                    Modifier.fillMaxWidth().height((if (few) 72 else 88).dp * frac)
                        .clip(RoundedCornerShape(topStart = if (few) 4.dp else 2.dp, topEnd = if (few) 4.dp else 2.dp))
                        .background((s.average?.let(::nuvioCGraphColor) ?: Color(0xFF34343E)).copy(alpha = if (onScreen) 1f else 0.35f))
                )
                if (!v66 || few) Text(
                    if (few) "S${s.number}" else if (i % tickEvery == 0) "${s.number}" else "",
                    fontSize = if (few) 10.sp else 7.sp, color = Color(0xFF8A8A92), maxLines = 1, textAlign = TextAlign.Center,
                    modifier = Modifier.heightIn(min = if (few) 12.dp else 9.dp)
                )
            }
        }
    }
    if (v66 && !few) { // [fork] round 66: tick labels in their own row, centred under their bar, never cut to one bar's width
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().height(12.dp)) {
            val n = seasons.size
            val slot = (maxWidth + 2.dp) / n
            nuvioCGraphTicks(n).forEach { i ->
                val centre = slot * i + (slot - 2.dp) / 2
                Text(
                    "${seasons[i].number}", fontSize = 9.sp, color = Color(0xFF8A8A92), maxLines = 1, softWrap = false, textAlign = TextAlign.Center,
                    modifier = Modifier.width(28.dp).offset(x = (centre - 14.dp).coerceIn(0.dp, maxWidth - 28.dp))
                )
            }
        }
    }
}

/** [fork] round 66: which season bars get a number under them on a long show (every Nth, about 8 labels, always the last). */
internal fun nuvioCGraphTicks(count: Int): List<Int> {
    if (count <= 0) return emptyList()
    val every = ((count + 7) / 8).coerceAtLeast(1)
    val ticks = (0 until count step every).toMutableList()
    if (ticks.last() != count - 1) { if (count - 1 - ticks.last() < every / 2 + 1 && ticks.size > 1) ticks.removeAt(ticks.lastIndex); ticks += count - 1 }
    return ticks
}
