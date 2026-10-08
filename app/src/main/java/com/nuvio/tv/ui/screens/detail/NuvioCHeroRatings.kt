package com.nuvio.tv.ui.screens.detail

// [fork] Nuvio C: the hero's MDBList ratings line arrives a moment after the page opens, and the
// whole hero block shifted when it did. While MDBList is still loading, an invisible copy of the
// line (same height) holds its place. If MDBList finds nothing the space goes, as before, and the
// space is never held longer than NUVIO_C_RATINGS_HOLD_MS (e.g. no network, MDBList never asked).
// Switch: NuvioCFeatures.HERO_RATINGS_SPACE.

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import kotlinx.coroutines.delay
import com.nuvio.tv.domain.model.MDBListRatings

/** One rating is enough: the line is a single row of same-height badges. */
internal val NUVIO_C_RATINGS_PLACEHOLDER = MDBListRatings(imdb = 7.0)

internal fun Modifier.nuvioCInvisible(): Modifier = alpha(0f)

internal const val NUVIO_C_RATINGS_HOLD_MS = 4_000L

/** [pending] for at most [NUVIO_C_RATINGS_HOLD_MS] per title ([key]). */
@Composable
internal fun rememberNuvioCRatingsHold(pending: Boolean, key: Any?): Boolean {
    var expired by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key, pending) {
        if (!pending) return@LaunchedEffect
        delay(NUVIO_C_RATINGS_HOLD_MS)
        expired = true
    }
    return pending && !expired
}
