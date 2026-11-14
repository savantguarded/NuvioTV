package com.nuvio.tv.ui.screens.player

// [fork] Nuvio C round 46 `osd_clean`: the "Clean" player controls, picked in Settings > Playback > Player controls
// style (Nuvio C = the controls as before). Kept here so PlayerScreen.kt only gets the hook and the row itself:
//  - NuvioCOsdStyle: the saved choice (fork prefs, this TV).
//  - NuvioCOsdPill: a labelled pill button (Subtitles, Audio, Sources, Details), focus memory like the round buttons.
//  - NuvioCOsdMoreSheet: the gear's sheet on the right, listing what sat in the hidden More row, each with its value.
// Nothing here touches playback; the sheet is its own window, only composed while open, and dims with HDR dim.

import android.content.Context
import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.ui.util.nuvioCListGate
import androidx.compose.material.icons.filled.Check
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The saved "Player controls style". */
internal object NuvioCOsdStyle {
    private const val PREFS = "nuvio_c_osd"
    private const val KEY = "controls_style"
    const val NUVIO_C = "nuvio_c"
    const val CLEAN = "clean"
    private val _style = MutableStateFlow(NUVIO_C)
    val style: StateFlow<String> = _style.asStateFlow()
    @Volatile private var loaded = false

    fun load(context: Context): String {
        if (!NuvioCFeatures.OSD_CLEAN) return NUVIO_C
        if (!loaded) {
            loaded = true
            _style.value = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, NUVIO_C)
                ?.takeIf { it == CLEAN || it == NUVIO_C } ?: NUVIO_C
        }
        return _style.value
    }

    fun set(context: Context, value: String) {
        _style.value = value
        loaded = true
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, value).apply()
    }
}

/** A labelled pill in the Clean row. Focus returns to it after its panel closes (same memory as the round buttons). */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun NuvioCOsdPill(
    label: String,
    iconPainter: Painter?,
    icon: ImageVector,
    onClick: () -> Unit,
    upFocusRequester: FocusRequester?,
    onDownKey: (() -> Unit)?,
    onFocused: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    val memory = LocalNuvioCFocusMemory.current
    val requester = memory?.requester(label, focusRequester) ?: focusRequester
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = { memory?.onClick(label); onClick() },
        modifier = Modifier
            .height(38.dp)
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .then(if (upFocusRequester != null) Modifier.focusProperties { up = upFocusRequester } else Modifier)
            .onPreviewKeyEvent { e ->
                val n = e.nativeKeyEvent
                when {
                    upFocusRequester != null && n.action == KeyEvent.ACTION_DOWN && n.keyCode == KeyEvent.KEYCODE_DPAD_UP -> {
                        runCatching { upFocusRequester.requestFocus() }; true
                    }
                    onDownKey != null && n.action == KeyEvent.ACTION_DOWN && n.keyCode == KeyEvent.KEYCODE_DPAD_DOWN -> {
                        onDownKey(); true
                    }
                    else -> false
                }
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(19.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = nuvioCOsd(Color(0xFF3A3A3A)).copy(alpha = 0.72f),
            contentColor = nuvioCOsd(Color.White),
            focusedContainerColor = nuvioCOsd(Color.White),
            focusedContentColor = Color.Black
        )
    ) {
        Row(
            Modifier.padding(start = 11.dp, end = 14.dp).fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (iconPainter != null) Icon(iconPainter, contentDescription = null, modifier = Modifier.size(17.dp))
            else Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

/** One option in a More sub-list. [closes] = the sheet closes before [onPick] runs (it opens something else). */
internal data class NuvioCMoreOption(val label: String, val selected: Boolean, val closes: Boolean = false, val onPick: () -> Unit)

/** [fork] round 46b: a line in the gear's sheet. [section] groups lines under a small heading. */
internal sealed class NuvioCMoreItem {
    abstract val key: String
    abstract val label: String
    abstract val section: String

    /** Runs something that opens its own panel or acts at once; the sheet closes first. */
    data class Action(override val key: String, override val label: String, override val section: String, val value: String? = null, val onClick: () -> Unit) : NuvioCMoreItem()
    /** Opens a sub-list in the same panel; the current pick is ticked and focused. */
    data class Choice(override val key: String, override val label: String, override val section: String, val value: String, val options: List<NuvioCMoreOption>) : NuvioCMoreItem()
    /** Flips in place. */
    data class Toggle(override val key: String, override val label: String, override val section: String, val on: Boolean, val onToggle: () -> Unit) : NuvioCMoreItem()
    /** Left / Right step it in place; OK resets it. */
    data class Stepper(
        override val key: String, override val label: String, override val section: String, val value: String,
        val onDecrease: () -> Unit, val onIncrease: () -> Unit, val onReset: () -> Unit
    ) : NuvioCMoreItem()
}

@Stable
internal class NuvioCMoreSheetState {
    var open by mutableStateOf(false)
    /** Line last used; the sheet opens on it next time. */
    var lastKey: String? = null
    /** The Choice whose sub-list is showing, or null for the main list. */
    var page by mutableStateOf<String?>(null)
}

/**
 * The gear's sheet: a panel on the right over the playing video. Lines with a choice open a sub-list in the same
 * panel (Back returns to the line), switches flip in place, delays step with Left / Right (OK resets), and lines that
 * open a panel close the sheet first, so that panel brings focus back to the gear when it closes. Back on the main
 * list closes the sheet; focus goes back to the gear.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun NuvioCOsdMoreSheet(
    state: NuvioCMoreSheetState,
    title: String,
    items: List<NuvioCMoreItem>,
    hdrDim: Boolean,
    onClosed: () -> Unit
) {
    if (!state.open) return
    val close = {
        state.open = false
        state.page = null
        onClosed()
    }
    Dialog(
        onDismissRequest = { if (state.page != null) state.page = null else close() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.setWindowAnimations(0)
            window?.setDimAmount(0f)
        }
        val shown = remember { MutableTransitionState(false).apply { targetState = true } }
        Box(Modifier.fillMaxSize()) {
            AnimatedVisibility(visibleState = shown, enter = fadeIn(tween(120))) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
            }
            AnimatedVisibility(
                visibleState = shown,
                enter = slideInHorizontally(tween(200)) { it / 3 } + fadeIn(tween(160)),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Column(
                    Modifier
                        .fillMaxHeight()
                        .width(420.dp)
                        .padding(vertical = 20.dp)
                        .padding(end = 20.dp)
                        .nuvioCHdrDim(hdrDim, isolated = true)
                        .clip(RoundedCornerShape(20.dp))
                        .background(NuvioTheme.colors.BackgroundElevated)
                        .padding(vertical = 20.dp)
                ) {
                    val choice = state.page?.let { k -> items.firstOrNull { it.key == k } as? NuvioCMoreItem.Choice }
                    if (choice != null) NuvioCMoreSubList(state, title, choice)
                    else NuvioCMoreMainList(state, title, items)
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.NuvioCMoreMainList(state: NuvioCMoreSheetState, title: String, items: List<NuvioCMoreItem>) {
    Text(title, style = MaterialTheme.typography.headlineSmall, color = NuvioTheme.colors.TextPrimary, modifier = Modifier.padding(horizontal = 24.dp))
    Spacer(Modifier.height(8.dp))
    val target = remember { FocusRequester() }
    val targetKey = items.firstOrNull { it.key == state.lastKey }?.key ?: items.firstOrNull()?.key
    LaunchedEffect(targetKey) { target.requestFocusAfterFrames() }
    // headings go in where the section changes
    val entries = buildList<Pair<String, NuvioCMoreItem?>> {
        var last: String? = null
        items.forEach { item ->
            if (item.section != last) { add("h:${item.section}" to null); last = item.section }
            add(item.key to item)
        }
    }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(
        initialFirstVisibleItemIndex = (entries.indexOfFirst { it.first == targetKey } - 2).coerceAtLeast(0)
    )
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxWidth().weight(1f).nuvioCListGate()
    ) {
        items(entries.size, key = { entries[it].first }, contentType = { if (entries[it].second == null) 0 else 1 }) { i ->
            val (key, item) = entries[i]
            if (item == null) {
                Text(
                    key.removePrefix("h:").uppercase(java.util.Locale.ROOT),
                    style = MaterialTheme.typography.labelMedium,
                    color = NuvioTheme.colors.TextTertiary,
                    modifier = Modifier.padding(start = 12.dp, top = if (i == 0) 4.dp else 14.dp, bottom = 4.dp)
                )
            } else {
                NuvioCMoreLine(state, item, if (key == targetKey) target else null)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NuvioCMoreLine(state: NuvioCMoreSheetState, item: NuvioCMoreItem, requester: FocusRequester?) {
    var focused by remember { mutableStateOf(false) }
    val onClick: () -> Unit = {
        state.lastKey = item.key
        when (item) {
            is NuvioCMoreItem.Action -> { state.open = false; state.page = null; item.onClick() }
            is NuvioCMoreItem.Choice -> state.page = item.key
            is NuvioCMoreItem.Toggle -> item.onToggle()
            is NuvioCMoreItem.Stepper -> item.onReset()
        }
    }
    val secondary = if (focused) Color.Black.copy(alpha = 0.62f) else NuvioTheme.colors.TextSecondary
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(48.dp)
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) state.lastKey = item.key }
            .then(
                if (item is NuvioCMoreItem.Stepper) Modifier.onPreviewKeyEvent { e ->
                    val n = e.nativeKeyEvent
                    if (n.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent n.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || n.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                    when (n.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> { item.onDecrease(); true }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> { item.onIncrease(); true }
                        else -> false
                    }
                } else Modifier
            ),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = NuvioTheme.colors.TextPrimary,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black
        )
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(item.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            when (item) {
                is NuvioCMoreItem.Action -> item.value?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = secondary, maxLines = 1, modifier = Modifier.padding(start = 12.dp)) }
                is NuvioCMoreItem.Choice -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.value, style = MaterialTheme.typography.bodyMedium, color = secondary, maxLines = 1, modifier = Modifier.padding(start = 12.dp))
                    Text("  ›", style = MaterialTheme.typography.bodyLarge, color = secondary)
                }
                is NuvioCMoreItem.Toggle -> NuvioCMoreSwitch(item.on, focused)
                is NuvioCMoreItem.Stepper -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("‹", style = MaterialTheme.typography.bodyLarge, color = secondary)
                    Text(item.value, style = MaterialTheme.typography.bodyMedium, color = secondary, maxLines = 1,
                        modifier = Modifier.padding(horizontal = 10.dp))
                    Text("›", style = MaterialTheme.typography.bodyLarge, color = secondary)
                }
            }
        }
    }
}

@Composable
private fun NuvioCMoreSwitch(on: Boolean, focused: Boolean) {
    val track = when {
        on -> NuvioTheme.colors.Secondary
        focused -> Color.Black.copy(alpha = 0.25f)
        else -> Color.White.copy(alpha = 0.22f)
    }
    Box(Modifier.width(36.dp).height(20.dp).clip(RoundedCornerShape(10.dp)).background(track).padding(2.dp)) {
        Box(Modifier.align(if (on) Alignment.CenterEnd else Alignment.CenterStart).size(16.dp).clip(RoundedCornerShape(8.dp)).background(Color.White))
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.NuvioCMoreSubList(state: NuvioCMoreSheetState, title: String, choice: NuvioCMoreItem.Choice) {
    Text("$title  ›", style = MaterialTheme.typography.bodySmall, color = NuvioTheme.colors.TextTertiary, modifier = Modifier.padding(horizontal = 24.dp))
    Text(choice.label, style = MaterialTheme.typography.headlineSmall, color = NuvioTheme.colors.TextPrimary, modifier = Modifier.padding(horizontal = 24.dp))
    Spacer(Modifier.height(10.dp))
    val target = remember(choice.key) { FocusRequester() }
    val selectedIndex = choice.options.indexOfFirst { it.selected }.coerceAtLeast(0)
    LaunchedEffect(choice.key) { target.requestFocusAfterFrames() }
    LazyColumn(
        state = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = (selectedIndex - 2).coerceAtLeast(0)),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxWidth().weight(1f)
    ) {
        items(choice.options.size, key = { "${choice.key}:$it" }) { i ->
            val option = choice.options[i]
            var focused by remember { mutableStateOf(false) }
            Surface(
                onClick = {
                    if (option.closes) { state.open = false; state.page = null } else state.page = null
                    option.onPick()
                },
                modifier = Modifier.fillMaxWidth().height(48.dp)
                    .then(if (i == selectedIndex) Modifier.focusRequester(target) else Modifier)
                    .onFocusChanged { focused = it.isFocused },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = NuvioTheme.colors.TextPrimary,
                    focusedContainerColor = Color.White,
                    focusedContentColor = Color.Black
                )
            ) {
                Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(option.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (option.selected) Icon(
                        androidx.compose.material.icons.Icons.Filled.Check, contentDescription = null,
                        tint = if (focused) Color.Black else NuvioTheme.colors.Secondary, modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
