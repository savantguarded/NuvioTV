@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.nuvio.tv.ui.screens.calendar

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.ui.screens.detail.requestFocusAfterFrames
import com.nuvio.tv.ui.theme.NuvioTheme
import java.time.LocalDate
import kotlinx.coroutines.launch

// [fork] Nuvio C cleanup: shared Calendar pieces kept from the removed Week view (Month and List use them).

/** A focusable surface that looks like the rest of the app in both styles. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun CalendarFocusCard(
    onClick: () -> Unit,
    shape: Shape,
    performance: Boolean,
    modifier: Modifier = Modifier,
    focusedScale: Float = 1.04f,
    onFocus: (Boolean) -> Unit = {},
    content: @Composable (focused: Boolean) -> Unit
) {
    val v2 = LocalV2Appearance.current != null
    var focused by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        modifier = modifier
            .onFocusChanged {
                focused = it.isFocused
                onFocus(it.isFocused)
            }
            .nuvioV2Focus(focused, shape, stationary = performance),
        shape = CardDefaults.shape(shape),
        colors = CardDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent
        ),
        border = CardDefaults.border(
            border = Border.None,
            focusedBorder = if (v2) Border.None else Border(NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs), shape = shape)
        ),
        scale = CardDefaults.scale(focusedScale = if (v2 || performance) 1f else focusedScale)
    ) { content(focused) }
}


@Composable
internal fun CalendarRoundButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    performance: Boolean,
    onClick: () -> Unit,
    tint: Color = NuvioTheme.colors.TextPrimary,
    size: Dp = 32.dp,
    modifier: Modifier = Modifier
) {
    val v2 = LocalV2Appearance.current != null
    CalendarFocusCard(onClick = onClick, shape = CircleShape, performance = performance, focusedScale = 1.1f, modifier = modifier) { focused ->
        Box(
            Modifier
                .size(size)
                .then(if (v2 && focused) Modifier.nuvioGlass(GlassRole.CONTROL, focused = true, shape = CircleShape) else Modifier)
                .clip(CircleShape)
                .background(
                    if (!v2 && focused) NuvioTheme.colors.FocusBackground
                    else NuvioTheme.colors.BackgroundCard.copy(alpha = if (v2) 0.45f else 0.8f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(size * 0.6f))
        }
    }
}

/** A title's picture with its time, label, logo and episode code laid over it. */
@Composable
internal fun CalendarArtTile(
    row: CalendarRow,
    code: String?,
    time: java.time.LocalTime?,
    showLabel: Boolean,
    logoHeight: Dp,
    modifier: Modifier = Modifier,
    picture: String? = null,
    formats: CalendarFormats = rememberCalendarFormats()
) {
    var noArt by remember(row.key) { mutableStateOf(false) } // [fork] round 33 calendar_no_art_card
    val cardOn = com.nuvio.tv.NuvioCFeatures.CALENDAR_NO_ART_CARD
    Box(modifier) {
        CalendarArtImage(
            url = picture ?: row.backdrop,
            fallback = row.poster,
            modifier = Modifier.fillMaxSize(),
            card = NuvioCCalendarCard(row.name, row.network, NuvioCCardSize.LARGE),
            onNoArt = { noArt = it }
        )
        if (!(cardOn && noArt)) Box(Modifier.fillMaxSize().background(calendarArtScrim()))
        Row(
            Modifier.align(Alignment.TopStart).fillMaxWidth().padding(NuvioTheme.spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            time?.let { CalendarBadge(formats.time(it)) } ?: Spacer(Modifier.width(1.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                if (showLabel) calendarLabelText(row.label)?.let { CalendarBadge(it, accent = true) }
                when (row.status) {
                    CalendarRowStatus.WATCHED -> CalendarStatusDot(Icons.Default.Check, stringResource(R.string.calendar_cd_watched))
                    CalendarRowStatus.BELL_ON -> CalendarStatusDot(Icons.Default.Notifications, stringResource(R.string.calendar_cd_bell_on))
                    else -> Unit
                }
            }
        }
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(NuvioTheme.spacing.sm),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)
        ) {
            if (cardOn && noArt) Spacer(Modifier.weight(1f)) // the title card carries the name
            else CalendarLogo(
                logo = row.logo,
                name = row.name,
                height = logoHeight,
                maxWidth = 200.dp,
                textStyle = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.weight(0.01f))
            code?.let { CalendarBadge(it) }
        }
    }
}

@Composable
private fun CalendarStatusDot(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String) {
    Box(
        Modifier.size(24.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.62f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = NuvioTheme.colors.Secondary, modifier = Modifier.size(16.dp))
    }
}
