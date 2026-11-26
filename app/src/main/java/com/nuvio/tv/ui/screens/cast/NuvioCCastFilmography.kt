package com.nuvio.tv.ui.screens.cast

// [fork] Nuvio C cast page: each filmography poster gets its title plus a smaller, dimmer line with
// the character played (or "Creator · Director · Writer" for crew pages). Both lines are cut with
// "…" until the poster is focused. Focused, a line only scrolls if it does not fit; when both
// overflow they take turns (title one pass, then the role line one pass, same speed and start
// delay as official, 3 rounds, then stop), so only one line moves at a time.
// The shared poster card (GridContentCard) is untouched: it is drawn here without its label.
// Switch: NuvioCFeatures.CAST_ACTING_ONLY.

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.core.tmdb.NuvioCFilmography
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PersonDetail
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.delay

private val ROLE_COLOR = Color(0xFF8F949B)
private val MARQUEE_VELOCITY = 45.dp // per second, as official FocusMarqueeText
private const val MARQUEE_DELAY_MS = 1_200L // official basicMarquee start / repeat delay
private const val MARQUEE_ROUNDS = 3 // official MarqueeIterations

private fun nuvioCKey(item: MetaPreview): String? {
    val tmdbId = item.id.removePrefix("tmdb:").toIntOrNull() ?: return null
    val media = if (item.type == ContentType.SERIES) "tv" else "movie"
    return "$media:$tmdbId"
}

/** Role / job line for a filmography item, from PersonDetail.nuvioCRoleLines. */
internal fun nuvioCRoleLine(lines: Map<String, String>, item: MetaPreview): String? {
    if (lines.isEmpty()) return null
    return nuvioCKey(item)?.let { lines[it] }?.takeIf { it.isNotBlank() }
}

/** Is the Nuvio C cast page layout in use (role lines, tighter spacing)? */
internal fun nuvioCCastLayout(person: PersonDetail, landscapePosters: Boolean): Boolean =
    NuvioCFeatures.CAST_ACTING_ONLY && person.nuvioCRoleLines.isNotEmpty() && !landscapePosters

/**
 * Newest first by the full release / first-air date, so titles from the same year are in order
 * too. Titles TMDB gives no date at all go last (as official).
 */
internal fun nuvioCNewestFirst(credits: List<MetaPreview>, dates: Map<String, String>): List<MetaPreview> =
    credits.sortedByDescending { nuvioCFilmoSortKey(NuvioCFilmography.sortDate(dates, nuvioCKey(it), it.releaseInfo)) }

/** [fork] round 66 filmo_undated_first: no date = newer than any dated title. Pure, for tests. */
internal fun nuvioCFilmoSortKey(date: String): String =
    if (date.isEmpty() && com.nuvio.tv.NuvioCFeatures.FILMO_UNDATED_FIRST) "9999-99-99" else date

/**
 * The role line adds ~18 dp under each poster, which pushed the row past the bottom of the screen.
 * In the Nuvio C layout the hero's top padding (32 dp) and the "Filmography" header's padding
 * (12 / 8 dp) shrink to make room: ~30 dp, enough for the line and the focused poster's zoom.
 * The hero keeps 24 dp on top and 12 dp below so the focused portrait's 1.1x zoom (12 dp each
 * side) is never cut by the screen edge or runs into the header; the role line keeps ~14 dp.
 */
internal val NUVIO_C_HERO_TOP = 24.dp // room for the focused portrait's 1.1x zoom (12dp each side)
internal val NUVIO_C_HERO_BOTTOM = 12.dp // zoomed portrait clears the Filmography header
internal val NUVIO_C_HEADER_TOP = 6.dp
internal val NUVIO_C_HEADER_BOTTOM = 4.dp

/** Wraps the poster card: tracks focus for the text lines drawn under it. */
@Composable
internal fun NuvioCFilmographyItem(
    item: MetaPreview,
    roleLine: String?,
    width: Dp,
    card: @Composable () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .width(width)
            .onFocusChanged { focused = it.hasFocus }
    ) {
        card()
        NuvioCTurnTakingLines(
            title = item.name,
            role = roleLine,
            focused = focused
        )
    }
}

@Composable
private fun NuvioCTurnTakingLines(title: String, role: String?, focused: Boolean) {
    val titleStyle = MaterialTheme.typography.titleMedium
    val roleStyle = MaterialTheme.typography.bodySmall.copy(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal
    )
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = NuvioTheme.spacing.xxs, end = NuvioTheme.spacing.xxs)
    ) {
        val boxPx = constraints.maxWidth
        val titleW = remember(title, titleStyle) {
            measurer.measure(title, titleStyle, softWrap = false, maxLines = 1).size.width
        }
        val roleW = remember(role, roleStyle) {
            role?.let { measurer.measure(it, roleStyle, softWrap = false, maxLines = 1).size.width } ?: 0
        }
        val titleOverflows = titleW > boxPx
        val roleOverflows = role != null && roleW > boxPx
        // 0 = nothing moving, 1 = title, 2 = role
        var moving by remember { mutableIntStateOf(0) }
        val titleOffset = remember { Animatable(0f) }
        val roleOffset = remember { Animatable(0f) }
        val gapPx = boxPx / 3f // official marquee spacing: a third of the box
        val pxPerSec = with(density) { MARQUEE_VELOCITY.toPx() }.coerceAtLeast(1f)

        LaunchedEffect(focused, titleOverflows, roleOverflows, boxPx) {
            titleOffset.snapTo(0f)
            roleOffset.snapTo(0f)
            moving = 0
            if (!focused) return@LaunchedEffect
            val order = buildList {
                if (titleOverflows) add(1)
                if (roleOverflows) add(2)
            }
            if (order.isEmpty()) return@LaunchedEffect
            repeat(MARQUEE_ROUNDS) {
                for (line in order) {
                    delay(MARQUEE_DELAY_MS)
                    val distance = (if (line == 1) titleW else roleW) + gapPx
                    val anim = if (line == 1) titleOffset else roleOffset
                    moving = line
                    anim.animateTo(
                        targetValue = -distance,
                        animationSpec = tween(
                            durationMillis = (distance / pxPerSec * 1000f).toInt().coerceAtLeast(1),
                            easing = LinearEasing
                        )
                    )
                    anim.snapTo(0f)
                    moving = 0
                }
            }
        }

        Column {
            NuvioCMarqueeLine(
                text = title,
                style = titleStyle,
                color = NuvioTheme.colors.TextPrimary,
                scrolling = focused && moving == 1,
                offsetPx = titleOffset.value,
                gapPx = gapPx,
                modifier = Modifier.padding(top = NuvioTheme.spacing.sm)
            )
            if (role != null) {
                NuvioCMarqueeLine(
                    text = role,
                    style = roleStyle,
                    color = ROLE_COLOR,
                    scrolling = focused && moving == 2,
                    offsetPx = roleOffset.value,
                    gapPx = gapPx,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun NuvioCMarqueeLine(
    text: String,
    style: TextStyle,
    color: Color,
    scrolling: Boolean,
    offsetPx: Float,
    gapPx: Float,
    modifier: Modifier = Modifier
) {
    if (!scrolling) {
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier.fillMaxWidth()
        )
        return
    }
    val gapDp = with(LocalDensity.current) { gapPx.toDp() }
    Box(modifier = modifier.fillMaxWidth().clipToBounds()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .wrapContentWidth(align = Alignment.Start, unbounded = true)
                .graphicsLayer { translationX = offsetPx }
        ) {
            Text(text = text, style = style, color = color, maxLines = 1, softWrap = false)
            Spacer(modifier = Modifier.width(gapDp))
            Text(text = text, style = style, color = color, maxLines = 1, softWrap = false)
        }
    }
}

/** [fork] round 51 filmography_filter: which credits the Filmography row shows. */
internal enum class NuvioCFilmographyFilter { ALL, MOVIES, SHOWS }

internal fun nuvioCFilterCredits(credits: List<MetaPreview>, filter: NuvioCFilmographyFilter): List<MetaPreview> = when (filter) {
    NuvioCFilmographyFilter.ALL -> credits
    NuvioCFilmographyFilter.MOVIES -> credits.filter { it.apiType == "movie" }
    NuvioCFilmographyFilter.SHOWS -> credits.filter { it.apiType != "movie" }
}

/** [fork] round 52: how many credits each chip would show. */
internal fun nuvioCFilmographyCounts(credits: List<MetaPreview>): Map<NuvioCFilmographyFilter, Int> {
    val movies = credits.count { it.apiType == "movie" }
    return mapOf(
        NuvioCFilmographyFilter.ALL to credits.size,
        NuvioCFilmographyFilter.MOVIES to movies,
        NuvioCFilmographyFilter.SHOWS to credits.size - movies
    )
}

/**
 * All / Movies / Shows chips. Left / Right move between them, Down enters the posters; focus is never taken from the row.
 * [fork] round 52 filmography_chips_inline ([inline]): sits on the "Filmography" line (no row of its own, so the posters
 * and their titles keep their room), Movies / Shows carry counts, and the chip you chose keeps focus while the row
 * is rebuilt from its first poster.
 */
@androidx.compose.runtime.Composable
internal fun NuvioCFilmographyFilterRow(
    selected: NuvioCFilmographyFilter,
    onSelect: (NuvioCFilmographyFilter) -> Unit,
    downTarget: androidx.compose.ui.focus.FocusRequester,
    counts: Map<NuvioCFilmographyFilter, Int> = emptyMap(),
    inline: Boolean = false
) {
    val base = listOf(
        NuvioCFilmographyFilter.ALL to androidx.compose.ui.res.stringResource(com.nuvio.tv.R.string.nuvio_c_film_all),
        NuvioCFilmographyFilter.MOVIES to androidx.compose.ui.res.stringResource(com.nuvio.tv.R.string.nuvio_c_film_movies),
        NuvioCFilmographyFilter.SHOWS to androidx.compose.ui.res.stringResource(com.nuvio.tv.R.string.nuvio_c_film_shows)
    )
    val labels = base.map { (f, label) -> f to (counts[f]?.takeIf { inline && f != NuvioCFilmographyFilter.ALL && !com.nuvio.tv.NuvioCFeatures.FILMOGRAPHY_TABS_NO_COUNT }?.let { "$label $it" } ?: label) }
    val requesters = remember { NuvioCFilmographyFilter.entries.associateWith { androidx.compose.ui.focus.FocusRequester() } }
    var refocus by remember { mutableStateOf<NuvioCFilmographyFilter?>(null) }
    androidx.compose.runtime.LaunchedEffect(refocus) {
        val target = refocus ?: return@LaunchedEffect
        // the row below is rebuilt for the new filter; keep focus on the chip that was pressed
        androidx.compose.runtime.withFrameNanos { }
        androidx.compose.runtime.withFrameNanos { }
        runCatching { requesters.getValue(target).requestFocus() }
        refocus = null
    }
    Row(
        if (inline) Modifier else Modifier.padding(horizontal = NuvioTheme.spacing.xxxl, vertical = NuvioTheme.spacing.xs),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(NuvioTheme.spacing.sm),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        labels.forEach { (f, label) ->
            var focused by remember { mutableStateOf(false) }
            val on = f == selected
            androidx.tv.material3.Surface(
                onClick = {
                    if (f != selected) {
                        onSelect(f)
                        if (inline) refocus = f
                    }
                },
                modifier = Modifier
                    .then(if (inline) Modifier.focusRequester(requesters.getValue(f)) else Modifier)
                    .onFocusChanged { focused = it.isFocused }
                    .androidx_focusProps(downTarget),
                shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(androidx.compose.foundation.shape.RoundedCornerShape(50)),
                scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = if (inline) 1f else 1.04f),
                colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                    containerColor = if (on) NuvioTheme.colors.Secondary.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.10f),
                    contentColor = Color.White,
                    focusedContainerColor = Color.White,
                    focusedContentColor = Color.Black
                )
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = if (inline) 4.dp else 6.dp)
                )
            }
        }
    }
}

private fun Modifier.androidx_focusProps(down: androidx.compose.ui.focus.FocusRequester): Modifier =
    this.then(Modifier.focusProperties { this.down = down })
