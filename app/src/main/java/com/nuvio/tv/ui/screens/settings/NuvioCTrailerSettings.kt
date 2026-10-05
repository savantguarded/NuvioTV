package com.nuvio.tv.ui.screens.settings

import androidx.compose.runtime.Composable
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
