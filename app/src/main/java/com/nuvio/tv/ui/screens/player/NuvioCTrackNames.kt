package com.nuvio.tv.ui.screens.player

// [fork] Nuvio C round 34 `track_names`: files name their tracks every which way ("Eng", "SDH", "English [Forced]",
// "eng · PGS"). The subtitle and audio lists already group them under the right language; the rows now read
// as that language plus the useful tags, with the file's own name kept as a dim second line.

import com.nuvio.tv.NuvioCFeatures
import com.nuvio.tv.ui.util.languageCodeToName
import java.util.Locale

internal object NuvioCTrackNames {
    private val tagRules = listOf(
        Regex("""(?i)\bSDH\b|hearing[ -]?impaired|\bHI\b""") to "SDH",
        Regex("""(?i)\bforced\b""") to "Forced",
        Regex("""(?i)\bCC\b|closed caption""") to "CC",
        Regex("""(?i)\bsigns?\b(\s*(&|and|/)\s*songs?)?""") to "Signs",
        Regex("""(?i)commentary""") to "Commentary",
        Regex("""(?i)\bdescriptive\b|audio description|\bAD\b""") to "AD"
    )

    /** "English", "English · SDH", "English · Forced", or null to keep the original when the language is unknown. */
    fun subtitleTitle(name: String, language: String?, forced: Boolean): String? {
        if (!NuvioCFeatures.TRACK_NAMES) return null
        val lang = languageName(language) ?: return null
        if (NuvioCFeatures.TRACK_LABEL_LANGUAGE) return lang // [fork] round 35: the language alone; tags go on the line under
        val tags = tags(name).toMutableList()
        if (forced && "Forced" !in tags) tags += "Forced"
        return (listOf(lang) + tags).joinToString(" · ")
    }

    /** Same for audio; the codec / channel details stay on the metadata line the panel already shows. */
    fun audioTitle(name: String, language: String?): String? {
        if (!NuvioCFeatures.TRACK_NAMES) return null
        val lang = languageName(language) ?: return null
        return (listOf(lang) + tags(name).filter { it == "Commentary" || it == "AD" }).joinToString(" · ")
    }

    /** The file's own name as a second line, only when it says something the title doesn't. */
    fun original(name: String, title: String?): String? {
        if (title == null) return null
        val n = name.trim()
        if (n.isEmpty() || n.equals(title, ignoreCase = true) || n.equals(title.substringBefore(" ·"), ignoreCase = true)) return null
        return n
    }

    /**
     * [fork] round 35 track_label_language: the line under a subtitle titled just "English": the file's own name when
     * it says more than the language, else the tags it carries (SDH / Forced / CC / Signs), else nothing.
     */
    fun subtitleDescription(name: String, title: String?, forced: Boolean): String? {
        if (title == null) return null
        original(name, title)?.let { return it }
        val tags = tags(name).toMutableList()
        if (forced && "Forced" !in tags) tags += "Forced"
        return tags.joinToString(" · ").ifBlank { null }
    }

    private fun tags(name: String): List<String> = tagRules.filter { (re, _) -> re.containsMatchIn(name) }.map { it.second }.distinct()

    private fun languageName(code: String?): String? {
        val c = code?.trim()?.takeIf { it.isNotEmpty() && !it.equals("und", true) && !it.equals("unknown", true) } ?: return null
        val name = runCatching { languageCodeToName(c) }.getOrNull()?.takeIf { it.isNotBlank() && !it.equals(c, ignoreCase = true) }
            ?: Locale.forLanguageTag(c).getDisplayLanguage(Locale.ENGLISH).takeIf { it.isNotBlank() && !it.equals(c, ignoreCase = true) }
            ?: return null
        return name.replaceFirstChar { it.titlecase(Locale.ENGLISH) }
    }
}
