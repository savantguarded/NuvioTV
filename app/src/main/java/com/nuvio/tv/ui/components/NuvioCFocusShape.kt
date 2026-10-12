package com.nuvio.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ButtonBorder
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.CardBorder
import androidx.tv.material3.CardDefaults
import com.nuvio.tv.NuvioCFeatures

// [fork] Nuvio C focus ring shape (switch `focus_ring_shape`): the focus ring always has the
// same shape as the thing it focuses.
//
// - Subtitle colour swatches (text / outline colour): the tv Card drew its own default
//   rounded-square focus border outside the circle, which the swatch row then clipped. The card's
//   borders are turned off and the swatch draws a circle ring inside its own bounds (selected =
//   white ring, focused = theme focus ring, both = focus ring with the white ring just inside).
// - Add-on manager / catalog order up and down buttons: a disabled one (first add-on's up, last
//   add-on's down) fell back to tv Button's default pill-shaped "focused disabled" border around a
//   rounded-square fill. That border now uses the theme ring in the button's own shape. Enabled
//   buttons keep official's look.

val nuvioCFocusRingShapeOn: Boolean get() = NuvioCFeatures.FOCUS_RING_SHAPE

/** No card borders at all: the swatch draws its own ring with [nuvioCSwatchRing]. */
@Composable
fun nuvioCNoCardBorder(): CardBorder = CardDefaults.border(
    border = Border.None,
    focusedBorder = Border.None,
    pressedBorder = Border.None
)

/** Circle rings drawn inside the swatch, so a list edge can never cut them. */
fun Modifier.nuvioCSwatchRing(
    focused: Boolean,
    selected: Boolean,
    focusRing: BorderStroke,
    width: Dp = 2.dp
): Modifier = drawWithContent {
    drawContent()
    val w = width.toPx()
    val outer = size.minDimension / 2f
    if (focused) {
        drawCircle(brush = focusRing.brush, radius = outer - w / 2f, style = Stroke(w))
    }
    if (selected) {
        val inset = if (focused) w + 1.dp.toPx() else 0f
        drawCircle(color = Color.White, radius = outer - inset - w / 2f, style = Stroke(w))
    }
}

/**
 * Focus border in the button's own shape. `ringWhenEnabled = false` keeps official's enabled look
 * (no ring, focus zoom) and only replaces the pill-shaped focused-disabled border.
 */
@Composable
fun nuvioCButtonBorder(focusRing: BorderStroke, shape: Shape, ringWhenEnabled: Boolean = true): ButtonBorder {
    val ring = Border(border = focusRing, shape = shape)
    return if (ringWhenEnabled) {
        ButtonDefaults.border(
            focusedBorder = ring,
            focusedDisabledBorder = ring
        )
    } else {
        ButtonDefaults.border(focusedDisabledBorder = ring)
    }
}
