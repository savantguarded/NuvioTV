package com.nuvio.tv.ui.screens.player

// [fork] Nuvio C: while the loading screen is up, a lane pinned to the bottom centre (same place as
// ysosrs123's fork) shows which add-on / debrid service the stream comes from and its release
// filename, so a wrong pick can be spotted before it starts. Official loading screen untouched.
// Switch: NuvioCFeatures.LOADING_FILENAME.

import androidx.annotation.VisibleForTesting
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.ui.theme.NuvioTheme
import java.util.Locale

@Composable
internal fun NuvioCLoadingLane(
    visible: Boolean,
    viewModel: PlayerViewModel,
    uiState: PlayerUiState,
    modifier: Modifier = Modifier
) {
    if (!NuvioCFeatures.LOADING_FILENAME) return
    val controller = viewModel.controller
    val sourceLine = remember(uiState.currentStreamAddonName, uiState.currentStreamName) {
        NuvioCLoadingLaneText.sourceLine(uiState.currentStreamAddonName, uiState.currentStreamName)
    }
    val filename = remember(uiState.currentStreamName, uiState.currentStreamUrl, controller.currentFilename) {
        NuvioCLoadingLaneText.filename(controller.currentFilename, controller.currentStreamDescription)
    }
    AnimatedVisibility(
        visible = visible && (sourceLine != null || filename != null),
        enter = fadeIn(animationSpec = tween(250)),
        exit = fadeOut(animationSpec = tween(200)),
        modifier = modifier
    ) {
        val shadow = Shadow(color = Color.Black.copy(alpha = 0.85f), offset = Offset(0f, 2f), blurRadius = 6f)
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = NuvioTheme.spacing.lg, vertical = NuvioTheme.spacing.xxl)
            ) {
                if (sourceLine != null) {
                    Text(
                        text = sourceLine,
                        style = MaterialTheme.typography.labelMedium.copy(shadow = shadow),
                        color = Color.White.copy(alpha = 0.72f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (filename != null) {
                    Text(
                        text = filename,
                        style = MaterialTheme.typography.labelMedium.copy(shadow = shadow),
                        color = Color.White.copy(alpha = 0.62f),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

/** Pure text rules (unit tested). */
@VisibleForTesting
internal object NuvioCLoadingLaneText {
    private val PROVIDERS = mapOf(
        "TB" to "TorBox", "RD" to "Real-Debrid", "AD" to "AllDebrid", "PM" to "Premiumize",
        "DL" to "Debrid-Link", "ED" to "EasyDebrid", "OC" to "Offcloud", "PKP" to "PikPak"
    )
    private val VIDEO_EXT = Regex("""\.(mkv|mp4|m4v|avi|ts|m2ts|mov|wmv|webm)$""", RegexOption.IGNORE_CASE)

    /** "Torrentio · TorBox": add-on name, then the debrid service named in the stream title. */
    fun sourceLine(addonName: String?, streamName: String?): String? {
        val addon = addonName?.trim()?.takeIf { it.isNotEmpty() }
        val provider = providerFromName(streamName)
        return listOfNotNull(addon, provider).distinct().joinToString(" · ").takeIf { it.isNotEmpty() }
    }

    /** Debrid tag at the start of a stream title: "[TB+] …", "[RD download] …", "TB⚡ …". */
    fun providerFromName(streamName: String?): String? {
        val name = streamName?.trim().orEmpty()
        val tag = Regex("""^\[?\s*([A-Za-z]{2,3})(?=[^A-Za-z]|$)""").find(name)?.groupValues?.get(1)
            ?.uppercase(Locale.US) ?: return null
        return PROVIDERS[tag]
    }

    /** The release filename, else the first description line when it looks like one. */
    fun filename(filename: String?, description: String?): String? {
        filename?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val first = description?.lineSequence()?.map { it.trim() }?.firstOrNull { it.isNotEmpty() } ?: return null
        val looksLikeRelease = VIDEO_EXT.containsMatchIn(first) ||
            (!first.contains(' ') && first.count { it == '.' } >= 3)
        return first.takeIf { looksLikeRelease }
    }
}
