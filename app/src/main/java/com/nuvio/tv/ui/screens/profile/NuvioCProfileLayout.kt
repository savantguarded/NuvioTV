package com.nuvio.tv.ui.screens.profile

// [fork] Nuvio C profile screen tweaks (choosing a profile; "Manage profiles" stays official).

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.R

// ---- Clean profile screen (2026-10-11, switch PROFILE_CLEAN) ------------------------------------
// Official layout and card sizes, minus "Who's watching?", the "Hold to manage profile" hint, the
// primary star and the "Primary" line; "Select a profile to continue" stays. Logo, line, profiles
// and an "Add Profile" pill centred under the profiles sit together in the middle of the screen.
// Hooks: a few `[fork]` lines in ProfileSelectionScreen.kt. Manage profiles stays official.

internal val LocalNuvioCProfileClean = androidx.compose.runtime.staticCompositionLocalOf { false }

internal fun nuvioCProfileClean(isManagementMode: Boolean): Boolean =
    NuvioCFeatures.PROFILE_CLEAN && !isManagementMode

/** Gap between "Select a profile to continue" and the profiles. */
internal val NUVIO_C_SUBTITLE_TO_PROFILES = 44.dp

@Composable
internal fun NuvioCAddProfileButton(
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    Spacer(modifier = Modifier.height(28.dp))
    androidx.tv.material3.Button(
        onClick = onClick,
        modifier = Modifier
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocused() },
        shape = androidx.tv.material3.ButtonDefaults.shape(shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp)),
        scale = androidx.tv.material3.ButtonDefaults.scale(focusedScale = 1.05f),
        colors = androidx.tv.material3.ButtonDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.10f),
            contentColor = Color.White.copy(alpha = 0.85f),
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = stringResource(R.string.profile_add_new), fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

// ---- Profiles down the left (round 7, 2026-10-13, switch PROFILE_LEFT) --------------------------
// The clean screen's own cards (same avatars, ring, focus zoom, press-and-hold menu, PIN) in one
// column on the left, each name beside its avatar, so the rotating profile background stays clear.
// Nothing written on the screen; a dark wash over the left third only. "Add profile" is the last
// row (official add circle). Four rows fit a 540 dp screen; more scroll with focus.
// Mockup: widget "nuvio_c_round7_profile_rail_and_subtitle_top", option A.

internal val LocalNuvioCProfileLeft = androidx.compose.runtime.staticCompositionLocalOf { false }

internal fun nuvioCProfileLeft(): Boolean = NuvioCFeatures.PROFILE_LEFT

/** Gap between an avatar and its name in the left column. */
internal val NUVIO_C_SIDE_NAME_GAP = 18.dp

internal fun Modifier.nuvioCProfileSideWidth(): Modifier = widthIn(max = 420.dp)

internal fun Modifier.nuvioCProfileLeftWash(): Modifier = drawBehind {
    drawRect(
        Brush.horizontalGradient(
            0f to Color.Black.copy(alpha = 0.82f),
            0.30f to Color.Black.copy(alpha = 0.45f),
            0.55f to Color.Transparent
        )
    )
}

/** Card container: the official column (avatar over name), or a row (name beside) on the left rail. */
@Composable
internal fun NuvioCProfileCardLayout(side: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
    if (side) Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) { content() }
    else Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) { content() }
}

@Composable
internal fun NuvioCProfileLeftColumn(itemCount: Int, content: @Composable () -> Unit) {
    // [fork] round 34 profile_block_centred: the list no longer fills (and centres itself in) the height left under
    // the heading; heading and list are centred together by the screen's column, with the list still scrolling.
    Box(modifier = if (NuvioCFeatures.PROFILE_BLOCK_CENTRED) Modifier else Modifier.fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp), // room for the 1.04 focus zoom at the ends
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) { content() }
    }
}

/** [fork] round 27 `profile_avatar_small`: on the "who's watching" screen (not Manage profiles) the profile
 * pictures, their rings and boxes are 10% smaller (96 -> 86 dp, 102 -> 92 focused). */
@Composable
@androidx.compose.runtime.ReadOnlyComposable
internal fun androidx.compose.ui.unit.Dp.nuvioCAvatar(): androidx.compose.ui.unit.Dp =
    if (NuvioCFeatures.PROFILE_AVATAR_SMALL && LocalNuvioCProfileClean.current) this * 0.9f else this

// ---- [fork] round 31 profile_check ----

/** Solid white disc with a black tick on the profile in use. Sits at the avatar's corner, just outside the ring
 *  (black outline keeps it apart from the ring and the picture), fully opaque, and scales with the card. */
@Composable
internal fun NuvioCProfileInUseBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .then(if (NuvioCFeatures.PROFILE_CHECK_EDGE) Modifier else Modifier.offset(x = 4.dp, y = 4.dp))
            .size(24.dp)
            .background(Color.Black, CircleShape)
            .padding(if (NuvioCFeatures.PROFILE_CHECK_EDGE) 1.5.dp else 2.dp)
            .background(Color.White, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        androidx.tv.material3.Icon(
            imageVector = androidx.compose.material.icons.Icons.Default.Check,
            contentDescription = null,
            tint = Color.Black,
            modifier = Modifier.size(15.dp)
        )
    }
}

/** [fork] round 32 `profile_check_edge`: offset from the avatar box's centre that puts the badge's centre on the
 *  ring at the lower-right 45 degree point (ring radius = [ringSize] / 2, badge 24 dp). */
internal fun nuvioCProfileBadgeOffset(ringSize: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp =
    ringSize / 2 * 0.7071f

/** "Choose a profile" with "Select a profile to continue" under it, above the profiles on the left rail. */
@Composable
internal fun NuvioCProfileHeading(subtitle: String) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.padding(
            start = 12.dp,
            // round 34 profile_block_centred: 2 dp + the list's 12 dp zoom room = 14 dp to the first ring
            bottom = if (NuvioCFeatures.PROFILE_BLOCK_CENTRED) 2.dp else if (NuvioCFeatures.PROFILE_HEADING_TIGHT) 10.dp else 22.dp,
            top = if (NuvioCFeatures.PROFILE_HEADING_DROP && !NuvioCFeatures.PROFILE_BLOCK_CENTRED) 40.dp else 0.dp
        ) // [fork] round 32, round 33 profile_heading_tight
    ) {
        if (NuvioCFeatures.PROFILE_GREETING) {
            // [fork] round 36 profile_greeting: a time-of-day greeting with "Choose a profile" under it
            val greeting = androidx.compose.ui.res.stringResource(androidx.compose.runtime.remember { nuvioCGreetingRes() })
            androidx.tv.material3.Text(
                text = greeting,
                color = com.nuvio.tv.ui.theme.NuvioTheme.colors.TextPrimary,
                fontSize = 30.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                letterSpacing = (-0.4).sp,
                maxLines = 1
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(2.dp))
            androidx.tv.material3.Text(
                text = androidx.compose.ui.res.stringResource(com.nuvio.tv.R.string.nuvio_c_profile_choose),
                color = com.nuvio.tv.ui.theme.NuvioTheme.colors.TextSecondary,
                fontSize = 15.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                maxLines = 1
            )
            return@Column
        }
        androidx.tv.material3.Text(
            text = androidx.compose.ui.res.stringResource(com.nuvio.tv.R.string.nuvio_c_profile_choose),
            color = com.nuvio.tv.ui.theme.NuvioTheme.colors.TextPrimary,
            fontSize = 26.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            maxLines = 1
        )
        if (NuvioCFeatures.PROFILE_HEADING_TIGHT) return@Column // [fork] round 33: no "Select a profile to continue" line
        androidx.compose.foundation.layout.Spacer(Modifier.height(4.dp))
        androidx.tv.material3.Text(
            text = subtitle,
            color = com.nuvio.tv.ui.theme.NuvioTheme.colors.TextSecondary,
            fontSize = 15.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            maxLines = 1
        )
    }
}

/** [fork] round 36 profile_greeting: greeting by the hour (morning 5-11, afternoon 12-16, evening 17-23, late 0-4). */
internal fun nuvioCGreetingRes(hour: Int = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)): Int = when (hour) {
    in 5..11 -> com.nuvio.tv.R.string.nuvio_c_greeting_morning
    in 12..16 -> com.nuvio.tv.R.string.nuvio_c_greeting_afternoon
    in 17..23 -> com.nuvio.tv.R.string.nuvio_c_greeting_evening
    else -> com.nuvio.tv.R.string.nuvio_c_greeting_late
}

/**
 * [fork] round 36 profile_greeting: a soft glow in the profile's own colour around the focused ring. Drawn behind,
 * at most 10 dp past the ring so it stays inside the list's 12 dp zoom room (nothing clips, no layout change).
 */
internal fun Modifier.nuvioCProfileGlow(color: Color, ring: androidx.compose.ui.unit.Dp, progress: () -> Float): Modifier =
    if (com.nuvio.tv.NuvioCFeatures.PROFILE_GLOW_LIGHT) {
        // [fork] round 37 profile_glow_light: the gradient is built once per size (drawWithCache) and only its
        // alpha follows focus, instead of a new gradient on every frame of every profile while the screen opens.
        drawWithCache {
            val ringR = ring.toPx() / 2f
            val outer = ringR + 10.dp.toPx()
            val brush = Brush.radialGradient(
                0f to color.copy(alpha = 0f),
                (ringR / outer) * 0.92f to color.copy(alpha = 0.55f),
                1f to color.copy(alpha = 0f),
                center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f),
                radius = outer
            )
            onDrawBehind {
                val p = progress()
                if (p > 0.01f) drawCircle(brush = brush, radius = outer, center = center, alpha = p.coerceIn(0f, 1f))
            }
        }
    } else drawBehind {
    val p = progress()
    if (p <= 0.01f) return@drawBehind
    val ringR = ring.toPx() / 2f
    val outer = ringR + 10.dp.toPx() // round 36c: 10 dp so the 1.04 focus zoom still stays inside the list's 12 dp room
    drawCircle(
        brush = Brush.radialGradient(
            0f to color.copy(alpha = 0f),
            (ringR / outer) * 0.92f to color.copy(alpha = 0.55f * p),
            1f to color.copy(alpha = 0f),
            center = center,
            radius = outer
        ),
        radius = outer,
        center = center
    )
}
