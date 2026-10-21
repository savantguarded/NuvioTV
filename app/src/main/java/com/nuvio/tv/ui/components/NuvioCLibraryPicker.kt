package com.nuvio.tv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.R
import com.nuvio.tv.core.tracking.LOCAL_LIBRARY_LIST_KEY
import com.nuvio.tv.core.tracking.providerId
import com.nuvio.tv.domain.model.LibraryListTab
import com.nuvio.tv.domain.model.LibrarySourceMode
import com.nuvio.tv.domain.model.localizedMembershipTitle
import com.nuvio.tv.ui.theme.NuvioTheme

// [fork] Nuvio C "Add to library" list (switch `library_picker`, 2026-10-12). Used by the three
// copies of the dialog (details page, Home, poster menu).
//  - Order: the lists of the service set as your Library source come first (tagged "Library"),
//    then Nuvio Library, then the rest in official order. Local source = Nuvio Library first.
//  - No focus zoom (the 1.1x zoom ran past the list's top / bottom and was cut), plus a little
//    room around the list, so nothing clips. Official colours and focus fill kept.
//  - A selected list shows a check at the end instead of a "✓" in front of its name.

object NuvioCLibraryPicker {
    val enabled: Boolean get() = NuvioCFeatures.LIBRARY_PICKER

    fun isLibrarySource(tab: LibraryListTab, source: LibrarySourceMode?): Boolean {
        if (source == null) return false
        val id = source.providerId ?: return tab.key == LOCAL_LIBRARY_LIST_KEY
        return tab.trackingProviderId.equals(id.storageId, ignoreCase = true)
    }

    /** Library source's lists first, the rest in their given order (stable). */
    fun order(tabs: List<LibraryListTab>, source: LibrarySourceMode?): List<LibraryListTab> {
        if (!enabled || source == null) return tabs
        val (first, rest) = tabs.partition { isLibrarySource(it, source) }
        return first + rest
    }
}

@Composable
fun NuvioCLibraryPickerList(
    tabs: List<LibraryListTab>,
    source: LibrarySourceMode?,
    membership: Map<String, Boolean>,
    isPending: Boolean,
    firstFocusRequester: FocusRequester,
    onToggle: (String) -> Unit
) {
    val room = 6.dp
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
            .nuvioCFocusRoom(horizontal = room, top = room, bottom = room),
        contentPadding = nuvioCFocusRoomPadding(horizontal = room, top = room, bottom = room),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(tabs, key = { it.key }) { tab ->
            val selected = membership[tab.key] == true
            val isSource = source != null && source != LibrarySourceMode.LOCAL && NuvioCLibraryPicker.isLibrarySource(tab, source)
            Button(
                onClick = { onToggle(tab.key) },
                enabled = !isPending,
                scale = ButtonDefaults.scale(focusedScale = 1f),
                modifier = if (tab.key == tabs.firstOrNull()?.key) {
                    Modifier.fillMaxWidth().focusRequester(firstFocusRequester)
                } else {
                    Modifier.fillMaxWidth()
                },
                colors = ButtonDefaults.colors(
                    containerColor = if (selected) NuvioTheme.colors.FocusBackground else NuvioTheme.colors.BackgroundCard,
                    contentColor = NuvioTheme.colors.TextPrimary
                )
            ) {
                if (com.nuvio.tv.NuvioCFeatures.LIBRARY_PICKER_TICKS) { // [fork] library_picker_ticks: fixed tick column
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = tab.localizedMembershipTitle(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (isSource) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.library_title),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LocalContentColor.current.copy(alpha = 0.6f),
                                    maxLines = 1
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                            if (selected) {
                                Icon(imageVector = Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                } else
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = tab.localizedMembershipTitle(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isSource) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.library_title),
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalContentColor.current.copy(alpha = 0.6f),
                            maxLines = 1
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (selected) {
                        Icon(imageVector = Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
