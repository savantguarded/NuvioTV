package com.nuvio.tv.ui.screens.settings

// [fork] Nuvio C round 31 `osd_button_order`: Settings > Playback > Player buttons. A list of the player's
// buttons in their order. OK on a name picks it up (Up / Down then moves it, OK again puts it down); Right
// reaches its Main row / More switch. Reset puts back the fork's default layout.

import com.nuvio.tv.ui.screens.player.nuvioCLabel
import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.R
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.screens.calendar.PanelActionRow
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import com.nuvio.tv.ui.screens.player.NuvioCOsdOrder
import com.nuvio.tv.ui.theme.NuvioTheme

@Composable
internal fun NuvioCOsdButtonsRow() {
    if (!NuvioCFeatures.OSD_BUTTON_ORDER) return
    var open by remember { mutableStateOf(false) }
    SettingsActionRow(
        title = stringResource(R.string.nuvio_c_osd_buttons),
        subtitle = stringResource(R.string.nuvio_c_osd_buttons_sub),
        onClick = { open = true }
    )
    if (open) NuvioCOsdButtonsDialog(onDismiss = { open = false })
}

@Composable
private fun NuvioCOsdButtonsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    remember { NuvioCOsdOrder.load(context) }
    val slots by NuvioCOsdOrder.slots.collectAsState()
    var moving by remember { mutableStateOf<Int?>(null) }
    val requesters = remember { List(slots.size + 1) { FocusRequester() } }
    val listState = rememberLazyListState()
    var pendingFocus by remember { mutableStateOf<Int?>(0) }
    LaunchedEffect(pendingFocus) {
        val i = pendingFocus ?: return@LaunchedEffect
        listState.scrollToItem(i.coerceIn(0, slots.lastIndex))
        requesters.getOrNull(i)?.requestFocusAfterFrames()
        pendingFocus = null
    }
    NuvioDialog(
        onDismiss = { if (moving != null) moving = null else onDismiss() },
        title = stringResource(R.string.nuvio_c_osd_buttons),
        subtitle = stringResource(R.string.nuvio_c_osd_buttons_dialog_sub),
        width = 600.dp
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
            verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.xs)
        ) {
            itemsIndexed(slots, key = { _, slot -> slot.button.key }) { index, slot ->
                val isMoving = moving == index
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val shape = RoundedCornerShape(NuvioTheme.radii.md)
                    Card(
                        onClick = { moving = if (isMoving) null else index },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(requesters[index])
                            .onPreviewKeyEvent { event ->
                                val m = moving
                                if (m == null || m != index || event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                                val delta = when (event.nativeKeyEvent.keyCode) {
                                    KeyEvent.KEYCODE_DPAD_UP -> -1
                                    KeyEvent.KEYCODE_DPAD_DOWN -> 1
                                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> return@onPreviewKeyEvent true
                                    else -> return@onPreviewKeyEvent false
                                }
                                val next = NuvioCOsdOrder.moved(slots, index, delta)
                                if (next !== slots) {
                                    NuvioCOsdOrder.save(context, next)
                                    moving = index + delta
                                    pendingFocus = index + delta
                                }
                                true
                            },
                        colors = CardDefaults.colors(
                            containerColor = if (isMoving) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f),
                            focusedContainerColor = if (isMoving) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.12f)
                        ),
                        shape = CardDefaults.shape(shape),
                        border = CardDefaults.border(
                            border = Border(BorderStroke(1.dp, Color.Transparent), shape = shape),
                            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = shape)
                        ),
                        scale = CardDefaults.scale(focusedScale = 1f, pressedScale = 1f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = (if (isMoving) "↕  " else "${index + 1}.  ") + stringResource(slot.button.nuvioCLabel()),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Card(
                        onClick = {
                            if (moving == null) NuvioCOsdOrder.save(context, NuvioCOsdOrder.toggled(slots, index))
                        },
                        modifier = Modifier.width(132.dp),
                        colors = CardDefaults.colors(
                            containerColor = if (slot.inMore) Color.White.copy(alpha = 0.06f) else NuvioTheme.colors.Secondary.copy(alpha = 0.85f),
                            focusedContainerColor = if (slot.inMore) Color.White.copy(alpha = 0.12f) else NuvioTheme.colors.Secondary
                        ),
                        shape = CardDefaults.shape(RoundedCornerShape(999.dp)),
                        border = CardDefaults.border(
                            border = Border(BorderStroke(1.dp, Color.Transparent), shape = RoundedCornerShape(999.dp)),
                            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(999.dp))
                        ),
                        scale = CardDefaults.scale(focusedScale = 1f, pressedScale = 1f)
                    ) {
                        Text(
                            text = stringResource(if (slot.inMore) R.string.nuvio_c_osd_buttons_more else R.string.nuvio_c_osd_buttons_main),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (slot.inMore) Color.White else NuvioTheme.colors.OnSecondary,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }
        PanelActionRow(
            label = stringResource(R.string.nuvio_c_osd_buttons_reset),
            onClick = {
                moving = null
                NuvioCOsdOrder.reset(context)
                pendingFocus = 0
            },
            focusRequester = requesters.last()
        )
    }
}
