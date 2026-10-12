package com.nuvio.tv.ui.screens.player

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.R
import com.nuvio.tv.ui.components.NuvioCLogoBox
import com.nuvio.tv.ui.components.NuvioCLogoSize
import com.nuvio.tv.ui.util.localizeEpisodeTitle
import com.nuvio.tv.ui.util.nuvioCLogoContrast
import kotlinx.coroutines.delay
import java.util.Date
import java.util.concurrent.TimeUnit

// [fork] Nuvio C pause screen (switch `pause_screen_c`, round 6, 2026-10-12). Replaces official's
// pause screen, same trigger (paused, after official's delay) and same keys (anything closes it,
// as before). Netflix style: the screen behind is darkened a little more on the left, "You're
// watching" above the logo (slides down into place), the episode line (series) or year (movies)
// under it, clock top right, a slim progress line with time and "Paused · ends at" at the bottom.
// No synopsis, no cast row. Nothing per frame: one fade + one slide when it opens. Bottom
// subtitles move up above the progress line while it shows (same lift as the controls).
// HDR: text and logo drawn through the same colour dim as the player controls.

/** Pause-screen logo box: bigger than the OSD logo (same visual-weight rules). */
private val PAUSE_LOGO = NuvioCLogoBox(area = 16_000f, maxWidth = 340.dp, maxHeight = 110.dp)

@Composable
internal fun NuvioCPauseScreen(
    visible: Boolean,
    viewModel: PlayerViewModel,
    liftState: NuvioCOsdState, // subtitles move up above the progress line while this shows
    title: String,
    logo: String?,
    season: Int?,
    episode: Int?,
    episodeTitle: String?,
    year: String?,
    isLive: Boolean,
    hdrDim: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(400)),
        exit = fadeOut(animationSpec = tween(250)),
        modifier = modifier
    ) {
        androidx.compose.runtime.CompositionLocalProvider(LocalNuvioCHdrDim provides hdrDim) {
            NuvioCPauseContent(viewModel, liftState, title, logo, season, episode, episodeTitle, year, isLive)
        }
    }
}

@Composable
private fun NuvioCPauseContent(
    viewModel: PlayerViewModel,
    liftState: NuvioCOsdState,
    title: String,
    logo: String?,
    season: Int?,
    episode: Int?,
    episodeTitle: String?,
    year: String?,
    isLive: Boolean
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    // Logo (and the block) slide down into place once, as the screen fades in.
    val slide = remember { Animatable(1f) }
    LaunchedEffect(Unit) { slide.animateTo(0f, tween(650, easing = FastOutSlowInEasing)) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                // Gentle all-over darkening, heavier on the left where the text sits, and a soft
                // bottom band for the progress line.
                drawRect(Color.Black.copy(alpha = 0.38f))
                drawRect(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.50f),
                        0.55f to Color.Black.copy(alpha = 0.18f),
                        1f to Color.Transparent
                    )
                )
                drawRect(
                    Brush.verticalGradient(
                        0.70f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.35f)
                    )
                )
            }
    ) {
        if (!isLive) NuvioCPauseClock(Modifier.align(Alignment.TopEnd).padding(top = 32.dp, end = 48.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 72.dp)
                .widthIn(max = 560.dp)
                .graphicsLayer {
                    translationY = -with(density) { 56.dp.toPx() } * slide.value
                }
        ) {
            Text(
                text = stringResource(R.string.nuvio_c_pause_watching),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                color = nuvioCOsd(Color.White.copy(alpha = 0.62f))
            )
            var logoFailed by remember(logo) { mutableStateOf(false) }
            var aspect by remember(logo) { mutableStateOf(NuvioCLogoSize.known(logo)) }
            if (!logo.isNullOrBlank() && !logoFailed) {
                val request = remember(context, logo) {
                    ImageRequest.Builder(context).data(logo).crossfade(true).nuvioCLogoContrast().build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    colorFilter = nuvioCOsdColorFilter(),
                    modifier = Modifier.size(PAUSE_LOGO.sizeFor(aspect)),
                    onSuccess = { aspect = NuvioCLogoSize.aspectOf(it) },
                    onError = { logoFailed = true }
                )
            } else {
                Text(
                    text = title,
                    style = MaterialTheme.typography.displaySmall,
                    color = nuvioCOsd(Color.White),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val line = if (season != null && episode != null) {
                val code = stringResource(R.string.season_episode_format, season, episode)
                val name = episodeTitle?.takeIf { it.isNotBlank() }?.localizeEpisodeTitle(context)
                if (name != null) "$code · $name" else code
            } else {
                year?.takeIf { it.isNotBlank() }
            }
            if (line != null) {
                Text(
                    text = line,
                    style = MaterialTheme.typography.titleMedium,
                    color = nuvioCOsd(Color.White.copy(alpha = 0.9f)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (!isLive) {
            NuvioCPauseProgress(
                viewModel = viewModel,
                liftState = liftState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 72.dp, end = 72.dp, bottom = 48.dp)
            )
        }
    }
}

@Composable
private fun NuvioCPauseProgress(viewModel: PlayerViewModel, liftState: NuvioCOsdState, modifier: Modifier) {
    androidx.compose.runtime.DisposableEffect(liftState) { onDispose { liftState.pauseTop = Float.NaN } }
    val timeline by viewModel.playbackTimeline.collectAsState()
    val duration = timeline.duration
    if (duration <= 0L) return
    val position = timeline.currentPosition.coerceIn(0L, duration)
    val context = LocalContext.current
    // "Ends at" moves on while you stay paused: re-read the clock once a minute.
    var minute by remember { mutableLongStateOf(System.currentTimeMillis() / 60_000L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L)
            minute = System.currentTimeMillis() / 60_000L
        }
    }
    val endsAt = remember(position, duration, minute) {
        DateFormat.getTimeFormat(context).format(Date(System.currentTimeMillis() + (duration - position)))
    }
    Column(
        modifier = modifier.onGloballyPositioned { liftState.pauseTop = it.positionInWindow().y },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(nuvioCOsd(Color.White.copy(alpha = 0.22f)), RoundedCornerShape(2.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(position.toFloat() / duration)
                    .height(3.dp)
                    .background(nuvioCOsd(Color.White.copy(alpha = 0.9f)), RoundedCornerShape(2.dp))
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "${nuvioCPauseTime(position)} / ${nuvioCPauseTime(duration)}",
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                color = nuvioCOsd(Color.White.copy(alpha = 0.7f))
            )
            Text(
                text = stringResource(R.string.nuvio_c_pause_paused) + " · " + stringResource(R.string.player_ends_at, endsAt),
                style = MaterialTheme.typography.labelLarge,
                color = nuvioCOsd(Color.White.copy(alpha = 0.7f))
            )
        }
    }
}

@Composable
private fun NuvioCPauseClock(modifier: Modifier) {
    val context = LocalContext.current
    val formatter = remember(context) { DateFormat.getTimeFormat(context) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay((60_000L - now % 60_000L).coerceAtLeast(1_000L))
        }
    }
    Text(
        text = formatter.format(Date(now)),
        style = MaterialTheme.typography.headlineSmall,
        color = nuvioCOsd(Color.White.copy(alpha = 0.85f)),
        modifier = modifier
    )
}

private fun nuvioCPauseTime(ms: Long): String {
    val h = TimeUnit.MILLISECONDS.toHours(ms)
    val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    val s = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
