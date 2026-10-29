package com.nuvio.tv.ui.screens.home

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.R

// [fork] Nuvio C round 31 (switch `offline_home`): while the TV has no internet, a small notice at the top
// of Home says so (not focusable, so it never takes focus from the rows), and the moment the connection is
// back Home reloads by itself. The Featured carousel letting the rows take focus when it has no titles is in
// NuvioCHomeFeatured.kt.

private fun Context.nuvioCOnline(): Boolean {
    val cm = getSystemService(ConnectivityManager::class.java) ?: return true
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

@Composable
internal fun BoxScope.NuvioCOfflineNotice(onBackOnline: () -> Unit) {
    if (!NuvioCFeatures.OFFLINE_HOME) return
    val context = LocalContext.current
    var online by remember { mutableStateOf(context.nuvioCOnline()) }
    val backOnline by rememberUpdatedState(onBackOnline)
    DisposableEffect(context) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                main.post {
                    val was = online
                    online = true
                    if (!was) backOnline()
                }
            }
            override fun onLost(network: Network) {
                main.post { online = context.nuvioCOnline() }
            }
        }
        runCatching { cm?.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { cm?.unregisterNetworkCallback(callback) } }
    }
    AnimatedVisibility(
        visible = !online,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp).zIndex(10f)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black.copy(alpha = 0.78f))
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.nuvio_c_offline_notice),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}
