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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.focus.focusRequester
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

internal fun Context.nuvioCOnline(): Boolean {
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
    // [fork] round 51 offline_notice: "Back online" for 2 s when the connection returns
    var showBack by remember { mutableStateOf(false) }
    var wasOffline by remember { mutableStateOf(!online) }
    androidx.compose.runtime.LaunchedEffect(online) {
        if (!NuvioCFeatures.OFFLINE_NOTICE_V2) return@LaunchedEffect
        if (!online) { wasOffline = true; showBack = false }
        else if (wasOffline) { wasOffline = false; showBack = true; kotlinx.coroutines.delay(2_000); showBack = false }
    }
    AnimatedVisibility(
        visible = !online || (NuvioCFeatures.OFFLINE_NOTICE_V2 && showBack),
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
                text = if (NuvioCFeatures.OFFLINE_NOTICE_V2) stringResource(if (online) R.string.nuvio_c_back_online else R.string.nuvio_c_offline_notice_v2)
                else stringResource(R.string.nuvio_c_offline_notice),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}


// ---- [fork] Nuvio C round 52 offline_everywhere ---------------------------------------------------------------

/** Live "has internet" state for any screen; follows the default network. */
@Composable
internal fun rememberNuvioCOnline(): androidx.compose.runtime.State<Boolean> {
    val context = LocalContext.current
    val online = remember { mutableStateOf(context.nuvioCOnline()) }
    DisposableEffect(context) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { main.post { online.value = true } }
            override fun onLost(network: Network) { main.post { online.value = context.nuvioCOnline() } }
        }
        runCatching { cm?.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { cm?.unregisterNetworkCallback(callback) } }
    }
    return online
}

/**
 * Player: a small chip in the top corner while the connection is gone ("No internet"), "Back online" for 2 s when it
 * returns. Never focusable. The stream itself keeps retrying its buffering on its own as before.
 */
@Composable
fun BoxScope.NuvioCPlayerOfflineChip() {
    if (!NuvioCFeatures.OFFLINE_EVERYWHERE) return
    val online by rememberNuvioCOnline()
    var showBack by remember { mutableStateOf(false) }
    var wasOffline by remember { mutableStateOf(!online) }
    androidx.compose.runtime.LaunchedEffect(online) {
        if (!online) { wasOffline = true; showBack = false }
        else if (wasOffline) { wasOffline = false; showBack = true; kotlinx.coroutines.delay(2_000); showBack = false }
    }
    AnimatedVisibility(
        visible = !online || showBack,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 32.dp).zIndex(20f)
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.72f)).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.padding(end = 8.dp).clip(RoundedCornerShape(4.dp)).background(if (online) Color(0xFF3ECF8E) else Color(0xFFFFB27A)).padding(4.dp))
            Text(
                text = stringResource(if (online) R.string.nuvio_c_back_online else R.string.nuvio_c_offline_chip),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}

/**
 * Home on a cold start with no internet and nothing saved on this TV: one full-screen message with Network settings
 * and Try again. When the connection comes back Home carries on by itself (no press needed).
 */
@Composable
internal fun NuvioCOfflineColdStart(onRetry: () -> Unit) {
    val context = LocalContext.current
    val first = remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        Text(stringResource(R.string.nuvio_c_offline_cold_title), style = MaterialTheme.typography.headlineSmall, color = Color.White)
        Text(
            stringResource(R.string.nuvio_c_offline_cold_sub),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.65f),
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
        )
        androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
            androidx.tv.material3.Button(
                onClick = {
                    runCatching {
                        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                    }.onFailure {
                        runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                },
                modifier = Modifier.focusRequester(first)
            ) { Text(stringResource(R.string.nuvio_c_offline_settings)) }
            androidx.tv.material3.Button(onClick = onRetry) { Text(stringResource(R.string.nuvio_c_offline_retry)) }
        }
    }
}

