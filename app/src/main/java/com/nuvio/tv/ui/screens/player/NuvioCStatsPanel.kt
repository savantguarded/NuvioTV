package com.nuvio.tv.ui.screens.player

import androidx.annotation.OptIn
import androidx.compose.foundation.background
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

private data class NuvioCStatsRow(val label: String, val value: String, val severity: DebugStatSeverity = DebugStatSeverity.NORMAL)
private data class NuvioCStatsSection(val header: String, val rows: List<NuvioCStatsRow>)

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
            val controller = viewModel.controller
            val player = viewModel.exoPlayer
            val snapshot = player?.let {
                PlayerSnapshot(
                    aheadMs = (it.bufferedPosition - it.currentPosition).coerceAtLeast(0L),
                    videoBitrate = runCatching { it.videoFormat?.bitrate }.getOrNull() ?: -1,
                    audioBitrate = runCatching { it.audioFormat?.bitrate }.getOrNull() ?: -1,
                    durationMs = runCatching { it.duration }.getOrNull() ?: -1L,
                    droppedFrames = runCatching { it.videoDecoderCounters?.droppedBufferCount }.getOrNull() ?: 0,
                    fileSizeBytes = viewModel.getCurrentFileSizeBytes(),
                    nativeMemoryBytes = viewModel.getPlayerNativeMemoryBytes()
                )
            }
            val info = runCatching { controller.buildStreamInfoData() }.getOrNull()
            val audioMime = runCatching { player?.audioFormat?.sampleMimeType }.getOrNull()
            val passthrough = player != null && audioMime != null && audioMime != MimeTypes.AUDIO_RAW &&
                controller.playbackSpeedAwareAudioSink?.let { sink ->
                    player.audioFormat?.let { f -> !sink.shouldForcePcmForFormat(f) && sink.supportsFormat(f) }
                } == true
            val visual = runCatching { nuvioCVisualTag(controller, controller.uiState.value) }.getOrNull()
            val server = NuvioCStreamServer.host(runCatching { controller.getCurrentStreamUrl() }.getOrNull())
            val engineName = when (controller.currentInternalPlayerEngine) {
                InternalPlayerEngine.MVP_PLAYER -> "mpv"
                else -> "ExoPlayer"
            }
            val live = withContext(Dispatchers.IO) { sampler.sample(snapshot) }.associateBy { it.label }
            sections = buildList {
                add(NuvioCStatsSection("SOURCE", listOfNotNull(
                    NuvioCStatsRow("Engine", engineName),
                    info?.addonName?.takeIf { it.isNotBlank() }?.let { NuvioCStatsRow("Add-on", it) },
                    server?.let { NuvioCStatsRow("Server", it) },
                    info?.filename?.takeIf { it.isNotBlank() }?.let { NuvioCStatsRow("File", it) },
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
                    info?.videoBitrate?.takeIf { it > 0 }?.let { NuvioCStatsRow("Bitrate", mbps(it)) },
                    live["dropped"]?.let { NuvioCStatsRow("Dropped", it.value, it.severity) }
                )))
                add(NuvioCStatsSection("AUDIO", listOfNotNull(
                    listOfNotNull(info?.audioCodec, info?.audioChannels, info?.audioLanguage?.uppercase(Locale.US))
                        .takeIf { it.isNotEmpty() }?.let { NuvioCStatsRow("Audio", it.joinToString(" · ")) },
                    if (player == null) null else NuvioCStatsRow("Route", buildString {
                        append(if (passthrough) "Passthrough" else "Decoded in app")
                        val night = NuvioCNight.level.value
                        if (night != NightModeLevel.OFF) append(" · Night ${night.name.lowercase(Locale.US).replaceFirstChar { it.uppercase() }}")
                    })
                )))
                add(NuvioCStatsSection("NETWORK", listOfNotNull(
                    live["buffer"]?.let { NuvioCStatsRow("Buffer", it.value, it.severity) },
                    live["network"]?.let { NuvioCStatsRow("Speed", it.value, it.severity) }
                )))
                add(NuvioCStatsSection("SYSTEM", listOfNotNull(
                    live["cpu"]?.let { NuvioCStatsRow("CPU", it.value, it.severity) },
                    live["memory"]?.let { NuvioCStatsRow("Memory", it.value, it.severity) },
                    // Paging and heat only when they need attention, so the panel stays clear of the OSD.
                    live["paging"]?.takeIf { it.warn }?.let { NuvioCStatsRow("Paging", it.value, it.severity) },
                    live["thermal"]?.takeIf { it.warn }?.let { NuvioCStatsRow("Thermal", it.value, it.severity) },
                    live["throttle"]?.let { NuvioCStatsRow("Throttle", it.value, it.severity) }
                )))
            }.filter { it.rows.isNotEmpty() }
            delay(1000L)
        }
    }

    if (sections.isEmpty()) return
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium)
    val valueStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, lineHeight = 13.sp)
    Column(
        modifier = modifier
            .width(380.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xB8000000))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        sections.forEachIndexed { i, section ->
            Text(
                text = section.header,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold),
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = if (i == 0) 0.dp else 5.dp)
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.12f)))
            section.rows.forEach { row ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = row.label,
                        style = labelStyle,
                        color = Color.White.copy(alpha = 0.55f),
                        maxLines = 1,
                        modifier = Modifier.width(88.dp)
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
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun mbps(bps: Int): String = String.format(Locale.US, "%.1f Mbps", bps / 1_000_000.0)
