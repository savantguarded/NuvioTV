package com.nuvio.tv.ui.screens.taste

// [fork] Nuvio C round 65 TASTE_CARDS_OPEN: every Taste card with data takes focus (8% lighter fill, 2 dp accent edge,
// no zoom, "Open ›") and opens its page (boards E, F, N, O, P). Pages keep the Taste rules: #15161B cards at ~85% over
// the blurred backdrop, 1 dp white 7% edge, 14 dp corners, 40 dp rows stacked from the top (whole rows only, scrolling
// one row at a time), 20 x 30 thumbnails, title 12 sp + details 10 sp, cut text scrolling in turns on focus.
// Back from a page lands on the card it came from; Back from a title opened on a page lands on that row.

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.nuvio.tv.data.nuvioc.NuvioCTasteBar
import com.nuvio.tv.data.nuvioc.NuvioCTasteCard
import com.nuvio.tv.data.nuvioc.NuvioCTasteCardPage
import com.nuvio.tv.data.nuvioc.NuvioCTasteItem
import com.nuvio.tv.data.nuvioc.NuvioCTastePageKind
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import java.util.Locale

/** Opens a card: (card, hint, return key). Null = cards don't open (switch off, or outside the Taste tabs). */
internal val LocalTasteOpen65 = staticCompositionLocalOf<((NuvioCTasteCard, String?, String) -> Unit)?> { null }
internal val LocalTasteAccent65 = staticCompositionLocalOf { Color(0xFF7AA2FF) }
/** Opens a cast page (Wrapped "Most seen face"). */
internal val LocalTastePerson65 = staticCompositionLocalOf<((Int, String, Boolean) -> Unit)?> { null }

private val P_CARD = Color(0xD915161B) // #15161B at ~85%
private val P_EDGE = Color.White.copy(alpha = 0.07f)
private val P_MUTE = Color.White.copy(alpha = 0.64f)
private val P_INNER = Color(0xFF202128)
private const val ROW = 40

/**
 * A focusable card: OK opens [card] (or runs [action], e.g. a title or a cast page). No data ([enabled] false) = not
 * focusable. Focus = 8% lighter fill + 2 dp accent edge + "Open ›", no zoom. Put it first in the card's modifier.
 */
@Composable
internal fun Modifier.tasteOpen65(
    card: NuvioCTasteCard?,
    hint: String? = null,
    enabled: Boolean = true,
    returnKey: String = card?.name ?: "",
    radius: Int = 14,
    hintLabel: Boolean = true,
    action: (() -> Unit)? = null
): Modifier {
    if (!com.nuvio.tv.NuvioCFeatures.TASTE_CARDS_OPEN || !enabled) return this
    val open = LocalTasteOpen65.current ?: return this
    if (card == null && action == null) return this
    var focused by remember { mutableStateOf(false) }
    val req = remember { FocusRequester() }
    val accent = LocalTasteAccent65.current
    val tm = rememberTextMeasurer()
    val returning = NuvioCTasteReturn.key == "card|$returnKey"
    if (returning) LaunchedEffect(Unit) { if (runCatching { req.requestFocusAfterFrames(3) }.getOrDefault(false)) NuvioCTasteReturn.key = null }
    val fire = { if (action != null) { NuvioCTasteReturn.key = "card|$returnKey"; action() } else open(card!!, hint, returnKey) }
    return this
        .focusRequester(req)
        .onFocusChanged { focused = it.isFocused }
        .focusable()
        .onKeyEvent { e ->
            if (e.type == KeyEventType.KeyUp && (e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter)) { fire(); true } else false
        }
        .drawWithContent {
            drawContent()
            if (focused) {
                val r = CornerRadius(radius.dp.toPx())
                drawRoundRect(Color.White.copy(alpha = 0.08f), cornerRadius = r)
                val w = 2.dp.toPx()
                drawRoundRect(accent, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w), cornerRadius = r, style = Stroke(w))
                if (hintLabel && !com.nuvio.tv.NuvioCFeatures.TASTE_V66) { // [fork] round 66: no "Open ›" over the card's content
                    val text = tm.measure("Open ›", TextStyle(fontSize = 10.sp, color = accent, fontWeight = FontWeight.SemiBold))
                    drawText(text, topLeft = Offset(size.width - text.size.width - 12.dp.toPx(), 10.dp.toPx()))
                }
            }
        }
}

private fun scopeChip(open: NuvioCTasteViewModel.NuvioCCardOpen) = open.scopeLabel

@Composable
internal fun NuvioCTastePage65(viewModel: NuvioCTasteViewModel, onOpenTitle: (String, String) -> Unit) {
    val open = viewModel.openCard ?: return
    val page by viewModel.cardPage.collectAsState()
    BackHandler {
        NuvioCTasteReturn.key = "card|${open.returnKey}"
        viewModel.closeTasteCard()
    }
    val openTitle: (String, String, String) -> Unit = { id, type, rowKey ->
        NuvioCTasteReturn.key = "page|$rowKey"
        onOpenTitle(id, type)
    }
    Column(Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, top = 24.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (com.nuvio.tv.NuvioCFeatures.TASTE_V66) Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // [fork] round 66: crumb, title, summary and the scope chip share one baseline, centred in the row
            Text("${open.crumb} ›", fontSize = 12.sp, color = P_MUTE, maxLines = 1, modifier = Modifier.alignByBaseline())
            Text(page?.title ?: open.card.title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1, modifier = Modifier.alignByBaseline())
            Text(page?.summary.orEmpty(), fontSize = 12.sp, color = P_MUTE, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFeatureSettings = "tnum"), modifier = Modifier.weight(1f).alignByBaseline())
            Text(scopeChip(open), fontSize = 12.sp, color = Color.White, maxLines = 1,
                modifier = Modifier.alignByBaseline().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.1f)).padding(horizontal = 12.dp, vertical = 4.dp))
        } else Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${open.crumb} ›", fontSize = 12.sp, color = P_MUTE, maxLines = 1)
            Spacer(Modifier.width(10.dp))
            Text(page?.title ?: open.card.title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1)
            Spacer(Modifier.width(10.dp))
            Text(page?.summary.orEmpty(), fontSize = 12.sp, color = P_MUTE, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFeatureSettings = "tnum"), modifier = Modifier.weight(1f))
            Text(scopeChip(open), fontSize = 12.sp, color = Color.White, maxLines = 1,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.1f)).padding(horizontal = 12.dp, vertical = 4.dp))
        }
        val p = page
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                p == null -> Text("Reading your history", fontSize = 14.sp, color = P_MUTE, modifier = Modifier.align(Alignment.Center))
                p.empty -> Text("Nothing to show for this range", fontSize = 14.sp, color = P_MUTE, modifier = Modifier.align(Alignment.Center))
                p.kind == NuvioCTastePageKind.BREAKDOWN -> Breakdown65(p, openTitle)
                p.kind == NuvioCTastePageKind.TIMELINE -> Timeline65(p, openTitle)
                else -> Ranked65(p, openTitle)
            }
        }
    }
}

@Composable
private fun PanelCard(modifier: Modifier, title: String, dot: Color, aside: String? = null, horizontal: Int = 8, content: @Composable () -> Unit) {
    Column(modifier.background(P_CARD, RoundedCornerShape(14.dp)).border(1.dp, P_EDGE, RoundedCornerShape(14.dp)).padding(horizontal = horizontal.dp, vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth().height(26.dp).padding(start = if (horizontal < 16) 8.dp else 0.dp, end = if (horizontal < 16) 8.dp else 0.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(7.dp).background(dot, RoundedCornerShape(4.dp)))
            Text(title.uppercase(Locale.US), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp, color = P_MUTE, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
            if (aside != null) Text(aside, fontSize = 10.sp, color = P_MUTE, maxLines = 1)
        }
        content()
    }
}

/** Rows stack from the top; the box is a whole number of 40 dp rows so no row is ever cut (round 66: plus the focus room). */
@Composable
private fun WholeRows(content: @Composable (rows: Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val room = if (com.nuvio.tv.NuvioCFeatures.TASTE_V66) FOCUS_ROOM_66 * 2 else 0
        val rows = ((maxHeight.value - room) / ROW).toInt().coerceAtLeast(1)
        Box(Modifier.fillMaxWidth().height((rows * ROW + room).dp)) { content(rows) }
    }
}

/** [fork] round 66: dp of focus room above the first and below the last row of every list. */
private const val FOCUS_ROOM_66 = 3
private val listPad66: androidx.compose.foundation.layout.PaddingValues
    get() = androidx.compose.foundation.layout.PaddingValues(vertical = if (com.nuvio.tv.NuvioCFeatures.TASTE_V66) FOCUS_ROOM_66.dp else 0.dp)

/**
 * [fork] round 66 taste_v66: a held Up / Down moves one row at a time at the list's pace (repeats closer than 110 ms
 * are dropped) and never leaves the list; a single, deliberate press can still leave it (Up from the first row).
 */
@Composable
private fun Modifier.heldListKeys66(): Modifier {
    if (!com.nuvio.tv.NuvioCFeatures.TASTE_V66) return this
    val state = remember { longArrayOf(0L, 0L) } // [0] last move time, [1] 1 = the key is held
    return this
        .onPreviewKeyEvent { e ->
            if (e.key != Key.DirectionUp && e.key != Key.DirectionDown) return@onPreviewKeyEvent false
            if (e.type == KeyEventType.KeyUp) { state[1] = 0L; return@onPreviewKeyEvent false }
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val held = e.nativeKeyEvent.repeatCount > 0
            state[1] = if (held) 1L else 0L
            val now = android.os.SystemClock.uptimeMillis()
            if (held && now - state[0] < 110L) return@onPreviewKeyEvent true
            state[0] = now
            false
        }
        .focusProperties { onExit = { if (state[1] == 1L) cancelFocusChange() } }
        .focusGroup()
}

@Composable
private fun Thumb(url: String?) {
    val context = LocalContext.current
    if (url != null) {
        val r = remember(url) { ImageRequest.Builder(context).data(url).size(48, 72).build() }
        AsyncImage(r, null, contentScale = ContentScale.Crop, modifier = Modifier.width(20.dp).height(30.dp).clip(RoundedCornerShape(4.dp)).background(P_INNER))
    } else Box(Modifier.width(20.dp).height(30.dp).clip(RoundedCornerShape(4.dp)).background(P_INNER))
}

/** One 40 dp title row: thumbnail, title + details (scroll in turns when cut), optional trailing columns. */
@Composable
private fun ItemRow65(
    item: NuvioCTasteItem,
    rowKey: String,
    modifier: Modifier = Modifier,
    index: Int? = null,
    onOpen: (String, String, String) -> Unit,
    trailing: @Composable () -> Unit = {}
) {
    var focused by remember { mutableStateOf(false) }
    val accent = LocalTasteAccent65.current
    val req = remember { FocusRequester() }
    if (NuvioCTasteReturn.key == "page|$rowKey") LaunchedEffect(Unit) { if (runCatching { req.requestFocusAfterFrames(3) }.getOrDefault(false)) NuvioCTasteReturn.key = null }
    Surface(
        onClick = { val id = item.id; val type = item.type; if (id != null && type != null) onOpen(id, type, rowKey) },
        modifier = modifier.height(ROW.dp).focusRequester(req).onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(9.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent, contentColor = Color.White,
            focusedContainerColor = Color.White.copy(alpha = 0.08f), focusedContentColor = Color.White
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, accent), shape = RoundedCornerShape(9.dp)))
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (index != null) Text("$index", fontSize = 11.sp, color = P_MUTE, maxLines = 1, style = TextStyle(fontFeatureSettings = "tnum"), modifier = Modifier.width(22.dp))
            Thumb(item.poster)
            Box(Modifier.weight(1f)) {
                if (com.nuvio.tv.NuvioCFeatures.MARQUEE_MORE) com.nuvio.tv.ui.components.NuvioCTurnMarquee(
                    first = com.nuvio.tv.ui.components.NuvioCMarqueeSpec(item.name, TextStyle(fontSize = 12.sp, lineHeight = 15.sp), Color.White),
                    second = com.nuvio.tv.ui.components.NuvioCMarqueeSpec(item.sub, TextStyle(fontSize = 10.sp, lineHeight = 13.sp, fontFeatureSettings = "tnum"), P_MUTE),
                    focused = focused
                ) else Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(item.name, fontSize = 12.sp, lineHeight = 15.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.sub, fontSize = 10.sp, lineHeight = 13.sp, color = P_MUTE, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            trailing()
        }
    }
}

// ---- T1: breakdown + titles -------------------------------------------------------------------------------------

@Composable
private fun Breakdown65(p: NuvioCTasteCardPage, onOpen: (String, String, String) -> Unit) {
    val accent = LocalTasteAccent65.current
    var selected by remember(p) { mutableIntStateOf(p.focusGroup.coerceIn(0, (p.groups.size - 1).coerceAtLeast(0))) }
    val groupReqs = remember(p) { p.groups.map { FocusRequester() } }
    val firstTitle = remember(p) { FocusRequester() }
    LaunchedEffect(p) {
        if (NuvioCTasteReturn.key?.startsWith("page|") != true) runCatching { groupReqs.getOrNull(selected)?.requestFocusAfterFrames(3) }
    }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PanelCard(Modifier.width(260.dp).fillMaxHeight(), p.leftTitle, accent) {
            WholeRows { _ ->
                LazyColumn(Modifier.fillMaxSize().heldListKeys66(), contentPadding = listPad66) {
                    itemsIndexed(p.groups) { i, g ->
                        var focused by remember { mutableStateOf(false) }
                        Row(
                            Modifier.fillMaxWidth().height(ROW.dp)
                                .focusRequester(groupReqs[i])
                                .onFocusChanged { focused = it.isFocused; if (it.isFocused) selected = i }
                                .focusProperties { right = firstTitle }
                                .focusable()
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (focused || i == selected) Color.White.copy(alpha = 0.08f) else Color.Transparent)
                                .border(2.dp, if (focused) accent else Color.Transparent, RoundedCornerShape(9.dp))
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(Modifier.weight(1f)) {
                                if (com.nuvio.tv.NuvioCFeatures.MARQUEE_MORE) com.nuvio.tv.ui.components.NuvioCTurnMarquee(
                                    first = com.nuvio.tv.ui.components.NuvioCMarqueeSpec(g.label, TextStyle(fontSize = 12.sp), Color.White),
                                    second = null,
                                    focused = focused
                                ) else Text(g.label, fontSize = 12.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(g.value, fontSize = 10.sp, color = P_MUTE, maxLines = 1, style = TextStyle(fontFeatureSettings = "tnum"))
                        }
                    }
                }
            }
        }
        val group = p.groups.getOrNull(selected)
        PanelCard(Modifier.weight(1f).fillMaxHeight(), group?.label ?: "", accent, aside = group?.let { "${it.items.size} titles · newest first" }) {
            WholeRows { _ ->
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize().focusProperties { left = groupReqs.getOrNull(selected) ?: FocusRequester.Default }.heldListKeys66(),
                    contentPadding = listPad66
                ) {
                    itemsIndexed(group?.items.orEmpty(), key = { i, it -> "${group?.label}|$i|${it.id}" }) { i, item ->
                        ItemRow65(item, "${p.card.name}|${group?.label}|${item.id}", Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(firstTitle) else Modifier), onOpen = onOpen)
                    }
                }
            }
        }
    }
}

// ---- T2: timeline -----------------------------------------------------------------------------------------------

@Composable
private fun Timeline65(p: NuvioCTasteCardPage, onOpen: (String, String, String) -> Unit) {
    val accent = LocalTasteAccent65.current
    val teal = Color(0xFF4FD1C5)
    var focus by remember(p) { mutableIntStateOf(p.focusBar) }
    var chartFocused by remember { mutableStateOf(false) }
    val chartReq = remember { FocusRequester() }
    LaunchedEffect(p) { if (NuvioCTasteReturn.key?.startsWith("page|") != true) runCatching { chartReq.requestFocusAfterFrames(3) } }
    val max = p.bars.maxOfOrNull { it.minutes }?.coerceAtLeast(1) ?: 1
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PanelCard(Modifier.fillMaxWidth().height(190.dp), p.barsTitle, teal, aside = if (com.nuvio.tv.NuvioCFeatures.TASTE_V66) null else "Left / Right walks the bars · Down lists the titles", horizontal = 16) {
            Row(
                Modifier.fillMaxSize()
                    .focusRequester(chartReq)
                    .onFocusChanged { chartFocused = it.isFocused }
                    .focusable()
                    .onPreviewKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key) {
                            Key.DirectionLeft -> { focus = (focus - 1).coerceAtLeast(0); true }
                            Key.DirectionRight -> { focus = (focus + 1).coerceAtMost(p.bars.lastIndex); true }
                            else -> false
                        }
                    },
                horizontalArrangement = Arrangement.spacedBy(if (p.bars.size > 24) 3.dp else 4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                p.bars.forEachIndexed { i, b ->
                    val on = i == focus
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom)) {
                        Box(
                            Modifier.fillMaxWidth().height((112 * b.minutes / max).coerceAtLeast(2).dp)
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(if (b.minutes == 0) Color(0xFF2A2B33) else if (on) teal else teal.copy(alpha = 0.55f))
                                .then(if (on && chartFocused) Modifier.border(2.dp, accent, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)) else Modifier)
                        )
                        Text(b.tick, fontSize = 9.sp, lineHeight = 11.sp, color = if (on) Color.White else Color.White.copy(alpha = 0.5f), maxLines = 1,
                            textAlign = TextAlign.Center, style = TextStyle(fontFeatureSettings = "tnum"), modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        val bar: NuvioCTasteBar? = p.bars.getOrNull(focus)
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PanelCard(Modifier.width(260.dp).fillMaxHeight(), bar?.heading ?: "", teal, horizontal = 16) {
                bar?.ledger?.forEachIndexed { i, (k, v) ->
                    Row(Modifier.fillMaxWidth().height(39.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(k, fontSize = 12.sp, color = P_MUTE, maxLines = 1)
                        Spacer(Modifier.weight(1f))
                        Text(v, fontSize = 12.sp, color = Color.White, maxLines = 1, style = TextStyle(fontFeatureSettings = "tnum"))
                    }
                    if (i < bar.ledger.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(P_EDGE))
                }
            }
            PanelCard(Modifier.weight(1f).fillMaxHeight(), "Watched then", teal) {
                if (bar == null || bar.items.isEmpty()) Text("Nothing watched", fontSize = 12.sp, color = P_MUTE, modifier = Modifier.padding(horizontal = 8.dp))
                else WholeRows { _ ->
                    LazyColumn(Modifier.fillMaxSize().focusProperties { up = chartReq }.heldListKeys66(), contentPadding = listPad66) {
                        itemsIndexed(bar.items, key = { i, it -> "$focus|$i|${it.id}" }) { _, item ->
                            ItemRow65(item, "${p.card.name}|${bar.heading}|${item.id}", Modifier.fillMaxWidth(), onOpen = onOpen) {
                                Text(item.value.orEmpty(), fontSize = 11.sp, color = P_MUTE, maxLines = 1, style = TextStyle(fontFeatureSettings = "tnum"))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---- T3: ranked list --------------------------------------------------------------------------------------------

private enum class GapMode(val label: String) { ALL("Biggest gaps"), HIGHER("You higher"), LOWER("You lower") }

@Composable
private fun Ranked65(p: NuvioCTasteCardPage, onOpen: (String, String, String) -> Unit) {
    val accent = LocalTasteAccent65.current
    val gap = p.card == NuvioCTasteCard.VS_TMDB
    var mode by remember(p) { mutableStateOf(GapMode.ALL) }
    val rows = remember(p, mode) {
        when (mode) {
            GapMode.ALL -> p.ranked
            GapMode.HIGHER -> p.ranked.filter { (it.you ?: 0) >= (it.crowd ?: 0.0) }
            GapMode.LOWER -> p.ranked.filter { (it.you ?: 0) < (it.crowd ?: 0.0) }
        }
    }
    val firstRow = remember { FocusRequester() }
    LaunchedEffect(p) { if (NuvioCTasteReturn.key?.startsWith("page|") != true) runCatching { firstRow.requestFocusAfterFrames(3) } }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (gap) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GapMode.entries.forEach { m ->
                var f by remember { mutableStateOf(false) }
                Text(m.label, fontSize = 12.sp, color = if (m == mode) Color.White else P_MUTE, maxLines = 1,
                    modifier = Modifier.onFocusChanged { f = it.isFocused; if (it.isFocused) mode = m }.focusable()
                        .clip(RoundedCornerShape(12.dp)).background(if (m == mode) Color.White.copy(alpha = 0.1f) else Color.Transparent)
                        .border(2.dp, if (f) accent else Color.Transparent, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 4.dp))
            }
        }
        PanelCard(Modifier.fillMaxWidth().weight(1f), p.columns.firstOrNull() ?: "", accent, aside = if (gap) null else p.columns.drop(1).joinToString("   ")) {
            if (gap) Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Spacer(Modifier.weight(1f))
                listOf("YOU" to 44, "TMDB" to 44, "GAP" to 220).forEach { (t, w) ->
                    Text(t, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp, color = P_MUTE, textAlign = TextAlign.Center, modifier = Modifier.width(w.dp))
                }
            }
            if (rows.isEmpty()) Text("Nothing here", fontSize = 12.sp, color = P_MUTE, modifier = Modifier.padding(8.dp))
            else WholeRows { _ ->
                LazyColumn(Modifier.fillMaxSize().heldListKeys66(), contentPadding = listPad66) {
                    itemsIndexed(rows, key = { i, it -> "$mode|$i|${it.id}" }) { i, item ->
                        ItemRow65(item, "${p.card.name}|${mode.name}|${item.id}", Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(firstRow) else Modifier),
                            index = i + 1, onOpen = onOpen) {
                            if (gap) {
                                val you = item.you ?: 0
                                val crowd = item.crowd ?: 0.0
                                Text("$you", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.End, modifier = Modifier.width(44.dp))
                                Text(String.format(Locale.US, "%.1f", crowd), fontSize = 13.sp, color = P_MUTE, textAlign = TextAlign.End, modifier = Modifier.width(44.dp))
                                GapBar(you - crowd, Modifier.width(220.dp))
                            } else {
                                Text(item.value.orEmpty(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, style = TextStyle(fontFeatureSettings = "tnum"))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GapBar(gap: Double, modifier: Modifier) {
    val pct = (kotlin.math.abs(gap) / 5.0).coerceAtMost(1.0).toFloat() * 0.5f
    Box(modifier.height(14.dp).drawWithContent {
        val h = 8.dp.toPx()
        val top = (size.height - h) / 2
        drawRoundRect(Color(0xFF202128), topLeft = Offset(0f, top), size = Size(size.width, h), cornerRadius = CornerRadius(h / 2))
        val w = size.width * pct
        val left = if (gap >= 0) size.width / 2 else size.width / 2 - w
        drawRoundRect(if (gap >= 0) Color(0xFF4FD1C5) else Color(0xFFF6B44B), topLeft = Offset(left, top), size = Size(w, h), cornerRadius = CornerRadius(h / 2))
        drawRect(Color.White.copy(alpha = 0.25f), topLeft = Offset(size.width / 2, 0f), size = Size(1.dp.toPx(), size.height))
    })
}
