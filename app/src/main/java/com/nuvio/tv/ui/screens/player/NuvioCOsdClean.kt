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
            .height(44.dp)
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
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(22.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = nuvioCOsd(Color(0xFF3A3A3A)).copy(alpha = 0.72f),
            contentColor = nuvioCOsd(Color.White),
            focusedContainerColor = nuvioCOsd(Color.White),
            focusedContentColor = Color.Black
        )
    ) {
        Row(
            Modifier.padding(start = 12.dp, end = 16.dp).fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (iconPainter != null) Icon(iconPainter, contentDescription = null, modifier = Modifier.size(18.dp))
            else Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

/** One line in the gear's sheet. */
internal data class NuvioCMoreRow(val key: String, val label: String, val value: String?, val onClick: () -> Unit)

@Stable
internal class NuvioCMoreSheetState {
    var open by mutableStateOf(false)
    /** Row last pressed; the sheet opens on it next time. */
    var lastKey: String? = null
}

/**
 * The gear's sheet: a panel on the right over the playing video, rows with their current value. OK runs the row
 * (the sheet closes first, so a panel it opens comes back to the gear when it closes); Back closes the sheet and
 * focus goes back to the gear.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun NuvioCOsdMoreSheet(
    state: NuvioCMoreSheetState,
    title: String,
    rows: List<NuvioCMoreRow>,
    hdrDim: Boolean,
    onClosed: () -> Unit
) {
    if (!state.open) return
    val close = {
        state.open = false
        onClosed()
    }
    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.setWindowAnimations(0)
            window?.setDimAmount(0f)
        }
        val shown = remember { MutableTransitionState(false).apply { targetState = true } }
        val first = remember { FocusRequester() }
        val startIndex = rows.indexOfFirst { it.key == state.lastKey }.coerceAtLeast(0)
        LaunchedEffect(Unit) { first.requestFocusAfterFrames() }
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
                    Text(title, style = MaterialTheme.typography.headlineSmall, color = NuvioTheme.colors.TextPrimary,
                        modifier = Modifier.padding(horizontal = 24.dp))
                    Spacer(Modifier.height(12.dp))
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    ) {
                        items(rows.size, key = { rows[it].key }) { i ->
                            val row = rows[i]
                            var focused by remember { mutableStateOf(false) }
                            Surface(
                                onClick = {
                                    state.lastKey = row.key
                                    state.open = false
                                    row.onClick()
                                },
                                modifier = Modifier.fillMaxWidth().height(50.dp)
                                    .then(if (i == startIndex) Modifier.focusRequester(first) else Modifier)
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
                                    Text(row.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f))
                                    row.value?.let {
                                        Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                                            color = if (focused) Color.Black.copy(alpha = 0.65f) else NuvioTheme.colors.TextSecondary,
                                            modifier = Modifier.padding(start = 12.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
