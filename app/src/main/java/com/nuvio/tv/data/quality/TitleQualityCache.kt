package com.nuvio.tv.data.quality

// [fork] Nuvio C round 60 `detail_quality_line`: memory + disk cache of the compact result only (tags, fetched
// time, expiry), never a Torrentio answer. Shared by every profile: it holds public release facts, nothing personal.

import android.content.Context
import androidx.core.content.edit

enum class CachedKind { FOUND, NOT_ON_DIGITAL }

data class CachedQuality(val kind: CachedKind, val quality: TitleQuality?, val fetchedAt: Long, val expiresAt: Long) {
    fun fresh(now: Long) = now < expiresAt

    fun encode(): String = listOf(
        kind.name, quality?.resolution?.name.orEmpty(), quality?.range?.name.orEmpty(), quality?.audio?.name.orEmpty(),
        quality?.source?.name.orEmpty(), fetchedAt.toString(), expiresAt.toString()
    ).joinToString("|")

    companion object {
        fun decode(raw: String?): CachedQuality? = runCatching {
            val p = raw!!.split("|")
            if (p.size != 7) return null
            val kind = CachedKind.valueOf(p[0])
            val q = p[1].takeIf { it.isNotEmpty() }?.let { r ->
                TitleQuality(
                    QualityResolution.valueOf(r),
                    p[2].takeIf { it.isNotEmpty() }?.let { QualityRange.valueOf(it) },
                    p[3].takeIf { it.isNotEmpty() }?.let { QualityAudio.valueOf(it) },
                    p[4].takeIf { it.isNotEmpty() }?.let { QualitySource.valueOf(it) }
                )
            }
            if (kind == CachedKind.FOUND && q == null) return null
            CachedQuality(kind, q, p[5].toLong(), p[6].toLong())
        }.getOrNull()
    }
}

class TitleQualityCache(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val memory = object : LinkedHashMap<String, CachedQuality>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedQuality>?) = size > MEMORY_MAX
    }
    @Volatile private var warmed = false

    /** Loads the disk copy into memory once (call off the main thread); drops expired and surplus entries. */
    @Synchronized fun warm() {
        if (warmed) return
        warmed = true
        val now = System.currentTimeMillis()
        val all = prefs.all.mapNotNull { (k, v) -> CachedQuality.decode(v as? String)?.let { k to it } }
        val keep = all.filter { it.second.fresh(now) }.sortedByDescending { it.second.fetchedAt }.take(DISK_MAX)
        keep.reversed().forEach { (k, v) -> memory[k] = v }
        if (keep.size != prefs.all.size) prefs.edit { clear(); keep.forEach { (k, v) -> putString(k, v.encode()) } }
    }

    /** Memory only, safe on the main thread (first frame). */
    @Synchronized fun peek(key: String, now: Long = System.currentTimeMillis()): CachedQuality? =
        memory[key]?.takeIf { it.fresh(now) }

    @Synchronized fun get(key: String, now: Long = System.currentTimeMillis()): CachedQuality? {
        warm()
        return peek(key, now)
    }

    @Synchronized fun put(key: String, value: CachedQuality) {
        memory[key] = value
        prefs.edit { putString(key, value.encode()) }
    }

    companion object {
        private const val PREFS = "nuvio_c_quality"
        private const val MEMORY_MAX = 300
        private const val DISK_MAX = 300
    }
}
