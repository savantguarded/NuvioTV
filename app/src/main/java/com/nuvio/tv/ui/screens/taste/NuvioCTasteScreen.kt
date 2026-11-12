package com.nuvio.tv.ui.screens.taste

// [fork] Nuvio C round 39 `taste_page`: the profile's Taste page (sidebar, under Calendar).
//
// Layout, top to bottom: header (profile dot, "Your taste", time range), the one-line summary, four headline numbers,
// Top genres beside How you rate (or What you pick), When you watch beside Decades, Your people, then the shelves:
// Spin my taste, Your gaps, Unfinished, Rewatch. Sections appear only once there's enough to be true (thresholds in
// NuvioCTasteStats); an empty profile gets a short invitation instead. Charts are drawn once (no animation), the only
// pictures are people's photos, the shelves' posters and a dim backdrop collage fetched small.
// Everything that looks tappable is: genres and decades start Roulette with that choice, people open their page,
// posters open the title.

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.data.nuvioc.NuvioCTastePersonStat
import com.nuvio.tv.data.nuvioc.NuvioCTasteRange
import com.nuvio.tv.data.nuvioc.NuvioCTasteRepository
import com.nuvio.tv.data.nuvioc.NuvioCTasteShelfItem
import com.nuvio.tv.data.nuvioc.NuvioCTasteState
import com.nuvio.tv.data.nuvioc.NuvioCTasteStats
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import com.nuvio.tv.ui.screens.shuffle.NuvioCRoulettePreset
import com.nuvio.tv.ui.theme.NuvioTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject

private val EDGE = 48.dp

@HiltViewModel
class NuvioCTasteViewModel @Inject constructor(
    private val repository: NuvioCTasteRepository,
    private val profiles: ProfileManager,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {
    val state: StateFlow<NuvioCTasteState> = repository.state
    private val _gaps = MutableStateFlow<List<NuvioCTasteShelfItem>>(emptyList())
    val gaps: StateFlow<List<NuvioCTasteShelfItem>> = _gaps.asStateFlow()
    private var gapsFor = -1L

    val accent: Color
        get() = runCatching { Color(android.graphics.Color.parseColor(profiles.activeProfile?.avatarColorHex ?: "")) }
            .getOrDefault(Color(0xFF6FA8FF))
    val profileName: String get() = profiles.activeProfile?.name.orEmpty()

    fun open(force: Boolean = false) {
        // [fork] round 42 taste_profile_strict: nothing from another profile's visit is reused on this page
        val profileNow = profiles.activeProfileId.value
        if (com.nuvio.tv.NuvioCFeatures.TASTE_PROFILE_STRICT && openedProfile != profileNow) {
            if (openedProfile != null) {
                opened = false
                _rows.value = emptyList(); _review.value = null; _panel.value = null; _monthData.value = null; _live.value = null
                memories.clear(); spotlightCache.clear(); changedIds = emptySet(); lastArea = null; monthFrom = null; panelFrom = null
                countUp = true
                pageListState = androidx.compose.foundation.lazy.LazyListState()
            }
            openedProfile = profileNow
        }
        if (com.nuvio.tv.NuvioCFeatures.TASTE_REFRESH_FEEDBACK && force) {
            _refreshing.value = true
            viewModelScope.launch {
                runCatching { repository.open(true) }
                _refreshing.value = false
                _updatedAt.value = System.currentTimeMillis()
            }
        } else viewModelScope.launch { repository.open(force) }
        // [fork] round 40: a new visit gets new spotlight cards and rows; the live tiles are read again
        if (!opened || force) {
            opened = true
            seed = System.nanoTime()
            rowsFor = -1L
            reviewFor = -1L // [fork] round 41 taste_review
            if (com.nuvio.tv.NuvioCFeatures.TASTE_LIVE) viewModelScope.launch { _live.value = runCatching { repository.live() }.getOrNull() }
        }
    }

    // ---- [fork] round 40 taste_v2 ------------------------------------------------------------------------
    private var opened = false
    private var openedProfile: Int? = null
    /** [fork] round 42 taste_focus_keep: the page's scroll position, kept for the whole visit (Back from a title too). */
    var pageListState = androidx.compose.foundation.lazy.LazyListState()
        private set
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    private val _updatedAt = MutableStateFlow(0L)
    /** When the last refresh-button rebuild finished (the "Updated" note shows for a moment). */
    val updatedAt: StateFlow<Long> = _updatedAt.asStateFlow()

    /** [fork] round 42: long press on refresh: re-read every title's details, then rebuild. */
    fun refreshFull() {
        repository.requestRefetchAll()
        open(force = true)
    }
    private var seed = System.nanoTime()
    private val _live = MutableStateFlow<com.nuvio.tv.data.nuvioc.NuvioCTasteLive?>(null)
    val live: StateFlow<com.nuvio.tv.data.nuvioc.NuvioCTasteLive?> = _live.asStateFlow()
    private val _rows = MutableStateFlow<List<com.nuvio.tv.data.nuvioc.NuvioCTasteRow>>(emptyList())
    val rows: StateFlow<List<com.nuvio.tv.data.nuvioc.NuvioCTasteRow>> = _rows.asStateFlow()
    private var rowsFor = -1L
    private val _monthData = MutableStateFlow<Pair<NuvioCTasteStats, List<com.nuvio.tv.data.nuvioc.NuvioCTasteInsight>>?>(null)
    val monthData: StateFlow<Pair<NuvioCTasteStats, List<com.nuvio.tv.data.nuvioc.NuvioCTasteInsight>>?> = _monthData.asStateFlow()
    private val memories = HashMap<String, NuvioCTasteRowMemory>()
    /** The section focus was last in (Back lands there). */
    var lastArea: String? = null
    /** Tiles count up on the first open of the page only. */
    var countUp = true
        private set
    private val prefs by lazy { context.getSharedPreferences("nuvio_c_taste_cards", android.content.Context.MODE_PRIVATE) }
    /** Spotlight cards whose value changed since you last saw that card. */
    var changedIds: Set<String> = emptySet()
        private set
    private val spotlightCache = HashMap<String, List<com.nuvio.tv.data.nuvioc.NuvioCTasteInsight>>()

    fun rowMemory(id: String): NuvioCTasteRowMemory = memories.getOrPut(id) { NuvioCTasteRowMemory() }

    // ---- [fork] round 41 ----------------------------------------------------------------------------------
    /** taste_smooth: the count-up has played this visit (scrolling the tiles back into view shows plain numbers). */
    fun markCounted() { countUp = false }

    /** taste_polish: this visit's backdrop, one of the recent ones, chosen once per open. */
    fun backdropFor(urls: List<String>): String? = if (urls.isEmpty()) null else urls[((seed ushr 1) % urls.size).toInt()]

    private val _review = MutableStateFlow<com.nuvio.tv.data.nuvioc.NuvioCTasteReview?>(null)
    val review: StateFlow<com.nuvio.tv.data.nuvioc.NuvioCTasteReview?> = _review.asStateFlow()
    private var reviewFor = -1L
    fun loadReview(builtAt: Long) {
        if (!com.nuvio.tv.NuvioCFeatures.TASTE_REVIEW || builtAt == reviewFor) return
        reviewFor = builtAt
        if (com.nuvio.tv.NuvioCFeatures.TASTE_REVIEW_FALLBACK) {
            // [fork] round 42 taste_review_fallback: retried while the profile's data is still being read
            viewModelScope.launch {
                repeat(8) {
                    when (val r = runCatching { repository.reviewResult() }.getOrNull()) {
                        is com.nuvio.tv.data.nuvioc.NuvioCTasteReviewResult.Ready -> { _review.value = r.review; return@launch }
                        com.nuvio.tv.data.nuvioc.NuvioCTasteReviewResult.None -> { _review.value = null; return@launch }
                        else -> kotlinx.coroutines.delay(1_500)
                    }
                }
            }
            return
        }
        viewModelScope.launch { _review.value = runCatching { repository.review() }.getOrNull() }
    }

    /** taste_back: what opened the month view ("monthPill", "review", "recaps"), so Back can return there. */
    var monthFrom: String? = null

    /** taste_card_titles: the open titles panel (null = closed). */
    data class Panel(
        val card: com.nuvio.tv.data.nuvioc.NuvioCTasteInsight,
        val scope: String,
        val items: List<com.nuvio.tv.data.nuvioc.NuvioCTastePanelItem>? = null,
        val sort: Int = 0,
        /** [fork] round 42 taste_recap_panel: set when the panel shows a Recap month. */
        val recap: com.nuvio.tv.data.nuvioc.NuvioCTasteRecapDetail? = null
    )
    private val _panel = MutableStateFlow<Panel?>(null)
    val panel: StateFlow<Panel?> = _panel.asStateFlow()
    /** The panel row last focused (Back from a title lands on it). */
    var panelIndex = 0
    /** The spotlight card the panel was opened from. */
    var panelFrom: String? = null

    fun openPanel(card: com.nuvio.tv.data.nuvioc.NuvioCTasteInsight, scope: String, from: String) {
        panelIndex = 0
        panelFrom = from
        _panel.value = Panel(card, scope)
        viewModelScope.launch {
            val items = runCatching { repository.panel(card.keys) }.getOrDefault(emptyList())
            _panel.value = _panel.value?.takeIf { it.card.id == card.id }?.copy(items = items)
        }
    }

    /** [fork] round 42 taste_recap_panel: a Recap's month in the titles panel, with its breakdown on top. */
    fun openRecapPanel(month: java.time.YearMonth, from: String) {
        panelIndex = 0
        panelFrom = from
        val scope = month.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.US) + " " + month.year
        viewModelScope.launch {
            val detail = runCatching { repository.recapDetail(month) }.getOrNull() ?: return@launch
            val card = com.nuvio.tv.data.nuvioc.NuvioCTasteInsight(
                id = "recap:$month",
                kicker = "Recap",
                value = "${detail.titles} ${if (detail.titles == 1) "title" else "titles"} · ${detail.hours} h",
                line = recapLine(detail),
                action = com.nuvio.tv.data.nuvioc.NuvioCTasteAction.Month(month.toString()),
                keys = detail.keys
            )
            _panel.value = Panel(card, scope, recap = detail)
            val window = com.nuvio.tv.data.nuvioc.tasteMonthWindow(month, java.time.ZoneId.systemDefault())
            val items = runCatching { repository.panel(detail.keys, window) }.getOrDefault(emptyList())
            _panel.value = _panel.value?.takeIf { it.card.id == card.id }?.copy(items = items)
        }
    }

    private fun recapLine(d: com.nuvio.tv.data.nuvioc.NuvioCTasteRecapDetail): String = listOfNotNull(
        d.films.takeIf { it > 0 }?.let { "$it ${if (it == 1) "film" else "films"}" },
        d.episodes.takeIf { it > 0 }?.let { "$it ${if (it == 1) "episode" else "episodes"} from ${d.shows} ${if (d.shows == 1) "show" else "shows"}" }
    ).joinToString(" · ")

    fun sortPanel(sort: Int) {
        val p = _panel.value ?: return
        val items = p.items ?: return
        panelIndex = 0
        _panel.value = p.copy(sort = sort, items = when (sort) {
            1 -> items.sortedWith(compareByDescending<com.nuvio.tv.data.nuvioc.NuvioCTastePanelItem> { it.score ?: -1 }.thenByDescending { it.lastWatched })
            2 -> items.sortedBy { it.name.lowercase(Locale.US) }
            else -> items.sortedByDescending { it.lastWatched }
        })
    }

    fun closePanel() { _panel.value = null }

    fun loadRows(stats: NuvioCTasteStats, builtAt: Long) {
        if (!com.nuvio.tv.NuvioCFeatures.TASTE_ROWS || builtAt == rowsFor || stats.titles == 0) return
        rowsFor = builtAt
        val mySeed = seed
        viewModelScope.launch {
            val list = runCatching { repository.rows(stats, mySeed) }.getOrDefault(emptyList())
            if (mySeed == seed) {
                _rows.value = list
                countUp = false
            }
        }
    }

    fun selectMonth(month: java.time.YearMonth?) {
        if (month == null) { _monthData.value = null; return }
        viewModelScope.launch { _monthData.value = repository.month(month) }
    }

    /** This visit's six cards for [key] (a range or a month); the same until the page is opened again. */
    fun spotlight(all: List<com.nuvio.tv.data.nuvioc.NuvioCTasteInsight>, key: String): List<com.nuvio.tv.data.nuvioc.NuvioCTasteInsight> {
        if (all.isEmpty()) return emptyList()
        // [fork] round 42 taste_focus_keep: the same cards all visit, even when a background rebuild changes the
        // list; each keeps its slot and takes its fresh value, so closing a panel lands on the card that opened it.
        if (com.nuvio.tv.NuvioCFeatures.TASTE_FOCUS_KEEP) {
            spotlightCache["$seed:$key"]?.let { kept -> return kept.map { c -> all.firstOrNull { it.id == c.id } ?: c } }
        }
        val cacheKey = if (com.nuvio.tv.NuvioCFeatures.TASTE_FOCUS_KEEP) "$seed:$key" else "$seed:$key:${all.size}"
        spotlightCache[cacheKey]?.let { return it }
        val now = java.time.ZonedDateTime.now()
        val weekend = now.dayOfWeek == java.time.DayOfWeek.SATURDAY || now.dayOfWeek == java.time.DayOfWeek.SUNDAY
        val picked = com.nuvio.tv.data.nuvioc.pickTasteSpotlight(all, seed xor key.hashCode().toLong(), now.hour, weekend)
        // the dot: a card shown before whose value is different now
        val profile = profiles.activeProfileId.value
        val changed = picked.filter { c ->
            val before = prefs.getString("p$profile:${c.id}", null)
            before != null && before != c.value
        }.map { it.id }.toSet()
        changedIds = changedIds + changed
        prefs.edit().apply { picked.forEach { putString("p$profile:${it.id}", it.value) } }.apply()
        spotlightCache[cacheKey] = picked
        return picked
    }

    /** "since March 2026" when the history is under two years old, else null. */
    fun sinceLabel(state: NuvioCTasteState): String? {
        val first = state.months.firstOrNull() ?: return null
        if (java.time.YearMonth.now().minusMonths(24).isAfter(first)) return null
        return "since " + first.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.US) + " " + first.year
    }

    /** The range pills worth showing: "This year" and "90 days" only when they differ from All time. */
    fun rangesFor(all: NuvioCTasteStats?): List<NuvioCTasteRange> {
        val s = repository.state.value
        if (all == null || !all.showNumbers) return emptyList()
        val year = s.all[NuvioCTasteRange.YEAR]
        val days = s.all[NuvioCTasteRange.DAYS_90]
        return listOfNotNull(
            NuvioCTasteRange.ALL,
            NuvioCTasteRange.YEAR.takeIf { year != null && year.titles in 1 until all.titles },
            NuvioCTasteRange.DAYS_90.takeIf { days != null && days.titles > 0 && (year == null || days.titles < year.titles) }
        )
    }

    /** One discover call per build, only once the page has its numbers. */
    fun loadGaps(stats: NuvioCTasteStats, builtAt: Long) {
        if (builtAt == gapsFor || stats.genres.isEmpty()) return
        gapsFor = builtAt
        viewModelScope.launch {
            _gaps.value = runCatching { repository.gaps(stats, repository.watchedKeys()) }.getOrDefault(emptyList())
        }
    }
}

@Composable
fun NuvioCTasteScreen(
    onOpenTitle: (id: String, type: String) -> Unit,
    onOpenPerson: (id: Int, name: String, crew: Boolean) -> Unit,
    onOpenRoulette: () -> Unit,
    viewModel: NuvioCTasteViewModel = hiltViewModel()
) {
    if (com.nuvio.tv.NuvioCFeatures.TASTE_V2) { // [fork] round 40
        NuvioCTastePage(viewModel, onOpenTitle, onOpenPerson, onOpenRoulette)
        return
    }
    val state by viewModel.state.collectAsState()
    val gaps by viewModel.gaps.collectAsState()
    var range by rememberSaveable { mutableStateOf(NuvioCTasteRange.ALL) }
    LaunchedEffect(Unit) { viewModel.open() }
    val stats = state.all[range] ?: state.all[NuvioCTasteRange.ALL]
    val all = state.all[NuvioCTasteRange.ALL]
    LaunchedEffect(state.builtAt) { all?.let { viewModel.loadGaps(it, state.builtAt) } }
    val accent = remember { viewModel.accent }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(stats == null) { firstFocus.requestFocusAfterFrames() }

    fun spin(genre: String?, year: Int?) {
        NuvioCRoulettePreset.set(
            NuvioCRoulettePreset.Preset(
                mood = NuvioCRoulettePreset.moodFor(genre),
                decade = NuvioCRoulettePreset.decadeFor(year)
            )
        )
        onOpenRoulette()
    }

    Box(Modifier.fillMaxSize().background(NuvioTheme.colors.Background)) {
        TasteBackdrop(all?.backdrops.orEmpty())
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 28.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            item(key = "header") {
                TasteHeader(
                    name = viewModel.profileName,
                    accent = accent,
                    range = range,
                    showRanges = all?.showNumbers == true,
                    onRange = { range = it },
                    onRefresh = { viewModel.open(force = true) },
                    firstFocus = firstFocus
                )
            }
            val s = stats
            item(key = "summary") {
                Column(Modifier.padding(horizontal = EDGE)) {
                    Text(
                        text = when {
                            s == null && state.pending > 0 -> "Getting your history ready (${state.pending} titles to look up the first time)…"
                            s == null -> "Getting your history ready…"
                            else -> NuvioCTasteCopy.summary(s)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = NuvioTheme.colors.TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (s?.localOnly == true) {
                        Text(NuvioCTasteCopy.localLine(), style = MaterialTheme.typography.bodySmall, color = NuvioTheme.colors.TextTertiary)
                    }
                }
            }
            if (s == null) return@LazyColumn
            if (s.empty) {
                item(key = "empty") { TasteEmpty(accent, onStart = { spin(null, null) }) }
                return@LazyColumn
            }
            if (s.showNumbers) item(key = "metrics") { TasteMetrics(s) }
            item(key = "genres_ratings") {
                Row(Modifier.fillMaxWidth().padding(horizontal = EDGE), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Column(Modifier.weight(1.1f)) {
                        if (s.genres.isNotEmpty()) TasteGenres(s, accent, compact = !s.showGenres) { spin(it, null) }
                    }
                    Column(Modifier.weight(1f)) {
                        if (s.showRatings) TasteRatings(s, accent) else TastePick(s, accent)
                    }
                }
            }
            if (s.showHabits || s.decades.size >= 2) item(key = "habits_decades") {
                Row(Modifier.fillMaxWidth().padding(horizontal = EDGE), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Column(Modifier.weight(1.1f)) { if (s.showHabits) TasteHabits(s, accent) }
                    Column(Modifier.weight(1f)) { if (s.decades.size >= 2 && s.showNumbers) TasteDecades(s, accent) { spin(s.genres.firstOrNull()?.name, it) } }
                }
            }
            if (s.showPeople) item(key = "people") { TastePeople(s, onOpenPerson) }
            item(key = "spin") {
                Column(Modifier.padding(horizontal = EDGE)) {
                    SectionTitle("Do something with it")
                    TasteButton("Spin my taste", accent, primary = true) { spin(s.genres.firstOrNull()?.name, s.topDecade) }
                }
            }
            if (gaps.isNotEmpty() && range == NuvioCTasteRange.ALL) item(key = "gaps") {
                TasteShelf("Your gaps", "Well-loved ${s.genres.firstOrNull()?.name?.lowercase() ?: ""} you haven't seen yet.", gaps, onOpenTitle)
            }
            if (s.unfinished.isNotEmpty() && range == NuvioCTasteRange.ALL) item(key = "unfinished") {
                TasteShelf("Unfinished", "Shows you started and stepped away from.", s.unfinished, onOpenTitle)
            }
            if (s.rewatch.isNotEmpty() && range == NuvioCTasteRange.ALL) item(key = "rewatch") {
                TasteShelf(
                    "Rewatch",
                    if (s.anyRatings) "Films you rated 9 or higher and haven't seen in three years." else "Films you finished that the crowd rates highly, not seen in three years.",
                    s.rewatch,
                    onOpenTitle
                )
            }
            NuvioCTasteCopy.growingLine(s)?.let { line ->
                item(key = "growing") {
                    Text(line, style = MaterialTheme.typography.bodySmall, color = NuvioTheme.colors.TextTertiary, modifier = Modifier.padding(horizontal = EDGE))
                }
            }
        }
    }
}

// ---- parts -----------------------------------------------------------------------------------------------

@Composable
private fun TasteBackdrop(urls: List<String>) {
    if (urls.isEmpty()) return
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().height(420.dp).graphicsLayer { alpha = 0.22f }) {
        urls.take(3).forEach { url ->
            val request = remember(url) { ImageRequest.Builder(context).data(url).size(320, 180).crossfade(300).build() }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Low,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
    Box(
        Modifier.fillMaxWidth().height(420.dp).background(
            Brush.verticalGradient(listOf(NuvioTheme.colors.Background.copy(alpha = 0.35f), NuvioTheme.colors.Background))
        )
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TasteHeader(
    name: String,
    accent: Color,
    range: NuvioCTasteRange,
    showRanges: Boolean,
    onRange: (NuvioCTasteRange) -> Unit,
    onRefresh: () -> Unit,
    firstFocus: FocusRequester
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = EDGE), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(accent.copy(alpha = 0.9f)), contentAlignment = Alignment.Center) {
            Text(name.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(14.dp))
        Text("Your taste", style = MaterialTheme.typography.headlineMedium, color = NuvioTheme.colors.TextPrimary, maxLines = 1)
        Spacer(Modifier.weight(1f))
        if (showRanges) {
            NuvioCTasteRange.entries.forEachIndexed { i, r ->
                TastePill(NuvioCTasteCopy.rangeLabel(r), selected = r == range, accent = accent,
                    modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier) { onRange(r) }
                Spacer(Modifier.width(8.dp))
            }
        }
        Surface(
            onClick = onRefresh,
            modifier = (if (!showRanges) Modifier.focusRequester(firstFocus) else Modifier).size(36.dp),
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
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TastePill(label: String, selected: Boolean, accent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(32.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White.copy(alpha = 0.16f) else Color.Transparent,
            contentColor = if (selected) NuvioTheme.colors.TextPrimary else NuvioTheme.colors.TextSecondary,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, if (selected) accent.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.10f)), shape = RoundedCornerShape(50))
        )
    ) {
        Box(Modifier.fillMaxHeight().padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

@Composable
private fun SectionTitle(text: String, line: String? = null) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = NuvioTheme.colors.TextPrimary, fontWeight = FontWeight.SemiBold)
    if (line != null) {
        Text(line, style = MaterialTheme.typography.bodySmall, color = NuvioTheme.colors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun TasteMetrics(s: NuvioCTasteStats) {
    val nf = remember { NumberFormat.getIntegerInstance(Locale.US) }
    val tiles = buildList {
        add("Hours watched" to nf.format(s.hours))
        add((if (s.shows > 0 && s.movies > 0) "Films and shows" else if (s.shows > 0) "Shows" else "Films") to nf.format(s.titles))
        if (s.rated >= 5) add("Titles rated" to nf.format(s.rated))
        else if (s.episodes > 0) add("Episodes" to nf.format(s.episodes))
        when {
            s.finishedPercent != null -> add("Shows finished" to "${s.finishedPercent}%")
            s.longestStreak >= 3 -> add("Longest streak" to "${s.longestStreak} days")
            s.episodes > 0 && s.rated >= 5 -> add("Episodes" to nf.format(s.episodes))
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = EDGE), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.forEach { (label, value) ->
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.06f)).padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = NuvioTheme.colors.TextSecondary, maxLines = 1)
                Text(value, style = MaterialTheme.typography.headlineSmall, color = NuvioTheme.colors.TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TasteGenres(s: NuvioCTasteStats, accent: Color, compact: Boolean, onGenre: (String) -> Unit) {
    SectionTitle("Top genres")
    if (compact) {
        Text(s.genres.take(3).joinToString(" · ") { it.name }, style = MaterialTheme.typography.bodyLarge, color = NuvioTheme.colors.TextPrimary)
        return
    }
    val max = s.genres.maxOf { it.percent }.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        s.genres.take(5).forEachIndexed { i, g ->
            Surface(
                onClick = { onGenre(g.name) },
                modifier = Modifier.fillMaxWidth(),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = NuvioTheme.colors.TextSecondary,
                    focusedContainerColor = Color.White.copy(alpha = 0.12f),
                    focusedContentColor = NuvioTheme.colors.TextPrimary
                )
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(g.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(96.dp))
                    val fraction = g.percent.toFloat() / max
                    val color = if (i < 2) accent else Color.White.copy(alpha = 0.32f)
                    Canvas(Modifier.weight(1f).height(8.dp)) {
                        drawRoundRect(color, size = Size(size.width * fraction, size.height), cornerRadius = CornerRadius(size.height / 2))
                    }
                    Text("${g.percent}%", style = MaterialTheme.typography.bodySmall, maxLines = 1, modifier = Modifier.padding(start = 10.dp).width(36.dp))
                }
            }
        }
    }
}

@Composable
private fun TasteRatings(s: NuvioCTasteStats, accent: Color) {
    SectionTitle("How you rate", NuvioCTasteCopy.ratingLine(s))
    val counts = s.ratingCounts
    val max = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    val muted = Color.White.copy(alpha = 0.20f)
    Canvas(Modifier.fillMaxWidth().height(86.dp)) {
        val gap = 5.dp.toPx()
        val w = (size.width - gap * (counts.size - 1)) / counts.size
        counts.forEachIndexed { i, c ->
            val h = (size.height * c / max).coerceAtLeast(2.dp.toPx())
            drawRoundRect(if (i >= 6) accent else muted, topLeft = Offset(i * (w + gap), size.height - h), size = Size(w, h), cornerRadius = CornerRadius(3.dp.toPx()))
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text("½ star", style = MaterialTheme.typography.labelSmall, color = NuvioTheme.colors.TextTertiary)
        Spacer(Modifier.weight(1f))
        Text("5 stars", style = MaterialTheme.typography.labelSmall, color = NuvioTheme.colors.TextTertiary)
    }
}

@Composable
private fun TastePick(s: NuvioCTasteStats, accent: Color) {
    val avg = s.crowdAverage ?: return
    SectionTitle("What you pick")
    Row(verticalAlignment = Alignment.Bottom) {
        Text(String.format(Locale.US, "%.1f", avg), style = MaterialTheme.typography.displaySmall, color = accent, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Text("average crowd rating", style = MaterialTheme.typography.bodyMedium, color = NuvioTheme.colors.TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
    }
    NuvioCTasteCopy.pickLine(s)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = NuvioTheme.colors.TextSecondary) }
}

@Composable
private fun TasteHabits(s: NuvioCTasteStats, accent: Color) {
    SectionTitle("When you watch", NuvioCTasteCopy.habitLine(s) ?: NuvioCTasteCopy.finishLine(s))
    val heat = s.heat
    val max = (heat.maxOrNull() ?: 0).coerceAtLeast(1)
    val empty = Color.White.copy(alpha = 0.07f)
    Canvas(Modifier.fillMaxWidth().height(84.dp)) {
        val cols = 26
        val gap = 3.dp.toPx()
        val cell = minOf((size.width - gap * (cols - 1)) / cols, (size.height - gap * 6) / 7)
        heat.forEachIndexed { i, c ->
            val col = i / 7
            val row = i % 7
            val color = if (c == 0) empty else accent.copy(alpha = 0.35f + 0.65f * c / max)
            drawRoundRect(color, topLeft = Offset(col * (cell + gap), row * (cell + gap)), size = Size(cell, cell), cornerRadius = CornerRadius(2.dp.toPx()))
        }
    }
    if (s.habitLine() != null) NuvioCTasteCopy.finishLine(s)?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = NuvioTheme.colors.TextSecondary, modifier = Modifier.padding(top = 6.dp))
    }
}

private fun NuvioCTasteStats.habitLine(): String? = NuvioCTasteCopy.habitLine(this)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TasteDecades(s: NuvioCTasteStats, accent: Color, onDecade: (Int) -> Unit) {
    SectionTitle("Decades")
    val max = s.decades.maxOf { it.second }.coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        s.decades.takeLast(8).forEach { (decade, count) ->
            Surface(
                onClick = { onDecade(decade) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = NuvioTheme.colors.TextTertiary,
                    focusedContainerColor = Color.White.copy(alpha = 0.10f),
                    focusedContentColor = NuvioTheme.colors.TextPrimary
                )
            ) {
                Column(Modifier.fillMaxSize().padding(4.dp), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                    val color = if (decade == s.topDecade) accent else Color.White.copy(alpha = 0.32f)
                    Canvas(Modifier.fillMaxWidth().weight(1f)) {
                        val h = (size.height * count / max).coerceAtLeast(2.dp.toPx())
                        drawRoundRect(color, topLeft = Offset(0f, size.height - h), size = Size(size.width, h), cornerRadius = CornerRadius(3.dp.toPx()))
                    }
                    Text(NuvioCTasteCopy.decade(decade), style = MaterialTheme.typography.labelSmall, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TastePeople(s: NuvioCTasteStats, onOpenPerson: (Int, String, Boolean) -> Unit) {
    Column {
        Column(Modifier.padding(horizontal = EDGE)) { SectionTitle("Your people", NuvioCTasteCopy.peopleLine(s)) }
        LazyRow(contentPadding = PaddingValues(horizontal = EDGE), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            items(s.people, key = { it.person.id }) { p -> TastePersonCard(p, onOpenPerson) }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TastePersonCard(p: NuvioCTastePersonStat, onOpenPerson: (Int, String, Boolean) -> Unit) {
    val context = LocalContext.current
    Column(Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = { onOpenPerson(p.person.id, p.person.name, p.director) },
            modifier = Modifier.size(80.dp),
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
            colors = ClickableSurfaceDefaults.colors(containerColor = Color.White.copy(alpha = 0.08f), focusedContainerColor = Color.White.copy(alpha = 0.08f)),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = CircleShape))
        ) {
            val photo = p.person.photo
            if (photo != null) {
                val request = remember(photo) { ImageRequest.Builder(context).data(photo).size(160, 160).crossfade(200).build() }
                AsyncImage(model = request, contentDescription = p.person.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(p.person.name.split(' ').mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString(""), style = MaterialTheme.typography.titleMedium, color = NuvioTheme.colors.TextSecondary)
                }
            }
        }
        Text(p.person.name, style = MaterialTheme.typography.bodySmall, color = NuvioTheme.colors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        Text(if (p.director) "Director · ${p.count}" else "${p.count} titles", style = MaterialTheme.typography.labelSmall, color = NuvioTheme.colors.TextTertiary, maxLines = 1)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TasteButton(label: String, accent: Color, primary: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.height(44.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (primary) accent.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.10f),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black
        )
    ) {
        Row(Modifier.fillMaxHeight().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(com.nuvio.tv.ui.components.NuvioCIcons.Dice, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TasteShelf(title: String, line: String, items: List<NuvioCTasteShelfItem>, onOpenTitle: (String, String) -> Unit) {
    val context = LocalContext.current
    Column {
        Column(Modifier.padding(horizontal = EDGE)) { SectionTitle(title, line) }
        LazyRow(contentPadding = PaddingValues(horizontal = EDGE, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(items, key = { it.id }) { item ->
                Column(Modifier.width(120.dp)) {
                    Card(
                        onClick = { onOpenTitle(item.id, item.type) },
                        modifier = Modifier.width(120.dp).height(180.dp),
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
                    Text(item.name, style = MaterialTheme.typography.bodySmall, color = NuvioTheme.colors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun TasteEmpty(accent: Color, onStart: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = EDGE, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(Icons.Default.Insights, contentDescription = null, tint = NuvioTheme.colors.TextTertiary, modifier = Modifier.size(40.dp))
        Text(NuvioCTasteCopy.empty(), style = MaterialTheme.typography.bodyLarge, color = NuvioTheme.colors.TextSecondary)
        TasteButton("Start with Roulette", accent, primary = true, onClick = onStart)
    }
}
