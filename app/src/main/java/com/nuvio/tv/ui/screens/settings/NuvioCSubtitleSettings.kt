package com.nuvio.tv.ui.screens.settings

// [fork] Nuvio C subtitle settings row(s), hooked into PlaybackSubtitleSettings with one line.

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.R
import com.nuvio.tv.core.nuvioc.NuvioCPrefs

/** "Prefer SDH subtitles" toggle, right under "Use Forced Subtitles". */
@Composable
internal fun NuvioCPreferSdhRow(enabled: Boolean, stripSdh: Boolean) {
    if (!NuvioCFeatures.PREFER_SDH) return
    val context = LocalContext.current
    var on by remember { mutableStateOf(NuvioCPrefs.preferSdh(context)) }
    SettingsToggleRow(
        title = stringResource(R.string.nuvio_c_prefer_sdh),
        subtitle = stringResource(
            if (on && stripSdh) R.string.nuvio_c_prefer_sdh_strip_warning else R.string.nuvio_c_prefer_sdh_desc
        ),
        checked = on,
        onToggle = {
            on = !on
            NuvioCPrefs.setPreferSdh(context, on)
        },
        enabled = enabled
    )
}
