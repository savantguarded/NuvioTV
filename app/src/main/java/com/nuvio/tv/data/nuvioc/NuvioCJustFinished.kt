package com.nuvio.tv.data.nuvioc

// [fork] Nuvio C round 28 `post_play_rating`: the player notes a movie you just finished (played past
// the end-credits marker, or the post-play threshold when there's no marker, having actually watched
// most of what you covered). When that movie's details page shows within 10 minutes, it asks for your
// rating once. Memory only: nothing is kept across app restarts.
object NuvioCJustFinished {
    private const val WINDOW_MS = 10 * 60 * 1000L
    @Volatile private var id: String? = null
    @Volatile private var at = 0L

    fun mark(contentId: String) {
        id = contentId
        at = System.currentTimeMillis()
    }

    /** True once, for the details page of the movie just finished. */
    fun take(ids: Collection<String?>): Boolean {
        val pending = id ?: return false
        if (System.currentTimeMillis() - at > WINDOW_MS) {
            id = null
            return false
        }
        if (ids.none { it != null && it == pending }) return false
        id = null
        return true
    }
}
