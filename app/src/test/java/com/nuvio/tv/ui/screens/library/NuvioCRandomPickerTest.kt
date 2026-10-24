package com.nuvio.tv.ui.screens.library

import com.nuvio.tv.domain.model.LibraryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

// [fork] Nuvio C round 27 library_random
class NuvioCRandomPickerTest {

    private fun entry(id: String) = LibraryEntry(
        id = id, type = "movie", name = id, poster = null, background = null, logo = null,
        description = null, releaseInfo = null, imdbRating = null, genres = emptyList(), addonBaseUrl = null
    )

    @Test
    fun emptyPageGivesNothing() {
        assertNull(NuvioCRandomPicker().pick(emptyList()))
    }

    @Test
    fun singleTitleIsAlwaysPicked() {
        val one = listOf(entry("a"))
        val picker = NuvioCRandomPicker()
        repeat(5) { assertEquals("a", picker.pick(one)?.id) }
    }

    @Test
    fun neverRepeatsTheLastPickWhenThereIsAChoice() {
        val two = listOf(entry("a"), entry("b"))
        val picker = NuvioCRandomPicker()
        var last = picker.pick(two)?.id
        repeat(20) {
            val next = picker.pick(two)?.id
            assertNotEquals(last, next)
            last = next
        }
    }

    @Test
    fun skipsTheLastFivePicks() {
        val items = (1..8).map { entry("t$it") }
        val picker = NuvioCRandomPicker()
        val recent = ArrayDeque<String>()
        repeat(40) {
            val id = picker.pick(items)!!.id
            assert(id !in recent) { "$id repeated within the last five picks" }
            recent.addLast(id)
            if (recent.size > 5) recent.removeFirst()
        }
    }
}
