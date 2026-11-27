package com.nuvio.tv.ui.screens.collection

// [fork] Nuvio C round 67 `COLLECTION_META_ORDER`: collection titles get their info in Home's order. Home asks your
// meta add-on and TMDB at the same time, applies the add-on meta first and TMDB on top only for the fields its
// settings allow; collections asked TMDB first and the add-on only when TMDB failed, so a title showed TMDB's text,
// then switched when its details page loaded the add-on meta. Pure merges, for tests.

import com.nuvio.tv.core.tmdb.TmdbEnrichment
import com.nuvio.tv.domain.model.Meta
import com.nuvio.tv.domain.model.MetaPreview

internal object NuvioCFolderMetaOrder {
    val on: Boolean get() = com.nuvio.tv.NuvioCFeatures.COLLECTION_META_ORDER

    /** The add-on meta as the base layer (Home's `updateCatalogItemWithMeta` fields, plus the year line). */
    fun mergeAddon(current: MetaPreview, meta: Meta): MetaPreview {
        val seasons = meta.videos.asSequence().mapNotNull { it.season }.filter { it > 0 }.distinct().count().takeIf { it > 0 }
        return current.copy(
            background = meta.backdropUrl ?: current.backdropUrl,
            logo = meta.logo ?: current.logo,
            description = meta.description ?: current.description,
            imdbRating = meta.imdbRating ?: current.imdbRating,
            genres = if (meta.genres.isNotEmpty()) meta.genres else current.genres,
            runtime = meta.runtime ?: current.runtime,
            status = meta.status ?: current.status,
            ageRating = meta.ageRating ?: current.ageRating,
            language = meta.language ?: current.language,
            country = meta.country ?: current.country,
            seasonCount = seasons ?: current.seasonCount,
            releaseInfo = meta.releaseInfo?.takeIf { it.isNotBlank() } ?: current.releaseInfo,
            trailerYtIds = if (meta.trailerYtIds.isNotEmpty()) meta.trailerYtIds else current.trailerYtIds
        )
    }

    /** TMDB on top, only where its settings allow (the collection's existing TMDB merge, unchanged). */
    fun mergeTmdb(
        current: MetaPreview, e: TmdbEnrichment,
        useBasicInfo: Boolean, useArtwork: Boolean, useDetails: Boolean, isModern: Boolean
    ): MetaPreview {
        var r = current
        if (useBasicInfo) r = r.copy(
            name = if (isModern) e.localizedTitle ?: r.name else r.name,
            description = e.description ?: r.description,
            genres = if (e.genres.isNotEmpty()) e.genres else r.genres
        )
        if (useArtwork) r = r.copy(background = e.backdrop ?: r.background, logo = e.logo ?: r.logo)
        if (useDetails) r = r.copy(
            runtime = e.runtimeMinutes?.toString() ?: r.runtime,
            ageRating = e.ageRating ?: r.ageRating,
            status = e.status ?: r.status
        )
        val yt = e.trailers.mapNotNull { it.ytId }.distinct()
        if (yt.isNotEmpty() && r.trailerYtIds.isEmpty()) r = r.copy(trailerYtIds = yt)
        return r
    }

    /** Both layers in Home's order. Pure, for tests. */
    fun merge(
        current: MetaPreview, addon: Meta?, tmdb: TmdbEnrichment?,
        useBasicInfo: Boolean, useArtwork: Boolean, useDetails: Boolean, isModern: Boolean
    ): MetaPreview {
        var r = current
        if (addon != null) r = mergeAddon(r, addon)
        if (tmdb != null) r = mergeTmdb(r, tmdb, useBasicInfo, useArtwork, useDetails, isModern)
        return r
    }
}
