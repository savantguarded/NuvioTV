package com.nuvio.tv.ui.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nuvio.tv.R
import com.nuvio.tv.ui.components.MonochromePosterPlaceholder
import com.nuvio.tv.ui.theme.NuvioTheme

/**
 * A landscape picture: the first of [url], [alternate] and [fallback] that loads; one that fails hands over to the
 * next, so a missing episode picture shows the backdrop and then the poster.
 */
@Composable
internal fun CalendarArtImage(
    url: String?,
    modifier: Modifier = Modifier,
    fallback: String? = null,
    alternate: String? = null,
    alignment: Alignment = Alignment.Center,
    card: NuvioCCalendarCard? = null, // [fork] round 33 calendar_no_art_card
    onNoArt: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val candidates = remember(url, alternate, fallback) {
        listOfNotNull(url, alternate, fallback).filter(String::isNotBlank).distinct()
    }
    var index by remember(candidates) { mutableStateOf(0) }
    val chosen = candidates.getOrNull(index)
    Box(modifier.background(NuvioTheme.colors.BackgroundCard)) {
        androidx.compose.runtime.LaunchedEffect(chosen == null) { onNoArt(chosen == null) }
        if (chosen == null) {
            if (com.nuvio.tv.NuvioCFeatures.CALENDAR_NO_ART_CARD && card != null) NuvioCCalendarTitleCard(card)
            else MonochromePosterPlaceholder()
            return@Box
        }
        val request = remember(chosen) { ImageRequest.Builder(context).data(chosen).crossfade(false).build() }
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = alignment,
            onError = { index++ },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/** The show's logo, or its name when there is no logo or it does not load. */
@Composable
internal fun CalendarLogo(
    logo: String?,
    name: String?,
    height: Dp,
    maxWidth: Dp,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.BottomStart,
    maxTitleLines: Int = 2 // [fork] round 27 calendar_week_fixed_hero: 1 in the Week hero
) {
    var failed by remember(logo) { mutableStateOf(false) }
    val title = name ?: stringResource(R.string.calendar_unknown_title)
    if (logo.isNullOrBlank() || failed) {
        Text(
            text = title,
            style = textStyle,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = maxTitleLines,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier.widthIn(max = maxWidth)
        )
        return
    }
    val context = LocalContext.current
    val density = LocalDensity.current
    val heightPx = with(density) { height.roundToPx() }.coerceAtLeast(1)
    val request = remember(logo, heightPx) {
        ImageRequest.Builder(context).data(logo).size(heightPx * 12, heightPx * 2).crossfade(false).build()
    }
    Box(modifier.height(height).widthIn(max = maxWidth)) {
        AsyncImage(
            model = request,
            contentDescription = title,
            contentScale = ContentScale.Fit,
            alignment = alignment,
            onError = { failed = true },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/** A dark fade from the bottom of a picture so the logo and badges stay readable. */
internal fun calendarArtScrim(): Brush =
    Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.78f))

/** "★ 8.4 · Drama"; null when neither is known. */
@Composable
internal fun calendarRatingGenre(rating: Double?, genre: String?): String? {
    val locale = LocalConfiguration.current.locales[0] ?: java.util.Locale.getDefault()
    val score = rating?.takeIf { it > 0 }?.let { "★ " + String.format(locale, "%.1f", it) }
    val parts = listOfNotNull(score, genre?.takeIf(String::isNotBlank))
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
