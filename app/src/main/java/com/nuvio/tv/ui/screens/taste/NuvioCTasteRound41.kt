package com.nuvio.tv.ui.screens.taste

// [fork] Nuvio C round 41: Taste page polish, kept in its own file so each part can be switched off or reverted.
//
// taste_smooth       rememberTasteVerticalPivot: the focused section's top settles at a fixed line (30% down), so a held
//                    Up / Down moves the page in even steps instead of long jumps.
// taste_polish       TasteLegendColumns (two columns sized to the names), TasteSpinCardV2 (wider, gradient, poster fan,
//                    compact chips), TasteRotatingBackdrop (one recent backdrop per visit, faded in once).
// taste_review       TasteReviewBanner: Month in review above Spotlight.
// taste_card_titles  TasteTitlesPanel: a spotlight card's titles in a right-side panel (cached facts only, no network).

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nuvio.tv.data.nuvioc.NuvioCTasteAction
import com.nuvio.tv.data.nuvioc.NuvioCTastePanelItem
import com.nuvio.tv.data.nuvioc.NuvioCTasteReview
import com.nuvio.tv.ui.components.FocusMarqueeText
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.util.nuvioCListGate
import java.time.format.TextStyle
import java.util.Locale

private val R41_EDGE = 48.dp

// ---- taste_smooth -------------------------------------------------------------------------------------------

/** The focused section's top settles 30% down the screen; the list clamps at its ends, so the top stays put. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun rememberTasteVerticalPivot(): BringIntoViewSpec = remember {
    object : BringIntoViewSpec {
        override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
            val line = containerSize * 0.3f
            // a section taller than the space under the line goes to the top instead
            return if (size > containerSize - line) offset else offset - line
        }
    }
}

// ---- taste_polish -------------------------------------------------------------------------------------------

/** Two columns of three, each as wide as its longest name, so every genre and Others shows in full. */
@Composable
internal fun TasteLegendColumns(items: List<Triple<String, Int, Color>>) {
    Row(horizontalArrangement = Arrangement.spacedBy(34.dp)) {
        listOf(items.take(3), items.drop(3)).filter { it.isNotEmpty() }.forEach { col ->
            Column(Modifier.width(IntrinsicSize.Max), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                col.forEach { (name, p, color) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                        Text(name, style = MaterialTheme.typography.bodySmall, color = NuvioTheme.colors.TextSecondary, maxLines = 1,
                            modifier = Modifier.padding(start = 8.dp))
                        Spacer(Modifier.weight(1f).widthIn(min = 14.dp))
                        Text("$p%", style = MaterialTheme.typography.bodySmall, color = NuvioTheme.colors.TextTertiary, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Spin my taste: accent gradient, a fan of three posters from your top genre, the dice, title and compact chips. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun TasteSpinCardV2(title: String, chips: List<String>, posters: List<String>, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val context = LocalContext.current
    val brush = remember(accent) {
        Brush.linearGradient(
            listOf(lerp(accent, Color.Black, 0.12f), lerp(accent, Color.Black, 0.45f), lerp(accent, Color.Black, 0.78f)),
            start = Offset(0f, 0f), end = Offset(700f, 450f)
        )
    }
    Surface(
        onClick = onClick,
        modifier = modifier.width(280.dp).height(150.dp),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent, contentColor = Color.White,
            focusedContainerColor = Color.Transparent, focusedContentColor = Color.White
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)), shape = shape),
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = shape)
        )
    ) {
        Box(Modifier.fillMaxSize().background(brush)) {
            Box(Modifier.fillMaxSize().background(
                Brush.radialGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent), center = Offset(560f, 0f), radius = 420f)
            ))
            // the fan: three cached posters, drawn once
            if (posters.isNotEmpty()) Box(Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 14.dp).width(112.dp).height(96.dp)) {
                val spots = listOf(Triple(0.dp, 12.dp, -10f), Triple(60.dp, 12.dp, 10f), Triple(30.dp, 2.dp, 0f))
                val order = when (posters.size) { 1 -> listOf(2); 2 -> listOf(0, 1); else -> listOf(0, 1, 2) }
                order.forEachIndexed { i, spot ->
                    val url = posters.getOrNull(i) ?: return@forEachIndexed
                    val (x, y, r) = spots[spot]
                    val request = remember(url) { ImageRequest.Builder(context).data(url).size(104, 156).build() }
                    AsyncImage(
                        model = request, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.offset(x = x, y = y).width(52.dp).height(78.dp)
                            .graphicsLayer { rotationZ = r }
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.4f))
                    )
                }
            }
            Box(Modifier.padding(16.dp).size(34.dp).clip(RoundedCornerShape(10.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                Icon(com.nuvio.tv.ui.components.NuvioCIcons.Dice, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    chips.take(3).forEach { c ->
                        Box(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.16f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text(c, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = Color.White, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** One wide backdrop from a recent title, chosen per visit, with details-page style scrims. Static while scrolling. */
@Composable
internal fun TasteRotatingBackdrop(url: String?) {
    if (url == null) return
    val context = LocalContext.current
    val bg = NuvioTheme.colors.Background
    val request = remember(url) {
        ImageRequest.Builder(context).data(url.replace("/w1280/", "/w780/")).size(960, 540).crossfade(400).build()
    }
    Box(Modifier.fillMaxWidth().height(380.dp)) {
        AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop,
            filterQuality = androidx.compose.ui.graphics.FilterQuality.Low,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.6f })
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
            0f to bg, 0.38f to bg.copy(alpha = 0.82f), 0.75f to bg.copy(alpha = 0.25f), 1f to bg.copy(alpha = 0.45f)
        )))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to bg)))
    }
}

// ---- taste_review -------------------------------------------------------------------------------------------

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun TasteReviewBanner(review: NuvioCTasteReview, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val context = LocalContext.current
    val panel = Color(0xFF15161D)
    Surface(
        onClick = onClick,
        modifier = modifier.padding(horizontal = R41_EDGE).fillMaxWidth().height(118.dp),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.015f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = panel, contentColor = NuvioTheme.colors.TextPrimary,
            focusedContainerColor = Color(0xFF1C1D25), focusedContentColor = NuvioTheme.colors.TextPrimary
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)), shape = shape),
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = shape)
        )
    ) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.width(300.dp).fillMaxHeight()) {
                Column(Modifier.fillMaxSize().padding(6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    review.posters.chunked(6).take(2).forEach { line ->
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            line.forEach { url ->
                                val request = remember(url) { ImageRequest.Builder(context).data(url).size(92, 138).build() }
                                AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop,
                                    modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.05f)))
                            }
                            repeat(6 - line.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0.55f to Color.Transparent, 1f to panel)))
            }
            Column(Modifier.weight(1f).padding(start = 6.dp, end = 20.dp, top = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(accent))
                    val name = review.month.month.getDisplayName(TextStyle.FULL, Locale.US).uppercase(Locale.US)
                    Text(if (review.current) "$name SO FAR" else "$name IN REVIEW", style = MaterialTheme.typography.labelSmall,
                        color = NuvioTheme.colors.TextTertiary, maxLines = 1, modifier = Modifier.padding(start = 8.dp).weight(1f))
                    Text("Open month ›", style = MaterialTheme.typography.labelSmall, color = NuvioTheme.colors.TextSecondary, maxLines = 1)
                }
                Text(review.headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    ReviewStat("${review.hours} h", "Watched")
                    ReviewStat(review.titles.toString(), "Titles")
                    review.topGenre?.let { ReviewStat(it, "Top genre") }
                    review.mostWatched?.let { ReviewStat(it, "Most watched", Modifier.widthIn(max = 180.dp)) }
                    review.biggestDay?.let { d ->
                        ReviewStat(d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US) + " " + d.dayOfMonth, "Biggest day")
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = NuvioTheme.colors.TextPrimary,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.labelSmall, color = NuvioTheme.colors.TextSecondary, maxLines = 1)
    }
}

// ---- taste_card_titles --------------------------------------------------------------------------------------

/**
 * The titles behind a spotlight card, in a right-side panel over the dimmed page. Focus stays inside it; Back closes
 * it (handled by the page). Rows remember the last one focused, so Back from a title lands on it.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun TasteTitlesPanel(
    panel: NuvioCTasteViewModel.Panel?,
    listFocus: FocusRequester,
    startIndex: () -> Int,
    onIndex: (Int) -> Unit,
    onAction: (NuvioCTasteAction) -> Unit,
    onSort: (Int) -> Unit,
    onOpenTitle: (String, String) -> Unit,
    isOpen: () -> Boolean = { panel != null }
) {
    // keep the last panel drawn while it slides out
    var last by remember { mutableStateOf(panel) }
    if (panel != null) last = panel
    val shown = last
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = panel != null, enter = fadeIn(tween(180)), exit = fadeOut(tween(160))) {
            Box(Modifier.fillMaxSize().background(Color(0xFF060709).copy(alpha = 0.62f)))
        }
        AnimatedVisibility(
            visible = panel != null,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(tween(220)) { it } + fadeIn(tween(220)),
            exit = slideOutHorizontally(tween(180)) { it } + fadeOut(tween(180))
        ) {
            if (shown != null) TastePanelBody(shown, listFocus, startIndex, onIndex, onAction, onSort, onOpenTitle, isOpen)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TastePanelBody(
    p: NuvioCTasteViewModel.Panel,
    listFocus: FocusRequester,
    startIndex: () -> Int,
    onIndex: (Int) -> Unit,
    onAction: (NuvioCTasteAction) -> Unit,
    onSort: (Int) -> Unit,
    onOpenTitle: (String, String) -> Unit,
    isOpen: () -> Boolean = { true }
) {
    val card = p.card
    val items = p.items
    val sortFocus = remember { FocusRequester() }
    BoxWithConstraints(Modifier.fillMaxHeight()) {
        Column(
            Modifier.width(maxOf(360.dp, maxWidth * 0.4f).coerceAtMost(maxWidth)).fillMaxHeight()
                .background(Brush.verticalGradient(listOf(Color(0xFF17181E), Color(0xFF111216))))
                .then(
                    if (com.nuvio.tv.NuvioCFeatures.TASTE_PANEL_FOCUS) {
                        // [fork] round 42 taste_panel_focus: the exit guard sits on the panel itself. After focusGroup()
                        // it landed on the list's own group too, so Up from the list to the chips was cancelled.
                        // [fork] round 42b taste_close_focus: only while the panel is open. During its slide-out the
                        // guard kept focus from going back to the card, so it fell to the first item on screen.
                        Modifier.focusProperties {
                            onExit = { if (!com.nuvio.tv.NuvioCFeatures.TASTE_CLOSE_FOCUS || isOpen()) cancelFocusChange() }
                        }.focusGroup()
                    } else {
                        Modifier.focusGroup().focusProperties { onExit = { cancelFocusChange() } }
                    }
                )
                .padding(start = 24.dp, end = 24.dp, top = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text((card.kicker + " · " + p.scope).uppercase(Locale.US), style = MaterialTheme.typography.labelSmall,
                    color = NuvioTheme.colors.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(card.value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = NuvioTheme.colors.TextPrimary,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                val sub = if (p.recap != null) card.line.orEmpty()
                    else listOfNotNull(items?.let { "${it.size} ${if (it.size == 1) "title" else "titles"}" }, card.line).joinToString(" · ")
                if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = NuvioTheme.colors.TextSecondary,
                    maxLines = 1, softWrap = false,
                    modifier = if (p.recap != null) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier,
                    overflow = if (p.recap != null) TextOverflow.Clip else TextOverflow.Ellipsis)
            }
            p.recap?.let { TasteRecapBreakdown(it) } // [fork] round 42 taste_recap_panel
            val focusFix = com.nuvio.tv.NuvioCFeatures.TASTE_PANEL_FOCUS
            val hasList = !items.isNullOrEmpty()
            card.action?.let { a ->
                PanelPill(actionLabel(a), selected = true,
                    modifier = if (focusFix) Modifier.focusProperties { down = sortFocus } else Modifier) { onAction(a) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Recent", "Your rating", "A to Z").forEachIndexed { i, label ->
                    val base = if (i == 0) Modifier.focusRequester(sortFocus) else Modifier
                    PanelPill(label, selected = p.sort == i,
                        modifier = if (focusFix && hasList) base.focusProperties { down = listFocus } else base) { onSort(i) }
                }
            }
            when {
                items == null -> Text("Loading titles", style = MaterialTheme.typography.bodyMedium, color = NuvioTheme.colors.TextSecondary)
                items.isEmpty() -> Text("No titles to show yet", style = MaterialTheme.typography.bodyMedium, color = NuvioTheme.colors.TextSecondary)
                else -> PanelList(items, p.sort, listFocus, startIndex, onIndex, onOpenTitle,
                    upTo = if (com.nuvio.tv.NuvioCFeatures.TASTE_PANEL_FOCUS) sortFocus else null, recap = p.recap != null)
            }
        }
    }
    // [fork] round 42 taste_panel_focus: a new sort keeps focus on its chip; only the first load moves focus
    LaunchedEffect(items != null, if (com.nuvio.tv.NuvioCFeatures.TASTE_PANEL_FOCUS) p.card.id else p.sort) {
        if (items == null) return@LaunchedEffect
        if (items.isEmpty()) sortFocus.requestFocusAfterFrames() else if (!listFocus.requestFocusAfterFrames()) sortFocus.requestFocusAfterFrames()
    }
}

private fun actionLabel(a: NuvioCTasteAction): String = when (a) {
    is NuvioCTasteAction.Spin -> "Spin this"
    is NuvioCTasteAction.Person -> "Open ${a.name}"
    is NuvioCTasteAction.Title -> "Open title"
    is NuvioCTasteAction.Month -> "Month view"
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PanelPill(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(30.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f),
            contentColor = if (selected) NuvioTheme.colors.TextPrimary else NuvioTheme.colors.TextSecondary,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black
        )
    ) {
        Box(Modifier.fillMaxHeight().padding(horizontal = 13.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun PanelList(
    items: List<NuvioCTastePanelItem>,
    sort: Int,
    listFocus: FocusRequester,
    startIndex: () -> Int,
    onIndex: (Int) -> Unit,
    onOpenTitle: (String, String) -> Unit,
    upTo: FocusRequester? = null,
    recap: Boolean = false
) {
    val context = LocalContext.current
    val start = startIndex().coerceIn(0, (items.size - 1).coerceAtLeast(0))
    val requesters = remember(items, sort) { List(items.size) { FocusRequester() } }
    val listState = remember(items, sort) { androidx.compose.foundation.lazy.LazyListState(firstVisibleItemIndex = (start - 2).coerceAtLeast(0)) }
    var current by remember(items, sort) { mutableStateOf(start) }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth()
            .focusRequester(listFocus)
            .focusRestorer { requesters.getOrNull(current) ?: FocusRequester.Default }
            .then(if (com.nuvio.tv.NuvioCFeatures.TASTE_SMOOTH) Modifier.nuvioCPanelGate() else Modifier),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        itemsIndexed(items, key = { _, it -> it.id + it.type }) { i, item ->
            var focused by remember { mutableStateOf(false) }
            Surface(
                onClick = { onOpenTitle(item.id, item.type) },
                modifier = Modifier.fillMaxWidth().focusRequester(requesters[i])
                    .then(if (i == 0 && upTo != null) Modifier.focusProperties { up = upTo } else Modifier)
                    .onFocusChanged { focused = it.isFocused; if (it.isFocused) { current = i; onIndex(i) } },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent, contentColor = NuvioTheme.colors.TextPrimary,
                    focusedContainerColor = Color.White, focusedContentColor = Color.Black
                )
            ) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val request = remember(item.poster) { item.poster?.let { ImageRequest.Builder(context).data(it).size(80, 120).build() } }
                    Box(Modifier.width(40.dp).height(60.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.06f))) {
                        if (request != null) AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        FocusMarqueeText(text = item.name, focused = focused, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (focused) Color.Black else NuvioTheme.colors.TextPrimary)
                        val meta = if (recap) listOfNotNull(
                            if (item.type == "series") "Series" else listOfNotNull(item.year?.toString(), "Film").joinToString(" · "),
                            item.episodes?.let { "$it ${if (it == 1) "episode" else "episodes"}" },
                            item.lastWatched.takeIf { it > 0 }?.let { RECAP_DAY.format(java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault())) }
                        ).joinToString(" · ")
                        else listOfNotNull(item.year?.toString(), if (item.type == "series") "Series" else "Film").joinToString(" · ")
                        FocusMarqueeText(text = meta, focused = focused, style = MaterialTheme.typography.labelSmall,
                            color = if (focused) Color(0xFF555555) else NuvioTheme.colors.TextSecondary)
                    }
                    item.score?.let { s ->
                        Row(Modifier.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(12.dp),
                                tint = if (focused) Color(0xFF333333) else NuvioTheme.colors.TextSecondary)
                            val stars = s / 2.0
                            Text(if (s % 2 == 0) stars.toInt().toString() else String.format(Locale.US, "%.1f", stars),
                                style = MaterialTheme.typography.labelMedium, color = if (focused) Color(0xFF333333) else NuvioTheme.colors.TextSecondary,
                                modifier = Modifier.padding(start = 3.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Held Up / Down in the panel list: one step every 112 ms (same gate as Calendar). */
private fun Modifier.nuvioCPanelGate(): Modifier = this.nuvioCListGate()

/** Up / Down from an item jump to fixed targets (used by the review banner). */
internal fun Modifier.nuvioCTasteVertical(up: FocusRequester?, down: FocusRequester?): Modifier = onPreviewKeyEvent { e ->
    val n = e.nativeKeyEvent
    if (n.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
    when (n.keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> up?.let { runCatching { it.requestFocus() }.getOrDefault(false) } ?: false
        KeyEvent.KEYCODE_DPAD_DOWN -> down?.let { runCatching { it.requestFocus() }.getOrDefault(false) } ?: false
        else -> false
    }
}

// ---- [fork] round 42 taste_recap_panel -------------------------------------------------------------------

private val RECAP_DAY = java.time.format.DateTimeFormatter.ofPattern("d MMM", Locale.US)
private val RECAP_BIG_DAY = java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)

/** Biggest day and most watched as two small tiles, then the top three genres as bars. Long text scrolls. */
@Composable
private fun TasteRecapBreakdown(d: com.nuvio.tv.data.nuvioc.NuvioCTasteRecapDetail) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val day = d.biggestDay?.let { RECAP_BIG_DAY.format(it) + " · " + recapDuration(d.biggestDayMinutes) }
            RecapTile("Biggest day", day ?: "None", Modifier.weight(1f))
            RecapTile("Most watched", d.mostWatched ?: "None", Modifier.weight(1f))
        }
        if (d.genres.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            val top = d.genres.first().second.coerceAtLeast(1)
            d.genres.forEachIndexed { i, (name, pct) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.labelMedium, color = NuvioTheme.colors.TextSecondary,
                        maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
                        modifier = Modifier.width(92.dp).basicMarquee(iterations = Int.MAX_VALUE))
                    Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.08f))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(pct.toFloat() / top * 0.92f + 0.08f)
                            .clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.85f - i * 0.2f)))
                    }
                    Text("$pct%", style = MaterialTheme.typography.labelMedium, color = NuvioTheme.colors.TextTertiary,
                        maxLines = 1, modifier = Modifier.width(40.dp).padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun RecapTile(label: String, value: String, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.06f)).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = NuvioTheme.colors.TextTertiary, maxLines = 1)
        Text(value, style = MaterialTheme.typography.labelLarge, color = NuvioTheme.colors.TextPrimary,
            maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE))
    }
}

private fun recapDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m m"
        m == 0 -> "$h h"
        else -> "$h h $m m"
    }
}
