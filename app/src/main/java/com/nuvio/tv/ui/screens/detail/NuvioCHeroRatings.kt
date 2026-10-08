package com.nuvio.tv.ui.screens.detail

// [fork] Nuvio C: the hero's MDBList ratings line arrives a moment after the page opens, and the
// whole hero block shifted when it did. While MDBList is still loading, an invisible copy of the
// line (same height) holds its place. If MDBList finds nothing the space goes, as before.
// Switch: NuvioCFeatures.HERO_RATINGS_SPACE.

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.nuvio.tv.domain.model.MDBListRatings

/** One rating is enough: the line is a single row of same-height badges. */
internal val NUVIO_C_RATINGS_PLACEHOLDER = MDBListRatings(imdb = 7.0)

internal fun Modifier.nuvioCInvisible(): Modifier = alpha(0f)
