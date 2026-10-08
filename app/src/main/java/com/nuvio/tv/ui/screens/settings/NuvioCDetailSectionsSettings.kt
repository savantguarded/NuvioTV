package com.nuvio.tv.ui.screens.settings

// [fork] Settings › Layout › Details › "Details sections": one row per section on/off, then each
// section with its own on/off and ▲ ▼ to reorder, the same pattern as MDBList's rating order.
// Kept on this TV only (NuvioCSectionPrefs). Switch: NuvioCFeatures.SECTION_ROWS.

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.R
import com.nuvio.tv.data.local.NuvioCSectionPrefs
import com.nuvio.tv.data.local.rememberNuvioCSectionSettings
import com.nuvio.tv.ui.theme.NuvioTheme

@Composable
internal fun NuvioCDetailSectionsSettings() {
    if (!NuvioCFeatures.SECTION_ROWS) return
    val context = LocalContext.current
    val settings = rememberNuvioCSectionSettings()

    SettingsSectionLabel(text = stringResource(R.string.nuvio_c_sections_group))
    SettingsToggleRow(
        title = stringResource(R.string.nuvio_c_sections_rows),
        subtitle = stringResource(R.string.nuvio_c_sections_rows_sub),
        checked = settings.rows,
        onToggle = { NuvioCSectionPrefs.setRows(context, !settings.rows) }
    )
    settings.order.forEachIndexed { index, section ->
        val visible = section !in settings.hidden
        NuvioCSectionOrderRow(
            title = nuvioCSectionLabel(section),
            checked = visible,
            enabled = settings.rows,
            onToggle = { NuvioCSectionPrefs.setHidden(context, section, visible) },
            onMoveUp = if (index > 0 && settings.rows) {
                { NuvioCSectionPrefs.move(context, section, -1) }
            } else null,
            onMoveDown = if (index < settings.order.lastIndex && settings.rows) {
                { NuvioCSectionPrefs.move(context, section, 1) }
            } else null
        )
    }
    SettingsActionRow(
        title = stringResource(R.string.nuvio_c_sections_reset),
        subtitle = null,
        enabled = settings.rows,
        onClick = { NuvioCSectionPrefs.reset(context) }
    )
}

@Composable
private fun nuvioCSectionLabel(section: String): String = when (section) {
    "CAST" -> stringResource(R.string.detail_tab_cast)
    "RATINGS" -> stringResource(R.string.detail_tab_ratings)
    "MORE_LIKE_THIS" -> stringResource(R.string.detail_tab_more_like_this)
    "TRAILER" -> stringResource(R.string.detail_tab_trailer)
    "COLLECTION" -> stringResource(R.string.tmdb_collections_title)
    else -> section
}

/** Same layout and buttons as MDBList's RatingOrderToggleRow (private there, so mirrored here). */
@Composable
private fun NuvioCSectionOrderRow(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.xs)
    ) {
        SettingsToggleRow(
            title = title,
            subtitle = null,
            checked = checked,
            enabled = enabled,
            onToggle = onToggle,
            modifier = Modifier.weight(1f)
        )
        NuvioCMoveButton("▲", onMoveUp)
        NuvioCMoveButton("▼", onMoveDown)
    }
}

@Composable
private fun NuvioCMoveButton(label: String, onClick: (() -> Unit)?) {
    Button(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        modifier = Modifier.width(40.dp),
        colors = ButtonDefaults.colors(
            containerColor = NuvioTheme.colors.BackgroundElevated,
            contentColor = NuvioTheme.colors.TextPrimary,
            disabledContainerColor = NuvioTheme.colors.BackgroundElevated,
            disabledContentColor = NuvioTheme.colors.TextTertiary
        ),
        scale = ButtonDefaults.scale(focusedScale = 1.05f),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
