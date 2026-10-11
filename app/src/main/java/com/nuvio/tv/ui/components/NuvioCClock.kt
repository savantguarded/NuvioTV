package com.nuvio.tv.ui.components

// [fork] Nuvio C clock (2026-10-11): a small persistent clock top-right while browsing catalogs,
// like Plex. Shown on Home and inside collection folders only (Charles's pick); the player has its
// own clock. Updates once a minute on the minute, follows the TV's 12 / 24-hour setting
// with AM / PM like the player clock, on a see-through rounded square.
// Hooked with one line in MainActivity over the navigation host. Switch: NuvioCFeatures.BROWSE_CLOCK.

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.NuvioCFeatures
import kotlinx.coroutines.delay
import java.util.Date

/** Routes that show the clock: Home and collection folders. Add a route prefix here to show it elsewhere. */
private val NUVIO_C_CLOCK_ROUTES = listOf("home", "folder_detail")

internal fun nuvioCClockRoute(route: String?): Boolean =
    route != null && NUVIO_C_CLOCK_ROUTES.any { route == it || route.startsWith("$it/") || route.startsWith("$it?") }

@Composable
fun NuvioCBrowseClock(route: String?) {
    if (!NuvioCFeatures.BROWSE_CLOCK) return
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
        AnimatedVisibility(
            visible = nuvioCClockRoute(route),
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(150))
        ) {
            val context = LocalContext.current
            val now by produceState(System.currentTimeMillis()) {
                while (true) {
                    value = System.currentTimeMillis()
                    delay(60_000L - value % 60_000L + 50L) // next minute boundary
                }
            }
            // 2026-10-11 round 4: same format as the player clock ("4:14 AM", or 24 h if the TV is
            // set to it) on a see-through rounded square.
            val formatter = remember(context) { DateFormat.getTimeFormat(context) }
            Text(
                text = formatter.format(Date(now)),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = Color.White.copy(alpha = 0.95f),
                maxLines = 1,
                modifier = Modifier
                    .padding(top = 20.dp, end = 28.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}
