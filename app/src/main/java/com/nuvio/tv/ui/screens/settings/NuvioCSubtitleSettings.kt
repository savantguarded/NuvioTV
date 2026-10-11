package com.nuvio.tv.ui.screens.settings

// [fork] Nuvio C subtitle settings row(s), hooked into PlaybackSubtitleSettings with one line.

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
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

/** "Subtitle Font": opens the same pick-one list as Appearance > Font, each name in its own font. */
@Composable
internal fun NuvioCSubtitleFontRow(enabled: Boolean) {
    if (!NuvioCFeatures.SUBTITLE_FONT) return
    val context = LocalContext.current
    val fonts = com.nuvio.tv.ui.screens.player.NuvioCSubtitleFont
    remember { fonts.load(context) }
    var showDialog by remember { mutableStateOf(false) }
    SettingsActionRow(
        title = stringResource(R.string.nuvio_c_subtitle_font),
        subtitle = stringResource(R.string.nuvio_c_subtitle_font_desc),
        value = fonts.current.label,
        onClick = { showDialog = true },
        enabled = enabled
    )
    if (showDialog) {
        val options = remember {
            fonts.options.map { option ->
                SettingsPickerOption(
                    value = option.id,
                    title = option.label,
                    titleFontFamily = if (option.file == null) null else {
                        fonts.typefaceFor(context, option)?.let { FontFamily(it) }
                    }
                )
            }
        }
        SettingsSingleChoiceDialog(
            title = stringResource(R.string.nuvio_c_subtitle_font),
            options = options,
            selectedValue = fonts.current.id,
            onOptionSelected = { id ->
                fonts.select(context, id)
                showDialog = false
            },
            onDismiss = { showDialog = false },
            width = 400.dp,
            maxHeight = 280.dp
        )
    }
}
