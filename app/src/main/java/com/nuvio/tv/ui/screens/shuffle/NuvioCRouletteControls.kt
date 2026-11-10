package com.nuvio.tv.ui.screens.shuffle

// [fork] Nuvio C round 37 `roulette_chips` + `roulette_refine`: Roulette's chips say what they do.
//  - On / off choices (Unwatched) read as a toggle: a tick and the accent colour when on, plain when off.
//  - Choices with several values (type and mood on the result screen, every Refine row) open a dropdown
//    built like official's Library pickers, with focus landing on the current value.
//  - Refine opens the app's standard dialog (NuvioDialog); changes apply when it closes, one roll at most.

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.data.nuvioc.NuvioCRouletteAcclaim
import com.nuvio.tv.data.nuvioc.NuvioCRouletteDecade
import com.nuvio.tv.data.nuvioc.NuvioCRouletteLength
import com.nuvio.tv.data.nuvioc.NuvioCRoulettePool
import com.nuvio.tv.data.nuvioc.NuvioCRouletteRefine
import com.nuvio.tv.data.nuvioc.NuvioCRouletteRegion
import com.nuvio.tv.data.nuvioc.NuvioCShuffleKind
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.delay

internal data class RouletteOption<T>(val label: String, val value: T)

private val CHIP_HEIGHT = 32.dp

/** A chip that is on or off. On: tick + accent; off: plain. Focus is white either way, the tick stays. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun RouletteToggleChip(label: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, leadingTune: Boolean = false) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(CHIP_HEIGHT),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (on) NuvioTheme.colors.Secondary else Color.White.copy(alpha = 0.07f),
            contentColor = if (on) NuvioTheme.colors.OnSecondary else NuvioTheme.colors.TextSecondary,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black
        )
    ) {
        Row(Modifier.fillMaxHeight().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                leadingTune -> { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
                on -> { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
            }
            Text(text = label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

/** A chip showing the current value with a chevron; OK opens the list (Library-picker focus handling). */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun <T> RouletteDropdownChip(
    value: String,
    options: List<RouletteOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(
            onClick = { expanded = true },
            modifier = Modifier.height(CHIP_HEIGHT),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.16f),
                contentColor = Color.White,
                focusedContainerColor = Color.White,
                focusedContentColor = Color.Black
            )
        ) {
            Row(Modifier.fillMaxHeight().padding(start = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = value, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                Spacer(Modifier.width(2.dp))
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
        RouletteMenu(expanded = expanded, options = options, selected = selected, minWidth = 180.dp,
            onDismiss = { expanded = false }, onSelect = { expanded = false; onSelect(it) })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun <T> RouletteMenu(
    expanded: Boolean,
    options: List<RouletteOption<T>>,
    selected: T,
    minWidth: androidx.compose.ui.unit.Dp,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit
) {
    val selectedRequester = remember { FocusRequester() }
    val bring = remember { BringIntoViewRequester() }
    var focusedIndex by remember(expanded) { mutableStateOf(options.indexOfFirst { it.value == selected }) }
    LaunchedEffect(expanded) {
        if (!expanded) return@LaunchedEffect
        var ok = selectedRequester.requestFocusAfterFrames(frames = 3)
        var attempt = 0
        while (!ok && attempt < 6) {
            delay(32)
            ok = runCatching { selectedRequester.requestFocus() }.getOrDefault(false)
            attempt++
        }
        if (ok) runCatching { bring.bringIntoView() }
        delay(48)
        if (runCatching { selectedRequester.requestFocus() }.getOrDefault(false)) runCatching { bring.bringIntoView() }
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(min = minWidth).heightIn(max = 340.dp),
        shape = RoundedCornerShape(14.dp),
        containerColor = NuvioTheme.colors.BackgroundCard,
        tonalElevation = NuvioTheme.spacing.none,
        shadowElevation = NuvioTheme.spacing.sm,
        border = BorderStroke(NuvioTheme.spacing.hairline, NuvioTheme.colors.Border)
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = option.value == selected
            val isFocused = index == focusedIndex
            val textColor = if (isFocused) NuvioTheme.colors.OnSecondary else NuvioTheme.colors.TextPrimary
            DropdownMenuItem(
                modifier = Modifier
                    .then(if (isSelected) Modifier.focusRequester(selectedRequester).bringIntoViewRequester(bring) else Modifier)
                    .padding(horizontal = 6.dp, vertical = NuvioTheme.spacing.xxs)
                    .background(
                        color = when {
                            isFocused -> NuvioTheme.colors.Secondary
                            isSelected -> NuvioTheme.colors.FocusBackground
                            else -> Color.Transparent
                        },
                        shape = RoundedCornerShape(10.dp)
                    )
                    .onFocusChanged { if (it.isFocused || it.hasFocus) focusedIndex = index else if (focusedIndex == index) focusedIndex = -1 },
                text = { Text(text = option.label, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailingIcon = if (isSelected) ({ Icon(Icons.Default.Check, contentDescription = null, tint = textColor, modifier = Modifier.size(18.dp)) }) else null,
                onClick = { onSelect(option.value) },
                colors = MenuDefaults.itemColors(textColor = textColor)
            )
        }
    }
}

// ---- Refine ---------------------------------------------------------------------------------------------

@Composable
internal fun RouletteRefineChip(refine: NuvioCRouletteRefine, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val n = refine.active
    RouletteToggleChip(
        label = if (n > 0) stringResource(R.string.nuvio_c_refine_chip_count, n) else stringResource(R.string.nuvio_c_refine_chip),
        on = n > 0,
        onClick = onClick,
        modifier = modifier,
        leadingTune = true
    )
}

/** The Refine dialog. Changes are kept locally and handed back once, when it closes. */
@Composable
internal fun RouletteRefineDialog(
    kind: NuvioCShuffleKind,
    initial: NuvioCRouletteRefine,
    onClose: (NuvioCRouletteRefine) -> Unit
) {
    var refine by remember { mutableStateOf(initial) }
    val episodes = kind == NuvioCShuffleKind.EPISODES
    val filtersOn = com.nuvio.tv.NuvioCFeatures.ROULETTE_REFINE
    val firstRow = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstRow.requestFocusAfterFrames() }
    NuvioDialog(
        onDismiss = { onClose(refine) },
        title = stringResource(R.string.nuvio_c_refine_title),
        subtitle = stringResource(if (episodes) R.string.nuvio_c_refine_sub_episodes else R.string.nuvio_c_refine_sub),
        width = 540.dp,
        contentSpacing = 10.dp
    ) {
        if (filtersOn) {
            RefineRow(
                label = stringResource(R.string.nuvio_c_refine_decade),
                options = NuvioCRouletteDecade.entries.map { RouletteOption(decadeLabel(it), it) },
                selected = refine.decade,
                onSelect = { refine = refine.copy(decade = it) },
                modifier = Modifier.focusRequester(firstRow)
            )
            if (!episodes) RefineRow(
                label = stringResource(R.string.nuvio_c_refine_region),
                options = NuvioCRouletteRegion.entries.map { RouletteOption(regionLabel(it), it) },
                selected = refine.region,
                onSelect = { refine = refine.copy(region = it) }
            )
            RefineRow(
                label = stringResource(R.string.nuvio_c_refine_length),
                options = NuvioCRouletteLength.entries.map { RouletteOption(lengthLabel(it, kind), it) },
                selected = refine.length,
                onSelect = { refine = refine.copy(length = it) }
            )
            if (!episodes) RefineRow(
                label = stringResource(R.string.nuvio_c_refine_acclaim),
                options = NuvioCRouletteAcclaim.entries.map { RouletteOption(acclaimLabel(it), it) },
                selected = refine.acclaim,
                onSelect = { refine = refine.copy(acclaim = it) }
            )
        }
        if (com.nuvio.tv.NuvioCFeatures.ROULETTE_POOL_SIZE && !episodes) {
            if (filtersOn) Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(NuvioTheme.colors.Border))
            RefineRow(
                label = stringResource(R.string.nuvio_c_refine_pool),
                hint = stringResource(
                    when (refine.pool) {
                        NuvioCRoulettePool.CURATED -> R.string.nuvio_c_refine_pool_curated_hint
                        NuvioCRoulettePool.WIDE -> R.string.nuvio_c_refine_pool_wide_hint
                        NuvioCRoulettePool.DEEP -> R.string.nuvio_c_refine_pool_deep_hint
                    }
                ),
                options = NuvioCRoulettePool.entries.map { RouletteOption(poolLabel(it), it) },
                selected = refine.pool,
                onSelect = { refine = refine.copy(pool = it) },
                modifier = if (!filtersOn) Modifier.focusRequester(firstRow) else Modifier
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
            RefineButton(stringResource(R.string.nuvio_c_refine_reset)) { refine = NuvioCRouletteRefine(pool = refine.pool) }
            RefineButton(stringResource(R.string.nuvio_c_refine_done), primary = true) { onClose(refine) }
        }
    }
}

/** One Refine row: label left, value right. OK opens the list; Left / Right step through the values. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun <T> RefineRow(
    label: String,
    options: List<RouletteOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null
) {
    var expanded by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val index = options.indexOfFirst { it.value == selected }.coerceAtLeast(0)
    val shape = RoundedCornerShape(14.dp)
    Box {
        Card(
            onClick = { expanded = true },
            modifier = modifier
                .fillMaxWidth()
                .onSizeChanged { size = it }
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { e ->
                    val n = e.nativeKeyEvent
                    if (n.action != android.view.KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                    when (n.keyCode) {
                        android.view.KeyEvent.KEYCODE_DPAD_LEFT -> { onSelect(options[(index - 1).mod(options.size)].value); true }
                        android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> { onSelect(options[(index + 1).mod(options.size)].value); true }
                        else -> false
                    }
                },
            shape = CardDefaults.shape(shape),
            colors = CardDefaults.colors(containerColor = NuvioTheme.colors.BackgroundCard, focusedContainerColor = NuvioTheme.colors.FocusBackground),
            border = CardDefaults.border(
                border = androidx.tv.material3.Border(BorderStroke(NuvioTheme.spacing.hairline, NuvioTheme.colors.Border), shape = shape),
                focusedBorder = androidx.tv.material3.Border(NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs), shape = shape)
            ),
            scale = CardDefaults.scale(focusedScale = 1.0f, pressedScale = 1.0f)
        ) {
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.titleSmall, color = NuvioTheme.colors.TextPrimary, maxLines = 1)
                    if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = NuvioTheme.colors.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(12.dp))
                val isDefault = index == 0
                Text(
                    text = options[index].label,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isDefault) NuvioTheme.colors.TextSecondary else NuvioTheme.colors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 240.dp)
                )
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = if (focused) NuvioTheme.colors.FocusRing else NuvioTheme.colors.TextSecondary, modifier = Modifier.padding(start = 4.dp).size(20.dp))
            }
        }
        RouletteMenu(
            expanded = expanded, options = options, selected = selected,
            minWidth = with(LocalDensity.current) { size.width.toDp() },
            onDismiss = { expanded = false }, onSelect = { expanded = false; onSelect(it) }
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun RefineButton(label: String, primary: Boolean = false, onClick: () -> Unit) {
    androidx.tv.material3.Button(
        onClick = onClick,
        colors = androidx.tv.material3.ButtonDefaults.colors(
            containerColor = if (primary) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.1f),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black
        )
    ) { Text(label) }
}

// ---- labels ---------------------------------------------------------------------------------------------

@Composable
internal fun decadeLabel(d: NuvioCRouletteDecade): String = when (d) {
    NuvioCRouletteDecade.ANY -> stringResource(R.string.nuvio_c_refine_any_decade)
    NuvioCRouletteDecade.EARLIER -> stringResource(R.string.nuvio_c_refine_before_1960)
    else -> d.key
}

@Composable
internal fun regionLabel(r: NuvioCRouletteRegion): String = stringResource(
    when (r) {
        NuvioCRouletteRegion.ANY -> R.string.nuvio_c_refine_anywhere
        NuvioCRouletteRegion.AFRICA -> R.string.nuvio_c_region_africa
        NuvioCRouletteRegion.EUROPE -> R.string.nuvio_c_region_europe
        NuvioCRouletteRegion.NORDIC -> R.string.nuvio_c_region_nordic
        NuvioCRouletteRegion.EAST_ASIA -> R.string.nuvio_c_region_east_asia
        NuvioCRouletteRegion.SOUTH_ASIA -> R.string.nuvio_c_region_south_asia
        NuvioCRouletteRegion.MIDDLE_EAST -> R.string.nuvio_c_region_middle_east
        NuvioCRouletteRegion.LATIN_AMERICA -> R.string.nuvio_c_region_latin_america
        NuvioCRouletteRegion.NORTH_AMERICA -> R.string.nuvio_c_region_north_america
        NuvioCRouletteRegion.OCEANIA -> R.string.nuvio_c_region_oceania
    }
)

@Composable
internal fun lengthLabel(l: NuvioCRouletteLength, kind: NuvioCShuffleKind): String {
    val films = kind == NuvioCShuffleKind.MOVIES
    val tv = kind == NuvioCShuffleKind.SHOWS || kind == NuvioCShuffleKind.EPISODES
    return stringResource(
        when (l) {
            NuvioCRouletteLength.ANY -> R.string.nuvio_c_refine_any_length
            NuvioCRouletteLength.SHORT -> if (films) R.string.nuvio_c_length_film_short else if (tv) R.string.nuvio_c_length_tv_short else R.string.nuvio_c_length_short
            NuvioCRouletteLength.STANDARD -> if (films) R.string.nuvio_c_length_film_standard else if (tv) R.string.nuvio_c_length_tv_standard else R.string.nuvio_c_length_standard
            NuvioCRouletteLength.LONG -> if (films) R.string.nuvio_c_length_film_long else if (tv) R.string.nuvio_c_length_tv_long else R.string.nuvio_c_length_long
        }
    )
}

@Composable
internal fun acclaimLabel(a: NuvioCRouletteAcclaim): String = stringResource(
    when (a) {
        NuvioCRouletteAcclaim.ANY -> R.string.nuvio_c_refine_any_acclaim
        NuvioCRouletteAcclaim.CROWD -> R.string.nuvio_c_acclaim_crowd
        NuvioCRouletteAcclaim.TOP -> R.string.nuvio_c_acclaim_top
        NuvioCRouletteAcclaim.GEMS -> R.string.nuvio_c_acclaim_gems
        NuvioCRouletteAcclaim.CULT -> R.string.nuvio_c_acclaim_cult
    }
)

@Composable
internal fun poolLabel(p: NuvioCRoulettePool): String = stringResource(
    when (p) {
        NuvioCRoulettePool.CURATED -> R.string.nuvio_c_pool_curated
        NuvioCRoulettePool.WIDE -> R.string.nuvio_c_pool_wide
        NuvioCRoulettePool.DEEP -> R.string.nuvio_c_pool_deep
    }
)
