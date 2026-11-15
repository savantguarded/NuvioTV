package com.nuvio.tv

import com.nuvio.tv.core.tmdb.nuvioCEnglishTagline
import com.nuvio.tv.ui.screens.detail.nuvioCCleanKeywords
import com.nuvio.tv.ui.screens.player.NuvioCCleanOrder
import com.nuvio.tv.ui.screens.player.NuvioCCleanZone
import com.nuvio.tv.ui.screens.player.NuvioCOsdButton
import com.nuvio.tv.ui.screens.taste.TASTE_ROTATING_KINDS
import com.nuvio.tv.ui.screens.taste.tasteRotateOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NuvioCRound47Test {
    @Test fun taglineEnglishOnly() {
        assertEquals("Postwar Japan. From zero to minus.", nuvioCEnglishTagline("Postwar Japan. From zero to minus.", "en-US"))
        assertNull(nuvioCEnglishTagline("生きて、抗え。", "en-US"))
        assertNull(nuvioCEnglishTagline("Une phrase", "fr-FR"))
        assertEquals("Amélie's world", nuvioCEnglishTagline("Amélie's world", "en"))
        assertNull(nuvioCEnglishTagline("  ", "en"))
    }

    @Test fun keywordsSkipThemesAndNameStingers() {
        val k = nuvioCCleanKeywords(listOf("kaiju", "duringcreditsstinger", "post-war japan", "Kaiju", "atomic bomb"), listOf("Kaiju"))
        assertEquals(listOf("Mid-credits scene", "Post-war Japan", "Atomic Bomb"), k)
    }

    @Test fun rotationNeverLeadsTwiceWithTheSameKind() {
        var lastFirst: String? = null
        var lastOrder: String? = null
        repeat(40) { i ->
            val o = tasteRotateOrder(TASTE_ROTATING_KINDS, i * 7919L, lastFirst, lastOrder)
            assertEquals(TASTE_ROTATING_KINDS.toSet(), o.toSet())
            assertNotEquals(lastFirst, o.first())
            assertNotEquals(lastOrder, o.joinToString(","))
            lastFirst = o.first(); lastOrder = o.joinToString(",")
        }
    }

    @Test fun cleanOrderZones() {
        val d = NuvioCCleanOrder.defaults
        assertEquals(d, NuvioCCleanOrder.parse(NuvioCCleanOrder.encode(d)))
        val i = d.indexOfFirst { it.button == NuvioCOsdButton.START_OVER }
        val cycled = NuvioCCleanOrder.cycled(d, i)
        assertEquals(NuvioCCleanZone.MORE, cycled.first { it.button == NuvioCOsdButton.START_OVER }.zone)
        // zones stay grouped after a cycle
        assertEquals(cycled.map { it.zone.ordinal }.sorted(), cycled.map { it.zone.ordinal })
        // a zone a button can't take is reset to its default
        val bad = NuvioCCleanOrder.parse("next:l,subtitles:c")
        assertEquals(NuvioCCleanZone.CENTRE, bad.first { it.button == NuvioCOsdButton.NEXT }.zone)
        assertEquals(NuvioCCleanZone.LEFT, bad.first { it.button == NuvioCOsdButton.SUBTITLES }.zone)
        // moving never crosses into another zone
        val first = d.indexOfFirst { it.zone == NuvioCCleanZone.RIGHT }
        assertTrue(NuvioCCleanOrder.moved(d, first, -1) === d)
    }
}
