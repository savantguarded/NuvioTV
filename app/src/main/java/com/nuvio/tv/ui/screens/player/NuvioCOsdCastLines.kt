package com.nuvio.tv.ui.screens.player

// [fork] Nuvio C round 29 `osd_cast`: the Cast panel's own two-line marquee (name, then character), one line
// moving at a time, same timing as the cast filmography page. Kept separate from the filmography code on
// purpose: that tweak is an optional patch and this panel must still compile when it is dropped.

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

private val VELOCITY = 45.dp // per second, as official FocusMarqueeText
private const val START_DELAY_MS = 1_200L
private const val ROUNDS = 3

@Composable
internal fun NuvioCCastLines(
    title: String,
    role: String?,
    focused: Boolean,
    titleStyle: TextStyle,
    roleStyle: TextStyle,
    titleColor: Color,
    roleColor: Color
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val boxPx = constraints.maxWidth
        val titleW = remember(title, titleStyle) { measurer.measure(title, titleStyle, softWrap = false, maxLines = 1).size.width }
        val roleW = remember(role, roleStyle) { role?.let { measurer.measure(it, roleStyle, softWrap = false, maxLines = 1).size.width } ?: 0 }
        val titleOverflows = titleW > boxPx
        val roleOverflows = role != null && roleW > boxPx
        var moving by remember { mutableIntStateOf(0) } // 0 none, 1 name, 2 character
        val titleOffset = remember { Animatable(0f) }
        val roleOffset = remember { Animatable(0f) }
        val gapPx = boxPx / 3f
        val pxPerSec = with(density) { VELOCITY.toPx() }.coerceAtLeast(1f)

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
            repeat(ROUNDS) {
                for (line in order) {
                    delay(START_DELAY_MS)
                    val distance = (if (line == 1) titleW else roleW) + gapPx
                    val anim = if (line == 1) titleOffset else roleOffset
                    moving = line
                    anim.animateTo(-distance, tween((distance / pxPerSec * 1000f).toInt().coerceAtLeast(1), easing = LinearEasing))
                    anim.snapTo(0f)
                    moving = 0
                }
            }
        }

        Column {
            Line(title, titleStyle, titleColor, focused && moving == 1, titleOffset.value, gapPx)
            if (role != null) Line(role, roleStyle, roleColor, focused && moving == 2, roleOffset.value, gapPx, Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun Line(text: String, style: TextStyle, color: Color, scrolling: Boolean, offsetPx: Float, gapPx: Float, modifier: Modifier = Modifier) {
    if (!scrolling) {
        Text(text, style = style, color = color, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, modifier = modifier.fillMaxWidth())
        return
    }
    val gapDp = with(LocalDensity.current) { gapPx.toDp() }
    Box(modifier.fillMaxWidth().clipToBounds()) {
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
