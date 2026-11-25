package com.nuvio.tv.ui.screens.player

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.data.local.InternalPlayerEngine
import com.nuvio.tv.data.local.NightModeLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

// [fork] Nuvio C round 31 (switch `stats_panel_right`): the stats HUD laid out like ysosrs123's playback
// stats panel: a 380 dp panel on the right, grouped into Playback, Source, Video, Audio, Network and System
// under small headers. Source names the server the file actually streams from (the host the player was
// redirected to, which official's RedirectReportingDataSource already reports), so you can see where a
// debrid file is served from. Everything comes from what the player and official's samplers already know;
// nothing extra is downloaded. Sampled once a second while it is open, same as the old list.

/** Last redirect target the ExoPlayer data source reported, per requested url. */
internal object NuvioCStreamServer {
    @Volatile private var requested: String? = null
    @Volatile private var resolved: String? = null

    fun record(requestedUrl: String, resolvedUrl: String) {
        requested = requestedUrl
        resolved = resolvedUrl
    }

    /** Host serving [url]: the redirect target's host when [url] was redirected, else [url]'s own host. */
    fun host(url: String?): String? {
        val target = if (url != null && url == requested) resolved else url
        return target?.let { runCatching { java.net.URI(it).host }.getOrNull() }?.takeIf { it.isNotBlank() }
    }
}

internal data class NuvioCStatsRow(val label: String, val value: String, val severity: DebugStatSeverity = DebugStatSeverity.NORMAL, val marquee: Boolean = false)
internal data class NuvioCStatsSection(val header: String, val rows: List<NuvioCStatsRow>)

/**
 * [fork] round 65 STATS_STEADY: every row that can apply to this engine, in a fixed order, from the first second:
 * "—" until known, "OK" for Paging / Thermal when they are fine. The panel's rows (and so its size) never change
 * while it is open. Pure, for tests.
 */
internal fun nuvioCStatsTemplate(isMpv: Boolean, full: Boolean): List<Pair<String, List<String>>> = listOfNotNull(
    if (full) "PLAYBACK" to listOf("Position", "Rebuffers") else null,
    "SOURCE" to listOf("Engine", "Add-on", "Server", "File", "Container", "Size", "File bitrate"),
    "VIDEO" to listOf("Video", "Picture", "HDR", "Decoder", "Display", "Frame lead", "Bitrate", "Dropped"),
    "AUDIO" to listOf("Audio", "Route", "Output", "Decoder", "Underruns"),
    "NETWORK" to (listOf("Buffer", "Speed") + (if (isMpv) listOf("Cache") else emptyList()) + listOf("Loaded", "Last request", "Load errors")),
    "SYSTEM" to listOf("CPU", "Memory", "Paging", "Thermal", "Throttle", "CPU clock")
)

internal fun nuvioCSteadySections(sampled: List<NuvioCStatsSection>, isMpv: Boolean, full: Boolean): List<NuvioCStatsSection> =
    nuvioCStatsTemplate(isMpv, full).map { (header, labels) ->
        val got = sampled.firstOrNull { it.header == header }?.rows.orEmpty().associateBy { it.label }
        NuvioCStatsSection(header, labels.map { label ->
            got[label] ?: NuvioCStatsRow(label, if (label == "Paging" || label == "Thermal") "OK" else "—", marquee = label == "File" && full)
        })
    }

@OptIn(UnstableApi::class)
@Composable
internal fun NuvioCStatsPanel(viewModel: PlayerViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sampler = remember { DebugStatsSampler(context) }
    var sections by remember { mutableStateOf(emptyList<NuvioCStatsSection>()) }

    LaunchedEffect(Unit) {
        var waited = 0L
        while (viewModel.exoPlayer?.playbackState != Player.STATE_READY && waited < 3_000L) {
            delay(250L)
            waited += 250L
        }
        while (true) {
            // [fork] round 32 stats_panel_safe: one bad read skips that second instead of taking the app down.
            try {
            val controller = viewModel.controller
            val player = viewModel.exoPlayer
            val snapshot = player?.let {
                PlayerSnapshot(
                    aheadMs = (it.bufferedPosition - it.currentPosition).coerceAtLeast(0L),
                    videoBitrate = runCatching { it.videoFormat?.bitrate }.getOrNull() ?: -1,
                    audioBitrate = runCatching { it.audioFormat?.bitrate }.getOrNull() ?: -1,
                    durationMs = runCatching { it.duration }.getOrNull() ?: -1L,
                    droppedFrames = runCatching { it.videoDecoderCounters?.droppedBufferCount }.getOrNull() ?: 0,
                    fileSizeBytes = runCatching { viewModel.getCurrentFileSizeBytes() }.getOrNull(),
                    nativeMemoryBytes = runCatching { viewModel.getPlayerNativeMemoryBytes() }.getOrNull()
                )
            }
            val info = runCatching { controller.buildStreamInfoData() }.getOrNull()
            val audioMime = runCatching { player?.audioFormat?.sampleMimeType }.getOrNull()
            // [fork] round 32: read the sink's own playback-thread state; never call DefaultAudioSink from here.
            val passthrough = player != null && audioMime != null && audioMime != MimeTypes.AUDIO_RAW &&
                controller.playbackSpeedAwareAudioSink?.nuvioCPassthrough == true
            val visual = runCatching { nuvioCVisualTag(controller, controller.uiState.value) }.getOrNull()
            val server = NuvioCStreamServer.host(runCatching { controller.getCurrentStreamUrl() }.getOrNull())
            val engineName = when (controller.currentInternalPlayerEngine) {
                InternalPlayerEngine.MVP_PLAYER -> "mpv"
                else -> "ExoPlayer"
            }
            val live = withContext(Dispatchers.IO) { sampler.sample(snapshot) }.associateBy { it.label }
            // [fork] round 34 stats_panel_full: more rows from the player's own data
            val full = com.nuvio.tv.NuvioCFeatures.STATS_PANEL_FULL
            val counters = if (full) NuvioCPlaybackStats.current else null
            val isMpv = controller.currentInternalPlayerEngine == InternalPlayerEngine.MVP_PLAYER
            val mpvStats = if (full && isMpv) runCatching { controller.mpvView?.nuvioCStats() }.getOrNull().orEmpty() else emptyMap()
            val videoFormat = if (full && !isMpv) runCatching { player?.videoFormat }.getOrNull() else null
            val positionText = if (full) runCatching {
                val pos = if (isMpv) controller.currentPlaybackPositionMs() ?: -1L else player?.currentPosition ?: -1L
                val dur = if (isMpv) controller.lastKnownDuration else player?.duration ?: -1L
                if (pos >= 0 && dur > 0) "${clock(pos)} / ${clock(dur)}" else null
            }.getOrNull() else null
            val hdrMeta = if (!full) null else if (isMpv) {
                listOfNotNull(mpvStats["primaries"], mpvStats["gamma"], mpvStats["maxcll"]?.let { "MaxCLL $it" }, mpvStats["maxfall"]?.let { "MaxFALL $it" })
                    .joinToString(" · ").ifBlank { null }
            } else {
                val c = videoFormat?.colorInfo
                listOfNotNull(
                    when (c?.colorTransfer) { androidx.media3.common.C.COLOR_TRANSFER_ST2084 -> "PQ"; androidx.media3.common.C.COLOR_TRANSFER_HLG -> "HLG"; else -> null },
                    if (c?.colorSpace == androidx.media3.common.C.COLOR_SPACE_BT2020) "BT.2020" else null,
                    NuvioCPlaybackStats.hdrStatic(c?.hdrStaticInfo)
                ).joinToString(" · ").ifBlank { null }
            }
            val container = if (!full) null else if (isMpv) mpvStats["container"] else videoFormat?.containerMimeType?.let(::containerName)
            sections = buildList {
                if (full) add(NuvioCStatsSection("PLAYBACK", listOfNotNull(
                    positionText?.let { NuvioCStatsRow("Position", it) },
                    counters?.let { NuvioCStatsRow("Rebuffers", it.rebuffers.toString(), if (it.rebuffers > 2) DebugStatSeverity.WARNING else DebugStatSeverity.NORMAL) }
                )))
                add(NuvioCStatsSection("SOURCE", listOfNotNull(
                    NuvioCStatsRow("Engine", engineName),
                    info?.addonName?.takeIf { it.isNotBlank() }?.let { NuvioCStatsRow("Add-on", it) },
                    server?.let { NuvioCStatsRow("Server", it) },
                    info?.filename?.takeIf { it.isNotBlank() }?.let { NuvioCStatsRow("File", it, marquee = full) },
                    container?.let { NuvioCStatsRow("Container", it) },
                    NuvioCOsdBadges.sizeLabel(info?.fileSize)?.let { NuvioCStatsRow("Size", it) },
                    info?.fileBitrate?.takeIf { it > 0 }?.let { NuvioCStatsRow("File bitrate", mbps(it)) }
                )))
                add(NuvioCStatsSection("VIDEO", listOfNotNull(
                    listOfNotNull(
                        info?.videoCodec,
                        if (info?.videoWidth != null && info.videoHeight != null) "${info.videoWidth}×${info.videoHeight}" else null,
                        info?.videoFrameRate?.let { String.format(Locale.US, "%.3f", it).trimEnd('0').trimEnd('.') }
                    ).takeIf { it.isNotEmpty() }?.let { NuvioCStatsRow("Video", it.joinToString(" · ")) },
                    visual?.let { NuvioCStatsRow("Picture", if (it == "DV") "Dolby Vision" else it) },
                    hdrMeta?.let { NuvioCStatsRow("HDR", it) },
                    (if (isMpv) mpvStats["decoder"] else counters?.videoDecoder)?.takeIf { full }?.let { NuvioCStatsRow("Decoder", it) },
                    if (full) NuvioCPlaybackStats.displayMode(context)?.let { NuvioCStatsRow("Display", it) } else null,
                    counters?.frameLeadMs?.let { NuvioCStatsRow("Frame lead", String.format(Locale.US, "%.1f ms", it)) },
                    info?.videoBitrate?.takeIf { it > 0 }?.let { NuvioCStatsRow("Bitrate", mbps(it)) },
                    (live["dropped"]?.let { NuvioCStatsRow("Dropped", it.value, it.severity) }
                        ?: mpvStats["drops"]?.takeIf { full }?.let { NuvioCStatsRow("Dropped", it) })
                )))
                add(NuvioCStatsSection("AUDIO", listOfNotNull(
                    listOfNotNull(info?.audioCodec, info?.audioChannels, info?.audioLanguage?.uppercase(Locale.US))
                        .takeIf { it.isNotEmpty() }?.let { NuvioCStatsRow("Audio", it.joinToString(" · ")) },
                    if (player == null) null else NuvioCStatsRow("Route", buildString {
                        append(if (passthrough) "Passthrough" else "Decoded in app")
                        val night = NuvioCNight.level.value
                        if (night != NightModeLevel.OFF) append(" · Night ${night.name.lowercase(Locale.US).replaceFirstChar { it.uppercase() }}")
                        // [fork] round 52 decoded_dialogue_lift: the lift only counts when night mode is off (never both)
                        else NuvioCPassthrough.playingLift.value.takeIf { it != NightModeLevel.OFF }?.let {
                            append(" · Dialogue lift ${it.name.lowercase(Locale.US).replaceFirstChar { c -> c.uppercase() }}")
                        }
                    }),
                    // [fork] round 52 decoded_surround: what the TV says its output takes, to tell a TV downmix from the app's
                    if (player == null || passthrough || !com.nuvio.tv.NuvioCFeatures.DECODED_SURROUND) null else NuvioCStatsRow("Output", buildString {
                        val reported = runCatching { androidx.media3.exoplayer.audio.AudioCapabilities.getCapabilities(context, androidx.media3.common.AudioAttributes.DEFAULT, null).maxChannelCount }.getOrNull()
                        append(if (NuvioCPassthrough.wantsSurround(context)) "PCM up to 8 ch" else "PCM")
                        if (reported != null) append(" · TV reports $reported ch")
                    }),
                    counters?.audioDecoder?.takeIf { !passthrough }?.let { NuvioCStatsRow("Decoder", it) },
                    counters?.let { NuvioCStatsRow("Underruns", it.underruns.toString(), if (it.underruns > 0) DebugStatSeverity.WARNING else DebugStatSeverity.NORMAL) }
                )))
                add(NuvioCStatsSection("NETWORK", listOfNotNull(
                    live["buffer"]?.let { NuvioCStatsRow("Buffer", it.value, it.severity) },
                    live["network"]?.let { NuvioCStatsRow("Speed", it.value, it.severity) },
                    mpvStats["cache"]?.toDoubleOrNull()?.let { NuvioCStatsRow("Cache", String.format(Locale.US, "%.0f s", it)) },
                    counters?.takeIf { it.loadedBytes > 0 }?.let { NuvioCStatsRow("Loaded", NuvioCOsdBadges.sizeLabel(it.loadedBytes) ?: "") },
                    counters?.takeIf { it.lastRequestMs >= 0 }?.let { NuvioCStatsRow("Last request", "${it.lastRequestMs} ms") },
                    counters?.let { NuvioCStatsRow("Load errors", it.loadErrors.toString(), if (it.loadErrors > 0) DebugStatSeverity.WARNING else DebugStatSeverity.NORMAL) }
                )))
                add(NuvioCStatsSection("SYSTEM", listOfNotNull(
                    live["cpu"]?.let { NuvioCStatsRow("CPU", it.value, it.severity) },
                    live["memory"]?.let { NuvioCStatsRow("Memory", it.value, it.severity) },
                    // Paging and heat only when they need attention, so the panel stays clear of the OSD.
                    live["paging"]?.takeIf { it.warn }?.let { NuvioCStatsRow("Paging", it.value, it.severity) },
                    live["thermal"]?.takeIf { it.warn }?.let { NuvioCStatsRow("Thermal", it.value, it.severity) },
                    live["throttle"]?.let { NuvioCStatsRow("Throttle", it.value, it.severity) },
                    if (full) NuvioCPlaybackStats.cpuClock()?.let { NuvioCStatsRow("CPU clock", it) } else null
                )))
            }.filter { it.rows.isNotEmpty() }.let { if (com.nuvio.tv.NuvioCFeatures.STATS_STEADY) nuvioCSteadySections(it, isMpv, full) else it } // [fork] round 65
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                if (!com.nuvio.tv.NuvioCFeatures.STATS_PANEL_SAFE) throw t
                android.util.Log.w("NuvioCStats", "stats sample skipped", t)
            }
            delay(1000L)
        }
    }

    if (sections.isEmpty()) return
    // [fork] round 35 hud_fit: tighter rows, and the whole panel shrinks to fit when it's still taller than the
    // screen space left, so it always ends with SYSTEM on screen (it used to run off the bottom).
    val fit = com.nuvio.tv.NuvioCFeatures.HUD_FIT
    val big = com.nuvio.tv.NuvioCFeatures.HUD_TEXT_LARGER // [fork] round 53e: easier to read from the sofa
    val fixed = com.nuvio.tv.NuvioCFeatures.HUD_FIXED // [fork] round 54: bigger type, tight rows, one fixed box
    val lineH = if (fixed) 16.sp else if (big) 15.sp else if (fit) 12.sp else 13.sp
    val textSize = if (fixed) 14.sp else if (big) 12.5.sp else 10.5.sp
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = textSize, lineHeight = lineH, fontWeight = FontWeight.Medium)
    val valueStyle = MaterialTheme.typography.labelSmall.copy(fontSize = textSize, lineHeight = lineH,
        fontFeatureSettings = if (com.nuvio.tv.NuvioCFeatures.STATS_STEADY) "tnum" else null) // [fork] round 65: tabular digits
    val nuvioCScale = remember { floatArrayOf(-1f) } // [fork] round 65 stats_steady: the fit scale, worked out once
    Column(
        modifier = modifier
            .then(if (fit) Modifier.nuvioCScaleToFitHeight(if (com.nuvio.tv.NuvioCFeatures.STATS_STEADY) nuvioCScale else null) else Modifier)
            .width(if (fixed) 520.dp else if (big) 460.dp else 380.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xB8000000))
            .padding(horizontal = 16.dp, vertical = if (fixed) 8.dp else if (fit) 10.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (fixed) 0.dp else if (fit) 1.dp else 2.dp)
    ) {
        sections.forEachIndexed { i, section ->
            Text(
                text = section.header,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = if (fixed) 11.sp else if (big) 10.5.sp else 9.sp, lineHeight = if (fixed) 13.sp else androidx.compose.ui.unit.TextUnit.Unspecified, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold),
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = if (i == 0) 0.dp else if (fixed) 5.dp else if (fit) 4.dp else 5.dp)
            )
            Box(Modifier.fillMaxWidth().padding(bottom = if (fixed) 2.dp else 0.dp).height(1.dp).background(Color.White.copy(alpha = 0.12f)))
            section.rows.forEach { row ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = row.label,
                        style = labelStyle,
                        color = Color.White.copy(alpha = 0.55f),
                        maxLines = 1,
                        modifier = Modifier.width(if (fixed) 124.dp else if (big) 104.dp else 88.dp)
                    )
                    Text(
                        text = row.value,
                        style = valueStyle,
                        color = when (row.severity) {
                            DebugStatSeverity.DANGER -> Color(0xFFF44336)
                            DebugStatSeverity.WARNING -> Color(0xFFFFB300)
                            DebugStatSeverity.NORMAL -> Color(0xFFF0F0F0)
                        },
                        fontWeight = if (row.severity == DebugStatSeverity.NORMAL) FontWeight.Normal else FontWeight.Bold,
                        maxLines = 1,
                        softWrap = !row.marquee,
                        overflow = if (row.marquee) TextOverflow.Clip else TextOverflow.Ellipsis,
                        // [fork] round 34: the filename scrolls like ysosrs123's (3 rounds, 2 s wait, 20 dp/s)
                        modifier = if (row.marquee) Modifier.weight(1f).basicMarquee(iterations = 3, velocity = 20.dp, initialDelayMillis = 2000) else Modifier
                    )
                }
            }
        }
    }
}

/**
 * [fork] round 35 hud_fit: measures the content at its natural height and, when that is taller than the space it was
 * given, scales it down from the top-right corner so all of it shows. No change when it already fits.
 */
private fun Modifier.nuvioCScaleToFitHeight(once: FloatArray? = null): Modifier = this.layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
    val maxH = constraints.maxHeight
    val fresh = if (constraints.hasBoundedHeight && placeable.height > maxH && placeable.height > 0) maxH.toFloat() / placeable.height else 1f
    // [fork] round 65 stats_steady: the first measure (full row set) decides the scale for as long as the panel is open
    val scale = if (once == null) fresh else { if (once[0] < 0f) once[0] = fresh; once[0] }
    val w = placeable.width
    val h = (placeable.height * scale).toInt().coerceAtLeast(1)
    layout(w, h) {
        placeable.placeWithLayer(0, 0) {
            scaleX = scale
            scaleY = scale
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f)
        }
    }
}

private fun mbps(bps: Int): String = String.format(Locale.US, "%.1f Mbps", bps / 1_000_000.0)

private fun clock(ms: Long): String {
    val t = ms / 1000
    return if (t >= 3600) String.format(Locale.US, "%d:%02d:%02d", t / 3600, (t / 60) % 60, t % 60)
    else String.format(Locale.US, "%d:%02d", t / 60, t % 60)
}

private fun containerName(mime: String): String = when (mime) {
    "video/x-matroska", "video/webm" -> "Matroska"
    "video/mp4" -> "MP4"
    "video/mp2t" -> "MPEG-TS"
    "application/x-mpegURL", "application/vnd.apple.mpegurl" -> "HLS"
    "application/dash+xml" -> "DASH"
    else -> mime.substringAfter('/')
}
