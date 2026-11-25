package com.nuvio.tv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import kotlin.math.max
import kotlin.math.min
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import com.nuvio.tv.core.player.LetterboxDetector
import com.nuvio.tv.core.player.LetterboxSampler
import com.nuvio.tv.core.player.LetterboxTracker
import com.nuvio.tv.core.player.LocalTrailerPlayerPool
import com.nuvio.tv.core.player.TrailerPlayerPool
import com.nuvio.tv.data.local.SubtitleStyleSettings
import com.nuvio.tv.data.trailer.TrailerSubtitleTrack
import com.nuvio.tv.ui.screens.player.SUBTITLE_BASE_TEXT_SP
import com.nuvio.tv.ui.screens.player.rememberSubtitleTypeface
import android.graphics.Typeface
import android.util.TypedValue
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.MutableStateFlow
import com.nuvio.tv.data.trailer.YoutubeChunkedDataSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import android.view.LayoutInflater
import android.view.TextureView
import com.nuvio.tv.R
import kotlinx.coroutines.delay

private fun buildTrailerMediaItem(videoUrl: String, subtitle: TrailerSubtitleTrack?): MediaItem =
    MediaItem.Builder()
        .setUri(videoUrl)
        .apply {
            if (subtitle != null) {
                setSubtitleConfigurations(
                    listOf(
                        MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.url))
                            .setMimeType(MimeTypes.TEXT_VTT)
                            .setLanguage(subtitle.languageCode)
                            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                            .build()
                    )
                )
            }
        }
        .build()

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun TrailerPlayer(
    trailerUrl: String?,
    trailerAudioUrl: String? = null,
    isPlaying: Boolean,
    isPaused: Boolean = false,
    onEnded: () -> Unit,
    onError: () -> Unit = {},
    onFirstFrameRendered: () -> Unit = {},
    muted: Boolean = false,
    seekRequestToken: Int = 0,
    seekDeltaMs: Long = 0L,
    onProgressChanged: (positionMs: Long, durationMs: Long) -> Unit = { _, _ -> },
    onRemoteKey: (keyCode: Int, action: Int, repeatCount: Int) -> Boolean = { _, _, _ -> false },
    cropToFill: Boolean = false,
    overscanZoom: Float = 1f,
    autoCropLetterbox: Boolean = false,
    nuvioCHold: Boolean = false, // [fork] page covered: pause here, carry on from the same spot (NuvioCTrailerHold.kt)
    nuvioCFadeInOnStart: Boolean = false, // [fork] start silent and fade the sound in over 1 s (background trailer with sound)
    nuvioCResume: Boolean = false, // [fork] round 31 trailer_resume: details background trailer only (NuvioCTrailerResume.kt)
    detectLetterbox: Boolean = false,
    modifier: Modifier = Modifier,
    enter: EnterTransition = fadeIn(animationSpec = tween(800)),
    exit: ExitTransition = fadeOut(animationSpec = tween(500)),
    trailerPlayerPool: TrailerPlayerPool? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activityLifecycleOwner = remember(context) { context as? androidx.lifecycle.LifecycleOwner ?: lifecycleOwner }
    val currentIsPlaying by rememberUpdatedState(isPlaying)
    val currentTrailerUrl by rememberUpdatedState(trailerUrl)
    val currentTrailerAudioUrl by rememberUpdatedState(trailerAudioUrl)
    val currentOnEnded by rememberUpdatedState(onEnded)
    val currentOnError by rememberUpdatedState(onError)
    val currentOnFirstFrameRendered by rememberUpdatedState(onFirstFrameRendered)
    val currentOnProgressChanged by rememberUpdatedState(onProgressChanged)
    val currentOnRemoteKey by rememberUpdatedState(onRemoteKey)
    val currentResume by rememberUpdatedState(nuvioCResume) // [fork] trailer_resume
    val zoomScale = if (cropToFill) overscanZoom.coerceAtLeast(1f) else 1f
    val resolvedPool = trailerPlayerPool ?: LocalTrailerPlayerPool.current
    val ownerToken = remember { Any() }
    val rootView = LocalView.current
    val incomingHandoff = remember(trailerUrl, resolvedPool) { resolvedPool?.pendingHandoffFor(trailerUrl) }
    val seamlessHandoff = remember(incomingHandoff, rootView) {
        incomingHandoff?.sourceBounds?.coversView(rootView.width, rootView.height) == true
    }
    val hasRenderedFirstFrameState = remember(trailerUrl) { mutableStateOf(seamlessHandoff) }
    var hasRenderedFirstFrame by hasRenderedFirstFrameState
    val currentHasRenderedFirstFrameState by rememberUpdatedState(hasRenderedFirstFrameState)
    var subtitleTrack by remember(trailerUrl) { mutableStateOf<TrailerSubtitleTrack?>(null) }
    val currentSubtitleTrack by rememberUpdatedState(subtitleTrack)
    val subtitleStyle by remember(resolvedPool) {
        resolvedPool?.subtitleStyle ?: MutableStateFlow(SubtitleStyleSettings())
    }.collectAsState()
    val subtitleTypeface = rememberSubtitleTypeface(subtitleStyle.fontId)
    val playerAlphaState = animateFloatAsState(
        targetValue = if (isPlaying && hasRenderedFirstFrame) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "trailerFirstFrameAlpha"
    )
    val playerViewRef = remember { mutableStateOf<PlayerView?>(null) }
    var letterboxZoom by remember(trailerUrl) { mutableFloatStateOf(incomingHandoff?.letterboxZoom ?: 1f) }
    var letterboxZoomLocked by remember(trailerUrl) { mutableStateOf(incomingHandoff?.letterboxZoom != null) }
    val letterboxZoomState = animateFloatAsState(
        targetValue = if (autoCropLetterbox) letterboxZoom else 1f,
        animationSpec = tween(durationMillis = 400),
        label = "trailerLetterboxZoom"
    )
    val lastBounds = remember { arrayOfNulls<Rect>(1) }

    // Use the shared pool instance instead of creating a new ExoPlayer per focus.
    // The pool keeps one ExoPlayer alive across poster focus changes, eliminating
    // the expensive create/teardown cycle that was the app-launch bottleneck.
    val trailerPlayer = remember(trailerUrl, resolvedPool) {
        if (trailerUrl != null) {
            resolvedPool?.acquire()
        } else {
            null
        }
    }

    // Configure player settings when acquired
    LaunchedEffect(trailerPlayer, muted, cropToFill) {
        val player = trailerPlayer ?: return@LaunchedEffect
        if (muted || !player.isPlaying) player.volume = if (muted) 0f else 1f // [fork] unmute mid-play fades in below
        player.videoScalingMode = if (cropToFill) {
            C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
        } else {
            C.VIDEO_SCALING_MODE_SCALE_TO_FIT
        }
    }

    // [fork] background trailer with sound: fade in from silence once the first frame is up
    LaunchedEffect(trailerPlayer, hasRenderedFirstFrame, nuvioCFadeInOnStart) {
        val player = trailerPlayer ?: return@LaunchedEffect
        if (!nuvioCFadeInOnStart || muted || !hasRenderedFirstFrame || player.volume >= 1f) return@LaunchedEffect
        val start = player.volume
        for (step in 1..10) {
            player.volume = start + (1f - start) * step / 10f
            delay(100)
        }
    }

    // [fork] fade the sound in when a playing trailer is unmuted (trailer button on a muted background trailer)
    LaunchedEffect(trailerPlayer, muted) {
        val player = trailerPlayer ?: return@LaunchedEffect
        if (muted || !player.isPlaying) return@LaunchedEffect
        val start = player.volume
        for (step in 1..10) {
            player.volume = start + (1f - start) * step / 10f
            delay(100)
        }
    }

    LaunchedEffect(isPlaying, trailerUrl, trailerAudioUrl, trailerPlayer) { // [fork] no `muted` key: unmuting must not reload
        val player = trailerPlayer ?: return@LaunchedEffect
        player.volume = if (muted || (nuvioCFadeInOnStart && isPlaying)) 0f else 1f // [fork] fade-in starts silent
        if (isPlaying && trailerUrl != null) {
            if (resolvedPool?.isLoadedBy(ownerToken, trailerUrl) == true && player.currentMediaItem != null) {
                player.playWhenReady = true
                return@LaunchedEffect
            }
            val claimedHandoff = resolvedPool?.claimHandoff(ownerToken, trailerUrl)
            if (claimedHandoff != null) {
                letterboxZoom = claimedHandoff.letterboxZoom ?: 1f
                letterboxZoomLocked = claimedHandoff.letterboxZoom != null
                player.playWhenReady = true
                return@LaunchedEffect
            }
            hasRenderedFirstFrame = false
            val subtitle = resolvedPool?.subtitleFor(trailerUrl)
            subtitleTrack = subtitle
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setPreferredTextLanguage(subtitle?.languageCode)
                .build()
            if (!trailerAudioUrl.isNullOrBlank()) {
                val mediaSourceFactory = DefaultMediaSourceFactory(YoutubeChunkedDataSourceFactory())
                val videoSource = mediaSourceFactory.createMediaSource(buildTrailerMediaItem(trailerUrl, subtitle))
                val audioSource = mediaSourceFactory.createMediaSource(MediaItem.fromUri(trailerAudioUrl))
                player.setMediaSource(MergingMediaSource(videoSource, audioSource))
            } else {
                player.setMediaItem(buildTrailerMediaItem(trailerUrl, subtitle))
            }
            if (nuvioCResume) NuvioCTrailerResume.startAt(trailerUrl).takeIf { it > 0L }?.let { player.seekTo(it) } // [fork] trailer_resume
            player.prepare()
            resolvedPool?.onMediaLoaded(ownerToken, trailerUrl)
            lastBounds[0]?.let { resolvedPool?.onOwnerBounds(ownerToken, it) }
            player.playWhenReady = true
        } else {
            hasRenderedFirstFrame = false
            if (resolvedPool?.isHandoffPending() == true) return@LaunchedEffect
            if (resolvedPool?.isOwnerOrFree(ownerToken) == false) return@LaunchedEffect
            player.playWhenReady = false
            // Defer heavy stop and clear until focus settling/collapse has finished
            delay(150)
            if (!isPlaying) {
                player.stop()
                player.clearMediaItems()
                resolvedPool?.onMediaCleared(ownerToken)
            }
        }
    }

    LaunchedEffect(trailerPlayer, cropToFill) {
        val player = trailerPlayer ?: return@LaunchedEffect
        player.videoScalingMode = if (cropToFill) {
            C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
        } else {
            C.VIDEO_SCALING_MODE_SCALE_TO_FIT
        }
    }

    val letterboxDetectionEnabled = autoCropLetterbox || detectLetterbox
    LaunchedEffect(letterboxDetectionEnabled, hasRenderedFirstFrame, trailerPlayer) {
        if (letterboxZoomLocked) return@LaunchedEffect
        letterboxZoom = 1f
        if (!letterboxDetectionEnabled || !hasRenderedFirstFrame) return@LaunchedEffect
        val player = trailerPlayer ?: return@LaunchedEffect
        val tracker = LetterboxTracker()
        val sampler = LetterboxSampler()
        val nuvioCSmooth = com.nuvio.tv.NuvioCFeatures.BG_TRAILER_SMOOTH // [fork] bg_trailer_smooth
        var nuvioCClean = 0 // [fork] frames in a row with picture and no bars
        try {
            while (true) {
                delay(LetterboxDetector.SAMPLE_INTERVAL_MS)
                if (LetterboxDetector.isSampleWindowOver(player.currentPosition, player.duration)) break
                if (!player.isPlaying) continue
                // [fork] the sample is a GPU read-back on the UI thread (dearer on HD frames): skip it while the user is moving
                if (nuvioCSmooth && com.nuvio.tv.core.NuvioCInput.recentlyActive(NUVIO_C_SAMPLE_QUIET_MS)) continue
                // [fork] round 52 details_smooth: nor while the page is still gliding after the key was let go
                if (com.nuvio.tv.NuvioCFeatures.DETAILS_SMOOTH && com.nuvio.tv.core.NuvioCInput.pageScrolling) continue
                val textureView = playerViewRef.value?.videoSurfaceView as? TextureView ?: continue
                val bar = sampler.sample(textureView) ?: continue
                if (nuvioCSmooth) { // [fork] no bars on 10 real frames in a row: full-frame trailer, stop checking
                    nuvioCClean = if (bar <= 0f) nuvioCClean + 1 else 0
                    if (nuvioCClean >= NUVIO_C_CLEAN_SAMPLES_TO_STOP) break
                }
                letterboxZoom = tracker.onSample(bar) ?: continue
                resolvedPool?.onLetterboxZoomChanged(ownerToken, letterboxZoom)
                break
            }
        } finally {
            sampler.release()
        }
    }

    val nuvioCHoldBusy by NuvioCTrailerHoldEffect( // [fork]
        player = trailerPlayer,
        pool = resolvedPool,
        isPlaying = isPlaying,
        hold = nuvioCHold,
        isPaused = isPaused,
        muted = muted,
        trailerUrl = trailerUrl,
        trailerAudioUrl = trailerAudioUrl,
        reattachView = { playerViewRef.value?.let { view -> view.player = null; view.player = trailerPlayer } },
        onLost = { currentOnEnded() }
    )

    LaunchedEffect(isPaused, trailerPlayer) {
        val player = trailerPlayer ?: return@LaunchedEffect
        if (!isPlaying) return@LaunchedEffect
        player.playWhenReady = !isPaused && !nuvioCHoldBusy // [fork] a held trailer resumes through NuvioCTrailerHold
    }

    LaunchedEffect(seekRequestToken, seekDeltaMs, trailerPlayer) {
        val player = trailerPlayer ?: return@LaunchedEffect
        if (seekRequestToken <= 0) return@LaunchedEffect
        val duration = player.duration.takeIf { it > 0 } ?: 0L
        val current = player.currentPosition
        val target = (current + seekDeltaMs).coerceIn(0L, duration.coerceAtLeast(0L))
        player.seekTo(target)
    }

    LaunchedEffect(trailerPlayer, isPlaying) {
        val player = trailerPlayer ?: return@LaunchedEffect
        while (isPlaying) {
            val position = player.currentPosition.coerceAtLeast(0L)
            val duration = player.duration.takeIf { it > 0 } ?: 0L
            if (currentResume && player.isPlaying) NuvioCTrailerResume.record(currentTrailerUrl, position) // [fork] trailer_resume
            currentOnProgressChanged(position, duration)
            delay(250)
        }
        currentOnProgressChanged(0L, 0L)
    }

    DisposableEffect(activityLifecycleOwner, trailerPlayer) {
        val player = trailerPlayer ?: return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED &&
                    currentIsPlaying &&
                    player.playWhenReady &&
                    !nuvioCHoldBusy // [fork] not another page's trailer ending
                ) {
                    NuvioCTrailerResume.forget(currentTrailerUrl) // [fork] trailer_resume: watched to the end
                    currentOnEnded()
                }
            }

            override fun onRenderedFirstFrame() {
                currentHasRenderedFirstFrameState.value = true
                currentOnFirstFrameRendered()
            }

            // A trailer that fails to load or decode never renders a frame, so without this the
            // caller stays in its trailer layout over a transparent player.
            override fun onPlayerError(error: PlaybackException) {
                if (currentIsPlaying) {
                    currentOnError()
                }
            }
        }
        // Registering on a resumed screen delivers ON_RESUME at once; only reload media the
        // lifecycle stopped, so the first load stays with the effect above and gets its subtitles.
        var stoppedByLifecycle = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (currentIsPlaying && !currentTrailerUrl.isNullOrBlank()) {
                        if (player.currentMediaItem == null && stoppedByLifecycle) {
                            if (!currentTrailerAudioUrl.isNullOrBlank()) {
                                val mediaSourceFactory = DefaultMediaSourceFactory(YoutubeChunkedDataSourceFactory())
                                val videoSource = mediaSourceFactory.createMediaSource(buildTrailerMediaItem(currentTrailerUrl!!, currentSubtitleTrack))
                                val audioSource = mediaSourceFactory.createMediaSource(MediaItem.fromUri(currentTrailerAudioUrl!!))
                                player.setMediaSource(MergingMediaSource(videoSource, audioSource))
                            } else {
                                player.setMediaItem(buildTrailerMediaItem(currentTrailerUrl!!, currentSubtitleTrack))
                            }
                            player.prepare()
                            resolvedPool?.onMediaLoaded(ownerToken, currentTrailerUrl!!)
                        }
                        player.playWhenReady = true
                    }
                }
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP -> {
                    stoppedByLifecycle = true
                    player.playWhenReady = false
                    player.pause()
                    player.stop()
                    player.clearMediaItems()
                }
                // Do NOT release on destroy — the pool owns the lifecycle.
                else -> Unit
            }
        }
        player.addListener(listener)
        activityLifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            runCatching { activityLifecycleOwner.lifecycle.removeObserver(observer) }
            runCatching { player.removeListener(listener) }
            // Only stop — never release. The pool manages the ExoPlayer lifecycle.
            resolvedPool?.stopIfOwner(ownerToken)
        }
    }

    if (trailerPlayer != null) {
        AnimatedVisibility(
            visible = isPlaying,
            enter = enter,
            exit = exit
        ) {
            AndroidView(
                factory = { ctx ->
                    (LayoutInflater.from(ctx).inflate(R.layout.trailer_player_view, null) as PlayerView).apply {
                        playerViewRef.value = this
                        player = trailerPlayer
                        isFocusable = false
                        isFocusableInTouchMode = false
                        setOnKeyListener { _, keyCode, event ->
                            currentOnRemoteKey(keyCode, event.action, event.repeatCount)
                        }
                        keepScreenOn = true
                        // YouTube caption tracks ship their own colours and a black
                        // window; dropping the embedded styles leaves a plain readable
                        // caption instead of the source's own box.
                        subtitleView?.apply {
                            setApplyEmbeddedStyles(false)
                            setApplyEmbeddedFontSizes(false)
                            setBottomPaddingFraction(SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION)
                        }
                        resizeMode = if (cropToFill) {
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        } else {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    }
                },
                update = { view ->
                    // Re-attach player in case it was reclaimed after yield
                    if (view.player !== trailerPlayer) {
                        view.player = trailerPlayer
                    }
                    view.subtitleView?.applyTrailerSubtitleStyle(subtitleStyle, subtitleTypeface)
                    view.resizeMode = if (cropToFill) {
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    } else {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                onRelease = { view ->
                    playerViewRef.value = null
                    view.player = null
                    view.keepScreenOn = false
                },
                modifier = modifier
                    .onGloballyPositioned { coordinates ->
                        val bounds = coordinates.boundsInWindow()
                        lastBounds[0] = bounds
                        resolvedPool?.onOwnerBounds(ownerToken, bounds)
                    }
                    .clipToBounds()
                    .graphicsLayer {
                        alpha = playerAlphaState.value
                        scaleX = zoomScale * letterboxZoomState.value
                        scaleY = zoomScale * letterboxZoomState.value
                    }
            )
        }
    }
}

// [fork] bg_trailer_smooth: black-bar sampling waits this long after a key press, and gives up after
// this many frames with picture and no bars (about 4 s of a full-frame trailer).
private const val NUVIO_C_SAMPLE_QUIET_MS = 900L
private const val NUVIO_C_CLEAN_SAMPLES_TO_STOP = 10
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun SubtitleView.applyTrailerSubtitleStyle(style: SubtitleStyleSettings, typeface: Typeface?) {
    val metrics = resources.displayMetrics
    val textSizePx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        SUBTITLE_BASE_TEXT_SP * style.size / 100f,
        metrics
    )
    setFractionalTextSize(textSizePx / min(metrics.widthPixels, metrics.heightPixels).coerceAtLeast(1))
    val edgeType = when {
        style.outlineEnabled -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
        style.shadowEnabled -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
        else -> CaptionStyleCompat.EDGE_TYPE_NONE
    }
    val edgeColor = if (style.outlineEnabled) {
        style.outlineColor
    } else {
        android.graphics.Color.argb(style.shadowOpacity.coerceIn(0, 100) * 255 / 100, 0, 0, 0)
    }
    setStyle(
        CaptionStyleCompat(
            style.textColor,
            style.backgroundColor,
            android.graphics.Color.TRANSPARENT,
            edgeType,
            edgeColor,
            Typeface.create(typeface ?: Typeface.DEFAULT, if (style.bold) Typeface.BOLD else Typeface.NORMAL)
        )
    )
}

private fun Rect.coversView(viewWidth: Int, viewHeight: Int): Boolean {
    if (viewWidth <= 0 || viewHeight <= 0) return false
    val coveredWidth = (min(right, viewWidth.toFloat()) - max(left, 0f)).coerceAtLeast(0f)
    val coveredHeight = (min(bottom, viewHeight.toFloat()) - max(top, 0f)).coerceAtLeast(0f)
    return coveredWidth * coveredHeight >= viewWidth * viewHeight * FULL_SCREEN_SOURCE_COVERAGE
}

private const val FULL_SCREEN_SOURCE_COVERAGE = 0.95f
