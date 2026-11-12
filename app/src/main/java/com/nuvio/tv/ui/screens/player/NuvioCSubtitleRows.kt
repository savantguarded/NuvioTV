package com.nuvio.tv.ui.screens.player

// [fork] Nuvio C round 39 `subtitle_panel_clean`: tidier add-on subtitle rows in the player's subtitle panel.
//  - Opaque add-on ids (OpenSubtitles v3's long "v3_e1_…" tokens and the like) are never shown.
//  - A readable release name, when an add-on gives one, stays as one ellipsized line under the language.
//  - Several subtitles of one language from the same add-on are numbered ("English 1", "English 2"…).
//  - The source chip reads "OpenSubtitles · SRT" when the file type is known from its address.
// Built-in tracks are untouched (the round 34/35 track names already read "English · SDH").

import com.nuvio.tv.domain.model.Subtitle
import java.util.Locale

internal object NuvioCSubtitleRows {

    private val TOKEN = Regex("^[A-Za-z0-9_\\-+=/.:]+$")
    private val FORMATS = listOf("srt", "ass", "ssa", "vtt", "sub", "ttml", "dfxp", "smi")

    /** True for ids that mean nothing to a person: no spaces and long, or a mix of case and digits like a hash. */
    fun looksLikeToken(raw: String?): Boolean {
        val v = raw?.trim().orEmpty()
        if (v.isEmpty()) return true
        if (v.contains(' ')) return false
        if (!TOKEN.matches(v)) return false
        if (v.length >= 24) return true
        if (v.all { it.isDigit() }) return true
        val digits = v.count { it.isDigit() }
        val upper = v.count { it.isUpperCase() }
        val lower = v.count { it.isLowerCase() }
        return v.length >= 12 && digits >= 3 && upper >= 2 && lower >= 2
    }

    /** "SRT" from the subtitle's address (path or a `format=` style query), null when unknown. */
    fun format(url: String?): String? {
        val u = url?.lowercase(Locale.US) ?: return null
        val path = u.substringBefore('?').substringBefore('#')
        FORMATS.firstOrNull { path.endsWith(".$it") }?.let { return it.uppercase(Locale.US) }
        val query = u.substringAfter('?', "")
        return FORMATS.firstOrNull { query.contains("=$it") }?.uppercase(Locale.US)
    }

    /** A readable release name from the add-on's id, or null (tokens, urls and plain language codes say nothing). */
    fun readableMeta(subtitle: Subtitle): String? {
        val id = subtitle.id.trim()
        if (id.isBlank() || id == subtitle.lang || id == subtitle.url || id.startsWith("http", ignoreCase = true)) return null
        if (looksLikeToken(id)) return null
        return id
    }

    fun <T> tidyAddonItems(
        items: List<T>,
        subtitleOf: (T) -> Subtitle?,
        rebuild: (item: T, title: String, source: String, meta: String?) -> T
    ): List<T> {
        val counts = items.groupingBy { subtitleOf(it)?.addonName.orEmpty() }.eachCount()
        val seen = HashMap<String, Int>()
        return items.map { item ->
            val sub = subtitleOf(item) ?: return@map item
            val addon = sub.addonName
            val n = (seen[addon] ?: 0) + 1
            seen[addon] = n
            val language = Subtitle.languageCodeToName(PlayerSubtitleUtils.normalizeLanguageCode(sub.lang))
            val title = if ((counts[addon] ?: 0) > 1) "$language $n" else language
            val source = listOfNotNull(addon.ifBlank { null }, format(sub.url)).joinToString(" · ")
            rebuild(item, title, source, readableMeta(sub))
        }
    }
}
