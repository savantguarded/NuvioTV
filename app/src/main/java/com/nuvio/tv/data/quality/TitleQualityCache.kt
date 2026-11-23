package com.nuvio.tv.data.quality

// [fork] Nuvio C round 60 `detail_quality_line`: memory + disk cache of the compact result only (tags, fetched
// time, expiry), never a Torrentio answer. Shared by every profile: it holds public release facts, nothing personal.
// Round 60d: one shared instance, loaded into memory shortly after start-up, with a title-id index, so a title seen
// before shows its badges on the first frame even right after a cold start.

import android.content.Context
import androidx.core.content.edit

enum class CachedKind { FOUND, NOT_ON_DIGITAL }

data class CachedQuality(val kind: CachedKind, val quality: TitleQuality?, val fetchedAt: Long, val expiresAt: Long) {
    fun fresh(now: Long) = now < expiresAt

    fun encode(): String = listOf(
        kind.name, quality?.resolution?.name.orEmpty(), quality?.range?.name.orEmpty(), quality?.audio?.name.orEmpty(),
        quality?.source?.name.orEmpty(), fetchedAt.toString(), expiresAt.toString(),
        quality?.editions.orEmpty().joinToString(",") { it.name } // round 64
    ).joinToString("|")

    companion object {
        fun decode(raw: String?): CachedQuality? = runCatching {
            val p = raw!!.split("|")
            // round 64: 8 fields (editions last); a round 60-63 entry (7 fields) is read again once
            if (p.size != 8) return null
            val kind = CachedKind.valueOf(p[0])
            val q = p[1].takeIf { it.isNotEmpty() }?.let { r ->
                TitleQuality(
                    QualityResolution.valueOf(r),
                    p[2].takeIf { it.isNotEmpty() }?.let { QualityRange.valueOf(it) },
                    p[3].takeIf { it.isNotEmpty() }?.let { QualityAudio.valueOf(it) },
                    p[4].takeIf { it.isNotEmpty() }?.let { QualitySource.valueOf(it) },
                    p[7].split(",").filter { it.isNotEmpty() }.mapNotNull { n -> runCatching { QualityEdition.valueOf(n) }.getOrNull() }
                )
            }
            if (kind == CachedKind.FOUND && q == null) return null
            CachedQuality(kind, q, p[5].toLong(), p[6].toLong())
        }.getOrNull()
    }
}

class TitleQualityCache private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val memory = object : LinkedHashMap<String, CachedQuality>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedQuality>?) = size > MEMORY_MAX
    }
    /** title id -> cache key, so the first frame needs no planning. */
    private val keyOfMeta = HashMap<String, String>()
    @Volatile private var warmed = false

    /** Loads the disk copy into memory once (call off the main thread); drops expired and surplus entries. */
    @Synchronized fun warm() {
        if (warmed) return
        warmed = true
        val now = System.currentTimeMillis()
        val all = prefs.all
        val results = all.mapNotNull { (k, v) ->
            if (k.startsWith(ID_PREFIX)) null else CachedQuality.decode(v as? String)?.let { k to it }
        }
        val keep = results.filter { it.second.fresh(now) }.sortedByDescending { it.second.fetchedAt }.take(DISK_MAX)
        keep.reversed().forEach { (k, v) -> memory[k] = v }
        val keptKeys = keep.mapTo(HashSet()) { it.first }
        val ids = all.mapNotNull { (k, v) ->
            val key = v as? String
            if (k.startsWith(ID_PREFIX) && key != null && key in keptKeys) k.removePrefix(ID_PREFIX) to key else null
        }
        ids.forEach { (id, key) -> keyOfMeta[id] = key }
        if (keep.size + ids.size != all.size) prefs.edit {
            clear()
            keep.forEach { (k, v) -> putString(k, v.encode()) }
            ids.forEach { (id, key) -> putString(ID_PREFIX + id, key) }
        }
    }

    /** Memory only, safe on the main thread. */
    @Synchronized fun peek(key: String, now: Long = System.currentTimeMillis()): CachedQuality? =
        memory[key]?.takeIf { it.fresh(now) }

    /** Memory only, O(1), for the first frame: the last result for this title id. */
    @Synchronized fun peekMeta(metaId: String, now: Long = System.currentTimeMillis()): CachedQuality? =
        keyOfMeta[metaId]?.let { peek(it, now) }

    @Synchronized fun get(key: String, now: Long = System.currentTimeMillis()): CachedQuality? {
        warm()
        return peek(key, now)
    }

    /** Call off the main thread (writes the disk copy). */
    @Synchronized fun put(key: String, value: CachedQuality, metaId: String? = null) {
        memory[key] = value
        prefs.edit {
            putString(key, value.encode())
            if (metaId != null) putString(ID_PREFIX + metaId, key)
        }
        if (metaId != null) remember(metaId, key)
    }

    /** Remembers which entry a title id uses (memory and disk; call off the main thread). */
    @Synchronized fun link(metaId: String, key: String) {
        if (keyOfMeta[metaId] == key) return
        remember(metaId, key)
        prefs.edit { putString(ID_PREFIX + metaId, key) }
    }

    private fun remember(metaId: String, key: String) {
        if (keyOfMeta.size > MEMORY_MAX * 2) keyOfMeta.clear()
        keyOfMeta[metaId] = key
    }

    companion object {
        private const val PREFS = "nuvio_c_quality"
        private const val ID_PREFIX = "id:"
        private const val MEMORY_MAX = 300
        private const val DISK_MAX = 300

        @Volatile private var instance: TitleQualityCache? = null

        fun shared(context: Context): TitleQualityCache =
            instance ?: synchronized(this) { instance ?: TitleQualityCache(context).also { instance = it } }

        /** Round 60d: load saved results into memory shortly after start-up (off the main thread). */
        fun warmShared(context: Context) { shared(context).warm() }
    }
}
