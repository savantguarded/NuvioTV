package com.nuvio.tv.ui.screens.home

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.CatalogDescriptor
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// [fork] Nuvio C round 27 `home_catalog_sweep` (idea from ysosrs123/NuvioTV-Fork, "reserved-headroom sweep").
// Official loads the first 4 catalogs in addon order and the rest only when their row scrolls into view (Classic
// looks one row ahead, and only after scrolling has stopped for 240 ms), so rows further down sat shimmering.
// Here the first 8 load in the order the rows appear on Home, and the rest load quietly in the background, top
// to bottom, one at a time, and only when:
//   - no row you are looking at (eager or scrolled-to) is still loading, so the sweep never queues ahead of it;
//   - focus hasn't moved on Home for a moment, so the row rebuild a result triggers doesn't land mid-scroll.
// It stops if Home reloads its catalogs (new generation). Same dedupe as the scroll-triggered path, so a catalog
// is never fetched twice. Tuned for a 2 GB TV: ysos uses 5 permits and 2 sweep loads; here 4 and 1.

internal const val NUVIO_C_EAGER_CATALOGS = 8
internal const val NUVIO_C_CATALOG_CONCURRENCY = 4
private const val SWEEP_POLL_MS = 150L
private const val SWEEP_QUIET_MS = 700L
private const val TAG = "NuvioCCatalogSweep"

/** [catalogs] in Home's row order (catalog order); anything not in it keeps its addon order at the end. */
internal fun HomeViewModel.nuvioCDisplayOrder(
    catalogs: List<Pair<Addon, CatalogDescriptor>>
): List<Pair<Addon, CatalogDescriptor>> {
    if (!NuvioCFeatures.HOME_CATALOG_SWEEP) return catalogs
    val byKey = catalogs.associateBy { (addon, catalog) ->
        catalogKey(addonId = addon.id, type = catalog.apiType, catalogId = catalog.id)
    }
    val order = snapshotCatalogState().first.filterNot { it.startsWith("collection_") }
    val orderSet = order.toSet()
    val ordered = order.mapNotNull { byKey[it] }
    val rest = catalogs.filterNot { (addon, catalog) ->
        catalogKey(addonId = addon.id, type = catalog.apiType, catalogId = catalog.id) in orderSet
    }
    return ordered + rest
}

internal fun HomeViewModel.nuvioCStartCatalogSweep(generation: Long) {
    if (!NuvioCFeatures.HOME_CATALOG_SWEEP) return
    nuvioCCatalogSweepJob?.cancel()
    nuvioCCatalogSweepJob = viewModelScope.launch {
        var inFlight: kotlinx.coroutines.Job? = null
        var loaded = 0
        while (isActive && generation == catalogLoadGeneration) {
            val sweepBusy = inFlight?.isActive == true
            val othersPending = pendingCatalogLoads > (if (sweepBusy) 1 else 0)
            val quiet = SystemClock.uptimeMillis() - nuvioCLastFocusMs >= SWEEP_QUIET_MS
            if (sweepBusy || othersPending || !quiet) {
                delay(SWEEP_POLL_MS)
                continue
            }
            val next = nuvioCPullNextLazyCatalog() ?: break
            pendingCatalogLoads = pendingCatalogLoads + 1
            inFlight = loadCatalogPipeline(next.first, next.second, generation)
            loaded++
        }
        if (loaded > 0) Log.i(TAG, "Background-loaded $loaded catalogs (generation $generation)")
    }
}

/** Claims the next pending lazy catalog in row order, with the scroll path's dedupe. Null when none are left. */
private fun HomeViewModel.nuvioCPullNextLazyCatalog(): Pair<Addon, CatalogDescriptor>? =
    synchronized(catalogStateLock) {
        val iterator = pendingLazyCatalogs.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            iterator.remove()
            if (lazyLoadRequestedKeys.add(entry.key)) return@synchronized entry.value
        }
        null
    }
