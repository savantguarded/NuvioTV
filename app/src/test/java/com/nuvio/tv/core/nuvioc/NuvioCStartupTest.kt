package com.nuvio.tv.core.nuvioc

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [fork] Nuvio C round 26b startup_settle. */
class NuvioCStartupTest {
    private fun frames(count: Int, ms: Long = 16L) = List(count) { ms }

    @Test fun settlesAfterAQuietStretch() = assertTrue(nuvioCStartupSettled(frames(10) + 2_200L + frames(60)))

    @Test fun aRecentStallMeansNotYet() = assertFalse(nuvioCStartupSettled(frames(60) + 2_500L + frames(20)))

    @Test fun tooShortIsNotSettled() = assertFalse(nuvioCStartupSettled(frames(20)))
}
