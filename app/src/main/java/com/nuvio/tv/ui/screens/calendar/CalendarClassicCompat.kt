package com.nuvio.tv.ui.screens.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.ui.theme.NuvioTheme

// [fork] library_calendar (round 15): the Calendar screens are ysosrs123's files, copied as they are.
// His screens draw a "classic" look and, when his v2 appearance is switched on, a glass look on top.
// This app has no v2 appearance, so here the v2 pieces are stand-ins that never turn on:
// LocalV2Appearance is always null, so every `if (v2)` branch is skipped and the classic look is drawn.
// The glass modifiers return the modifier unchanged; nothing blurs, nothing extra is drawn.
// scripts/nuvio-c-calendar-sync.sh copies his files again and points their imports here.

/** Stand-in for his v2 appearance preferences; never provided, so always null. */
class CalendarV2Appearance(val settingsBackground: Any? = null)

val LocalV2Appearance = staticCompositionLocalOf<CalendarV2Appearance?> { null }

@Composable
@Suppress("UNUSED_PARAMETER")
fun V2Atmosphere(modifier: Modifier = Modifier, rich: Boolean = true, background: Any? = null) = Unit

enum class GlassRole { NAVIGATION, CONTROL, PANEL, MODAL, HUD, CARD_FOCUS, TOOLTIP }

@Composable
@Suppress("UNUSED_PARAMETER")
fun NuvioGlassSurface(
    role: GlassRole,
    modifier: Modifier = Modifier,
    focused: Boolean = false,
    shape: Shape = RoundedCornerShape(20.dp),
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier.clip(shape).background(NuvioTheme.colors.BackgroundCard), content = content)
}

@Suppress("UNUSED_PARAMETER")
fun Modifier.nuvioGlass(
    role: GlassRole,
    focused: Boolean = false,
    shape: Shape = RoundedCornerShape(20.dp),
    trailingEdgeOnly: Boolean = false,
    neutralBackdrop: Boolean = false
): Modifier = this

@Suppress("UNUSED_PARAMETER")
fun Modifier.nuvioV2Focus(
    focused: Boolean,
    shape: Shape,
    accentOverride: Color? = null,
    hardwareShadow: Boolean = true,
    stationary: Boolean = false
): Modifier = this

@Suppress("UNUSED_PARAMETER")
fun Modifier.nuvioControlSurface(shape: Shape): Modifier = this

fun Modifier.sidebarPageContent(): Modifier = this

enum class VisualQualityTier { PERFORMANCE, ENHANCED, MAXIMUM }

class CalendarVisualQuality(val tier: VisualQualityTier)

/** His default when nothing is assessed: Performance (no blur, still focus). The lighter path suits this TV. */
val LocalVisualQuality = staticCompositionLocalOf { CalendarVisualQuality(VisualQualityTier.PERFORMANCE) }

class CalendarGlassTokens(val liveBlur: Boolean)

val LocalGlassTokens = staticCompositionLocalOf { CalendarGlassTokens(liveBlur = false) }

/** His PanelActionRow (ui/components/PanelComponents.kt there), classic look only. */
@Composable
internal fun PanelActionRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    onFocused: (() -> Unit)? = null
) {
    val contentAlpha = if (enabled) 1f else 0.45f
    var focused by remember { mutableStateOf(false) }
    val actionShape = RoundedCornerShape(999.dp)
    Card(
        onClick = { if (enabled) onClick() },
        modifier = modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused?.invoke() },
        colors = CardDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.08f),
            focusedContainerColor = Color.White.copy(alpha = 0.08f)
        ),
        shape = CardDefaults.shape(actionShape),
        border = CardDefaults.border(
            border = Border(BorderStroke(1.dp, Color.Transparent), shape = actionShape),
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = actionShape)
        ),
        scale = CardDefaults.scale(focusedScale = 1f, pressedScale = 1f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = contentAlpha),
                    modifier = Modifier.width(16.dp).height(16.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = contentAlpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
