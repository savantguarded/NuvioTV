package com.nuvio.tv.core.server

internal fun sanitizePendingAddonChange(
    mode: AddonWebConfigMode,
    proposedChange: PendingAddonChange,
    currentState: PageState
): PendingAddonChange = nuvioCSanitizeHealthSources(mode, sanitizePendingAddonChangeOfficial(mode, proposedChange, currentState)) // [fork] round 67

/**
 * [fork] round 67 addon_health: status sources from the phone page go through the same address check as the TV
 * (https only, no login, no key-like parameters, 300 characters, at most 4); anything else is dropped. Modes that
 * don't allow add-on management leave them unchanged.
 */
internal fun nuvioCSanitizeHealthSources(mode: AddonWebConfigMode, change: PendingAddonChange): PendingAddonChange {
    val raw = change.proposedHealthSources ?: return change
    if (!com.nuvio.tv.NuvioCFeatures.ADDON_HEALTH || !mode.allowAddonManagement) return change.copy(proposedHealthSources = null)
    val clean = raw.asSequence()
        .mapNotNull { (com.nuvio.tv.core.nuvioc.health.NuvioCHealthRules.checkSourceAddress(it) as? com.nuvio.tv.core.nuvioc.health.NuvioCHealthRules.Checked.Ok)?.address }
        .distinct().take(com.nuvio.tv.core.nuvioc.health.NuvioCHealthRules.MAX_SOURCES).toList()
    return change.copy(proposedHealthSources = clean)
}

private fun sanitizePendingAddonChangeOfficial(
    mode: AddonWebConfigMode,
    proposedChange: PendingAddonChange,
    currentState: PageState
): PendingAddonChange {
    if (
        mode.allowAddonManagement &&
        mode.allowCatalogManagement &&
        mode.allowCollectionManagement
    ) {
        return proposedChange
    }

    return proposedChange.copy(
        proposedUrls = if (mode.allowAddonManagement) {
            proposedChange.proposedUrls
        } else {
            currentState.addons.map { it.url }
        },
        proposedCatalogOrderKeys = if (mode.allowCatalogManagement) {
            proposedChange.proposedCatalogOrderKeys
        } else {
            currentState.catalogs.map { it.key }
        },
        proposedDisabledCatalogKeys = if (mode.allowCatalogManagement) {
            proposedChange.proposedDisabledCatalogKeys
        } else {
            currentState.catalogs
                .filter { it.isDisabled }
                .map { it.disableKey }
        },
        proposedCollectionsJson = if (mode.allowCollectionManagement) {
            proposedChange.proposedCollectionsJson
        } else {
            null
        },
        proposedDisabledCollectionKeys = if (mode.allowCollectionManagement) {
            proposedChange.proposedDisabledCollectionKeys
        } else {
            currentState.disabledCollectionKeys
        }
    )
}
