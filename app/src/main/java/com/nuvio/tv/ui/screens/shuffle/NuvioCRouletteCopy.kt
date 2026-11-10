package com.nuvio.tv.ui.screens.shuffle

// [fork] Nuvio C round 36 `roulette_copy` (Charles approved the canvas "Nuvio C Round 36", Roulette copy sheet):
// lines that rotate per visit (mood screen line, tile hints), spin captions with the real TMDB pool size or the
// mood, a kicker above the result logo, a quiet line after a few spins, and two "nothing left" messages.
// Actions keep their names (Shuffle, Shuffle again, Open). Consecutive visits never repeat a line.

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.nuvio.tv.R
import com.nuvio.tv.data.nuvioc.NuvioCShuffleKind
import com.nuvio.tv.data.nuvioc.NuvioCShuffleMood
import java.text.NumberFormat
import java.util.Locale

internal object NuvioCRouletteCopy {
    private enum class Band { MORNING, AFTERNOON, EVENING, NIGHT, LATE }

    /** Five time bands: morning 5-11, afternoon 12-16, evening 17-20, night 21-23, late 0-4. */
    private fun band(hour: Int): Band = when (hour) {
        in 5..11 -> Band.MORNING
        in 12..16 -> Band.AFTERNOON
        in 17..20 -> Band.EVENING
        in 21..23 -> Band.NIGHT
        else -> Band.LATE
    }

    @Composable
    private fun nowBand(): Band = remember { band(java.time.LocalTime.now().hour) }

    private fun <T> Array<T>.pick(visit: Int): T = this[Math.floorMod(visit, size)]

    /** Line under "Roulette" on the mood screen (or why nothing matched). */
    @Composable
    fun subLine(ui: NuvioCShuffleUi): String = when {
        ui.nothingLeft && ui.unwatched && ui.visit % 2 == 0 -> stringResource(R.string.nuvio_c_roulette_nothing_watched)
        ui.nothingLeft -> stringResource(R.string.nuvio_c_roulette_nothing_dry)
        else -> stringArrayResource(R.array.nuvio_c_roulette_sub).pick(ui.visit)
    }

    @Composable
    fun tileHint(kind: NuvioCShuffleKind, visit: Int): String {
        val arr = when (kind) {
            NuvioCShuffleKind.MOVIES -> when (nowBand()) {
                Band.MORNING -> R.array.nuvio_c_roulette_movies_morning
                Band.AFTERNOON -> R.array.nuvio_c_roulette_movies_afternoon
                Band.EVENING -> R.array.nuvio_c_roulette_movies_evening
                Band.NIGHT -> R.array.nuvio_c_roulette_movies_night
                Band.LATE -> R.array.nuvio_c_roulette_movies_late
            }
            NuvioCShuffleKind.SHOWS -> R.array.nuvio_c_roulette_shows
            NuvioCShuffleKind.EITHER -> R.array.nuvio_c_roulette_either
            NuvioCShuffleKind.ANIME -> R.array.nuvio_c_roulette_anime
            NuvioCShuffleKind.EPISODES -> R.array.nuvio_c_roulette_episodes // [fork] round 37
        }
        return stringArrayResource(arr).pick(visit)
    }

    /** Caption while the pick is found and the reel spins: mood wording, else the pool size, else plain. */
    @Composable
    fun spinLine(ui: NuvioCShuffleUi): String {
        if (ui.kind == NuvioCShuffleKind.EPISODES && com.nuvio.tv.NuvioCFeatures.ROULETTE_EPISODES) { // [fork] round 37
            return stringArrayResource(R.array.nuvio_c_roulette_spin_episodes).pick(ui.visit + ui.spins)
        }
        moodSpin(ui.mood)?.let { return stringResource(it) }
        if (com.nuvio.tv.NuvioCFeatures.ROULETTE_ANY_COPY) anySpin(ui)?.let { return it } // [fork] round 37
        val n = ui.poolSize
        if (n != null && n > 0) {
            val count = remember(n) { NumberFormat.getIntegerInstance(Locale.getDefault()).format(n) }
            return stringResource(
                when (ui.kind) {
                    NuvioCShuffleKind.MOVIES -> R.string.nuvio_c_roulette_spin_movies
                    NuvioCShuffleKind.SHOWS -> R.string.nuvio_c_roulette_spin_shows
                    else -> R.string.nuvio_c_roulette_spin_titles
                },
                count
            )
        }
        return stringResource(R.string.nuvio_c_shuffle_finding)
    }

    /**
     * [fork] round 37 roulette_any_copy: "Any" no longer always shows the bare count. Each spin takes one line from a
     * rotation (never the same twice in a row): the count about one spin in three, worded a few ways; plain lines;
     * and, once the reel is moving toward its pick, a line naming the decade it landed in.
     */
    @Composable
    private fun anySpin(ui: NuvioCShuffleUi): String? {
        val slot = Math.floorMod(ui.visit * 7 + ui.spins, 6)
        val n = ui.poolSize?.takeIf { it > 0 }
        val count = n?.let { remember(it) { NumberFormat.getIntegerInstance(Locale.getDefault()).format(it) } }
        val noun = stringResource(
            when (ui.kind) {
                NuvioCShuffleKind.MOVIES -> R.string.nuvio_c_roulette_noun_films
                NuvioCShuffleKind.SHOWS -> R.string.nuvio_c_roulette_noun_series
                else -> R.string.nuvio_c_roulette_noun_titles
            }
        )
        val year = ui.pick?.item?.year?.toIntOrNull()?.takeIf { (ui.spin?.token ?: 0) > 0 }
        return when (slot) {
            0 -> count?.let { stringResource(R.string.nuvio_c_roulette_any_count_1, it, noun) }
            1 -> year?.let { stringResource(R.string.nuvio_c_roulette_any_decade, "${it - it % 10}s") }
            3 -> count?.let { stringResource(R.string.nuvio_c_roulette_any_count_2, it) }
            5 -> year?.let { stringResource(R.string.nuvio_c_roulette_any_decade, "${it - it % 10}s") }
            else -> null
        } ?: stringArrayResource(R.array.nuvio_c_roulette_any_lines).pick(ui.visit + ui.spins)
    }

    private fun moodSpin(mood: NuvioCShuffleMood): Int? = when (mood) {
        NuvioCShuffleMood.ANY -> null
        NuvioCShuffleMood.FEEL_GOOD -> R.string.nuvio_c_roulette_spin_feel_good
        NuvioCShuffleMood.GRIPPING -> R.string.nuvio_c_roulette_spin_gripping
        NuvioCShuffleMood.FUNNY -> R.string.nuvio_c_roulette_spin_funny
        NuvioCShuffleMood.DARK -> R.string.nuvio_c_roulette_spin_dark
        NuvioCShuffleMood.MIND_BENDING -> R.string.nuvio_c_roulette_spin_mind_bending
        NuvioCShuffleMood.ROMANTIC -> R.string.nuvio_c_roulette_spin_romantic
        NuvioCShuffleMood.ANIMATED -> R.string.nuvio_c_roulette_spin_animated
        NuvioCShuffleMood.TRUE_STORY -> R.string.nuvio_c_roulette_spin_true_story
        NuvioCShuffleMood.WESTERN -> R.string.nuvio_c_roulette_spin_western
        NuvioCShuffleMood.DOCUMENTARY -> R.string.nuvio_c_roulette_spin_documentary
        NuvioCShuffleMood.ADVENTURE -> R.string.nuvio_c_roulette_spin_adventure
        NuvioCShuffleMood.FAMILY -> R.string.nuvio_c_roulette_spin_family
        NuvioCShuffleMood.MUSIC -> R.string.nuvio_c_roulette_spin_music
    }

    /** Small caps above the result logo: the mood's own pick where it reads well, else the time of day. */
    @Composable
    fun kicker(ui: NuvioCShuffleUi): String {
        val res = when {
            ui.kind == NuvioCShuffleKind.ANIME -> R.string.nuvio_c_roulette_kick_anime
            ui.kind == NuvioCShuffleKind.EPISODES -> R.string.nuvio_c_roulette_kick_episode // [fork] round 37
            ui.mood == NuvioCShuffleMood.FEEL_GOOD -> R.string.nuvio_c_roulette_kick_feel_good
            ui.mood == NuvioCShuffleMood.ROMANTIC -> R.string.nuvio_c_roulette_kick_romantic
            ui.mood == NuvioCShuffleMood.FAMILY -> R.string.nuvio_c_roulette_kick_family
            else -> when (nowBand()) {
                Band.MORNING -> R.string.nuvio_c_roulette_kick_morning
                Band.AFTERNOON -> R.string.nuvio_c_roulette_kick_afternoon
                Band.EVENING, Band.NIGHT -> R.string.nuvio_c_roulette_kick_tonight
                Band.LATE -> R.string.nuvio_c_roulette_kick_late
            }
        }
        return stringResource(res)
    }

    /** Quiet line under the buttons: 3rd and 4th spin, 5th to 7th, then from the 8th. */
    @Composable
    fun spinsLine(ui: NuvioCShuffleUi): String? {
        val b = nowBand()
        return when {
            ui.spins >= 8 -> stringResource(R.string.nuvio_c_roulette_spins_8)
            ui.spins >= 5 -> stringResource(if (b == Band.EVENING || b == Band.NIGHT) R.string.nuvio_c_roulette_spins_5_tonight else R.string.nuvio_c_roulette_spins_5)
            ui.spins >= 3 -> stringResource(R.string.nuvio_c_roulette_spins_3)
            else -> null
        }
    }
}
