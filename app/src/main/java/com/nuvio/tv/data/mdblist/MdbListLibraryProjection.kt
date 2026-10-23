package com.nuvio.tv.data.mdblist

import com.nuvio.tv.domain.model.LibraryEntry

internal class MdbListLibraryProjection(snapshot: MdbListLibrarySnapshot) {
    val entries: List<LibraryEntry>
    private val memberships = mutableMapOf<Pair<MdbListItemType, String>, Set<String>>()

    init {
        val index = MdbListMediaIndex(MdbListSyncSnapshot(0))
        snapshot.itemsByList.values.flatten().forEach { index.add(it.type, it.media) }
        val combined = linkedMapOf<String, LibraryEntry>()
        snapshot.itemsByList.forEach { (listKey, items) ->
            items.forEachIndexed { position, item ->
                val media = index.resolve(item.type, item.media.ids)
                val key = "${item.type}:${media.ids.key}"
                val previous = combined[key]
                val listKeys = previous?.listKeys.orEmpty() + listKey
                // Items arrive in the list's saved MDBList order, which is the provider order.
                val ranks = previous?.listRanks.orEmpty() + (listKey to position)
                combined[key] = LibraryEntry(
                    id = media.ids.contentId,
                    type = if (item.type == MdbListItemType.MOVIE) "movie" else "series",
                    name = media.title ?: media.ids.contentId,
                    poster = media.poster?.takeIf(String::isNotBlank) ?: nuvioCLibraryPosterFallback(media.ids.imdb), // [fork] library_poster_fallback
                    background = media.backdrop,
                    logo = null,
                    description = previous?.description ?: item.description,
                    releaseInfo = media.year?.toString(),
                    imdbRating = null,
                    genres = (previous?.genres.orEmpty() + item.genres).distinct(),
                    addonBaseUrl = null,
                    listKeys = listKeys,
                    listRanks = ranks,
                    listedAt = maxOf(previous?.listedAt ?: 0, item.listedAt),
                    imdbId = media.ids.imdb,
                    tmdbId = media.ids.tmdb?.takeIf { it <= Int.MAX_VALUE }?.toInt(),
                    traktId = media.ids.trakt?.takeIf { it <= Int.MAX_VALUE }?.toInt(),
                    trackingProviderId = "mdblist",
                    trackingProviderItemId = media.ids.mdblist,
                    trackingSourceUrl = media.ids.mdblist?.let { "https://mdblist.com/${if (item.type == MdbListItemType.MOVIE) "movie" else "show"}/$it" },
                    rawPosterUrl = media.poster
                )
                media.ids.aliases().forEach { alias -> memberships[item.type to alias] = listKeys }
            }
        }
        entries = combined.values.toList()
    }

    fun membership(id: String, type: String): Set<String> = memberships[mdbListLibraryType(type) to id].orEmpty()
}

/**
 * [fork] library_poster_fallback: MDBList sometimes has no poster for a title; Stremio's image service has one for any
 * IMDb id (the same posters Cinemeta uses), so the card is never empty. Null for titles without an IMDb id.
 */
internal fun nuvioCLibraryPosterFallback(imdb: String?): String? =
    if (com.nuvio.tv.NuvioCFeatures.LIBRARY_POSTER_FALLBACK && imdb != null && imdb.startsWith("tt")) {
        "https://images.metahub.space/poster/medium/$imdb/img"
    } else null
