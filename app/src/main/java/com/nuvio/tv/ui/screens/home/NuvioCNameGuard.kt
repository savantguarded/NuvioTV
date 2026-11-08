package com.nuvio.tv.ui.screens.home

// [fork] Nuvio C round 34 `upcoming_name_guard`: a saved watch-progress name can hold release text (the stream or
// file name, e.g. "Sweetpea S01E03"), and official builds every next-up / Upcoming card for that show from it, so
// the new season's card showed the last episode's code as the show's name. Also in official beta.13.

import com.nuvio.tv.NuvioCFeatures

internal object NuvioCNameGuard {
    private val episodeCode = Regex("""(?i)\bS\d{1,2}\s?E\d{1,3}\b|\b\d{1,2}x\d{2,3}\b""")
    private val releaseTokens = Regex(
        """(?i)\b(2160p|1080p|720p|576p|480p|4k|uhd|x26[45]|h\.?26[45]|hevc|avc|web-?dl|web-?rip|blu-?ray|bdrip|brrip|remux|hdtv|ddp?5\.1|atmos|truehd|dts|aac)\b|\.(mkv|mp4|avi|m4v|ts)\b"""
    )
    private val placeholderTitle = Regex("""(?i)^\s*(episode|ep\.?)\s*\d+\s*$""")

    fun looksLikeRelease(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        return episodeCode.containsMatchIn(name) || releaseTokens.containsMatchIn(name)
    }

    /** Show name for a card: the saved one unless it looks like release text and the meta add-on has a name. */
    fun showName(saved: String?, metaName: String?): String? {
        val s = saved?.trim()?.takeIf { it.isNotEmpty() }
        if (!NuvioCFeatures.UPCOMING_NAME_GUARD) return s ?: metaName
        val meta = metaName?.trim()?.takeIf { it.isNotEmpty() }
        return when {
            s == null -> meta
            meta != null && looksLikeRelease(s) -> meta
            else -> s
        }
    }

    /** "Episode 1"-style add-on placeholders: the season-premiere label for episode 1, else no title. */
    fun episodeTitle(title: String?, episode: Int?, premiere: () -> String?): String? {
        if (!NuvioCFeatures.UPCOMING_NAME_GUARD || title == null) return title
        if (!placeholderTitle.matches(title) && !looksLikeReleaseEpisodeOnly(title)) return title
        return if (episode == 1) premiere() else null
    }

    private fun looksLikeReleaseEpisodeOnly(title: String): Boolean = episodeCode.matches(title.trim())
}
