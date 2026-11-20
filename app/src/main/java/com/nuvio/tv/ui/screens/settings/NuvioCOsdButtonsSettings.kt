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

/** [fork] round 46 osd_clean: "Player controls style" (Nuvio C / Clean), picked from a list like the other settings (46b). */
@Composable
internal fun NuvioCOsdStyleRow() {
    if (!NuvioCFeatures.OSD_CLEAN) return
    val context = LocalContext.current
    LaunchedEffect(Unit) { com.nuvio.tv.ui.screens.player.NuvioCOsdStyle.load(context) }
    val style by com.nuvio.tv.ui.screens.player.NuvioCOsdStyle.style.collectAsState()
    var open by remember { mutableStateOf(false) }
    val nuvioC = com.nuvio.tv.ui.screens.player.NuvioCOsdStyle.NUVIO_C
    val clean = com.nuvio.tv.ui.screens.player.NuvioCOsdStyle.CLEAN
    val labels = mapOf(
        nuvioC to stringResource(R.string.nuvio_c_osd_style_nuvio_c),
        clean to stringResource(R.string.nuvio_c_osd_style_clean)
    )
    SettingsActionRow(
        title = stringResource(R.string.nuvio_c_osd_style),
        subtitle = stringResource(R.string.nuvio_c_osd_style_sub),
        value = labels[style],
        onClick = { open = true }
    )
    if (open) SettingsSingleChoiceDialog(
        title = stringResource(R.string.nuvio_c_osd_style),
        options = listOf(
            SettingsPickerOption(nuvioC, labels.getValue(nuvioC), stringResource(R.string.nuvio_c_osd_style_nuvio_c_desc)),
            SettingsPickerOption(clean, labels.getValue(clean), stringResource(R.string.nuvio_c_osd_style_clean_desc))
        ),
        selectedValue = style,
        onOptionSelected = {
            com.nuvio.tv.ui.screens.player.NuvioCOsdStyle.set(context, it)
            open = false
        },
        onDismiss = { open = false }
    )
}

@Composable
internal fun NuvioCOsdButtonsRow() {
    if (!NuvioCFeatures.OSD_BUTTON_ORDER) return
    var open by remember { mutableStateOf(false) }
    // [fork] round 47 osd_clean_v2: the list follows the controls style that's picked (each keeps its own order)
    val context = LocalContext.current
    LaunchedEffect(Unit) { com.nuvio.tv.ui.screens.player.NuvioCOsdStyle.load(context) }
    val style by com.nuvio.tv.ui.screens.player.NuvioCOsdStyle.style.collectAsState()
    val clean = NuvioCFeatures.OSD_CLEAN && style == com.nuvio.tv.ui.screens.player.NuvioCOsdStyle.CLEAN
    SettingsActionRow(
        title = stringResource(R.string.nuvio_c_osd_buttons),
        subtitle = stringResource(if (clean) R.string.nuvio_c_osd_buttons_clean_sub else R.string.nuvio_c_osd_buttons_sub),
        onClick = { open = true }
    )
    if (open) {
        if (clean) NuvioCCleanButtonsDialog(onDismiss = { open = false })
        else NuvioCOsdButtonsDialog(onDismiss = { open = false })
    }
}

/**
 * [fork] round 47 osd_clean_v2: Clean's own list, grouped Left, Centre, Right, More. OK on a name picks it up (Up /
 * Down move it inside its group, OK puts it down); Right reaches its zone pill, OK there moves it to the next zone it
 * can take (Start over and Next: Centre or More; the others: Left, Right or More). Play is fixed in the centre.
 */
@Composable
private fun NuvioCCleanButtonsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val order = com.nuvio.tv.ui.screens.player.NuvioCCleanOrder
    remember { order.load(context) }
    val slots by order.slots.collectAsState()
    var moving by remember { mutableStateOf<Int?>(null) }
    val nameRequesters = remember { List(slots.size + 1) { FocusRequester() } }
    val zoneRequesters = remember { List(slots.size) { FocusRequester() } }
    val listState = rememberLazyListState()
    var pendingFocus by remember { mutableStateOf<Pair<Int, Boolean>?>(0 to false) }
    var firstFocus by remember { mutableStateOf(true) }
    LaunchedEffect(pendingFocus) {
        val (i, zone) = pendingFocus ?: return@LaunchedEffect
        // [fork] 47b: no jump of the whole list on every move (it felt stiff); only the first focus scrolls
        val visible = listState.layoutInfo.visibleItemsInfo.any { it.index == i }
        if (firstFocus || !visible) listState.scrollToItem((if (firstFocus) i else i - 2).coerceIn(0, slots.lastIndex))
        firstFocus = false
        (if (zone) zoneRequesters.getOrNull(i) else nameRequesters.getOrNull(i))?.requestFocusAfterFrames()
        pendingFocus = null
    }
    val zoneLabels = mapOf(
        com.nuvio.tv.ui.screens.player.NuvioCCleanZone.LEFT to stringResource(R.string.nuvio_c_osd_zone_left),
        com.nuvio.tv.ui.screens.player.NuvioCCleanZone.CENTRE to stringResource(R.string.nuvio_c_osd_zone_centre),
        com.nuvio.tv.ui.screens.player.NuvioCCleanZone.RIGHT to stringResource(R.string.nuvio_c_osd_zone_right),
        com.nuvio.tv.ui.screens.player.NuvioCCleanZone.MORE to stringResource(R.string.nuvio_c_osd_buttons_more)
    )
    NuvioDialog(
        onDismiss = { if (moving != null) moving = null else onDismiss() },
        title = stringResource(R.string.nuvio_c_osd_buttons),
        subtitle = stringResource(R.string.nuvio_c_osd_buttons_clean_dialog_sub),
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
                            .focusRequester(nameRequesters[index])
                            .onPreviewKeyEvent { event ->
                                val m = moving
                                if (m == null || m != index || event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                                val delta = when (event.nativeKeyEvent.keyCode) {
                                    KeyEvent.KEYCODE_DPAD_UP -> -1
                                    KeyEvent.KEYCODE_DPAD_DOWN -> 1
                                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> return@onPreviewKeyEvent true
                                    else -> return@onPreviewKeyEvent false
                                }
                                val next = order.moved(slots, index, delta)
                                if (next !== slots) {
                                    order.save(context, next)
                                    // [fork] 47b: it may have stepped into another zone; follow it wherever it landed
                                    val at = order.slots.value.indexOfFirst { it.button == slot.button }.coerceAtLeast(0)
                                    moving = at
                                    pendingFocus = at to false
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
                            val name = if (slot.button == com.nuvio.tv.ui.screens.player.NuvioCOsdButton.AUDIO) stringResource(R.string.nuvio_c_osd_audio)
                                else stringResource(slot.button.nuvioCLabel())
                            Text(
                                text = (if (isMoving) "↕  " else "") + name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    val inMore = slot.zone == com.nuvio.tv.ui.screens.player.NuvioCCleanZone.MORE
                    Card(
                        onClick = {
                            if (moving == null) {
                                val next = order.cycled(slots, index)
                                order.save(context, next)
                                val sorted = order.slots.value
                                pendingFocus = sorted.indexOfFirst { it.button == slot.button }.coerceAtLeast(0) to true
                            }
                        },
                        modifier = Modifier.width(132.dp).focusRequester(zoneRequesters[index]),
                        colors = CardDefaults.colors(
                            containerColor = if (inMore) Color.White.copy(alpha = 0.06f) else NuvioTheme.colors.Secondary.copy(alpha = 0.85f),
                            focusedContainerColor = if (inMore) Color.White.copy(alpha = 0.12f) else NuvioTheme.colors.Secondary
                        ),
                        shape = CardDefaults.shape(RoundedCornerShape(999.dp)),
                        border = CardDefaults.border(
                            border = Border(BorderStroke(1.dp, Color.Transparent), shape = RoundedCornerShape(999.dp)),
                            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(999.dp))
                        ),
                        scale = CardDefaults.scale(focusedScale = 1f, pressedScale = 1f)
                    ) {
                        Text(
                            text = zoneLabels.getValue(slot.zone),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (inMore) Color.White else NuvioTheme.colors.OnSecondary,
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
                order.reset(context)
                pendingFocus = 0 to false
            },
            focusRequester = nameRequesters.last()
        )
    }
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
