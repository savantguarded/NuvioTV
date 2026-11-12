package com.nuvio.tv.ui.screens.taste

// [fork] Nuvio C round 40 `taste_v2`: the reworked Taste page.
//
// Top to bottom: "Taste" with the profile name and the range pills (All time, This year, 90 days, Month);
// the Taste DNA ring with its one-line summary beside the Spin my taste card (first focus); live tiles; six spotlight
// cards (redrawn each visit, each one opens what it describes); Your people; this visit's poster rows; Recaps.
// A month view swaps the live tiles for that month's numbers. The first build shows the genres forming from real
// lookup progress instead of a bare line. Charts are static native drawings; the only motion is the tile count-up
// (once, 200 ms), the first-build screen and marquees on focused, truncated text.
//
// Focus: Spin first; Down = live tiles are skipped (not focusable) so the spotlight's first card; Up from the hero =
// the pills. Every row restores the item you left and keeps its focused item at the row's start (no bounce), and a
// held Left on a row's first item stops there (a fresh press opens the sidebar).

import android.view.KeyEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nuvio.tv.data.nuvioc.NuvioCTasteAction
import com.nuvio.tv.data.nuvioc.NuvioCTasteInsight
import com.nuvio.tv.data.nuvioc.NuvioCTasteLive
import com.nuvio.tv.data.nuvioc.NuvioCTastePersonStat
import com.nuvio.tv.data.nuvioc.NuvioCTasteRange
import com.nuvio.tv.data.nuvioc.NuvioCTasteRecap
import com.nuvio.tv.data.nuvioc.NuvioCTasteRow
import com.nuvio.tv.data.nuvioc.NuvioCTasteShelfItem
import com.nuvio.tv.data.nuvioc.NuvioCTasteState
import com.nuvio.tv.data.nuvioc.NuvioCTasteStats
import com.nuvio.tv.data.nuvioc.NuvioCTasteVisual
import com.nuvio.tv.ui.components.FocusMarqueeText
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import com.nuvio.tv.ui.screens.shuffle.NuvioCRoulettePreset
import com.nuvio.tv.ui.screens.shuffle.moodLabel
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.util.nuvioCListGate
import java.text.NumberFormat
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val EDGE = 48.dp
private val NF: NumberFormat = NumberFormat.getIntegerInstance(Locale.US)

/** Ring and legend colours, a little muted so they sit on the dark page. */
internal val TASTE_RING = listOf(
    Color(0xFFF0607E), Color(0xFFF39A4A), Color(0xFFF2C95C), Color(0xFF4FC4BA), Color(0xFF5AA3F0), Color(0xFF9B8CF5)
)
private val TASTE_OTHERS = Color(0xFF6C6C76)

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun NuvioCTastePage(
    viewModel: NuvioCTasteViewModel,
    onOpenTitle: (id: String, type: String) -> Unit,
    onOpenPerson: (id: Int, name: String, crew: Boolean) -> Unit,
    onOpenRoulette: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val live by viewModel.live.collectAsState()
    val rows by viewModel.rows.collectAsState()
    val monthData by viewModel.monthData.collectAsState()
    val review by viewModel.review.collectAsState() // [fork] round 41 taste_review
    val panel by viewModel.panel.collectAsState() // [fork] round 41 taste_card_titles
    var range by rememberSaveable { mutableStateOf(NuvioCTasteRange.ALL) }
    var monthKey by rememberSaveable { mutableStateOf<String?>(null) }
    val month = monthKey?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
    LaunchedEffect(Unit) { viewModel.open() }
    LaunchedEffect(month) { viewModel.selectMonth(month) }

    val all = state.all[NuvioCTasteRange.ALL]
    val stats: NuvioCTasteStats? = if (month != null) monthData?.first ?: all else state.all[range] ?: all
    val cards: List<NuvioCTasteInsight> = remember(state.builtAt, range, month, monthData) {
        if (month != null) viewModel.spotlight(monthData?.second.orEmpty(), "m$month")
        else viewModel.spotlight(state.insights[range] ?: state.insights[NuvioCTasteRange.ALL].orEmpty(), range.name)
    }
    LaunchedEffect(state.builtAt) {
        all?.let { viewModel.loadRows(it, state.builtAt) }
        if (all != null) viewModel.loadReview(state.builtAt) // [fork] round 41 taste_review
    }
    val accent = remember { viewModel.accent }

    val spinFocus = remember { FocusRequester() }
    val pillFocus = remember { FocusRequester() }
    val cardsFocus = remember { FocusRequester() }
    // [fork] round 41: the review banner, the Month pill and the titles panel list
    val reviewFocus = remember { FocusRequester() }
    val monthPillFocus = remember { FocusRequester() }
    val panelListFocus = remember { FocusRequester() }
    // Each section has a requester; coming back (Back from a title or a person) lands on the section left, and a row
    // hands focus to the item last focused in it.
    val sections = remember { HashMap<String, FocusRequester>() }
    fun section(key: String) = sections.getOrPut(key) { FocusRequester() }
    sections["review"] = reviewFocus
    sections["panel"] = panelListFocus
    LaunchedEffect(stats != null) {
        if (stats == null) return@LaunchedEffect
        val target = viewModel.lastArea?.let { sections[it] ?: if (it == "spin") spinFocus else null } ?: spinFocus
        if (!target.requestFocusAfterFrames()) spinFocus.requestFocusAfterFrames()
    }

    fun spin(genre: String?, decade: Int?) {
        NuvioCRoulettePreset.set(NuvioCRoulettePreset.Preset(mood = NuvioCRoulettePreset.moodFor(genre), decade = NuvioCRoulettePreset.decadeFor(decade)))
        onOpenRoulette()
    }
    fun act(a: NuvioCTasteAction?) {
        when (a) {
            is NuvioCTasteAction.Spin -> spin(a.genre, a.decade)
            is NuvioCTasteAction.Person -> onOpenPerson(a.id, a.name, a.director)
            is NuvioCTasteAction.Title -> onOpenTitle(a.id, a.type)
            null -> Unit
        }
    }

    // ---- [fork] round 41 taste_back: Back from a month view returns to the default view, focus on what opened it
    var monthReturn by remember { mutableStateOf(false) }
    fun openMonth(m: YearMonth?, from: String) {
        if (m != null) viewModel.monthFrom = from
        monthKey = m?.toString()
    }
    LaunchedEffect(month) {
        if (!com.nuvio.tv.NuvioCFeatures.TASTE_BACK) return@LaunchedEffect
        if (month != null) {
            // the banner and Recaps aren't in a month view: land on Spin
            if (viewModel.monthFrom == "review" || viewModel.monthFrom == "recaps") spinFocus.requestFocusAfterFrames()
        } else if (monthReturn) {
            monthReturn = false
            val target = when (viewModel.monthFrom) {
                "review" -> reviewFocus
                "recaps" -> section("recaps")
                "monthPill" -> monthPillFocus
                else -> spinFocus
            }
            if (!target.requestFocusAfterFrames()) spinFocus.requestFocusAfterFrames()
        }
    }
    androidx.activity.compose.BackHandler(enabled = com.nuvio.tv.NuvioCFeatures.TASTE_BACK && month != null && panel == null) {
        monthReturn = true
        range = NuvioCTasteRange.ALL
        monthKey = null
    }
    // ---- [fork] round 41 taste_card_titles: the panel; Back closes it and focus returns to its card
    var panelReturn by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = panel != null) {
        panelReturn = true
        viewModel.closePanel()
    }
    LaunchedEffect(panel == null) {
        if (panel != null) { viewModel.lastArea = "panel"; return@LaunchedEffect }
        if (!panelReturn) return@LaunchedEffect
        panelReturn = false
        val from = viewModel.panelFrom
        viewModel.lastArea = from
        val target = from?.let { sections[it] } ?: cardsFocus
        if (!target.requestFocusAfterFrames()) spinFocus.requestFocusAfterFrames()
    }
    fun scopeLabel(): String = month?.let { it.month.getDisplayName(TextStyle.FULL, Locale.US) + " " + it.year } ?: NuvioCTasteCopy.rangeLabel(range)
    fun pressCard(card: NuvioCTasteInsight, from: String) {
        if (com.nuvio.tv.NuvioCFeatures.TASTE_CARD_TITLES && card.keys.isNotEmpty()) viewModel.openPanel(card, scopeLabel(), from) else act(card.action)
    }

    Box(Modifier.fillMaxSize().background(NuvioTheme.colors.Background)) {
        if (com.nuvio.tv.NuvioCFeatures.TASTE_POLISH) TasteRotatingBackdrop(viewModel.backdropFor(all?.recentBackdrops.orEmpty()))
        else TastePageBackdrop(all?.backdrops.orEmpty(), accent)
        val building = stats == null && com.nuvio.tv.NuvioCFeatures.TASTE_BUILD_ANIM && state.progressTotal > 0
        if (building) {
            TasteBuilding(state, accent)
            return@Box
        }
        val smooth = com.nuvio.tv.NuvioCFeatures.TASTE_SMOOTH
        val panelOpen = panel != null
        androidx.compose.runtime.CompositionLocalProvider(
            LocalBringIntoViewSpec provides (if (smooth) rememberTasteVerticalPivot() else LocalBringIntoViewSpec.current)
        ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize()
                .then(if (smooth) Modifier.nuvioCListGate() else Modifier)
                // [fork] round 41: while the titles panel is open, focus can't wander back into the page
                .focusProperties { onEnter = { if (panelOpen) cancelFocusChange() } }
                .focusGroup(),
            state = rememberLazyListState(),
            contentPadding = PaddingValues(top = 28.dp, bottom = 56.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp)
        ) {
            item(key = "header") {
                TasteTopBar(
                    name = viewModel.profileName,
                    since = all?.let { viewModel.sinceLabel(state) },
                    range = range,
                    month = month,
                    months = state.months,
                    ranges = viewModel.rangesFor(all),
                    onRange = { range = it; monthKey = null },
                    onMonth = { openMonth(it, "monthPill") },
                    monthPillFocus = monthPillFocus,
                    onRefresh = { viewModel.open(force = true) },
                    pillFocus = pillFocus,
                    downTo = spinFocus
                )
            }
            val s = stats
            if (s == null) {
                item(key = "wait") {
                    Text("Reading your history", style = MaterialTheme.typography.titleMedium, color = NuvioTheme.colors.TextSecondary, modifier = Modifier.padding(horizontal = EDGE))
                }
                return@LazyColumn
            }
            val light = !s.showNumbers
            val showReview = com.nuvio.tv.NuvioCFeatures.TASTE_REVIEW && month == null && !light && review != null
            item(key = "hero") {
                TasteHero(
                    stats = s,
                    summary = if (month != null) NuvioCTasteCopy.monthSummary(s, month) else NuvioCTasteCopy.pageSummary(s),
                    light = light,
                    accent = accent,
                    spinFocus = spinFocus,
                    onSpinFocused = { viewModel.lastArea = "spin" },
                    upTo = pillFocus,
                    downTo = if (showReview) reviewFocus else cardsFocus.takeIf { cards.isNotEmpty() },
                    spinPosters = s.spinPosters,
                    onSpin = { spin(s.genres.firstOrNull()?.name, s.topDecade) }
                )
            }
            item(key = "tiles") {
                if (month != null) TasteMonthTiles(s) else TasteLiveTiles(live, viewModel.countUp, onCounted = { if (smooth) viewModel.markCounted() })
            }
            val r = review
            if (showReview && r != null) item(key = "review") {
                TasteReviewBanner(
                    r, accent,
                    Modifier.focusRequester(reviewFocus)
                        .onFocusChanged { if (it.isFocused) viewModel.lastArea = "review" }
                        .nuvioCTasteVertical(up = spinFocus, down = cardsFocus.takeIf { cards.isNotEmpty() })
                ) { openMonth(r.month, "review") }
            }
            if (cards.isNotEmpty() && !light) {
                if (smooth) {
                    // [fork] round 41 taste_smooth: the title and each line of cards are their own list items
                    item(key = "spotlight:title") { TasteSpotlightTitle() }
                    cards.chunked(3).forEachIndexed { line, row ->
                        item(key = "spotlight:$line") {
                            TasteSpotlightLine(row, line, viewModel.changedIds, accent, cardsFocus, ::section, { viewModel.lastArea = it }, onCard = ::pressCard)
                        }
                    }
                } else item(key = "spotlight") {
                    TasteSpotlight(cards, viewModel.changedIds, accent, cardsFocus, ::section, { viewModel.lastArea = it }, onCard = { c, from -> pressCard(c, from) })
                }
            }
            if (month == null && !light && s.showPeople) item(key = "people") {
                TasteRowFrame("Your people") { hold ->
                    TastePeopleRow(s.people, viewModel.rowMemory("people"), hold, section("people"), { viewModel.lastArea = "people" }, onOpenPerson)
                }
            }
            if (month == null && range == NuvioCTasteRange.ALL) {
                rows.forEach { row ->
                    item(key = "row:" + row.id) {
                        TasteRowFrame(row.title) { hold ->
                            TastePosterRow(row.items, viewModel.rowMemory(row.id), hold, section("row:" + row.id), { viewModel.lastArea = "row:" + row.id }, onOpenTitle)
                        }
                    }
                }
            }
            if (com.nuvio.tv.NuvioCFeatures.TASTE_MONTH && state.recaps.isNotEmpty() && month == null) item(key = "recaps") {
                TasteRowFrame("Recaps") { hold ->
                    TasteRecapRow(state.recaps, viewModel.rowMemory("recaps"), hold, section("recaps"), { viewModel.lastArea = "recaps" }, accent) {
                        viewModel.lastArea = "spin"
                        openMonth(it.month, "recaps")
                    }
                }
            }
        }
        }
        // [fork] round 41 taste_card_titles
        if (com.nuvio.tv.NuvioCFeatures.TASTE_CARD_TITLES) TasteTitlesPanel(
            panel = panel,
            listFocus = panelListFocus,
            startIndex = { viewModel.panelIndex },
            onIndex = { viewModel.panelIndex = it },
            onAction = { act(it) },
            onSort = viewModel::sortPanel,
            onOpenTitle = onOpenTitle
        )
    }
}

// ---- top bar ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TasteTopBar(
    name: String,
    since: String?,
    range: NuvioCTasteRange,
    month: YearMonth?,
    months: List<YearMonth>,
    ranges: List<NuvioCTasteRange>,
    onRange: (NuvioCTasteRange) -> Unit,
    onMonth: (YearMonth?) -> Unit,
    onRefresh: () -> Unit,
    pillFocus: FocusRequester,
    downTo: FocusRequester,
    monthPillFocus: FocusRequester? = null
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = EDGE), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull("Taste", since).joinToString(" · ").uppercase(Locale.US),
                style = MaterialTheme.typography.labelMedium,
                color = NuvioTheme.colors.TextTertiary,
                maxLines = 1
            )
            Text(name.ifBlank { "Your taste" }, style = MaterialTheme.typography.headlineMedium, color = NuvioTheme.colors.TextPrimary,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.focusGroup().nuvioCDownTo(downTo)
        ) {
            ranges.forEachIndexed { i, r ->
                TasteRangePill(NuvioCTasteCopy.rangeLabel(r), selected = month == null && r == range,
                    modifier = if (i == 0) Modifier.focusRequester(pillFocus).nuvioCHeldLeftStop() else Modifier) { onRange(r) }
            }
            if (com.nuvio.tv.NuvioCFeatures.TASTE_MONTH && months.isNotEmpty()) {
                val label = month?.let { "‹ " + it.month.getDisplayName(TextStyle.FULL, Locale.US) + " " + it.year + " ›" } ?: "Month"
                TasteRangePill(
                    label,
                    selected = month != null,
                    modifier = (monthPillFocus?.let { Modifier.focusRequester(it) } ?: Modifier).onPreviewKeyEvent { e ->
                        val n = e.nativeKeyEvent
                        if (month == null || n.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                        val i = months.indexOf(month)
                        when (n.keyCode) {
                            KeyEvent.KEYCODE_DPAD_LEFT -> if (i > 0) { onMonth(months[i - 1]); true } else false
                            KeyEvent.KEYCODE_DPAD_RIGHT -> { if (i in 0 until months.lastIndex) onMonth(months[i + 1]); true }
                            else -> false
                        }
                    }
                ) { onMonth(if (month == null) months.last() else null) }
            }
            Surface(
                onClick = onRefresh,
                modifier = Modifier.size(32.dp),
                shape = ClickableSurfaceDefaults.shape(CircleShape),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.08f),
                    contentColor = NuvioTheme.colors.TextSecondary,
                    focusedContainerColor = Color.White,
                    focusedContentColor = Color.Black
                )
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TasteRangePill(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(32.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f),
            contentColor = if (selected) NuvioTheme.colors.TextPrimary else NuvioTheme.colors.TextSecondary,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black
        )
    ) {
        Box(Modifier.fillMaxHeight().padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

// ---- hero ------------------------------------------------------------------------------------------------

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TasteHero(
    stats: NuvioCTasteStats,
    summary: String,
    light: Boolean,
    accent: Color,
    spinFocus: FocusRequester,
    onSpinFocused: () -> Unit,
    upTo: FocusRequester,
    downTo: FocusRequester?,
    spinPosters: List<String> = emptyList(),
    onSpin: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = EDGE).height(if (light) 120.dp else 176.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!light && stats.genres.isNotEmpty()) TasteRing(stats, Modifier.size(164.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!light) Text("TASTE DNA", style = MaterialTheme.typography.labelSmall, color = NuvioTheme.colors.TextTertiary)
            Text(summary, style = MaterialTheme.typography.headlineSmall, color = NuvioTheme.colors.TextPrimary,
                fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!light && stats.genres.isNotEmpty()) TasteLegend(stats)
        }
        val spinModifier = Modifier.focusRequester(spinFocus).onFocusChanged { if (it.isFocused) onSpinFocused() }.nuvioCVertical(upTo, downTo)
        if (com.nuvio.tv.NuvioCFeatures.TASTE_POLISH) TasteSpinCardV2(
            title = if (light) "Spin something" else "Spin my taste",
            chips = spinChips(stats, light),
            posters = if (light) emptyList() else spinPosters,
            accent = accent,
            modifier = spinModifier
        ) { onSpin() }
        else TasteSpinCard(
            title = if (light) "Spin something" else "Spin my taste",
            chips = spinChips(stats, light),
            accent = accent,
            modifier = Modifier.width(250.dp).then(spinModifier)
        ) { onSpin() }
    }
}

@Composable
private fun spinChips(s: NuvioCTasteStats, light: Boolean): List<String> {
    if (light) return listOf("Movies", "Unwatched")
    val mood = NuvioCRoulettePreset.moodFor(s.genres.firstOrNull()?.name)
    return listOfNotNull(
        if (mood != com.nuvio.tv.data.nuvioc.NuvioCShuffleMood.ANY) moodLabel(mood) else null,
        s.topDecade?.let { com.nuvio.tv.data.nuvioc.tasteDecadeLabel(it) },
        "Unwatched"
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TasteSpinCard(title: String, chips: List<String>, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.height(150.dp).onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = accent.copy(alpha = 0.16f),
            contentColor = NuvioTheme.colors.TextPrimary,
            focusedContainerColor = accent.copy(alpha = 0.26f),
            focusedContentColor = NuvioTheme.colors.TextPrimary
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, accent.copy(alpha = 0.35f)), shape = RoundedCornerShape(18.dp)),
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(18.dp))
        )
    ) {
        Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(if (focused) Color.White else accent), contentAlignment = Alignment.Center) {
                Icon(com.nuvio.tv.ui.components.NuvioCIcons.Dice, contentDescription = null, tint = Color.Black, modifier = Modifier.size(22.dp))
            }
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                chips.take(3).forEach { c ->
                    Box(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.10f)).padding(horizontal = 9.dp, vertical = 3.dp)) {
                        Text(c, style = MaterialTheme.typography.labelSmall, color = NuvioTheme.colors.TextSecondary, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun TasteRing(s: NuvioCTasteStats, modifier: Modifier) {
    val shown = s.genres.take(5)
    val shares = shown.map { it.percent.coerceAtLeast(1) }
    val others = (100 - shares.sum()).coerceAtLeast(0)
    val parts = shares.mapIndexed { i, p -> p to TASTE_RING[i] } + (if (others > 0) listOf(others to TASTE_OTHERS) else emptyList())
    val total = parts.sumOf { it.first }.coerceAtLeast(1)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.15f
            val inset = stroke / 2
            var start = -90f
            val gap = if (parts.size > 1) 2.2f else 0f
            parts.forEach { (p, color) ->
                val sweep = 360f * p / total
                drawArc(color, start + gap / 2, (sweep - gap).coerceAtLeast(0.5f), useCenter = false,
                    topLeft = Offset(inset, inset), size = Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Butt))
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${shown.first().percent}%", style = MaterialTheme.typography.headlineSmall, color = NuvioTheme.colors.TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(shown.first().name, style = MaterialTheme.typography.labelMedium, color = NuvioTheme.colors.TextSecondary, maxLines = 1)
        }
    }
}

@Composable
private fun TasteLegend(s: NuvioCTasteStats) {
    val shown = s.genres.take(5)
    val others = (100 - shown.sumOf { it.percent }).coerceAtLeast(0)
    val items = shown.mapIndexed { i, g -> Triple(g.name, g.percent, TASTE_RING[i]) } + if (others > 0) listOf(Triple("Others", others, TASTE_OTHERS)) else emptyList()
    if (com.nuvio.tv.NuvioCFeatures.TASTE_POLISH) { TasteLegendColumns(items); return } // [fork] round 41
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        items.chunked(3).forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                line.forEach { (name, p, color) ->
                    Row(Modifier.width(150.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                        Text(name, style = MaterialTheme.typography.bodySmall, color = NuvioTheme.colors.TextSecondary, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp).weight(1f))
                        Text("$p%", style = MaterialTheme.typography.bodySmall, color = NuvioTheme.colors.TextTertiary, maxLines = 1)
                    }
                }
            }
        }
    }
}

// ---- tiles -----------------------------------------------------------------------------------------------

@Composable
private fun TasteLiveTiles(live: NuvioCTasteLive?, countUp: Boolean, onCounted: () -> Unit = {}) {
    // [fork] round 41 taste_smooth: once played, scrolling back to the tiles shows plain numbers
    if (countUp && live != null) LaunchedEffect(Unit) { kotlinx.coroutines.delay(260); onCounted() }
    val tiles = buildList {
        live?.watching?.let { add(Triple(Icons.Default.PlayArrow, it, "Watching")) }
        live?.upNext?.let { add(Triple(Icons.Default.SkipNext, it, "Up next")) }
        live?.upcoming?.let { add(Triple(Icons.Default.CalendarMonth, it, "Upcoming")) }
        live?.saved?.let { add(Triple(Icons.Default.Bookmark, it, "Saved")) }
        live?.ratedThisMonth?.let { add(Triple(Icons.Default.Star, it, "Rated this month")) }
    }
    TasteTileRow(tiles.map { (icon, n, label) -> TasteTile(icon, n, null, label) }, countUp)
}

@Composable
private fun TasteMonthTiles(s: NuvioCTasteStats) {
    val day = s.peakDay?.getDisplayName(TextStyle.FULL, Locale.US)
    val tiles = listOfNotNull(
        TasteTile(null, s.hours, null, "Hours"),
        TasteTile(null, s.titles, null, "Titles"),
        s.rated.takeIf { it > 0 }?.let { TasteTile(null, it, null, "Rated") },
        day?.let { TasteTile(null, null, it, "Busiest day") }
    )
    TasteTileRow(tiles, countUp = false)
}

private data class TasteTile(val icon: ImageVector?, val number: Int?, val text: String?, val label: String)

@Composable
private fun TasteTileRow(tiles: List<TasteTile>, countUp: Boolean) {
    if (tiles.isEmpty()) return
    Row(Modifier.fillMaxWidth().padding(horizontal = EDGE), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.forEach { t ->
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.05f))
                    .padding(vertical = 14.dp, horizontal = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                t.icon?.let { Icon(it, contentDescription = null, tint = NuvioTheme.colors.TextSecondary, modifier = Modifier.size(18.dp)) }
                val value = t.text ?: t.number?.let { n -> if (countUp) countUpText(n) else NF.format(n) } ?: ""
                Text(value, style = MaterialTheme.typography.headlineSmall, color = NuvioTheme.colors.TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(t.label, style = MaterialTheme.typography.bodySmall, color = NuvioTheme.colors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The number counts up once, in 200 ms. */
@Composable
private fun countUpText(target: Int): String {
    val a = remember { Animatable(0f) }
    LaunchedEffect(target) { a.animateTo(target.toFloat(), tween(200, easing = LinearEasing)) }
    return NF.format(a.value.toInt())
}

// ---- spotlight -------------------------------------------------------------------------------------------

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TasteSpotlight(
    cards: List<NuvioCTasteInsight>,
    changed: Set<String>,
    accent: Color,
    firstFocus: FocusRequester,
    section: (String) -> FocusRequester,
    onArea: (String) -> Unit,
    onCard: (NuvioCTasteInsight, String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TasteSpotlightTitle()
        cards.chunked(3).forEachIndexed { r, line ->
            TasteSpotlightLine(line, r, changed, accent, firstFocus, section, onArea, onCard)
        }
    }
}

@Composable
private fun TasteSpotlightTitle() {
    Text("Spotlight", style = MaterialTheme.typography.titleMedium, color = NuvioTheme.colors.TextPrimary, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = EDGE))
}

/** One line of three spotlight cards; [r] = the line's index. */
@Composable
private fun TasteSpotlightLine(
    line: List<NuvioCTasteInsight>,
    r: Int,
    changed: Set<String>,
    accent: Color,
    firstFocus: FocusRequester,
    section: (String) -> FocusRequester,
    onArea: (String) -> Unit,
    onCard: (NuvioCTasteInsight, String) -> Unit
) {
    Row(Modifier.padding(horizontal = EDGE), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        line.forEachIndexed { c, card ->
            val key = "card:${r * 3 + c}"
            TasteInsightCard(
                card, card.id in changed, accent,
                Modifier.weight(1f).then(if (r == 0 && c == 0) Modifier.focusRequester(firstFocus) else Modifier)
                    .focusRequester(section(key))
                    .onFocusChanged { if (it.isFocused) onArea(key) }
                    .then(if (c == 0) Modifier.nuvioCHeldLeftStop() else Modifier)
            ) { onCard(card, key) }
        }
        repeat(3 - line.size) { Spacer(Modifier.weight(1f)) }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TasteInsightCard(card: NuvioCTasteInsight, changed: Boolean, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.height(140.dp).onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.05f),
            contentColor = NuvioTheme.colors.TextPrimary,
            focusedContainerColor = Color.White.copy(alpha = 0.10f),
            focusedContentColor = NuvioTheme.colors.TextPrimary
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)), shape = RoundedCornerShape(16.dp)),
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(16.dp))
        )
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(card.kicker.uppercase(Locale.US), style = MaterialTheme.typography.labelSmall, color = NuvioTheme.colors.TextTertiary, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (changed) Box(Modifier.padding(start = 6.dp).size(6.dp).clip(CircleShape).background(accent))
            }
            Spacer(Modifier.height(6.dp))
            Text(card.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
            card.visual?.let { TasteVisual(it, accent) }
            card.line?.let { line ->
                FocusMarqueeText(text = line, focused = focused, style = MaterialTheme.typography.bodySmall, color = NuvioTheme.colors.TextSecondary,
                    modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun TasteVisual(v: NuvioCTasteVisual, accent: Color) {
    val dim = Color.White.copy(alpha = 0.12f)
    val labelStyle = MaterialTheme.typography.labelSmall
    when (v) {
        is NuvioCTasteVisual.Split -> Column {
            val colors = listOf(accent, Color(0xFF5AA3F0), Color(0xFF4FC4BA))
            Canvas(Modifier.fillMaxWidth().height(7.dp)) {
                val r = CornerRadius(size.height / 2)
                drawRoundRect(dim, cornerRadius = r)
                var x = 0f
                v.parts.forEachIndexed { i, f ->
                    val w = size.width * f.coerceIn(0f, 1f)
                    drawRoundRect(colors[i % colors.size], topLeft = Offset(x, 0f), size = Size(w, size.height), cornerRadius = r)
                    x += w
                }
            }
            if (v.start != null || v.end != null) Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(v.start.orEmpty(), style = labelStyle, color = NuvioTheme.colors.TextSecondary, maxLines = 1, modifier = Modifier.weight(1f))
                Text(v.end.orEmpty(), style = labelStyle, color = NuvioTheme.colors.TextSecondary, maxLines = 1)
            }
        }
        is NuvioCTasteVisual.Bars -> Column {
            val max = (v.values.maxOrNull() ?: 0).coerceAtLeast(1)
            Canvas(Modifier.fillMaxWidth().height(26.dp)) {
                val n = v.values.size.coerceAtLeast(1)
                val gap = 3.dp.toPx()
                val w = (size.width - gap * (n - 1)) / n
                v.values.forEachIndexed { i, value ->
                    val h = (size.height * value / max).coerceAtLeast(2.dp.toPx())
                    drawRoundRect(if (i == v.highlight) accent else dim, topLeft = Offset(i * (w + gap), size.height - h), size = Size(w, h), cornerRadius = CornerRadius(2.dp.toPx()))
                }
            }
            if (v.start != null || v.end != null) Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                Text(v.start.orEmpty(), style = labelStyle, color = NuvioTheme.colors.TextTertiary, maxLines = 1, modifier = Modifier.weight(1f))
                Text(v.end.orEmpty(), style = labelStyle, color = NuvioTheme.colors.TextTertiary, maxLines = 1)
            }
        }
        is NuvioCTasteVisual.Versus -> Column {
            Canvas(Modifier.fillMaxWidth().height(14.dp)) {
                val y = size.height / 2
                drawLine(dim, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
                val lo = (minOf(v.you, v.crowd) - 1.5).coerceAtLeast(0.0)
                val hi = (maxOf(v.you, v.crowd) + 1.5).coerceAtMost(10.0)
                fun x(score: Double) = (size.width * ((score - lo) / (hi - lo).coerceAtLeast(0.1))).toFloat()
                drawCircle(Color.White.copy(alpha = 0.45f), radius = 5.dp.toPx(), center = Offset(x(v.crowd), y))
                drawCircle(accent, radius = 6.dp.toPx(), center = Offset(x(v.you), y))
            }
            if (v.start != null || v.end != null) Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                Text(v.start.orEmpty(), style = labelStyle, color = NuvioTheme.colors.TextSecondary, maxLines = 1, modifier = Modifier.weight(1f), overflow = TextOverflow.Ellipsis)
                Text(v.end.orEmpty(), style = labelStyle, color = NuvioTheme.colors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        is NuvioCTasteVisual.Steps -> Canvas(Modifier.fillMaxWidth().height(8.dp)) {
            val n = v.total.coerceIn(1, 24)
            val gap = 3.dp.toPx()
            val w = (size.width - gap * (n - 1)) / n
            repeat(n) { i ->
                drawRoundRect(if (i < v.done) accent else dim, topLeft = Offset(i * (w + gap), 0f), size = Size(w, size.height), cornerRadius = CornerRadius(2.dp.toPx()))
            }
        }
    }
}

// ---- rows ------------------------------------------------------------------------------------------------

/** A row's title and the row; [content] gets the held-Left guard for its first item. */
@Composable
private fun TasteRowFrame(title: String, content: @Composable (Modifier) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = NuvioTheme.colors.TextPrimary, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = EDGE))
        content(Modifier.nuvioCHeldLeftStop())
    }
}

/** Per-row memory of the last focused item, kept for the page's life (Back from a title lands where you were). */
class NuvioCTasteRowMemory { var index by mutableIntStateOf(0) }

/** Keeps the focused item at the row's start padding, so moving into or along a row never bounces it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun rememberStartPivot(): BringIntoViewSpec {
    val startPx = with(LocalDensity.current) { EDGE.toPx() }
    return remember(startPx) {
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = offset - startPx
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun <T> TasteFocusRow(
    items: List<T>,
    memory: NuvioCTasteRowMemory,
    firstItemGuard: Modifier,
    spacing: androidx.compose.ui.unit.Dp,
    rowFocus: FocusRequester,
    onArea: () -> Unit,
    itemContent: @Composable (index: Int, item: T, modifier: Modifier) -> Unit
) {
    val requesters = remember(items.size) { List(items.size) { FocusRequester() } }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = memory.index.coerceIn(0, (items.size - 1).coerceAtLeast(0)))
    androidx.compose.runtime.CompositionLocalProvider(LocalBringIntoViewSpec provides rememberStartPivot()) {
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth()
                .focusRequester(rowFocus)
                .onFocusChanged { if (it.hasFocus) onArea() }
                .focusRestorer { requesters.getOrNull(memory.index.coerceIn(0, (items.size - 1).coerceAtLeast(0))) ?: FocusRequester.Default }
                .focusGroup(),
            contentPadding = PaddingValues(horizontal = EDGE, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(spacing)
        ) {
            itemsIndexed(items) { i, item ->
                itemContent(
                    i, item,
                    Modifier.focusRequester(requesters[i])
                        .onFocusChanged { if (it.isFocused) memory.index = i }
                        .then(if (i == 0) firstItemGuard else Modifier)
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TastePosterRow(items: List<NuvioCTasteShelfItem>, memory: NuvioCTasteRowMemory, guard: Modifier, rowFocus: FocusRequester, onArea: () -> Unit, onOpenTitle: (String, String) -> Unit) {
    val context = LocalContext.current
    TasteFocusRow(items, memory, guard, 14.dp, rowFocus, onArea) { _, item, mod ->
        var focused by remember { mutableStateOf(false) }
        Column(Modifier.width(120.dp)) {
            Card(
                onClick = { onOpenTitle(item.id, item.type) },
                modifier = mod.width(120.dp).height(180.dp).onFocusChanged { focused = it.isFocused },
                shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
                scale = CardDefaults.scale(focusedScale = 1.06f),
                border = CardDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(12.dp))),
                colors = CardDefaults.colors(containerColor = Color.White.copy(alpha = 0.06f))
            ) {
                if (item.poster != null) {
                    val request = remember(item.poster) { ImageRequest.Builder(context).data(item.poster).size(240, 360).crossfade(200).build() }
                    AsyncImage(model = request, contentDescription = item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Box(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) {
                        Text(item.name, style = MaterialTheme.typography.bodySmall, color = NuvioTheme.colors.TextSecondary, maxLines = 4)
                    }
                }
            }
            FocusMarqueeText(text = item.name, focused = focused, style = MaterialTheme.typography.bodySmall,
                color = if (focused) NuvioTheme.colors.TextPrimary else NuvioTheme.colors.TextSecondary, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TastePeopleRow(people: List<NuvioCTastePersonStat>, memory: NuvioCTasteRowMemory, guard: Modifier, rowFocus: FocusRequester, onArea: () -> Unit, onOpenPerson: (Int, String, Boolean) -> Unit) {
    val context = LocalContext.current
    TasteFocusRow(people, memory, guard, 18.dp, rowFocus, onArea) { _, p, mod ->
        var focused by remember { mutableStateOf(false) }
        Column(Modifier.width(104.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                onClick = { onOpenPerson(p.person.id, p.person.name, p.director) },
                modifier = mod.size(84.dp).onFocusChanged { focused = it.isFocused },
                shape = ClickableSurfaceDefaults.shape(CircleShape),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
                colors = ClickableSurfaceDefaults.colors(containerColor = Color.White.copy(alpha = 0.08f), focusedContainerColor = Color.White.copy(alpha = 0.08f)),
                border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = CircleShape))
            ) {
                val photo = p.person.photo
                if (photo != null) {
                    val request = remember(photo) { ImageRequest.Builder(context).data(photo).size(168, 168).crossfade(200).build() }
                    AsyncImage(model = request, contentDescription = p.person.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(p.person.name.split(' ').mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString(""),
                            style = MaterialTheme.typography.titleMedium, color = NuvioTheme.colors.TextSecondary)
                    }
                }
            }
            FocusMarqueeText(text = p.person.name, focused = focused, style = MaterialTheme.typography.bodySmall,
                color = NuvioTheme.colors.TextPrimary, modifier = Modifier.padding(top = 8.dp))
            Text(if (p.director) "Director · ${p.count}" else "${p.count} titles", style = MaterialTheme.typography.labelSmall,
                color = NuvioTheme.colors.TextTertiary, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TasteRecapRow(recaps: List<NuvioCTasteRecap>, memory: NuvioCTasteRowMemory, guard: Modifier, rowFocus: FocusRequester, onArea: () -> Unit, accent: Color, onOpen: (NuvioCTasteRecap) -> Unit) {
    TasteFocusRow(recaps, memory, guard, 12.dp, rowFocus, onArea) { _, r, mod ->
        Surface(
            onClick = { onOpen(r) },
            modifier = mod.width(196.dp).height(104.dp),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = accent.copy(alpha = 0.10f),
                contentColor = NuvioTheme.colors.TextPrimary,
                focusedContainerColor = accent.copy(alpha = 0.18f),
                focusedContentColor = NuvioTheme.colors.TextPrimary
            ),
            border = ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)), shape = RoundedCornerShape(16.dp)),
                focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(16.dp))
            )
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(r.month.month.getDisplayName(TextStyle.FULL, Locale.US) + " " + r.month.year, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text("${NF.format(r.titles)} titles · ${NF.format(r.hours)} h", style = MaterialTheme.typography.bodySmall, color = NuvioTheme.colors.TextSecondary, maxLines = 1)
                r.topGenre?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = accent, maxLines = 1) }
            }
        }
    }
}

// ---- backdrop and first build ----------------------------------------------------------------------------

@Composable
private fun TastePageBackdrop(urls: List<String>, accent: Color) {
    Box(Modifier.fillMaxWidth().height(460.dp).background(
        androidx.compose.ui.graphics.Brush.radialGradient(
            listOf(accent.copy(alpha = 0.16f), Color.Transparent),
            center = Offset(1500f, 0f), radius = 1100f
        )
    ))
    if (urls.isEmpty()) return
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().height(420.dp).graphicsLayer { alpha = 0.12f }) {
        urls.take(3).forEach { url ->
            val request = remember(url) { ImageRequest.Builder(context).data(url).size(320, 180).crossfade(300).build() }
            AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop,
                filterQuality = androidx.compose.ui.graphics.FilterQuality.Low, modifier = Modifier.weight(1f).fillMaxHeight())
        }
    }
    Box(Modifier.fillMaxWidth().height(420.dp).background(
        androidx.compose.ui.graphics.Brush.verticalGradient(listOf(NuvioTheme.colors.Background.copy(alpha = 0.3f), NuvioTheme.colors.Background))
    ))
}

/** First build: posters from the profile's history drift down into genre bars that grow as titles are read. */
@Composable
private fun TasteBuilding(state: NuvioCTasteState, accent: Color) {
    val context = LocalContext.current
    val total = state.progressTotal.coerceAtLeast(1)
    val done = (total - state.pending).coerceIn(0, total)
    val transition = rememberInfiniteTransition(label = "tasteBuild")
    val t by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(3_200, easing = LinearEasing), RepeatMode.Restart), label = "drift")
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("TASTE", style = MaterialTheme.typography.labelMedium, color = NuvioTheme.colors.TextTertiary)
        Spacer(Modifier.height(18.dp))
        Box(Modifier.width(520.dp).height(150.dp)) {
            state.progressPosters.take(5).forEachIndexed { i, url ->
                val phase = ((t + i * 0.2f) % 1f)
                val request = remember(url) { ImageRequest.Builder(context).data(url).size(108, 162).build() }
                AsyncImage(
                    model = request, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.offset(x = (40 + i * 96).dp).width(54.dp).height(81.dp).graphicsLayer {
                        translationY = phase * 70.dp.toPx()
                        val s = 1f - 0.6f * phase
                        scaleX = s; scaleY = s
                        alpha = when { phase < 0.2f -> phase / 0.2f; phase > 0.75f -> (1f - phase) / 0.25f; else -> 1f }
                    }.clip(RoundedCornerShape(6.dp))
                )
            }
        }
        val genres = state.progressGenres
        val max = (genres.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
        Row(Modifier.height(80.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            genres.forEachIndexed { i, (_, n) ->
                val h by animateFloatAsState(n.toFloat() / max, tween(600), label = "bar$i")
                Box(Modifier.width(48.dp).fillMaxHeight(h.coerceIn(0.06f, 1f)).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                    .background(TASTE_RING[i % TASTE_RING.size]))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
            genres.forEach { (g, _) ->
                Text(g, style = MaterialTheme.typography.labelSmall, color = NuvioTheme.colors.TextTertiary, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.width(48.dp))
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("${NF.format(done)} of ${NF.format(total)} titles read", style = MaterialTheme.typography.bodyMedium, color = NuvioTheme.colors.TextSecondary)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.width(220.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.1f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(done.toFloat() / total).background(accent))
        }
    }
}

// ---- focus helpers ---------------------------------------------------------------------------------------

/** A held Left that reaches this item stops here; only a fresh press moves on (to the sidebar). */
internal fun Modifier.nuvioCHeldLeftStop(): Modifier =
    if (!com.nuvio.tv.NuvioCFeatures.ROWS_NO_SIDEBAR_ON_HOLD) this else onPreviewKeyEvent { e ->
        val n = e.nativeKeyEvent
        n.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && n.action == KeyEvent.ACTION_DOWN && n.repeatCount > 0
    }

/** Down from the pills goes to the Spin card. */
private fun Modifier.nuvioCDownTo(target: FocusRequester): Modifier = onPreviewKeyEvent { e ->
    val n = e.nativeKeyEvent
    if (n.keyCode == KeyEvent.KEYCODE_DPAD_DOWN && n.action == KeyEvent.ACTION_DOWN) runCatching { target.requestFocus() }.getOrDefault(false) else false
}

/** Up from the Spin card = the pills; Down = the first spotlight card (the live tiles aren't focusable). */
private fun Modifier.nuvioCVertical(up: FocusRequester, down: FocusRequester?): Modifier = onPreviewKeyEvent { e ->
    val n = e.nativeKeyEvent
    if (n.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
    when (n.keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> runCatching { up.requestFocus() }.getOrDefault(false)
        KeyEvent.KEYCODE_DPAD_DOWN -> down?.let { runCatching { it.requestFocus() }.getOrDefault(false) } ?: false
        else -> false
    }
}
