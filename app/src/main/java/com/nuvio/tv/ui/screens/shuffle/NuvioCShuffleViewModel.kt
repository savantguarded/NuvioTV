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

@Immutable
data class NuvioCShuffleUi(
    val phase: NuvioCShufflePhase = NuvioCShufflePhase.MOOD,
    val kind: NuvioCShuffleKind = NuvioCShuffleKind.MOVIES,
    val mood: NuvioCShuffleMood = NuvioCShuffleMood.ANY,
    val unwatched: Boolean = true,
    /** Three posters per tile. */
    val fans: Map<NuvioCShuffleKind, List<String>> = emptyMap(),
    val pick: NuvioCShufflePick? = null,
    val spin: NuvioCReelSpin? = null,
    /** True once the reel has stopped on the pick. */
    val landed: Boolean = false,
    /** Shown on the mood screen when nothing fits the choices. */
    val nothingLeft: Boolean = false,
    val sourcesOn: Int = 0
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
    private val prefs: NuvioCShufflePrefs
) : ViewModel() {

    private val _ui = MutableStateFlow(fromPrefs(NuvioCShuffleUi()))
    val ui: StateFlow<NuvioCShuffleUi> = _ui.asStateFlow()

    private var rollJob: Job? = null
    private var token = 0
    private var titleOpened = false

    private fun fromPrefs(ui: NuvioCShuffleUi): NuvioCShuffleUi {
        val c = prefs.config.value
        return ui.copy(kind = c.kind, mood = c.mood, unwatched = c.unwatched, sourcesOn = NuvioCShuffleSource.entries.count { c.weight(it) > 0 })
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
        _ui.update { fromPrefs(it).copy(phase = NuvioCShufflePhase.MOOD, pick = null, spin = null, landed = false, nothingLeft = false) }
        loadFans()
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
        val on = !_ui.value.unwatched
        prefs.setUnwatched(on)
        _ui.update { it.copy(unwatched = on, nothingLeft = false) }
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
                spin = NuvioCReelSpin(-myToken, listOf(from), 0) // still frame while the pick is found
            )
        }
        rollJob = viewModelScope.launch {
            when (val outcome = pool.roll(state.kind, state.mood, state.unwatched)) {
                is NuvioCShuffleOutcome.Picked -> {
                    val pick = outcome.pick
                    pick.item.background?.let { context.imageLoader.enqueue(shuffleBackdropRequest(context, it)) }
                    val strip = pool.posters(state.kind, 16)
                    _ui.update {
                        it.copy(pick = pick, spin = nuvioCReelSpin(myToken, pick.item.poster, strip, from))
                    }
                }
                NuvioCShuffleOutcome.NothingLeft -> _ui.update {
                    it.copy(phase = NuvioCShufflePhase.MOOD, nothingLeft = true, spin = null, pick = null)
                }
            }
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
