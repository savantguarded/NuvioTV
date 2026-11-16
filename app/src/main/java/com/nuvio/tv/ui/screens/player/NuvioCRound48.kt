package com.nuvio.tv.ui.screens.player

// [fork] round 48: Next label, chapter double press, OSD shading, episode search indicator.

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.R

/** Focused Next button label: "Next · S1 E5" (round 48b: no episode name). */
@Composable
internal fun nuvioCNextLabel(next: NextEpisodeInfo?, fallback: String): String {
    if (!NuvioCFeatures.OSD_NEXT_LABEL || next == null) return fallback
    return stringResource(R.string.nuvio_c_next_label, next.season, next.episode)
}

/** Clean: whether the chapter name has its own line above the bar (names on and the file has chapters). */
internal fun nuvioCChapterLineShown(chapters: List<PlayerChapter>, showChapterTitle: Boolean): Boolean =
    showChapterTitle && chapters.isNotEmpty()

/** Height (px) Clean's time hangs above the seek bar when it has no row of its own; 0 when it sits on the chapter line. */
internal object NuvioCTimeOverhang {
    @Volatile var px = 0f
}

internal const val NEXT_TITLE_MAX = 32

internal fun nuvioCClipText(text: String, max: Int): String {
    val t = text.trim()
    return if (t.length <= max) t else t.take(max - 1).trimEnd() + "…"
}

/**
 * Seek bar double press: a second Left / Right within [WINDOW_MS] of a single (not held) press the same way
 * jumps to the previous / next chapter, worked out from where the first press started, so the first 10 s step
 * never shifts which chapter is picked. More presses keep jumping. Holding still scrubs.
 */
internal class NuvioCChapterTap {
    sealed interface Result {
        /** Normal seek step. */
        data object Seek : Result
        /** Jump to [targetMs]. */
        data class Jump(val targetMs: Long) : Result
        /** A held repeat of a jump press: swallow it. */
        data object Swallow : Result
    }

    private var lastDir = 0
    private var lastDownMs = Long.MIN_VALUE
    private var lastSingle = false
    private var origin = 0L
    private var jumping = false

    fun onDown(forward: Boolean, repeatCount: Int, eventTimeMs: Long, positionMs: Long, chapters: List<PlayerChapter>): Result {
        val dir = if (forward) 1 else -1
        if (repeatCount > 0) {
            if (dir == lastDir) lastSingle = false
            return if (jumping && dir == lastDir) Result.Swallow else Result.Seek
        }
        val second = dir == lastDir && lastSingle && eventTimeMs - lastDownMs in 0..WINDOW_MS
        lastDir = dir
        lastDownMs = eventTimeMs
        lastSingle = true
        if (!second || chapters.size < 2) {
            jumping = false
            origin = positionMs
            return Result.Seek
        }
        val target = if (forward) PlayerChapters.nextStartMs(chapters, origin) else PlayerChapters.previousStartMs(chapters, origin)
        if (target == null) {
            jumping = false
            origin = positionMs
            return Result.Seek
        }
        jumping = true
        origin = target
        return Result.Jump(target)
    }

    companion object {
        const val WINDOW_MS = 300L
    }
}

/** OSD bottom shade: darker through the title area, same 80% at the bottom edge (was a two-stop 0 -> 80%). */
internal fun nuvioCOsdBottomBrush(): Brush = if (NuvioCFeatures.OSD_SHADE) Brush.verticalGradient(
    0f to Color.Transparent,
    0.18f to Color.Black.copy(alpha = 0.16f),
    0.40f to Color.Black.copy(alpha = 0.42f),
    0.65f to Color.Black.copy(alpha = 0.62f),
    1f to Color.Black.copy(alpha = 0.82f)
) else Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))

/** OSD top shade: holds its strength behind the logo and clock before fading (was a two-stop 70% -> 0). */
internal fun nuvioCOsdTopBrush(): Brush = if (NuvioCFeatures.OSD_SHADE) Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.72f),
    0.45f to Color.Black.copy(alpha = 0.52f),
    0.75f to Color.Black.copy(alpha = 0.2f),
    1f to Color.Transparent
) else Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent))

internal val NUVIO_C_OSD_BOTTOM_SHADE_HEIGHT = 300.dp
internal val NUVIO_C_OSD_TOP_SHADE_HEIGHT = 170.dp

/** Faint shadow under OSD text so it holds on bright frames. */
internal fun TextStyle.nuvioCOsdShadow(): TextStyle =
    if (NuvioCFeatures.OSD_SHADE) copy(shadow = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 1f), blurRadius = 6f)) else this

/**
 * Over the clicked episode's thumbnail while auto source select is still asking the add-ons: a light wash, a small
 * spinner and "Finding streams". Inside the thumbnail, so the list never shifts and the title, date and code stay readable.
 */
@Composable
internal fun NuvioCEpisodeSearching(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            NuvioCSmallSpinner()
            Text(
                text = stringResource(R.string.nuvio_c_finding_streams),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.92f),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun NuvioCSmallSpinner() {
    val transition = rememberInfiniteTransition(label = "nuvio_c_episode_search")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "angle"
    )
    Spacer(
        Modifier.size(18.dp).drawBehind {
            val w = 2.dp.toPx()
            drawCircle(Color.White.copy(alpha = 0.25f), radius = size.minDimension / 2f - w / 2f, style = Stroke(w))
            drawArc(
                color = Color.White,
                startAngle = angle - 90f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(w / 2f, w / 2f),
                size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                style = Stroke(w, cap = StrokeCap.Round)
            )
        }
    )
}
