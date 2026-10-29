package com.nuvio.tv.ui.screens.player

// [fork] Nuvio C: while the loading screen is up, the release filename sits bottom centre (same
// place as ysosrs123's fork) in a soft pill, in full (wraps after dots / underscores, extension
// dimmed), so a wrong pick can be spotted before it starts. Official loading screen untouched.
// Switch: NuvioCFeatures.LOADING_FILENAME.

import androidx.annotation.VisibleForTesting
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
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
    // Round 6 (2026-10-12): filename only (no add-on / debrid line), shown in full, never shortened.
    val filename = remember(uiState.currentStreamName, uiState.currentStreamUrl, controller.currentFilename) {
        NuvioCLoadingLaneText.filename(controller.currentFilename, controller.currentStreamDescription)
    }
    AnimatedVisibility(
        visible = visible && filename != null,
        enter = fadeIn(animationSpec = tween(250)),
        exit = fadeOut(animationSpec = tween(200)),
        modifier = modifier
    ) {
        val name = filename ?: return@AnimatedVisibility
        val (base, ext) = remember(name) { NuvioCLoadingLaneText.splitExtension(name) }
        val text = remember(base, ext) {
            buildAnnotatedString {
                append(NuvioCLoadingLaneText.wrapFriendly(base))
                if (ext != null) withStyle(SpanStyle(color = Color.White.copy(alpha = 0.45f))) { append(ext) }
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = NuvioTheme.spacing.xxl, vertical = NuvioTheme.spacing.xxl)
                    .widthIn(max = 760.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 9.dp)
            ) {
                Text(
                    text = text,
                    style = (if (com.nuvio.tv.NuvioCFeatures.LOADING_TEXT_SMALLER) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge).copy( // [fork] round 32
                        fontFeatureSettings = "tnum",
                        lineHeight = if (com.nuvio.tv.NuvioCFeatures.LOADING_TEXT_SMALLER) 17.sp else 20.sp,
                        lineBreak = LineBreak.Paragraph
                    ),
                    color = Color.White.copy(alpha = 0.86f),
                    textAlign = TextAlign.Center
                )
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

    /** "Name.2160p.mkv" -> ("Name.2160p", ".mkv"); no known video extension -> (name, null). */
    fun splitExtension(name: String): Pair<String, String?> {
        val m = VIDEO_EXT.find(name) ?: return name to null
        return name.substring(0, m.range.first) to name.substring(m.range.first)
    }

    /** Lets a long release name wrap after dots, underscores and spaces' stand-ins instead of mid-word. */
    fun wrapFriendly(name: String): String = buildString(name.length + 16) {
        for (c in name) {
            append(c)
            if (c == '.' || c == '_') append('\u200B')
        }
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
