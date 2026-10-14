package com.nuvio.tv.data.mdblist

import com.nuvio.tv.core.tracking.TrackingLibraryProvider
import com.nuvio.tv.core.tracking.TrackingProviderId
import com.nuvio.tv.core.tracking.TrackingRefreshIntent
import com.nuvio.tv.domain.model.LibraryEntryInput
import com.nuvio.tv.domain.model.ListMembershipChanges
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MdbListTrackingLibraryProvider @Inject constructor(
    private val service: MdbListLibraryService,
    tracking: MdbListTrackingProvider
) : TrackingLibraryProvider {
    // [fork] library_watched: field-injected so the constructor stays official
    @javax.inject.Inject lateinit var nuvioCWatched: com.nuvio.tv.data.nuvioc.NuvioCWatchedLibrary

    override val providerId = TrackingProviderId.MDBLIST
    override val listManager = service.listManager
    override val listSorter = service.listSorter
    override val isAuthenticated = tracking.isAuthenticated
    override val isRefreshing = service.isRefreshing
    override val items by lazy { if (::nuvioCWatched.isInitialized) nuvioCWatched.mdbListItems(service.items) else service.items } // [fork] + Watched tab
    override val tabs by lazy { if (::nuvioCWatched.isInitialized) nuvioCWatched.mdbListTabs(service.tabs, providerId.storageId) else service.tabs } // [fork] + Watched tab

    override fun recognizesListKey(key: String): Boolean =
        key == MDBLIST_WATCHLIST_KEY || key.startsWith(MDBLIST_LIST_KEY_PREFIX) ||
            key.startsWith(MDBLIST_EXTERNAL_LIST_KEY_PREFIX)

    override fun observeMembership(itemId: String, itemType: String) = service.observeMembership(itemId, itemType)

    override fun toggledDefaultMembership(currentMembership: Map<String, Boolean>): Map<String, Boolean> =
        currentMembership + (MDBLIST_WATCHLIST_KEY to (currentMembership[MDBLIST_WATCHLIST_KEY] != true))

    override suspend fun getMembershipSnapshot(item: LibraryEntryInput) = service.getMembershipSnapshot(item)

    override suspend fun applyMembershipChanges(item: LibraryEntryInput, changes: ListMembershipChanges, destructiveRemovalConfirmed: Boolean) =
        service.applyMembershipChanges(item, changes)

    override suspend fun refresh(intent: TrackingRefreshIntent) = service.refresh(intent)
}
