package com.nuvio.tv.ui.screens.detail

import com.nuvio.tv.data.local.NuvioCSectionPrefs
import org.junit.Assert.assertEquals
import org.junit.Test

// [fork] Nuvio C one-row-per-section logic.
class NuvioCSectionRowsTest {
    private enum class Tab { CAST, RATINGS, MORE_LIKE_THIS, TRAILER, COLLECTION }

    private val available = listOf(
        Tab.CAST to "Cast", Tab.MORE_LIKE_THIS to "More like this",
        Tab.TRAILER to "Trailers", Tab.COLLECTION to "Saga"
    )
    private val selfTitled = setOf(Tab.CAST, Tab.RATINGS)

    @Test
    fun `rows off gives official single item with official key`() {
        val rows = NuvioCSectionRows(false, NuvioCSectionPrefs.DEFAULT_ORDER, emptySet())
        val entries = rows.entries(available, selfTitled)
        assertEquals(1, entries.size)
        assertEquals(null, entries[0].tab)
        assertEquals("cast_or_more_like", rows.key("cast_or_more_like", entries[0]))
        assertEquals(7, rows.commentsIndex(7, 2, 1))
    }

    @Test
    fun `rows follow saved order, skip hidden and missing, add headings`() {
        val rows = NuvioCSectionRows(true, listOf("TRAILER", "RATINGS", "CAST", "COLLECTION", "MORE_LIKE_THIS"), setOf("MORE_LIKE_THIS"))
        val entries = rows.entries(available, selfTitled)
        assertEquals(
            listOf("cast_or_more_like:hdr:TRAILER", "cast_or_more_like:TRAILER", "cast_or_more_like:CAST",
                "cast_or_more_like:hdr:COLLECTION", "cast_or_more_like:COLLECTION"),
            entries.map { rows.key("cast_or_more_like", it) }
        )
        assertEquals("Saga", entries[3].label)
        assertEquals(1 - 1 + 5, rows.commentsIndex(1, 1, entries.size))
    }

    @Test
    fun `saved order is cleaned up`() {
        assertEquals(
            listOf("TRAILER", "CAST", "RATINGS", "MORE_LIKE_THIS", "COLLECTION"),
            NuvioCSectionPrefs.normalize(listOf("TRAILER", "bogus", "CAST", "TRAILER"))
        )
        assertEquals(NuvioCSectionPrefs.DEFAULT_ORDER, NuvioCSectionPrefs.normalize(null))
    }

    @Test
    fun `loading sections keep their row in saved order, rows off unchanged`() {
        val rows = NuvioCSectionRows(true, NuvioCSectionPrefs.DEFAULT_ORDER, emptySet())
        val loaded = listOf(Tab.CAST to "Cast", Tab.TRAILER to "Trailers")
        val labels = mapOf(Tab.MORE_LIKE_THIS to "More like this", Tab.COLLECTION to "Collection")
        val withPending = rows.withPending(loaded, setOf("MORE_LIKE_THIS"), labels)
        assertEquals(
            listOf("cast_or_more_like:CAST", "cast_or_more_like:hdr:MORE_LIKE_THIS", "cast_or_more_like:MORE_LIKE_THIS",
                "cast_or_more_like:hdr:TRAILER", "cast_or_more_like:TRAILER"),
            rows.entries(withPending, selfTitled).map { rows.key("cast_or_more_like", it) }
        )
        // already loaded: not added twice
        assertEquals(available, rows.withPending(available, setOf("MORE_LIKE_THIS"), labels))
        val off = NuvioCSectionRows(false, NuvioCSectionPrefs.DEFAULT_ORDER, emptySet())
        assertEquals(loaded, off.withPending(loaded, setOf("MORE_LIKE_THIS"), labels))
    }
}
