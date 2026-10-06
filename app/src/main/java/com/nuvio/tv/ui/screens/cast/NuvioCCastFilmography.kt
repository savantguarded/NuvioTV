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
    credits.sortedByDescending { NuvioCFilmography.sortDate(dates, nuvioCKey(it), it.releaseInfo) }

/**
 * The role line adds ~18 dp under each poster, which pushed the row past the bottom of the screen.
 * In the Nuvio C layout the hero's top padding (32 dp) and the "Filmography" header's padding
 * (12 / 8 dp) shrink to make room: ~30 dp, enough for the line and the focused poster's zoom.
 */
internal val NUVIO_C_HERO_TOP = 12.dp
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
