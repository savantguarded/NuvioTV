package com.nuvio.tv.ui.screens.player

// [fork] Nuvio C player OSD additions, kept in this file so PlayerScreen.kt only gets a few hook lines:
//  - badges bottom-right: resolution · visual tag · audio · file size (plain text, year-line style)
//  - subtitle lift: while the OSD is open, subtitles move up so they clear the whole bottom block
//    (title, episode line, seek bar, buttons). Only the finished subtitle layer moves; saved
//    subtitle settings, cue parsing and libass rendering are never touched.

import android.view.View
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.nuvio.tv.NuvioCFeatures
import androidx.annotation.VisibleForTesting
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.ui.PlayerView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.data.local.InternalPlayerEngine
import com.nuvio.tv.data.local.SubtitleStyleSettings
import java.util.Locale
import kotlin.math.roundToInt

/** Layout facts the OSD hooks report, shared by the badges and the subtitle lift. */
@Stable
internal class NuvioCOsdState {
    /** Window Y of the top of the OSD's bottom block (title down to buttons). NaN = not laid out. */
    var bottomBlockTop by mutableFloatStateOf(Float.NaN)
    /** Window Y of the last text baseline of the title block (year or episode line). NaN = hidden. */
    var titleLastBaseline by mutableFloatStateOf(Float.NaN)
    var badgesWidthPx by mutableIntStateOf(0)
}

private val BADGE_TITLE_GAP = 24.dp
private val SUBTITLE_OSD_GAP = 12.dp
private const val LIFT_ANIM_MS = 200 // same as the OSD fade

/** Bottom block hook: remembers where its top is, for the subtitle lift. */
internal fun Modifier.nuvioCOsdBottomBlock(state: NuvioCOsdState): Modifier =
    onGloballyPositioned { state.bottomBlockTop = it.positionInWindow().y }

/** Title block hook: reports its last baseline and keeps clear of the badges on the right. */
@Composable
internal fun Modifier.nuvioCOsdTitleBlock(state: NuvioCOsdState): Modifier {
    DisposableEffect(state) {
        onDispose { state.titleLastBaseline = Float.NaN }
    }
    val endPadding = with(LocalDensity.current) {
        if (state.badgesWidthPx > 0) state.badgesWidthPx.toDp() + BADGE_TITLE_GAP else 0.dp
    }
    return this
        .padding(end = endPadding)
        .onGloballyPositioned { coords ->
            val baseline = coords[LastBaseline]
            val top = coords.positionInWindow().y
            state.titleLastBaseline = if (baseline != AlignmentLine.Unspecified) {
                top + baseline
            } else {
                top + coords.size.height * 0.8f
            }
        }
}

/**
 * Badges, bottom-right of the OSD, sharing a baseline with the last line of the title block.
 * Outline chips (Apple TV style): thin border, small caps, drawn once per OSD open (no blur,
 * no animation while playing). Refreshed when the audio track or stream changes.
 */
@Composable
internal fun NuvioCOsdBadges(
    viewModel: PlayerViewModel,
    uiState: PlayerUiState,
    state: NuvioCOsdState,
    endPadding: Dp
) {
    if (!NuvioCFeatures.OSD_BADGES) return
    val parts = remember(uiState.audioTracks, uiState.currentStreamName, uiState.isBuffering, uiState.internalPlayerEngine) {
        runCatching { buildBadgeParts(viewModel.controller, uiState) }.getOrNull().orEmpty()
    }
    if (parts.isEmpty()) {
        SideEffect { state.badgesWidthPx = 0 }
        return
    }
    val baseline = state.titleLastBaseline
    val alpha by animateFloatAsState(
        targetValue = if (baseline.isNaN()) 0f else 1f,
        animationSpec = tween(150),
        label = "nuvioCBadgesAlpha"
    )
    var originY by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { originY = it.positionInWindow().y }
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = endPadding)
                .onSizeChanged { state.badgesWidthPx = it.width }
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val target = state.titleLastBaseline
                    val rowBaseline = placeable[LastBaseline].takeIf { it != AlignmentLine.Unspecified }
                        ?: placeable.height
                    val y = if (target.isNaN()) 0 else (target - originY - rowBaseline).roundToInt()
                    layout(placeable.width, placeable.height) { placeable.place(0, y) }
                }
                .graphicsLayer { this.alpha = alpha }
        ) {
            parts.forEach { part ->
                Text(
                    text = part.uppercase(Locale.ROOT),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    ),
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1,
                    modifier = Modifier
                        .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp)
                )
            }
        }
    }
    DisposableEffect(state) { onDispose { state.badgesWidthPx = 0 } }
}

private fun buildBadgeParts(controller: PlayerRuntimeController, uiState: PlayerUiState): List<String> {
    val exoFormat = if (controller.currentInternalPlayerEngine == InternalPlayerEngine.MVP_PLAYER) null
    else controller._exoPlayer?.videoFormat
    val names = listOfNotNull(
        uiState.currentStreamName,
        controller.currentFilename,
        controller.currentStreamDescription
    ).joinToString(" ")
    val width = exoFormat?.width?.takeIf { it > 0 }
        ?: controller.currentVideoTrackWidth.takeIf { it > 0 }
        ?: controller.currentVideoWidth
    val height = exoFormat?.height?.takeIf { it > 0 }
        ?: controller.currentVideoTrackHeight.takeIf { it > 0 }
        ?: controller.currentVideoHeight
    val usingExo = exoFormat != null || controller.currentVideoTrackMimeType != null
    val visual = NuvioCOsdBadges.visualLabel(
        mimeType = exoFormat?.sampleMimeType ?: controller.currentVideoTrackMimeType,
        codecs = exoFormat?.codecs ?: controller.currentVideoTrackCodecs,
        colorTransfer = exoFormat?.colorInfo?.colorTransfer ?: controller.currentVideoTrackColorTransfer,
        decodedKnown = usingExo,
        dvStripped = controller.isMapDv7ToHevcActiveForCurrentPlayback || controller.forceDv7ToHevc,
        dvConverted = controller.isExperimentalDv7ToDv81ActiveForCurrentPlayback ||
            controller.isManualDv81Mode2ActiveForCurrentPlayback,
        names = names
    )
    val audio = uiState.audioTracks.firstOrNull { it.isSelected }
    return NuvioCOsdBadges.parts(
        NuvioCOsdBadges.resolutionLabel(width, height) ?: NuvioCOsdBadges.resolutionFromName(names),
        visual,
        NuvioCOsdBadges.audioLabel(audio?.codec, audio?.channelCount),
        NuvioCOsdBadges.sizeLabel(controller.currentVideoSize)
    )
}

/** Pure label rules (unit tested). */
@VisibleForTesting
internal object NuvioCOsdBadges {

    fun parts(vararg parts: String?): List<String> = parts.filterNotNull().filter { it.isNotBlank() }

    fun join(vararg parts: String?): String? =
        parts(*parts).joinToString(" · ").takeIf { it.isNotEmpty() }

    /** Uses the width first so cropped scope films (3840x1600, 1920x800) keep their class. */
    fun resolutionLabel(width: Int?, height: Int?): String? {
        val w = width?.takeIf { it > 0 } ?: return null
        val h = height?.takeIf { it > 0 } ?: return null
        return when {
            w >= 3200 || h >= 1900 -> "4K"
            w >= 2200 || h >= 1300 -> "1440p"
            w >= 1700 || h >= 1000 -> "1080p"
            w >= 1200 || h >= 700 -> "720p"
            h >= 540 -> "576p"
            h >= 440 -> "480p"
            else -> "SD"
        }
    }

    fun resolutionFromName(names: String): String? {
        val n = names.lowercase(Locale.US)
        return when {
            Regex("""\b(2160p|4k|uhd)\b""").containsMatchIn(n) -> "4K"
            Regex("""\b1440p\b""").containsMatchIn(n) -> "1440p"
            Regex("""\b1080[pi]\b""").containsMatchIn(n) -> "1080p"
            Regex("""\b720p\b""").containsMatchIn(n) -> "720p"
            Regex("""\b576[pi]\b""").containsMatchIn(n) -> "576p"
            Regex("""\b480[pi]\b""").containsMatchIn(n) -> "480p"
            else -> null
        }
    }

    /** DV / HDR10+ / HDR10 / HLG named in the stream title, or null. */
    fun visualFromName(names: String): String? {
        val n = names.lowercase(Locale.US)
        return when {
            Regex("""(\b|[._ ])(dv|dovi|dolby[ ._-]?vision)(\b|[._ ])""").containsMatchIn(n) -> "DV"
            Regex("""hdr10(\+|plus|p\b)""").containsMatchIn(n) -> "HDR10+"
            Regex("""\bhdr(10)?\b""").containsMatchIn(n) -> "HDR10"
            Regex("""\bhlg\b""").containsMatchIn(n) -> "HLG"
            else -> null
        }
    }

    /**
     * What the TV is actually being sent. A DV file played as its HDR10 base layer says HDR10.
     * When nothing was decoded by ExoPlayer (mpv), the stream title decides, else SDR.
     */
    fun visualLabel(
        mimeType: String?,
        codecs: String?,
        colorTransfer: Int?,
        decodedKnown: Boolean,
        dvStripped: Boolean,
        dvConverted: Boolean,
        names: String
    ): String {
        val fromName = visualFromName(names)
        if (!decodedKnown) return fromName ?: "SDR"
        val c = codecs?.lowercase(Locale.US).orEmpty()
        val isDv = mimeType?.lowercase(Locale.US) == MimeTypes.VIDEO_DOLBY_VISION ||
            c.startsWith("dvh") || c.startsWith("dva") || c.startsWith("dav1")
        return when {
            dvConverted -> "DV"
            isDv && !dvStripped -> "DV"
            colorTransfer == C.COLOR_TRANSFER_ST2084 -> if (fromName == "HDR10+") "HDR10+" else "HDR10"
            colorTransfer == C.COLOR_TRANSFER_HLG -> "HLG"
            isDv -> "HDR10" // DV stripped to its base layer
            colorTransfer == C.COLOR_TRANSFER_SDR -> "SDR"
            else -> fromName?.takeIf { it != "DV" } ?: "SDR"
        }
    }

    /** Codec as the audio menu names it (ExoPlayer) or mapped from mpv's ffmpeg name, plus channels. */
    fun audioLabel(codec: String?, channelCount: Int?): String? {
        val raw = codec?.trim()?.takeIf { it.isNotEmpty() }
        val name = when (raw?.lowercase(Locale.US)) {
            null -> null
            "e-ac-3-joc" -> return "E-AC-3 Atmos"
            "eac3", "e-ac-3" -> "E-AC-3"
            "ac3", "ac-3" -> "AC-3"
            "truehd", "mlp" -> "TrueHD"
            "dts" -> "DTS"
            "dts-hd", "dtshd" -> "DTS-HD"
            "aac" -> "AAC"
            "flac" -> "FLAC"
            "opus" -> "Opus"
            "vorbis" -> "Vorbis"
            "mp3" -> "MP3"
            "mp2" -> "MP2"
            "pcm_s16le", "pcm_s24le", "pcm_s32le", "pcm" -> "PCM"
            else -> raw
        }
        val channels = channelCount?.let { CustomDefaultTrackNameProvider.getChannelLayoutName(it) }
        return listOfNotNull(name, channels).joinToString(" ").takeIf { it.isNotEmpty() }
    }

    fun sizeLabel(bytes: Long?): String? {
        val b = bytes?.takeIf { it > 0 } ?: return null
        val gb = b / (1024.0 * 1024.0 * 1024.0)
        return if (gb >= 1.0) String.format(Locale.US, "%.1f GB", gb)
        else String.format(Locale.US, "%d MB", (b / (1024.0 * 1024.0)).roundToInt())
    }
}

/**
 * Moves subtitles above the OSD's bottom block while it is open (animated with the OSD fade),
 * then back. ExoPlayer: the subtitle view and the libass overlay are translated as finished
 * layers. mpv: plain-text subtitles get a temporary sub-pos; ASS on mpv is left alone.
 */
@Composable
internal fun NuvioCSubtitleLiftEffect(
    viewModel: PlayerViewModel,
    osdShown: Boolean,
    state: NuvioCOsdState,
    subtitleStyle: SubtitleStyleSettings
) {
    if (!NuvioCFeatures.SUBTITLE_LIFT) return
    val controller = viewModel.controller
    val gapPx = with(LocalDensity.current) { SUBTITLE_OSD_GAP.toPx() }
    val progress = animateFloatAsState(
        targetValue = if (osdShown) 1f else 0f,
        animationSpec = tween(LIFT_ANIM_MS),
        label = "nuvioCSubtitleLift"
    )
    val style by rememberUpdatedState(subtitleStyle)

    LaunchedEffect(controller) {
        snapshotFlow { progress.value to state.bottomBlockTop }.collect { (p, top) ->
            runCatching { controller.exoPlayerView?.nuvioCApplySubtitleLift(p, top, gapPx, style) }
        }
    }

    // mpv draws subtitles into its own surface, so it moves in one step at the start/end of the fade.
    val mpvLifted = osdShown && !state.bottomBlockTop.isNaN()
    LaunchedEffect(controller, mpvLifted, state.bottomBlockTop, subtitleStyle) {
        val mpv = controller.mpvView ?: return@LaunchedEffect
        runCatching {
            if (mpvLifted) {
                val loc = IntArray(2).also { mpv.getLocationInWindow(it) }
                val h = mpv.height
                if (h > 0) {
                    val bottom = loc[1] + h
                    val wanted = (bottom - (state.bottomBlockTop - gapPx)) / h
                    mpv.nuvioCSetSubtitleLift(wanted.toDouble(), subtitleStyle)
                }
            } else {
                mpv.nuvioCSetSubtitleLift(null, subtitleStyle)
            }
        }
    }

    DisposableEffect(controller) {
        onDispose {
            runCatching { controller.exoPlayerView?.nuvioCApplySubtitleLift(0f, Float.NaN, 0f, style) }
            runCatching { controller.mpvView?.nuvioCSetSubtitleLift(null, style) }
        }
    }
}

private fun PlayerView.nuvioCApplySubtitleLift(
    progress: Float,
    osdTop: Float,
    gapPx: Float,
    style: SubtitleStyleSettings
) {
    val limit = osdTop - gapPx
    fun View.windowTop(): Float {
        val loc = IntArray(2)
        getLocationInWindow(loc)
        return loc[1] - translationY // where it sits without our lift
    }

    subtitleView?.let { sv ->
        sv.translationY = if (progress <= 0f || osdTop.isNaN() || sv.height <= 0) 0f else {
            // Same maths as media3's SubtitleView: bottom padding, then the bottom fraction
            val fraction = (0.06f + (style.verticalOffset / 250f)).coerceIn(0f, 0.4f)
            val usable = sv.height - sv.paddingTop - sv.paddingBottom
            val subsBottom = sv.windowTop() + sv.height - sv.paddingBottom - usable * fraction
            -(subsBottom - limit).coerceAtLeast(0f) * progress
        }
    }

    for (id in intArrayOf(R.id.libass_overlay_container, R.id.libass_overlay_container_gl)) {
        val container = findViewById<android.widget.FrameLayout>(id) ?: continue
        container.translationY = if (progress <= 0f || osdTop.isNaN() || container.height <= 0) 0f else {
            // ASS dialogue usually sits ~4.5% of the frame above its bottom edge
            val subsBottom = container.windowTop() + container.height * (1f - 0.045f)
            -(subsBottom - limit).coerceAtLeast(0f) * progress
        }
    }
}
