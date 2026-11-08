package com.nuvio.tv.ui.screens.settings

// [fork] Nuvio C round 34 `night_schedule`: Automatic night mode rows under the Night mode switch in
// Settings > Playback > Audio. Same rows and choice dialogs as the rest of Settings.

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nuvio.tv.R
import com.nuvio.tv.data.local.NightModeLevel
import com.nuvio.tv.ui.screens.player.NuvioCNight
import com.nuvio.tv.ui.screens.player.nuvioCNightLabel
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class NuvioCNightDialog { FROM, UNTIL, LEVEL }

@Composable
internal fun NuvioCNightScheduleRows(enabled: Boolean) {
    val context = LocalContext.current
    remember { NuvioCNight.loadSchedule(context) }
    val schedule by NuvioCNight.schedule.collectAsState()
    var dialog by remember { mutableStateOf<NuvioCNightDialog?>(null) }

    SettingsToggleRow(
        title = stringResource(R.string.nuvio_c_night_auto),
        subtitle = stringResource(R.string.nuvio_c_night_auto_sub),
        checked = schedule.on,
        onToggle = { NuvioCNight.saveSchedule(context, schedule.copy(on = !schedule.on)) },
        enabled = enabled
    )
    if (!schedule.on) return
    SettingsActionRow(
        title = stringResource(R.string.nuvio_c_night_from),
        subtitle = null,
        value = nuvioCClock(schedule.fromMin),
        enabled = enabled,
        onClick = { dialog = NuvioCNightDialog.FROM }
    )
    SettingsActionRow(
        title = stringResource(R.string.nuvio_c_night_until),
        subtitle = null,
        value = nuvioCClock(schedule.untilMin),
        enabled = enabled,
        onClick = { dialog = NuvioCNightDialog.UNTIL }
    )
    SettingsActionRow(
        title = stringResource(R.string.nuvio_c_night_auto_level),
        subtitle = null,
        value = stringResource(nuvioCNightLabel(schedule.level)),
        enabled = enabled,
        onClick = { dialog = NuvioCNightDialog.LEVEL }
    )

    when (dialog) {
        NuvioCNightDialog.FROM, NuvioCNightDialog.UNTIL -> {
            val from = dialog == NuvioCNightDialog.FROM
            val times = (0 until 48).map { it * 30 }
            SettingsSingleChoiceDialog(
                title = stringResource(if (from) R.string.nuvio_c_night_from else R.string.nuvio_c_night_until),
                options = times.map { SettingsPickerOption(it, nuvioCClock(it)) },
                selectedValue = if (from) schedule.fromMin else schedule.untilMin,
                onOptionSelected = { m ->
                    NuvioCNight.saveSchedule(context, if (from) schedule.copy(fromMin = m) else schedule.copy(untilMin = m))
                    dialog = null
                },
                onDismiss = { dialog = null },
                maxHeight = 360.dp
            )
        }
        NuvioCNightDialog.LEVEL -> {
            val levels = NightModeLevel.entries.filter { it != NightModeLevel.OFF }
            SettingsSingleChoiceDialog(
                title = stringResource(R.string.nuvio_c_night_auto_level),
                options = levels.map { SettingsPickerOption(it, stringResource(nuvioCNightLabel(it))) },
                selectedValue = schedule.level,
                onOptionSelected = { l -> NuvioCNight.saveSchedule(context, schedule.copy(level = l)); dialog = null },
                onDismiss = { dialog = null }
            )
        }
        null -> Unit
    }
}

private fun nuvioCClock(minute: Int): String =
    LocalTime.of(minute / 60, minute % 60).format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
