package com.nuvio.tv.ui.screens.home

object HeroBackdropState {
    @Volatile
    var currentHeroBackdropUrl: String? = null
        private set

    /** Last backdrop URL that was actually displayed — survives navigation for seamless back transitions. */
    @Volatile
    var lastDisplayedUrl: String? = null

    fun update(url: String?) {
        currentHeroBackdropUrl = url
        if (!url.isNullOrBlank()) {
            lastDisplayedUrl = url
        }
    }

    /**
     * [fork] Nuvio C featured_seed (2026-10-17): the picture the next details page opens on, without
     * touching [lastDisplayedUrl] (that one is Modern's own hero, which must not change behind the
     * Featured carousel). Without it a carousel title opened on Modern's hidden hero picture, the
     * first Continue Watching card, until its own backdrop loaded.
     */
    fun seedNext(url: String?) {
        currentHeroBackdropUrl = url?.takeIf { it.isNotBlank() }
    }

    fun consumeAndClear(): String? {
        val url = currentHeroBackdropUrl
        currentHeroBackdropUrl = null
        return url
    }
}
