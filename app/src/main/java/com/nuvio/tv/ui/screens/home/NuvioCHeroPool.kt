package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.domain.model.MetaPreview

// [fork] Nuvio C hero pool (switch `hero_pool_shuffle`, 2026-10-15). Official takes 7 hero titles in
// total from the hero catalogs, picked by a fixed hash of catalog + id, so the carousel showed the
// same 7 all day (and every launch until the catalogs changed). Now 18 titles, a new random pick and
// order every app launch, mixed across catalogs. Within one run the order stays put (titles already
// shown keep their place; catalogs arriving later add theirs at the end), so slides never jump.
object NuvioCHeroPool {
    private const val OFFICIAL_SIZE = 7
    private const val SIZE = 18

    val size: Int get() = if (NuvioCFeatures.HERO_POOL_SHUFFLE) SIZE else OFFICIAL_SIZE

    /** Per process: a new value each app launch, 0 (official order) with the switch off. */
    val seed: Int = if (NuvioCFeatures.HERO_POOL_SHUFFLE) kotlin.random.Random.nextInt() else 0

    fun order(items: List<MetaPreview>, currentOrder: List<String>): List<MetaPreview> {
        if (!NuvioCFeatures.HERO_POOL_SHUFFLE || items.size < 2) return items
        val existing = HashMap<String, Int>(currentOrder.size * 2)
        currentOrder.forEachIndexed { i, id -> existing.putIfAbsent(id, i) }
        return items.sortedWith(
            compareBy<MetaPreview> { existing[it.id] ?: Int.MAX_VALUE }
                .thenBy { mix(it.id) }
                .thenBy { it.id }
        )
    }

    internal fun mix(id: String): Int {
        var h = id.hashCode() xor seed
        h = h xor (h ushr 16); h *= 0x45d9f3b; h = h xor (h ushr 16)
        return h
    }
}

// [fork] Nuvio C featured_badges (2026-10-17): which hero catalog each carousel title came from and
// its position there, for the "#3 in Top 20" line. Filled when the hero list is built from rows
// already loaded (no request); read when a slide is drawn.
object NuvioCHeroSource {
    data class Entry(val catalogName: String, val rank: Int)

    private val entries = java.util.concurrent.ConcurrentHashMap<String, Entry>()

    fun record(items: List<MetaPreview>, rows: List<com.nuvio.tv.domain.model.CatalogRow>) {
        if (!NuvioCFeatures.FEATURED_BADGES) return
        items.forEach { item ->
            for (row in rows) {
                val index = row.items.indexOfFirst { it.id == item.id }
                if (index >= 0) {
                    entries[item.id] = Entry(row.catalogName, index + 1)
                    break
                }
            }
        }
    }

    fun of(id: String): Entry? = entries[id]
}
