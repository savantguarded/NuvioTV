package com.nuvio.tv.ui.screens.shuffle

// [fork] Nuvio C round 28 `shuffle_page`: state of the Shuffle page (mood screen -> reel -> result).

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nuvio.tv.data.nuvioc.NuvioCShuffleKind
import com.nuvio.tv.data.nuvioc.NuvioCShuffleMood
import com.nuvio.tv.data.nuvioc.NuvioCShuffleOutcome
import com.nuvio.tv.data.nuvioc.NuvioCShufflePick
import com.nuvio.tv.data.nuvioc.NuvioCShufflePool
import com.nuvio.tv.data.nuvioc.NuvioCShufflePrefs
import com.nuvio.tv.data.nuvioc.NuvioCShuffleSource
import com.nuvio.tv.ui.components.NuvioCReelSpin
import com.nuvio.tv.ui.components.nuvioCReelSpin
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class NuvioCShufflePhase { MOOD, ROLLING, RESULT }

/**
 * [fork] round 40 roulette_empty: why a spin came back empty. [current] = titles at the chosen popularity before the
 * watched / recently-seen filters; [deeper] = the first lower popularity level that has titles (null = none).
 */
@Immutable
data class NuvioCRouletteEmpty(
    val current: Int,
    val deeper: com.nuvio.tv.data.nuvioc.NuvioCRoulettePool?,
    val deeperCount: Int
)

@Immutable
data class NuvioCShuffleUi(
    val phase: NuvioCShufflePhase = NuvioCShufflePhase.MOOD,
    val kind: NuvioCShuffleKind = NuvioCShuffleKind.MOVIES,
    val mood: NuvioCShuffleMood = NuvioCShuffleMood.ANY,
    val unwatched: Boolean = true,
    /** [fork] round 39 roulette_watched_cycle: Unwatched -> Watched -> All. */
    val watchFilter: com.nuvio.tv.data.nuvioc.NuvioCWatchFilter = com.nuvio.tv.data.nuvioc.NuvioCWatchFilter.UNWATCHED,
    /** Three posters per tile. */
    val fans: Map<NuvioCShuffleKind, List<String>> = emptyMap(),
    val pick: NuvioCShufflePick? = null,
    val spin: NuvioCReelSpin? = null,
    /** True once the reel has stopped on the pick. */
    val landed: Boolean = false,
    /** Shown on the mood screen when nothing fits the choices. */
    val nothingLeft: Boolean = false,
    /** [fork] round 40 roulette_empty: the message shown after an empty spin (null = none). */
    val empty: NuvioCRouletteEmpty? = null,
    /** [fork] round 40 roulette_popularity: titles per popularity level for the current filters (Refine shows them with an Origin set). */
    val popularityCounts: Map<com.nuvio.tv.data.nuvioc.NuvioCRoulettePool, Int> = emptyMap(),
    val sourcesOn: Int = 0,
    /** [fork] round 35 roulette_ratings: MDBList ratings for the current pick only (null until loaded / none). */
    val ratings: com.nuvio.tv.domain.model.MDBListRatings? = null,
    val ratingsFor: String? = null,
    val ratingOrder: List<String> = com.nuvio.tv.domain.model.MDBListSettings.DEFAULT_RATING_ORDER,
    /** [fork] round 36 roulette_copy: visit number (picks the rotating lines), spins this visit, pool size. */
    val visit: Int = 0,
    val spins: Int = 0,
    val poolSize: Int? = null,
    /** [fork] round 37 roulette_refine: Refine panel choices (pool size included). */
    val refine: com.nuvio.tv.data.nuvioc.NuvioCRouletteRefine = com.nuvio.tv.data.nuvioc.NuvioCRouletteRefine()
)

/** The backdrop request used both to fetch it early (during the reel) and to draw it, so it's a memory hit. */
internal fun shuffleBackdropRequest(context: Context, url: String): ImageRequest =
    ImageRequest.Builder(context.applicationContext)
        .data(url)
        .size(1280, 720)
        .memoryCacheKey("nuvioc_shuffle_bd_$url")
        .diskCacheKey(url)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .crossfade(false)
        .build()

@HiltViewModel
class NuvioCShuffleViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pool: NuvioCShufflePool,
    private val prefs: NuvioCShufflePrefs,
    private val mdbList: com.nuvio.tv.data.repository.MDBListRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(fromPrefs(NuvioCShuffleUi()))
    val ui: StateFlow<NuvioCShuffleUi> = _ui.asStateFlow()

    private var rollJob: Job? = null
    private var token = 0
    private var titleOpened = false
    private var ratingsJob: Job? = null
    private var visit = kotlin.random.Random.nextInt(1000) // [fork] round 36: consecutive visits never repeat a line
    private val refineByKind = HashMap<NuvioCShuffleKind, com.nuvio.tv.data.nuvioc.NuvioCRouletteRefine>() // [fork] round 39

    // [fork] round 35 roulette_ratings: one MDBList lookup for the title the reel lands on, never for the pool or
    // the posters flying past. Same call and 12 h disk cache as the details page, so opening it afterwards is free.
    private fun loadRatings(pick: NuvioCShufflePick) {
        ratingsJob?.cancel()
        if (!com.nuvio.tv.NuvioCFeatures.ROULETTE_RATINGS) return
        val item = pick.item
        val key = item.imdbId?.takeIf { it.isNotBlank() } ?: item.tmdbId?.let { "tmdb:$it" } ?: item.id
        if (_ui.value.ratingsFor == key && _ui.value.ratings != null) return
        _ui.update { it.copy(ratings = null, ratingsFor = key) }
        ratingsJob = viewModelScope.launch {
            val ratings = runCatching { mdbList.nuvioCRatingsForItem(key, item.type) }.getOrNull()?.takeUnless { it.isEmpty() }
            val order = runCatching { mdbList.nuvioCRatingOrder() }.getOrNull()?.takeIf { it.isNotEmpty() }
            _ui.update {
                if (it.ratingsFor != key) it
                else it.copy(ratings = ratings, ratingOrder = order ?: it.ratingOrder)
            }
        }
    }

    private fun fromPrefs(ui: NuvioCShuffleUi): NuvioCShuffleUi {
        val c = prefs.config.value
        return ui.copy(kind = c.kind, mood = c.mood, unwatched = c.watchFilter == com.nuvio.tv.data.nuvioc.NuvioCWatchFilter.UNWATCHED, watchFilter = c.watchFilter, sourcesOn = NuvioCShuffleSource.entries.count { c.weight(it) > 0 }, refine = c.refine)
    }

    /** Each time the page comes into view. Back from a title's details keeps the result; otherwise start at the mood screen. */
    fun onScreenShown() {
        if (titleOpened) {
            titleOpened = false
            return
        }
        rollJob?.cancel()
        pool.beginVisit()
        prefs.setMood(NuvioCShuffleMood.ANY) // [fork] roulette_page: the mood starts fresh on every visit
        if (com.nuvio.tv.NuvioCFeatures.ROULETTE_REFINE_RESET) {
            // [fork] round 39: Refine starts clean on every visit (pool size is a setting and stays), and movies and
            // shows keep their own Refine choices while you're here, so a show's filters never ride along on a film spin.
            refineByKind.clear()
            val current = prefs.config.value.refine
            if (current.active > 0) prefs.setRefine(current.cleared())
        }
        visit++ // [fork] round 36 roulette_copy
        _ui.update { fromPrefs(it).copy(phase = NuvioCShufflePhase.MOOD, pick = null, spin = null, landed = false, nothingLeft = false, empty = null, visit = visit, spins = 0, poolSize = null) }
        loadFans()
        // [fork] round 39 taste_page: "Spin my taste" (or a genre / decade on the Taste page) lands here with a preset.
        NuvioCRoulettePreset.take()?.let { preset ->
            prefs.setKind(preset.kind)
            prefs.setMood(preset.mood)
            val refine = prefs.config.value.refine.cleared().copy(decade = preset.decade)
            prefs.setRefine(refine)
            _ui.update { it.copy(kind = preset.kind, mood = preset.mood, refine = refine) }
            roll()
        }
    }

    private fun loadFans() {
        viewModelScope.launch {
            if (!pool.hasPool()) pool.warmUp()
            // Wait for the pool (built in the background) without blocking the screen.
            repeat(60) {
                if (pool.hasPool()) {
                    val fans = com.nuvio.tv.data.nuvioc.nuvioCShuffleKinds.associateWith { pool.posters(it, 3) } // [fork] roulette_anime
                    _ui.update { it.copy(fans = fans) }
                    return@launch
                }
                kotlinx.coroutines.delay(1_000)
            }
        }
    }

    fun refreshSourcesCount() = _ui.update { fromPrefs(it) }

    fun chooseKind(kind: NuvioCShuffleKind) {
        if (com.nuvio.tv.NuvioCFeatures.ROULETTE_REFINE_RESET && kind != _ui.value.kind) {
            // [fork] round 39: each type keeps its own Refine for this visit
            val now = _ui.value.refine
            refineByKind[_ui.value.kind] = now
            val next = refineByKind[kind] ?: now.cleared()
            if (next != now) prefs.setRefine(next)
            _ui.update { it.copy(refine = next) }
        }
        prefs.setKind(kind)
        _ui.update { it.copy(kind = kind) }
        roll()
    }

    fun setMood(mood: NuvioCShuffleMood, reroll: Boolean = false) {
        prefs.setMood(mood)
        _ui.update { it.copy(mood = mood, nothingLeft = false) }
        if (reroll) roll()
    }

    fun toggleUnwatched(reroll: Boolean = false) {
        if (com.nuvio.tv.NuvioCFeatures.ROULETTE_WATCHED_CYCLE) {
            // [fork] round 39: Unwatched -> Watched -> All
            val next = _ui.value.watchFilter.next()
            prefs.setWatchFilter(next)
            _ui.update { it.copy(watchFilter = next, unwatched = next == com.nuvio.tv.data.nuvioc.NuvioCWatchFilter.UNWATCHED, nothingLeft = false) }
            if (reroll) roll()
            return
        }
        val on = !_ui.value.unwatched
        prefs.setUnwatched(on)
        _ui.update { it.copy(unwatched = on, nothingLeft = false) }
        if (reroll) roll()
    }

    /** [fork] round 37 roulette_refine: new Refine choices; on the result screen they roll again. */
    fun setRefine(refine: com.nuvio.tv.data.nuvioc.NuvioCRouletteRefine, reroll: Boolean = false) {
        if (refine == _ui.value.refine) return
        prefs.setRefine(refine)
        _ui.update { it.copy(refine = refine, nothingLeft = false, empty = null) }
        if (reroll) roll()
    }

    fun cycleKind() {
        val kinds = com.nuvio.tv.data.nuvioc.nuvioCShuffleKinds // [fork] roulette_anime
        val next = kinds[(kinds.indexOf(_ui.value.kind) + 1).mod(kinds.size)]
        chooseKind(next)
    }

    fun cycleMood() {
        val moods = com.nuvio.tv.data.nuvioc.nuvioCShuffleMoods // [fork] roulette_v2
        val next = moods[(moods.indexOf(_ui.value.mood) + 1).mod(moods.size)]
        setMood(next, reroll = true)
    }

    /** Rolls with the current choices. The reel shows the poster on screen now and then lands on the pick. */
    fun roll() {
        rollJob?.cancel()
        val state = _ui.value
        val myToken = ++token
        val from = state.pick?.item?.poster ?: state.fans[state.kind]?.firstOrNull()
        _ui.update {
            it.copy(
                phase = NuvioCShufflePhase.ROLLING,
                landed = false,
                nothingLeft = false,
                empty = null,
                spin = NuvioCReelSpin(-myToken, listOf(from), 0), // still frame while the pick is found
                spins = it.spins + 1, // [fork] round 36 roulette_copy
                poolSize = null
            )
        }
        if (com.nuvio.tv.NuvioCFeatures.ROULETTE_COPY) viewModelScope.launch {
            val n = pool.poolSize(state.kind, state.mood, state.refine)
            _ui.update { if (token == myToken) it.copy(poolSize = n) else it }
        }
        rollJob = viewModelScope.launch {
            val watchedOnly = com.nuvio.tv.NuvioCFeatures.ROULETTE_WATCHED_CYCLE && state.watchFilter == com.nuvio.tv.data.nuvioc.NuvioCWatchFilter.WATCHED
            when (val outcome = pool.roll(state.kind, state.mood, state.unwatched, state.refine, watchedOnly)) {
                is NuvioCShuffleOutcome.Picked -> {
                    val pick = outcome.pick
                    pick.item.background?.let { context.imageLoader.enqueue(shuffleBackdropRequest(context, it)) }
                    val strip = pool.posters(state.kind, 16)
                    _ui.update {
                        it.copy(pick = pick, spin = nuvioCReelSpin(myToken, pick.item.poster, strip, from))
                    }
                    loadRatings(pick) // [fork] round 35
                }
                NuvioCShuffleOutcome.NothingLeft -> {
                    _ui.update { it.copy(phase = NuvioCShufflePhase.MOOD, nothingLeft = true, spin = null, pick = null) }
                    if (com.nuvio.tv.NuvioCFeatures.ROULETTE_EMPTY_NOTICE && state.kind != NuvioCShuffleKind.EPISODES) {
                        val notice = explainEmpty(state)
                        _ui.update { if (token == myToken) it.copy(empty = notice) else it }
                    }
                }
            }
        }
    }

    /** [fork] round 40 roulette_empty: counts at the chosen popularity and the first lower level with titles (TMDB totals, cached 24 h). */
    private suspend fun explainEmpty(state: NuvioCShuffleUi): NuvioCRouletteEmpty {
        val levels = com.nuvio.tv.data.nuvioc.NuvioCRoulettePool.entries
            .filter { com.nuvio.tv.NuvioCFeatures.ROULETTE_POPULARITY || it != com.nuvio.tv.data.nuvioc.NuvioCRoulettePool.CUTS }
        val current = pool.poolSize(state.kind, state.mood, state.refine) ?: 0
        var deeper: com.nuvio.tv.data.nuvioc.NuvioCRoulettePool? = null
        var deeperCount = 0
        if (current == 0 && com.nuvio.tv.NuvioCFeatures.ROULETTE_POOL_SIZE) {
            for (p in levels.dropWhile { it != state.refine.pool }.drop(1)) {
                val n = pool.poolSize(state.kind, state.mood, state.refine.copy(pool = p)) ?: 0
                if (n > 0) { deeper = p; deeperCount = n; break }
            }
        }
        return NuvioCRouletteEmpty(current, deeper, deeperCount)
    }

    fun dismissEmpty() = _ui.update { it.copy(empty = null) }

    /** [fork] round 40 roulette_popularity: load the per-level counts when Refine opens with an Origin set. */
    fun loadPopularityCounts() {
        val state = _ui.value
        if (!com.nuvio.tv.NuvioCFeatures.ROULETTE_POPULARITY || state.refine.region == com.nuvio.tv.data.nuvioc.NuvioCRouletteRegion.ANY ||
            state.kind == NuvioCShuffleKind.EPISODES) {
            _ui.update { it.copy(popularityCounts = emptyMap()) }
            return
        }
        _ui.update { it.copy(popularityCounts = emptyMap()) }
        viewModelScope.launch {
            val counts = com.nuvio.tv.data.nuvioc.NuvioCRoulettePool.entries.associateWith { p ->
                pool.poolSize(state.kind, state.mood, state.refine.copy(pool = p)) ?: 0
            }
            _ui.update { if (it.refine == state.refine && it.kind == state.kind && it.mood == state.mood) it.copy(popularityCounts = counts) else it }
        }
    }

    fun onLanded(spinToken: Int) {
        if (spinToken != token) return
        _ui.update { it.copy(phase = NuvioCShufflePhase.RESULT, landed = true) }
    }

    /** Back on the reel or the result returns to the mood screen. */
    fun backToMood() {
        rollJob?.cancel()
        token++
        _ui.update { it.copy(phase = NuvioCShufflePhase.MOOD, landed = false, spin = null) }
    }

    /** The details page is opening for the pick; coming back keeps the result. */
    fun onOpenTitle() {
        titleOpened = true
    }
}

/** [fork] round 39 taste_page: a one-shot Roulette starting point handed over by the Taste page. */
object NuvioCRoulettePreset {
    data class Preset(
        val kind: NuvioCShuffleKind = NuvioCShuffleKind.MOVIES,
        val mood: NuvioCShuffleMood = NuvioCShuffleMood.ANY,
        val decade: com.nuvio.tv.data.nuvioc.NuvioCRouletteDecade = com.nuvio.tv.data.nuvioc.NuvioCRouletteDecade.ANY
    )

    @Volatile private var pending: Preset? = null

    fun set(preset: Preset) { pending = preset }
    internal fun take(): Preset? = pending.also { pending = null }

    /** The closest Roulette mood for a Taste genre name. */
    fun moodFor(genre: String?): NuvioCShuffleMood = when (genre?.lowercase()) {
        "crime", "thriller", "action" -> NuvioCShuffleMood.GRIPPING
        "comedy" -> NuvioCShuffleMood.FUNNY
        "horror", "war" -> NuvioCShuffleMood.DARK
        "sci-fi", "mystery", "fantasy" -> NuvioCShuffleMood.MIND_BENDING
        "romance" -> NuvioCShuffleMood.ROMANTIC
        "animation" -> NuvioCShuffleMood.ANIMATED
        "history" -> NuvioCShuffleMood.TRUE_STORY
        "western" -> NuvioCShuffleMood.WESTERN
        "documentary" -> NuvioCShuffleMood.DOCUMENTARY
        "adventure" -> NuvioCShuffleMood.ADVENTURE
        "family" -> NuvioCShuffleMood.FAMILY
        "music" -> NuvioCShuffleMood.MUSIC
        else -> NuvioCShuffleMood.ANY
    }

    fun decadeFor(year: Int?): com.nuvio.tv.data.nuvioc.NuvioCRouletteDecade =
        com.nuvio.tv.data.nuvioc.NuvioCRouletteDecade.entries.firstOrNull { d ->
            year != null && d.from != null && d.to != null && year in d.from..d.to
        } ?: if (year != null && year < 1960) com.nuvio.tv.data.nuvioc.NuvioCRouletteDecade.EARLIER else com.nuvio.tv.data.nuvioc.NuvioCRouletteDecade.ANY
}
