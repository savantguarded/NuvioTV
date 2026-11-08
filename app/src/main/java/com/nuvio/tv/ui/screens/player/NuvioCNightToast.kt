package com.nuvio.tv.ui.screens.player

// [fork] Nuvio C round 34 `night_schedule`: "Night mode · Medium" when a title starts inside the schedule. Same pill as
// official's aspect-ratio indicator (black 85%, grey icon circle, top centre 80 dp down, 18 sp semibold), 2.5 s,
// shown once the loading screen has gone.

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.R
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.delay

@Composable
internal fun NuvioCNightToast(ready: Boolean, hdrDim: Boolean, modifier: Modifier = Modifier) {
    if (!NuvioCFeatures.NIGHT_SCHEDULE) return
    val level by NuvioCNight.autoStarted.collectAsState()
    var shown by remember { mutableStateOf(false) }
    val current = level
    LaunchedEffect(current, ready) {
        if (current == null || !ready) return@LaunchedEffect
        shown = true
        delay(2_500)
        shown = false
        delay(300)
        NuvioCNight.consumeAutoStarted()
    }
    AnimatedVisibility(visible = shown && current != null, enter = fadeIn(), exit = fadeOut(), modifier = modifier.nuvioCHdrDim(hdrDim)) {
        if (NuvioCFeatures.NIGHT_TOAST_COMPACT) {
            // [fork] round 35 night_toast_compact: a small pill near the top that just says "Night mode audio".
            Row(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.80f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Filled.Bedtime, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.nuvio_c_night_toast_audio),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    color = Color.White
                )
            }
            return@AnimatedVisibility
        }
        Row(
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(NuvioTheme.spacing.xl))
                .padding(horizontal = 20.dp, vertical = NuvioTheme.spacing.md),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(36.dp).background(Color(0xFF3B3B3B), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Filled.Bedtime, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(NuvioTheme.spacing.md))
            Text(
                text = stringResource(R.string.nuvio_c_night_toast, stringResource(nuvioCNightLabel(current ?: return@Row))),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                color = Color.White
            )
        }
    }
}
