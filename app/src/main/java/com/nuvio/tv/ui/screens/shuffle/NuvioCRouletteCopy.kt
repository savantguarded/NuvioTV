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
        }
        return stringArrayResource(arr).pick(visit)
    }

    /** Caption while the pick is found and the reel spins: mood wording, else the pool size, else plain. */
    @Composable
    fun spinLine(ui: NuvioCShuffleUi): String {
        moodSpin(ui.mood)?.let { return stringResource(it) }
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
