package com.nuvio.tv.ui.screens.profile

// [fork] Nuvio C minimal profile screen (choosing a profile, not "Manage profiles"): no logo,
// "Who's watching", hint or "Primary" text, so the profile background (e.g. a rotating poster)
// fills the screen. Official profile cards (focus ring, press-and-hold menu, PIN) are kept as
// they are, sit higher up so the bottom of the background stays clear, and "Add profile" is a
// pill centred under them instead of an extra card. Switch: NuvioCFeatures.PROFILE_MINIMAL.

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.R
import com.nuvio.tv.ui.theme.NuvioTheme

/** Space above / below the profiles: more below, so they sit in the upper part of the screen. */
internal const val NUVIO_C_PROFILE_TOP_WEIGHT = 0.42f
internal const val NUVIO_C_PROFILE_BOTTOM_WEIGHT = 0.58f

internal fun nuvioCProfileMinimal(isManagementMode: Boolean): Boolean =
    NuvioCFeatures.PROFILE_MINIMAL && !isManagementMode

/** Soft dark glow behind the profiles so names stay readable on any background. */
internal fun Modifier.nuvioCProfileScrim(on: Boolean): Modifier = if (!on) this else drawBehind {
    drawRect(
        Brush.radialGradient(
            colors = listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent),
            center = Offset(size.width / 2f, size.height * 0.4f),
            radius = size.width * 0.42f
        )
    )
}

@Composable
internal fun NuvioCAddProfilePill(
    onClick: () -> Unit,
    takeFocus: Boolean
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(takeFocus) {
        if (!takeFocus) return@LaunchedEffect
        repeat(2) { withFrameNanos { } }
        runCatching { focusRequester.requestFocus() }
    }
    Spacer(modifier = Modifier.size(28.dp))
    Button(
        onClick = onClick,
        modifier = Modifier.focusRequester(focusRequester),
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(50)),
        colors = ButtonDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.14f),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black
        ),
        border = ButtonDefaults.border(
            border = Border(
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                shape = RoundedCornerShape(50)
            ),
            focusedBorder = Border.None
        ),
        scale = ButtonDefaults.scale(focusedScale = 1.06f),
        contentPadding = PaddingValues(horizontal = 26.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(NuvioTheme.spacing.sm))
            Text(stringResource(R.string.profile_add), fontSize = 16.sp)
        }
    }
}
