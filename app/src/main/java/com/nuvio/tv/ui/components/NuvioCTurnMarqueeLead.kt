package com.nuvio.tv.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

// [fork] Nuvio C round 53b `calendar_turn_marquee`: two lines that take turns scrolling while focused, the top line
// first and then the bottom one (as on the cast page's filmography cards), never both at once. The bottom line can
// carry a fixed lead (a tag such as NEW SHOW) that stays put while the rest scrolls. Speed, pauses and rounds follow
// official FocusMarqueeText.

private val TURN_VELOCITY = 45.dp // per second
private const val TURN_DELAY_MS = 1_200L
private const val TURN_ROUNDS = 3

@Composable
fun NuvioCTurnMarqueeLead(
    top: String,
    topStyle: TextStyle,
    topColor: Color,
    bottom: String?,
    bottomStyle: TextStyle,
    bottomColor: Color,
    focused: Boolean,
    modifier: Modifier = Modifier,
    topWeight: FontWeight? = null,
    bottomLead: (@Composable () -> Unit)? = null
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val topS = if (topWeight != null) topStyle.copy(fontWeight = topWeight) else topStyle
    val topW = remember(top, topS) { measurer.measure(top, topS, softWrap = false, maxLines = 1).size.width }
    val bottomW = remember(bottom, bottomStyle) { bottom?.let { measurer.measure(it, bottomStyle, softWrap = false, maxLines = 1).size.width } ?: 0 }
    var bottomBox by remember { mutableIntStateOf(0) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val topBox = constraints.maxWidth
        val topOver = topW > topBox
        val bottomOver = bottom != null && bottomBox > 0 && bottomW > bottomBox
        var moving by remember { mutableIntStateOf(0) }
        val topOffset = remember { Animatable(0f) }
        val bottomOffset = remember { Animatable(0f) }
        val pxPerSec = with(density) { TURN_VELOCITY.toPx() }.coerceAtLeast(1f)
        LaunchedEffect(focused, topOver, bottomOver, topBox, bottomBox) {
            topOffset.snapTo(0f); bottomOffset.snapTo(0f); moving = 0
            if (!focused) return@LaunchedEffect
            val order = buildList { if (topOver) add(1); if (bottomOver) add(2) }
            if (order.isEmpty()) return@LaunchedEffect
            repeat(TURN_ROUNDS) {
                for (line in order) {
                    delay(TURN_DELAY_MS)
                    val gap = (if (line == 1) topBox else bottomBox) / 3f
                    val distance = (if (line == 1) topW else bottomW) + gap
                    val anim = if (line == 1) topOffset else bottomOffset
                    moving = line
                    anim.animateTo(-distance, tween((distance / pxPerSec * 1000f).toInt().coerceAtLeast(1), easing = LinearEasing))
                    anim.snapTo(0f)
                    moving = 0
                }
            }
        }
        Column {
            LeadTurnLine(top, topS, topColor, focused && moving == 1, topOffset.value, topBox / 3f, Modifier.fillMaxWidth())
            if (bottom != null || bottomLead != null) Row(verticalAlignment = Alignment.CenterVertically) {
                bottomLead?.invoke()
                if (bottom != null) LeadTurnLine(bottom, bottomStyle, bottomColor, focused && moving == 2, bottomOffset.value, bottomBox / 3f,
                    Modifier.weight(1f).onSizeChanged { bottomBox = it.width })
            }
        }
    }
}

@Composable
private fun LeadTurnLine(text: String, style: TextStyle, color: Color, scrolling: Boolean, offsetPx: Float, gapPx: Float, modifier: Modifier) {
    if (!scrolling) {
        Text(text, style = style, color = color, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = modifier)
        return
    }
    val gapDp = with(LocalDensity.current) { gapPx.toDp() }
    Box(modifier.clipToBounds()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.wrapContentWidth(align = Alignment.Start, unbounded = true).graphicsLayer { translationX = offsetPx }
        ) {
            Text(text, style = style, color = color, maxLines = 1, softWrap = false)
            Spacer(Modifier.width(gapDp))
            Text(text, style = style, color = color, maxLines = 1, softWrap = false)
        }
    }
}
