package com.nuvio.tv.ui.screens.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class NuvioCStarFillTest {
    @Test
    fun sevenIsThreeAndAHalfStars() {
        val fills = (0 until 5).map { nuvioCStarFill(7, it) }
        assertEquals(listOf(1f, 1f, 1f, 0.5f, 0f), fills)
    }

    @Test
    fun endsOfTheScale() {
        assertEquals(listOf(0.5f, 0f, 0f, 0f, 0f), (0 until 5).map { nuvioCStarFill(1, it) })
        assertEquals(listOf(1f, 1f, 1f, 1f, 1f), (0 until 5).map { nuvioCStarFill(10, it) })
    }
}
