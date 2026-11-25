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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.data.nuvioc.NuvioCTastePersonStat
import com.nuvio.tv.data.nuvioc.NuvioCTasteRange
import com.nuvio.tv.data.nuvioc.NuvioCTasteRepository
import com.nuvio.tv.data.nuvioc.NuvioCTasteShelfItem
import com.nuvio.tv.data.nuvioc.NuvioCTasteState
import com.nuvio.tv.data.nuvioc.NuvioCTasteStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

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

    // ---- [fork] round 52 taste_tabs -------------------------------------------------------------------------
    var tasteTab by mutableStateOf(0)
    private val _facts = MutableStateFlow<com.nuvio.tv.data.nuvioc.NuvioCTasteFacts?>(null)
    val facts: StateFlow<com.nuvio.tv.data.nuvioc.NuvioCTasteFacts?> = _facts.asStateFlow()
    private val _wrappedYears = MutableStateFlow<List<Int>>(emptyList())
    val wrappedYears: StateFlow<List<Int>> = _wrappedYears.asStateFlow()
    /** [fork] round 57 taste_v3: the Wrapped years have been read at least once this visit (so the tab list is final). */
    private val _yearsLoaded = MutableStateFlow(false)
    val yearsLoaded: StateFlow<Boolean> = _yearsLoaded.asStateFlow()
    private val _wrapped = MutableStateFlow<com.nuvio.tv.data.nuvioc.NuvioCTasteWrapped?>(null)
    val wrapped: StateFlow<com.nuvio.tv.data.nuvioc.NuvioCTasteWrapped?> = _wrapped.asStateFlow()

    fun loadFacts(range: NuvioCTasteRange, month: java.time.YearMonth?) {
        viewModelScope.launch { _facts.value = repository.facts(range, month) }
    }

    fun loadWrapped(year: Int?) {
        viewModelScope.launch {
            val years = repository.wrappedYears()
            _wrappedYears.value = years
            val y = year ?: years.firstOrNull()
            _wrapped.value = y?.let { repository.wrapped(it) }
        }
    }

    // ---- [fork] round 53 taste_tabs_v2 -----------------------------------------------------------------------
    private val _overview = MutableStateFlow<com.nuvio.tv.data.nuvioc.NuvioCTasteOverview?>(null)
    val overview: StateFlow<com.nuvio.tv.data.nuvioc.NuvioCTasteOverview?> = _overview.asStateFlow()
    private val _strip = MutableStateFlow<com.nuvio.tv.data.nuvioc.NuvioCTasteStatsStrip?>(null)
    val strip: StateFlow<com.nuvio.tv.data.nuvioc.NuvioCTasteStatsStrip?> = _strip.asStateFlow()
    private val _year = MutableStateFlow<com.nuvio.tv.data.nuvioc.NuvioCTasteYear?>(null)
    val year: StateFlow<com.nuvio.tv.data.nuvioc.NuvioCTasteYear?> = _year.asStateFlow()
    /** The range picked on Overview / Discover / Stats, kept for the whole visit (all three tabs share it). */
    var tasteRange by mutableStateOf(NuvioCTasteRange.ALL)
    var tasteMonth by mutableStateOf<java.time.YearMonth?>(null)
    var tasteYear by mutableStateOf<Int?>(null)
    /** The Discover item last opened, so Back lands on it again. */
    var discoverFocusKey: String? = null
    var discoverListState = androidx.compose.foundation.lazy.LazyListState()

    // ---- [fork] round 53b taste_scope: "all", "y:2026" or "m:2026-11", shared by Overview / Discover / Stats ----
    var tasteScopeKey by mutableStateOf(nuvioCDefaultScope())
    /** [fork] round 58: Taste opens on this month (Month first, All time last). */
    fun nuvioCDefaultScope(): String = if (com.nuvio.tv.NuvioCFeatures.TASTE_V58) "m:" + java.time.YearMonth.now() else "all"
    fun nuvioCOpenFresh() { NuvioCTasteReturn.key = null; closeTasteCard(); if (com.nuvio.tv.NuvioCFeatures.TASTE_V58) { tasteScopeKey = nuvioCDefaultScope(); discoverFocusKey = null } } // [fork] round 63: a fresh entry forgets the return row
    private val _scope = MutableStateFlow<com.nuvio.tv.data.nuvioc.NuvioCTasteScopeData?>(null)
    val scope: StateFlow<com.nuvio.tv.data.nuvioc.NuvioCTasteScopeData?> = _scope.asStateFlow()
    private var scopeJob: kotlinx.coroutines.Job? = null

    // ---- [fork] round 65 TASTE_CARDS_OPEN: the page a card opens (kept across a title opened from it) ----
    data class NuvioCCardOpen(val card: com.nuvio.tv.data.nuvioc.NuvioCTasteCard, val hint: String?, val crumb: String, val scopeLabel: String, val returnKey: String)
    var openCard by mutableStateOf<NuvioCCardOpen?>(null)
    private val _cardPage = MutableStateFlow<com.nuvio.tv.data.nuvioc.NuvioCTasteCardPage?>(null)
    val cardPage: StateFlow<com.nuvio.tv.data.nuvioc.NuvioCTasteCardPage?> = _cardPage.asStateFlow()
    private var cardJob: kotlinx.coroutines.Job? = null

    /** Opens [card] over the page's scope (Wrapped: its year). [returnKey] is the card / line focus lands on after Back. */
    fun openTasteCard(card: com.nuvio.tv.data.nuvioc.NuvioCTasteCard, hint: String?, crumb: String, wrapped: Boolean, scopeLabel: String, returnKey: String) {
        openCard = NuvioCCardOpen(card, hint, crumb, scopeLabel, returnKey)
        _cardPage.value = null
        cardJob?.cancel()
        val key = if (wrapped) tasteYear?.let { "y:$it" } ?: "all" else tasteScopeKey
        cardJob = viewModelScope.launch { _cardPage.value = repository.cardPage(card, hint, nuvioCWindow(key)) }
    }

    fun closeTasteCard() { cardJob?.cancel(); openCard = null; _cardPage.value = null }

    private fun nuvioCWindow(key: String): LongRange? {
        val zone = java.time.ZoneId.systemDefault()
        return when {
            key.startsWith("y:") -> key.drop(2).toIntOrNull()?.let { y ->
                java.time.LocalDate.of(y, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()..
                    (java.time.LocalDate.of(y + 1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli() - 1)
            }
            key.startsWith("m:") -> runCatching { java.time.YearMonth.parse(key.drop(2)) }.getOrNull()?.let { com.nuvio.tv.data.nuvioc.tasteMonthWindow(it, zone) }
            else -> null
        }
    }

    fun loadScope(key: String) {
        scopeJob?.cancel()
        scopeJob = viewModelScope.launch {
            val zone = java.time.ZoneId.systemDefault()
            val window = when {
                key.startsWith("y:") -> key.drop(2).toIntOrNull()?.let { y ->
                    java.time.LocalDate.of(y, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()..
                        (java.time.LocalDate.of(y + 1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli() - 1)
                }
                key.startsWith("m:") -> runCatching { java.time.YearMonth.parse(key.drop(2)) }.getOrNull()?.let { com.nuvio.tv.data.nuvioc.tasteMonthWindow(it, zone) }
                else -> null
            }
            _scope.value = repository.scope(window)
        }
    }

    fun loadRound53(range: NuvioCTasteRange, month: java.time.YearMonth?) {
        viewModelScope.launch {
            _overview.value = repository.overview(range, month)
            _strip.value = repository.statsStrip(range, month)
        }
    }

    fun loadYear(year: Int?) {
        viewModelScope.launch {
            val years = repository.wrappedYears()
            _wrappedYears.value = years
            _yearsLoaded.value = true
            val y = year?.takeIf { it in years } ?: years.firstOrNull()
            _year.value = y?.let { repository.year(it) }
        }
    }

    fun open(force: Boolean = false) {
        // [fork] round 42 taste_profile_strict: nothing from another profile's visit is reused on this page
        val profileNow = profiles.activeProfileId.value
        if (com.nuvio.tv.NuvioCFeatures.TASTE_PROFILE_STRICT && openedProfile != profileNow) {
            if (openedProfile != null) {
                opened = false
                _rows.value = emptyList(); _review.value = null; _panel.value = null; _monthData.value = null; _live.value = null
                _facts.value = null; _wrapped.value = null; _wrappedYears.value = emptyList(); _yearsLoaded.value = false; tasteTab = 0 // [fork] round 52
                _overview.value = null; _strip.value = null; _year.value = null; _scope.value = null; tasteScopeKey = nuvioCDefaultScope(); tasteRange = NuvioCTasteRange.ALL; tasteMonth = null; tasteYear = null; discoverFocusKey = null; discoverListState = androidx.compose.foundation.lazy.LazyListState() // [fork] round 53
                memories.clear(); spotlightCache.clear(); changedIds = emptySet(); lastArea = null; monthFrom = null; panelFrom = null
                lensFor = null; lens = null; _monthTitles.value = emptyList()
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

    // ---- [fork] round 47 taste_rotate ---------------------------------------------------------------------
    private var orderFor: Long? = null
    private var order: List<String> = emptyList()

    /** Where a section kind sits under Spotlight this visit (lower = higher up). New order each visit, per profile. */
    fun sectionRank(kind: String): Int {
        if (orderFor != seed) {
            val profile = profiles.activeProfileId.value
            val lastFirst = prefs.getString("p$profile:firstSection", null)
            val lastOrder = prefs.getString("p$profile:sectionOrder", null)
            order = tasteRotateOrder(TASTE_ROTATING_KINDS, seed, lastFirst, lastOrder)
            prefs.edit().putString("p$profile:firstSection", order.first()).putString("p$profile:sectionOrder", order.joinToString(",")).apply()
            orderFor = seed
        }
        val i = order.indexOf(kind)
        return if (i >= 0) i else order.size
    }

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
        if (month == null) { _monthData.value = null; _monthTitles.value = emptyList(); return }
        viewModelScope.launch { _monthData.value = repository.month(month) }
        // [fork] round 42b taste_month_titles: the titles watched that month, most recent first
        if (com.nuvio.tv.NuvioCFeatures.TASTE_MONTH_TITLES) viewModelScope.launch {
            _monthTitles.value = emptyList()
            val detail = runCatching { repository.recapDetail(month) }.getOrNull() ?: return@launch
            val window = com.nuvio.tv.data.nuvioc.tasteMonthWindow(month, java.time.ZoneId.systemDefault())
            val items = runCatching { repository.panel(detail.keys, window) }.getOrDefault(emptyList())
            _monthTitles.value = items.sortedByDescending { it.lastWatched }.map {
                com.nuvio.tv.data.nuvioc.NuvioCTasteShelfItem(
                    id = it.id, type = it.type, name = it.name, poster = it.poster,
                    note = it.episodes?.let { e -> "$e ${if (e == 1) "episode" else "episodes"}" } ?: it.year?.toString()
                )
            }
        }
    }
    private val _monthTitles = MutableStateFlow<List<com.nuvio.tv.data.nuvioc.NuvioCTasteShelfItem>>(emptyList())
    val monthTitles: StateFlow<List<com.nuvio.tv.data.nuvioc.NuvioCTasteShelfItem>> = _monthTitles.asStateFlow()

    // ---- [fork] round 42b taste_people_lenses ------------------------------------------------------------
    data class PeopleLens(val id: String, val title: String, val people: List<com.nuvio.tv.data.nuvioc.NuvioCTastePersonStat>)
    private var lensFor: Long? = null
    private var lens: PeopleLens? = null

    /** This visit's people row: one lens chosen at random (never the same twice running), from a wider pool. */
    fun peopleLens(state: NuvioCTasteState): PeopleLens? {
        val all = state.all[NuvioCTasteRange.ALL] ?: return null
        if (!com.nuvio.tv.NuvioCFeatures.TASTE_PEOPLE_LENSES) {
            return all.people.takeIf { it.isNotEmpty() }?.let { PeopleLens("all", "Your people", it) }
        }
        if (lensFor == seed) lens?.let { return it }
        val random = java.util.Random(seed xor 0x5EED)
        val pool = all.morePeople.ifEmpty { all.people }
        fun sample(list: List<com.nuvio.tv.data.nuvioc.NuvioCTastePersonStat>, n: Int) =
            list.take(n + 6).shuffled(random).take(n).sortedByDescending { it.count }
        val lenses = listOfNotNull(
            pool.filter { it.count >= 3 }.takeIf { it.size >= 4 }?.let { PeopleLens("all", "Your people", sample(it, 10)) },
            state.all[NuvioCTasteRange.DAYS_90]?.morePeople?.takeIf { it.size >= 4 }?.let { PeopleLens("lately", "Lately on your screen", it.take(10)) },
            state.all[NuvioCTasteRange.YEAR]?.morePeople?.takeIf { it.size >= 4 }?.let { PeopleLens("year", "Your people this year", sample(it, 10)) },
            pool.filter { it.director }.takeIf { it.size >= 4 }?.let { PeopleLens("directors", "Directors you return to", it.take(10)) },
            pool.filter { !it.director }.drop(4).takeIf { it.size >= 4 }?.let { PeopleLens("faces", "Faces you keep seeing", sample(it, 10)) }
        )
        if (lenses.isEmpty()) return all.people.takeIf { it.isNotEmpty() }?.let { PeopleLens("all", "Your people", it) }
        val profile = profiles.activeProfileId.value
        val last = prefs.getString("p$profile:peopleLens", null)
        val choices = lenses.filter { it.id != last }.ifEmpty { lenses }
        val picked = choices[random.nextInt(choices.size)]
        prefs.edit().putString("p$profile:peopleLens", picked.id).apply()
        lensFor = seed
        lens = picked
        return picked
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
        val profile = profiles.activeProfileId.value
        val picked = if (com.nuvio.tv.NuvioCFeatures.TASTE_V3) {
            // [fork] round 46 taste_v3: last visit's cards sit out while enough others exist; changed ones count double
            val lastShown = prefs.getStringSet("p$profile:last_spot:$key", emptySet()).orEmpty()
            val changedNow = all.filter { c -> prefs.getString("p$profile:${c.id}", null)?.let { it != c.value } == true }.map { it.id }.toSet()
            com.nuvio.tv.data.nuvioc.pickTasteSpotlightV3(all, seed xor key.hashCode().toLong(), now.hour, weekend, lastShown, changedNow)
                .also { p -> prefs.edit().putStringSet("p$profile:last_spot:$key", p.map { it.id }.toSet()).apply() }
        } else com.nuvio.tv.data.nuvioc.pickTasteSpotlight(all, seed xor key.hashCode().toLong(), now.hour, weekend)
        // the dot: a card shown before whose value is different now
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
    // [fork] round 53: header row, Overview / Discover / Stats / Wrapped (older Taste layouts removed 2026-11-20)
    // [fork] round 53e taste_bold: weights that really draw bold (the app font is variable)
    if (com.nuvio.tv.NuvioCFeatures.TASTE_BOLD) com.nuvio.tv.ui.theme.NuvioCWeightedFont(calm = com.nuvio.tv.NuvioCFeatures.TASTE_TYPE_CALM) { NuvioCTasteTabsV2(viewModel, onOpenTitle, onOpenPerson, onOpenRoulette) }
    else NuvioCTasteTabsV2(viewModel, onOpenTitle, onOpenPerson, onOpenRoulette)
}

// ---- parts -----------------------------------------------------------------------------------------------
