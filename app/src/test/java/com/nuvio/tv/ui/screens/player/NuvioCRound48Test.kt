package com.nuvio.tv.ui.screens.player

import org.junit.Assert.assertEquals
import org.junit.Test

class NuvioCRound48Test {

    private val chapters = listOf(
        PlayerChapter(0L, "Intro"),
        PlayerChapter(60_000L, "Part 1"),
        PlayerChapter(300_000L, "Part 2"),
        PlayerChapter(600_000L, "Credits")
    )

    private fun jump(r: NuvioCChapterTap.Result) = (r as NuvioCChapterTap.Result.Jump).targetMs

    @Test
    fun singlePress_seeks() {
        val tap = NuvioCChapterTap()
        assertEquals(NuvioCChapterTap.Result.Seek, tap.onDown(true, 0, 1_000, 100_000, chapters))
    }

    @Test
    fun quickSecondPress_jumpsFromFirstPressOrigin() {
        val tap = NuvioCChapterTap()
        tap.onDown(true, 0, 1_000, 290_000, chapters) // first press seeks +10 s past Part 2's start
        // second press: next chapter after 290 s (the origin) is Part 2, not Credits
        assertEquals(300_000L, jump(tap.onDown(true, 0, 1_200, 300_000, chapters)))
        // third quick press keeps going
        assertEquals(600_000L, jump(tap.onDown(true, 0, 1_400, 300_000, chapters)))
    }

    @Test
    fun previous_followsRestartThreshold() {
        val tap = NuvioCChapterTap()
        tap.onDown(false, 0, 1_000, 310_000, chapters) // 10 s into Part 2
        assertEquals(300_000L, jump(tap.onDown(false, 0, 1_250, 300_000, chapters)))
        assertEquals(60_000L, jump(tap.onDown(false, 0, 1_500, 300_000, chapters)))
    }

    @Test
    fun slowSecondPress_seeks() {
        val tap = NuvioCChapterTap()
        tap.onDown(true, 0, 1_000, 100_000, chapters)
        assertEquals(NuvioCChapterTap.Result.Seek, tap.onDown(true, 0, 1_000 + NuvioCChapterTap.WINDOW_MS + 1, 110_000, chapters))
    }

    @Test
    fun heldPress_neverTurnsIntoJump_andHoldAfterJumpIsSwallowed() {
        val tap = NuvioCChapterTap()
        tap.onDown(true, 0, 1_000, 100_000, chapters)
        assertEquals(NuvioCChapterTap.Result.Seek, tap.onDown(true, 1, 1_100, 110_000, chapters)) // held: scrubbing
        assertEquals(NuvioCChapterTap.Result.Seek, tap.onDown(true, 0, 1_200, 130_000, chapters)) // after a hold: plain seek
        val j = tap.onDown(true, 0, 1_300, 140_000, chapters)
        assertEquals(300_000L, jump(j))
        assertEquals(NuvioCChapterTap.Result.Swallow, tap.onDown(true, 1, 1_400, 300_000, chapters))
    }

    @Test
    fun oppositeDirection_seeks() {
        val tap = NuvioCChapterTap()
        tap.onDown(true, 0, 1_000, 100_000, chapters)
        assertEquals(NuvioCChapterTap.Result.Seek, tap.onDown(false, 0, 1_100, 110_000, chapters))
    }

    @Test
    fun noChapters_orPastLast_seeks() {
        val tap = NuvioCChapterTap()
        tap.onDown(true, 0, 1_000, 100_000, emptyList())
        assertEquals(NuvioCChapterTap.Result.Seek, tap.onDown(true, 0, 1_100, 110_000, emptyList()))
        val t2 = NuvioCChapterTap()
        t2.onDown(true, 0, 1_000, 650_000, chapters)
        assertEquals(NuvioCChapterTap.Result.Seek, t2.onDown(true, 0, 1_100, 660_000, chapters))
    }

    @Test
    fun clipText_cutsWithEllipsis() {
        assertEquals("Short", nuvioCClipText("Short", 32))
        val long = "A very long episode title that goes on and on"
        val clipped = nuvioCClipText(long, NEXT_TITLE_MAX)
        assertEquals(true, clipped.length <= NEXT_TITLE_MAX && clipped.endsWith("…"))
    }

    @Test
    fun chapterLine_onlyWithNamesOnAndChapters() {
        assertEquals(true, nuvioCChapterLineShown(chapters, true))
        assertEquals(false, nuvioCChapterLineShown(chapters, false))
        assertEquals(false, nuvioCChapterLineShown(emptyList(), true))
    }
}
