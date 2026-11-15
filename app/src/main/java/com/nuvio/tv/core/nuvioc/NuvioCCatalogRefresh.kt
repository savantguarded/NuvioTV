package com.nuvio.tv.core.nuvioc

// [fork] Nuvio C round 47 `catalog_refresh`: catalog names (and the rest of each add-on's manifest) are read again
// without a force close. Official refreshes manifests only when the installed add-on list changes, so on a TV that
// keeps Nuvio C in memory for days the names never move. This asks every enabled add-on for its manifest again:
//  - when the app comes back to the screen after 30 minutes or more away,
//  - when it comes back and the last refresh is 6 hours old,
//  - when you switch profile.
// It only calls official's own fetchAddon(): a changed manifest updates the cache through official's path and the
// rows pick up the new names; unchanged ones change nothing. It never runs while you're in the middle of browsing.
// `adb logcat -s NuvioCCatalogs` shows each pass.

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.domain.repository.AddonRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NuvioCCatalogRefresh @Inject constructor(
    private val addons: AddonRepository,
    private val profiles: ProfileManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = 0
    private var stoppedAt = 0L
    @Volatile private var lastRefresh = System.currentTimeMillis() // a cold start already reads fresh manifests
    @Volatile private var running = false

    fun start(app: Application) {
        if (!NuvioCFeatures.CATALOG_REFRESH) return
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                started++
                if (started != 1) return
                val now = System.currentTimeMillis()
                val away = if (stoppedAt > 0L) now - stoppedAt else 0L
                if (away >= AWAY_MS || (stoppedAt > 0L && now - lastRefresh >= EVERY_MS)) refresh("back after ${away / 60_000} min")
            }
            override fun onActivityStopped(activity: Activity) {
                started = (started - 1).coerceAtLeast(0)
                if (started == 0) stoppedAt = System.currentTimeMillis()
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        scope.launch {
            profiles.activeProfileId.drop(1).collect {
                delay(3_000) // after the profile's own start-up loading
                refresh("profile switch")
            }
        }
    }

    private fun refresh(reason: String) {
        if (running) return
        running = true
        scope.launch {
            try {
                val list = withTimeoutOrNull(10_000) { addons.getInstalledAddons().first { it.isNotEmpty() } }.orEmpty()
                    .filter { it.enabled }
                val results = list.map { a -> async { runCatching { addons.fetchAddon(a.baseUrl) }.getOrNull() } }.awaitAll()
                lastRefresh = System.currentTimeMillis()
                Log.i(TAG, "$reason: re-read ${list.size} add-on manifests (${results.count { it is com.nuvio.tv.core.network.NetworkResult.Success }} answered)")
            } finally {
                running = false
            }
        }
    }

    private companion object {
        const val TAG = "NuvioCCatalogs"
        const val AWAY_MS = 30 * 60 * 1000L
        const val EVERY_MS = 6 * 60 * 60 * 1000L
    }
}
