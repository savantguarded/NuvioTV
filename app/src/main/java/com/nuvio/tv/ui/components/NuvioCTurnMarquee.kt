package com.nuvio.tv.ui.components

// [fork] Nuvio C round 33 (switch `calendar_day_marquee`): two text lines that scroll on focus when they
// don't fit, taking turns (first line, then second, one at a time), as the cast filmography cards do
// (round 6). Same speed, gap, start delay and three rounds as official FocusMarqueeText. Moves with
// graphicsLayer translation only, so nothing is laid out again while it scrolls. Unfocused = ellipsized.

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

/** One line of [NuvioCTurnMarquee]. */
data class NuvioCMarqueeSpec(val text: String, val style: TextStyle, val color: Color)

private val VELOCITY = 45.dp // per second, as official FocusMarqueeText
private const val START_DELAY_MS = 1_200L
private const val ROUNDS = 3

@Composable
fun NuvioCTurnMarquee(
    first: NuvioCMarqueeSpec,
    second: NuvioCMarqueeSpec?,
    focused: Boolean,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val boxPx = constraints.maxWidth
        val firstW = remember(first.text, first.style) {
            measurer.measure(first.text, first.style, softWrap = false, maxLines = 1).size.width
        }
        val secondW = remember(second?.text, second?.style) {
            second?.let { measurer.measure(it.text, it.style, softWrap = false, maxLines = 1).size.width } ?: 0
        }
        val firstOver = firstW > boxPx
        val secondOver = second != null && secondW > boxPx
        var moving by remember { mutableIntStateOf(0) } // 0 none, 1 first, 2 second
        val firstOffset = remember { Animatable(0f) }
        val secondOffset = remember { Animatable(0f) }
        val gapPx = boxPx / 3f
        val pxPerSec = with(density) { VELOCITY.toPx() }.coerceAtLeast(1f)

        LaunchedEffect(focused, firstOver, secondOver, boxPx) {
            firstOffset.snapTo(0f)
            secondOffset.snapTo(0f)
            moving = 0
            if (!focused) return@LaunchedEffect
            val order = buildList { if (firstOver) add(1); if (secondOver) add(2) }
            if (order.isEmpty()) return@LaunchedEffect
            repeat(ROUNDS) {
                for (line in order) {
                    delay(START_DELAY_MS)
                    val distance = (if (line == 1) firstW else secondW) + gapPx
                    val anim = if (line == 1) firstOffset else secondOffset
                    moving = line
                    anim.animateTo(
                        -distance,
                        tween((distance / pxPerSec * 1000f).toInt().coerceAtLeast(1), easing = LinearEasing)
                    )
                    anim.snapTo(0f)
                    moving = 0
                }
            }
        }

        Column {
            MarqueeLine(first, focused && moving == 1, firstOffset.value, gapPx)
            if (second != null) MarqueeLine(second, focused && moving == 2, secondOffset.value, gapPx)
        }
    }
}

@Composable
private fun MarqueeLine(spec: NuvioCMarqueeSpec, scrolling: Boolean, offsetPx: Float, gapPx: Float) {
    if (!scrolling) {
        Text(
            text = spec.text,
            style = spec.style,
            color = spec.color,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        return
    }
    val gapDp = with(LocalDensity.current) { gapPx.toDp() }
    Box(Modifier.fillMaxWidth().clipToBounds()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .wrapContentWidth(align = Alignment.Start, unbounded = true)
                .graphicsLayer { translationX = offsetPx }
        ) {
            Text(text = spec.text, style = spec.style, color = spec.color, maxLines = 1, softWrap = false)
            Spacer(Modifier.width(gapDp))
            Text(text = spec.text, style = spec.style, color = spec.color, maxLines = 1, softWrap = false)
        }
    }
}
