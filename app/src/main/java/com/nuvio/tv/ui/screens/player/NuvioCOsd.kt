package com.nuvio.tv.ui.screens.player

// [fork] Nuvio C player OSD additions, kept in this file so PlayerScreen.kt only gets a few hook lines:
//  - badges bottom-right, one chip each: resolution · HDR format · source · audio · file size
//  - subtitle lift: while the OSD is open, bottom subtitles move up so they clear the whole bottom
//    block (title, episode line, seek bar, buttons); top subtitles stay put. Only the finished
//    subtitle picture moves; saved subtitle settings, cue parsing and libass rendering are untouched.

import android.view.View
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.R
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.ui.PlayerView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.derivedStateOf
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

/**
 * Title block hook: reports its last baseline and keeps clear of the badges on the right.
 * The badge width is read while measuring (layout phase), so a new width only re-measures the
 * title, it never recomposes the controls.
 */
@Composable
internal fun Modifier.nuvioCOsdTitleBlock(state: NuvioCOsdState): Modifier {
    DisposableEffect(state) {
        onDispose { state.titleLastBaseline = Float.NaN }
    }
    val gapPx = with(LocalDensity.current) { BADGE_TITLE_GAP.roundToPx() }
    return this
        .layout { measurable, constraints ->
            val badges = state.badgesWidthPx
            val pad = if (badges > 0 && constraints.hasBoundedWidth) badges + gapPx else 0
            val inner = constraints.copy(
                minWidth = (constraints.minWidth - pad).coerceAtLeast(0),
                maxWidth = if (constraints.hasBoundedWidth) (constraints.maxWidth - pad).coerceAtLeast(0) else constraints.maxWidth
            )
            val placeable = measurable.measure(inner)
            val width = if (constraints.hasBoundedWidth) (placeable.width + pad).coerceAtMost(constraints.maxWidth) else placeable.width
            layout(width, placeable.height) { placeable.place(0, 0) }
        }
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
 * Badges, bottom-right of the OSD, centred on the last line of the title block (Apple TV style,
 * 2026-10-10; 2026-10-11 round 4: no logo marks, every badge is the same outlined text tile, e.g.
 * 4K · DV · ATMOS 7.1 · REMUX, then the file size as plain text). Built once per
 * OSD open; positions are read while laying out, so moving the title never recomposes them.
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
    val placed by remember(state) { derivedStateOf { !state.titleLastBaseline.isNaN() } }
    val alpha by animateFloatAsState(
        targetValue = if (placed) 1f else 0f,
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
                    // Centre the row on the title's last text line (about 0.35 em above its baseline)
                    val lineCentre = target - 7.dp.toPx()
                    val y = if (target.isNaN()) 0 else (lineCentre - originY - placeable.height / 2f).roundToInt()
                    layout(placeable.width, placeable.height) { placeable.place(0, y) }
                }
                .graphicsLayer { this.alpha = alpha }
        ) {
            parts.forEach { part -> NuvioCBadgeView(part) }
        }
    }
    DisposableEffect(state) { onDispose { state.badgesWidthPx = 0 } }
}

// 2026-10-11: one height for every badge (tiles, marks and the size text), so they share one
// centre line; tile text is placed by its capital letters, not its line box (the line box keeps
// room for descenders, which made caps sit high and the size text look offset).
private val BADGE_HEIGHT = 14.dp
private val BADGE_TILE_SHAPE = RoundedCornerShape(2.5.dp)
private val BADGE_WHITE = Color(0xE6FFFFFF) // white 90%
private const val BADGE_CAP_HEIGHT_EM = 0.72f // cap height of the UI fonts, as a share of the font size

@Composable
private fun NuvioCBadgeView(part: NuvioCBadge) {
    val textStyle = MaterialTheme.typography.labelMedium.copy(
        fontSize = 9.5.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.2.sp
    )
    when (part.kind) {
        NuvioCBadgeKind.OUTLINED -> NuvioCBadgeText(
            text = part.text,
            style = textStyle,
            color = nuvioCOsd(BADGE_WHITE),
            modifier = Modifier.border(1.2.dp, nuvioCOsd(BADGE_WHITE), BADGE_TILE_SHAPE).padding(horizontal = 3.5.dp)
        )
        NuvioCBadgeKind.PLAIN -> NuvioCBadgeText(
            text = part.text,
            style = textStyle.copy(fontSize = 10.5.sp),
            color = nuvioCOsd(Color.White.copy(alpha = 0.6f)),
            modifier = Modifier
        )
    }
}

/** Badge text in a [BADGE_HEIGHT] box with its capitals centred vertically. */
@Composable
private fun NuvioCBadgeText(text: String, style: androidx.compose.ui.text.TextStyle, color: Color, modifier: Modifier) {
    Box(modifier = modifier.height(BADGE_HEIGHT)) {
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.layout { measurable, constraints ->
                val p = measurable.measure(constraints.copy(minHeight = 0))
                val box = BADGE_HEIGHT.roundToPx()
                val baseline = p[androidx.compose.ui.layout.FirstBaseline].takeIf { it != AlignmentLine.Unspecified } ?: p.height
                val cap = style.fontSize.toPx() * BADGE_CAP_HEIGHT_EM
                val y = ((box + cap) / 2f - baseline).roundToInt()
                layout(p.width, box) { p.place(0, y) }
            }
        )
    }
}

internal enum class NuvioCBadgeKind { OUTLINED, PLAIN }

/** One OSD badge. */
internal data class NuvioCBadge(
    val text: String,
    val kind: NuvioCBadgeKind = NuvioCBadgeKind.OUTLINED
)

private fun buildBadgeParts(controller: PlayerRuntimeController, uiState: PlayerUiState): List<NuvioCBadge> {
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
    val visual = nuvioCVisualTag(controller, uiState)
    nuvioCLogVisualInputs(controller, uiState, visual)
    val audio = uiState.audioTracks.firstOrNull { it.isSelected }
    val audioLabel = NuvioCOsdBadges.audioLabel(audio?.codec, audio?.channelCount, names)
    return buildList {
        (NuvioCOsdBadges.resolutionLabel(width, height) ?: NuvioCOsdBadges.resolutionFromName(names))
            ?.let { add(NuvioCBadge(it)) }
        if (visual != "SDR") add(NuvioCBadge(visual)) // SDR: no HDR badge; Dolby Vision = "DV"
        addAll(nuvioCAudioBadges(audioLabel, names))
        NuvioCOsdBadges.sourceFromName(names)?.let { add(NuvioCBadge(it, NuvioCBadgeKind.OUTLINED)) }
        NuvioCOsdBadges.sizeLabel(controller.currentVideoSize)?.let { add(NuvioCBadge(it, NuvioCBadgeKind.PLAIN)) }
    }
}

/** Audio tile: "ATMOS 7.1", "DTS:X 7.1", "DTS-HD MA 7.1", "DD+ 5.1"… (one outlined tile, round 4). */
internal fun nuvioCAudioBadges(audioLabel: String?, names: String): List<NuvioCBadge> {
    val label = audioLabel ?: return emptyList()
    return listOf(NuvioCBadge(NuvioCOsdBadges.audioTile(label, names)))
}

/** What the TV is being sent: DV, HDR10, HDR10+, HLG or SDR (badges and the HDR dim share it). */
internal fun nuvioCVisualTag(controller: PlayerRuntimeController, uiState: PlayerUiState): String {
    val exoFormat = if (controller.currentInternalPlayerEngine == InternalPlayerEngine.MVP_PLAYER) null
    else controller._exoPlayer?.videoFormat
    val names = listOfNotNull(
        uiState.currentStreamName,
        controller.currentFilename,
        controller.currentStreamDescription
    ).joinToString(" ")
    val usingExo = exoFormat != null || controller.currentVideoTrackMimeType != null
    return NuvioCOsdBadges.visualLabel(
        mimeType = exoFormat?.sampleMimeType ?: controller.currentVideoTrackMimeType,
        codecs = exoFormat?.codecs ?: controller.currentVideoTrackCodecs,
        colorTransfer = exoFormat?.colorInfo?.colorTransfer ?: controller.currentVideoTrackColorTransfer,
        decodedKnown = usingExo,
        dvStripped = controller.isMapDv7ToHevcActiveForCurrentPlayback || controller.forceDv7ToHevc,
        dvConverted = controller.isExperimentalDv7ToDv81ActiveForCurrentPlayback ||
            controller.isManualDv81Mode2ActiveForCurrentPlayback,
        names = names,
        decoderTransfer = NuvioCDecodedColor.transfer(controller, exoFormat),
        decoderHdr10Plus = NuvioCDecodedColor.hdr10Plus(controller, exoFormat) ||
            (exoFormat != null && NuvioCHdr10PlusSniff.found(exoFormat)) // [fork] round 4: from the stream itself
    )
}

/** One line per OSD open (tag NuvioCOsdBadges): what the badge rules were given. For checking files. */
internal fun nuvioCLogVisualInputs(controller: PlayerRuntimeController, uiState: PlayerUiState, tag: String) {
    runCatching {
        val exo = if (controller.currentInternalPlayerEngine == InternalPlayerEngine.MVP_PLAYER) null else controller._exoPlayer?.videoFormat
        android.util.Log.i(
            "NuvioCOsdBadges",
            "badge=$tag engine=${controller.currentInternalPlayerEngine} mime=${exo?.sampleMimeType ?: controller.currentVideoTrackMimeType} " +
                "codecs=${exo?.codecs ?: controller.currentVideoTrackCodecs} trackTransfer=${exo?.colorInfo?.colorTransfer ?: controller.currentVideoTrackColorTransfer} " +
                "decoderTransfer=${NuvioCDecodedColor.transfer(controller, exo)} hdr10plus=${NuvioCDecodedColor.hdr10Plus(controller, exo)} " +
                "mpvGamma=${controller.mpvView?.nuvioCVideoGamma()} file=${controller.currentFilename}"
        )
    }
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
     * When nothing was decoded by ExoPlayer (mpv), the stream title decides, then mpv's decoded
     * gamma, else SDR. [decoderTransfer] = what the video decoder itself reports (NuvioCDecodedColor):
     * it catches HDR files whose container and name don't say so ("2160p.WEB.h265").
     */
    fun visualLabel(
        mimeType: String?,
        codecs: String?,
        colorTransfer: Int?,
        decodedKnown: Boolean,
        dvStripped: Boolean,
        dvConverted: Boolean,
        names: String,
        decoderTransfer: Int? = null,
        decoderHdr10Plus: Boolean = false
    ): String {
        val fromName = visualFromName(names)
        if (!decodedKnown) return fromName ?: transferLabel(decoderTransfer, fromName) ?: "SDR"
        val c = codecs?.lowercase(Locale.US).orEmpty()
        val isDv = mimeType?.lowercase(Locale.US) == MimeTypes.VIDEO_DOLBY_VISION ||
            c.startsWith("dvh") || c.startsWith("dva") || c.startsWith("dav1")
        // Decoder saying PQ / HLG wins; otherwise the track info as before (a decoder "SDR" is ignored).
        val decoderHdr = decoderTransfer?.takeIf { it == C.COLOR_TRANSFER_ST2084 || it == C.COLOR_TRANSFER_HLG }
        val transfer = decoderHdr ?: colorTransfer?.takeIf { it != Format.NO_VALUE }
        return when {
            dvConverted -> "DV"
            isDv && !dvStripped -> "DV"
            // HDR10+ metadata only exists on PQ video, so it also counts when the colour isn't flagged
            decoderHdr10Plus && (transfer == null || transfer == C.COLOR_TRANSFER_ST2084) -> "HDR10+"
            transfer == C.COLOR_TRANSFER_ST2084 || transfer == C.COLOR_TRANSFER_HLG -> transferLabel(transfer, fromName)!!
            isDv -> "HDR10" // DV stripped to its base layer
            transfer == C.COLOR_TRANSFER_SDR -> "SDR"
            // Name says DV but the track isn't flagged: the TV gets the HDR10 base layer
            fromName == "DV" -> "HDR10"
            else -> fromName ?: "SDR"
        }
    }

    private fun transferLabel(transfer: Int?, fromName: String?): String? = when (transfer) {
        C.COLOR_TRANSFER_ST2084 -> if (fromName == "HDR10+") "HDR10+" else "HDR10"
        C.COLOR_TRANSFER_HLG -> "HLG"
        else -> null
    }

    /** Release source from the stream title / filename; null when it doesn't say. REMUX wins over BluRay. */
    fun sourceFromName(names: String): String? {
        val n = names.lowercase(Locale.US)
        fun has(re: String) = Regex(re).containsMatchIn(n)
        return when {
            has("""(\b|[._ -])(bd)?remux(\b|[._ -])""") -> "REMUX"
            has("""web[ ._-]?dl""") -> "WEB-DL"
            has("""web[ ._-]?rip""") -> "WEBRIP"
            has("""blu[ ._-]?ray|\bbd[ ._-]?rip|\bbr[ ._-]?rip|\bbdmv\b|\bbd(25|50|66|100)\b""") -> "BLURAY"
            has("""\bhdtv(rip)?\b""") -> "HDTV"
            has("""\bdvd(rip|r|5|9)?\b""") -> "DVD"
            has("""\bhdcam\b|\bcamrip\b|\btelesync\b|\bhdts\b""") -> "CAM" // not bare "cam": film titles use it
            has("""(\b|[._ ])web(\b|[._ ])""") -> "WEB"
            else -> null
        }
    }

    /** Short audio name plus channels, e.g. "DD+ 5.1", "Atmos 7.1", "TrueHD 7.1". */
    fun audioLabel(codec: String?, channelCount: Int?, names: String = ""): String? {
        val raw = codec?.trim()?.takeIf { it.isNotEmpty() }
        val atmosInName = Regex("""\batmos\b""").containsMatchIn(names.lowercase(Locale.US))
        val name = when (raw?.lowercase(Locale.US)) {
            null -> null
            "e-ac-3-joc" -> "Atmos"
            "eac3", "e-ac-3" -> "DD+"
            "ac3", "ac-3" -> "DD"
            // TrueHD Atmos isn't flagged by the decoder; trust the release name
            "truehd", "mlp" -> if (atmosInName) "Atmos" else "TrueHD"
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
        val channels = when (channelCount) {
            null -> null
            1 -> "1.0"
            2 -> "2.0"
            6 -> "5.1"
            8 -> "7.1"
            else -> channelCount.takeIf { it > 0 }?.let { "${it}ch" }
        }
        return listOfNotNull(name, channels).joinToString(" ").takeIf { it.isNotEmpty() }
    }

    /** "ATMOS" / "DTS" when the audio gets a logo mark, else null (unit tested). */
    fun audioMark(audioLabel: String, names: String = ""): String? {
        val l = audioLabel.lowercase(Locale.US)
        return when {
            l.startsWith("atmos") -> "ATMOS"
            l.startsWith("dts") -> "DTS"
            else -> null
        }
    }

    /** Text after the DTS mark: "X" for DTS:X, "HD MA" / "HD" for DTS-HD, null for core DTS. */
    fun dtsSuffix(audioLabel: String, names: String = ""): String? {
        val n = names.lowercase(Locale.US)
        val l = audioLabel.lowercase(Locale.US)
        return when {
            Regex("""dts[ ._-]?x(\b|[._ ])""").containsMatchIn(n) || Regex("""dts[:]x""").containsMatchIn(n) -> "X"
            Regex("""dts[ ._-]?hd[ ._-]?ma""").containsMatchIn(n) -> "HD MA"
            l.startsWith("dts-hd") || Regex("""dts[ ._-]?hd""").containsMatchIn(n) -> "HD"
            else -> null
        }
    }

    /** Audio badge text, upper case; the DTS family gets its full name from the release name. */
    fun audioTile(audioLabel: String, names: String = ""): String {
        val upper = audioLabel.uppercase(Locale.ROOT)
        if (audioMark(audioLabel, names) != "DTS") return upper
        val channels = Regex("""\s(\d\.\d|\d+CH)$""").find(upper)?.groupValues?.get(1)
        val name = when (dtsSuffix(audioLabel, names)) {
            "X" -> "DTS:X"
            "HD MA" -> "DTS-HD MA"
            "HD" -> "DTS-HD"
            else -> "DTS"
        }
        return listOfNotNull(name, channels).joinToString(" ")
    }

    fun sizeLabel(bytes: Long?): String? {
        val b = bytes?.takeIf { it > 0 } ?: return null
        val gb = b / (1024.0 * 1024.0 * 1024.0)
        return if (gb >= 1.0) String.format(Locale.US, "%.1f GB", gb)
        else String.format(Locale.US, "%d MB", (b / (1024.0 * 1024.0)).roundToInt())
    }
}

/**
 * Moves bottom subtitles above the OSD's bottom block while it is open (animated with the OSD fade),
 * then back. Top subtitles never move. ExoPlayer: the lower half of the finished subtitle view and
 * libass overlay is drawn moved up (NuvioCSubtitleLiftLayout). mpv: plain-text subtitles get a
 * temporary sub-pos, which only moves bottom-aligned lines; ASS on mpv is left alone.
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
    // Re-applied only when the block really moves (2 px steps): each apply is an mpv property set.
    val topKey = if (state.bottomBlockTop.isNaN()) Int.MIN_VALUE else (state.bottomBlockTop / 2f).roundToInt()
    LaunchedEffect(controller, mpvLifted, topKey, subtitleStyle) {
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
        return loc[1] - translationY
    }
    val off = progress <= 0f || osdTop.isNaN()

    // Only the bottom half of each subtitle layer moves (NuvioCSubtitleLiftLayout); the top half,
    // with top-positioned subtitles and signs, stays where it is.
    subtitleView?.let { sv ->
        sv.translationY = 0f // older builds moved the whole view
        val frame = sv.parent as? NuvioCSubtitleLiftLayout ?: return@let
        if (off || sv.height <= 0) {
            frame.setSubtitleLift(0f, 0f)
            return@let
        }
        // Same maths as media3's SubtitleView: bottom padding, then the bottom fraction
        val fraction = (0.06f + (style.verticalOffset / 250f)).coerceIn(0f, 0.4f)
        val usable = sv.height - sv.paddingTop - sv.paddingBottom
        val subsBottom = sv.windowTop() + sv.height - sv.paddingBottom - usable * fraction
        val lift = (subsBottom - limit).coerceAtLeast(0f) * progress
        frame.setSubtitleLift(lift, sv.top + sv.paddingTop + usable / 2f)
    }

    for (id in intArrayOf(R.id.libass_overlay_container, R.id.libass_overlay_container_gl)) {
        val container = findViewById<android.widget.FrameLayout>(id) ?: continue
        container.translationY = 0f
        val frame = container as? NuvioCSubtitleLiftLayout ?: continue
        if (off || container.height <= 0) {
            frame.setSubtitleLift(0f, 0f)
            continue
        }
        // ASS dialogue usually sits ~4.5% of the frame above its bottom edge
        val subsBottom = container.windowTop() + container.height * (1f - 0.045f)
        val lift = (subsBottom - limit).coerceAtLeast(0f) * progress
        frame.setSubtitleLift(lift, container.height / 2f)
    }
}

/**
 * Seek bar height without moving the OSD (2026-10-10 lag fix): the bar is 4 dp, 6 dp when focused,
 * but always takes 6 dp of room, so focusing it no longer shifts the title, badges and subtitles.
 */
internal fun Modifier.nuvioCSeekBarHeight(focused: Boolean): Modifier = layout { measurable, constraints ->
    val reserved = 6.dp.roundToPx()
    val h = (if (focused) 6.dp else 4.dp).roundToPx()
    val placeable = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
    layout(placeable.width, reserved) { placeable.place(0, (reserved - h) / 2) }
}

/**
 * Where the OSD's seek bar is (window px), so Skip Intro and the next-episode card can sit just
 * above it while the controls are open (2026-10-10). Official uses a fixed 122 dp, which the
 * taller Nuvio C bottom block (badges, time row) can reach.
 */
@Stable
internal class NuvioCOsdAnchor {
    var seekBarTop by mutableFloatStateOf(Float.NaN)
}

internal val LocalNuvioCOsdAnchor = staticCompositionLocalOf<NuvioCOsdAnchor?> { null }

internal fun Modifier.nuvioCSeekBarAnchor(anchor: NuvioCOsdAnchor?): Modifier =
    if (anchor == null || !NuvioCFeatures.SKIP_ABOVE_SEEK) this
    else onGloballyPositioned { anchor.seekBarTop = it.positionInWindow().y }

/** Forgets the seek bar position when the controls leave. */
@Composable
internal fun NuvioCOsdAnchorReset(anchor: NuvioCOsdAnchor) {
    DisposableEffect(anchor) { onDispose { anchor.seekBarTop = Float.NaN } }
}

/** Gap kept between Skip Intro (focused, 1.1x, ring included) and the top of the seek bar. */
private val SKIP_SEEK_GAP = 16.dp

/**
 * Bottom padding for an element that must clear the seek bar: [official] when the controls are
 * hidden or the bar hasn't been measured, else the distance from the window bottom to the bar's
 * top plus a 16 dp gap.
 */
@Composable
internal fun nuvioCAboveSeekBar(anchor: NuvioCOsdAnchor, controlsVisible: Boolean, official: Dp): Dp {
    if (!NuvioCFeatures.SKIP_ABOVE_SEEK || !controlsVisible) return official
    val top = anchor.seekBarTop
    if (top.isNaN()) return official
    val windowHeight = LocalView.current.rootView.height
    if (windowHeight <= 0) return official
    val density = LocalDensity.current
    val fromBottom = with(density) { (windowHeight - top).coerceAtLeast(0f).toDp() }
    return fromBottom + SKIP_SEEK_GAP
}

/**
 * The focused control's name, drawn just under it in the OSD's bottom padding (2026-10-10). Drawn,
 * not laid out, so the button row never moves; nothing is drawn while unfocused.
 */
@Composable
internal fun Modifier.nuvioCFocusLabel(focused: Boolean, label: String): Modifier {
    if (!NuvioCFeatures.OSD_FOCUS_LABEL) return this
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelSmall.copy(
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        color = nuvioCOsd(Color.White.copy(alpha = 0.85f)) // HDR dim
    )
    return drawWithCache {
        val text = if (focused) measurer.measure(label, style, maxLines = 1, softWrap = false) else null
        val gap = 7.dp.toPx() // clears the 1.1x focus zoom
        onDrawWithContent {
            drawContent()
            if (text != null) {
                drawText(
                    textLayoutResult = text,
                    topLeft = Offset((size.width - text.size.width) / 2f, size.height + gap)
                )
            }
        }
    }
}

/** "Subtitles · English SDH" / "Subtitles · Off" for the focus label. */
internal fun nuvioCSubtitleLabel(uiState: PlayerUiState, title: String, off: String): String {
    val addon = uiState.selectedAddonSubtitle
    val name = when {
        addon != null -> addon.getDisplayLanguage()
        else -> uiState.subtitleTracks.getOrNull(uiState.selectedSubtitleTrackIndex)?.name
    }
    return "$title · ${name?.takeIf { it.isNotBlank() } ?: off}"
}

/** "Audio · English 5.1" style label for the focus label. */
internal fun nuvioCAudioLabel(uiState: PlayerUiState, title: String): String {
    val track = uiState.audioTracks.getOrNull(uiState.selectedAudioTrackIndex)
        ?: uiState.audioTracks.firstOrNull { it.isSelected }
    return track?.name?.takeIf { it.isNotBlank() }?.let { "$title · $it" } ?: title
}
