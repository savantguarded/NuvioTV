package com.nuvio.tv.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.R
import com.nuvio.tv.data.local.NuvioCTrailerPrefs
import com.nuvio.tv.data.local.rememberNuvioCBackgroundTrailerMuted

// [fork] "Play Trailer Muted" for background trailers, shown under official's "Pause When Scrolling".
@Composable
internal fun NuvioCBackgroundTrailerMutedRow() {
    if (!NuvioCFeatures.BG_TRAILER_SOUND_TOGGLE || !NuvioCFeatures.BACKGROUND_TRAILERS) return
    val context = LocalContext.current
    val muted = rememberNuvioCBackgroundTrailerMuted()
    SettingsToggleRow(
        title = stringResource(R.string.layout_trailer_muted),
        subtitle = stringResource(R.string.nuvio_c_bg_trailer_muted_sub),
        checked = muted,
        onToggle = { NuvioCTrailerPrefs.setBackgroundTrailerMuted(context, !muted) }
    )
}

// [fork] "Prefer IMDb Trailers" (2026-10-11): IMDb first for every trailer, YouTube only when IMDb
// has nothing at 720p or better. Shown with the other trailer settings.
@Composable
internal fun NuvioCPreferImdbTrailersRow() {
    if (!NuvioCFeatures.IMDB_TRAILER_BACKUP) return
    val context = LocalContext.current
    var on by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(NuvioCTrailerPrefs.preferImdb(context)) }
    SettingsToggleRow(
        title = stringResource(R.string.nuvio_c_prefer_imdb_trailers),
        subtitle = stringResource(R.string.nuvio_c_prefer_imdb_trailers_sub),
        checked = on,
        onToggle = {
            on = !on
            NuvioCTrailerPrefs.setPreferImdb(context, on)
        }
    )
}
