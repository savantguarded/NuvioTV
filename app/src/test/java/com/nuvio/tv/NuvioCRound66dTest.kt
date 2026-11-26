package com.nuvio.tv

import androidx.compose.ui.unit.dp
import com.nuvio.tv.ui.screens.detail.NUVIO_C_CAROUSEL_BADGE_HEIGHT_66D
import com.nuvio.tv.ui.screens.detail.nuvioCIsLogoLabel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Round 66d: the carousel badge line is smaller than the details line (15 dp) and logos stay logos. */
class NuvioCRound66dTest {

    @Test fun carouselBadgesAreSmallerThanDetailsBadges() {
        assertTrue(NUVIO_C_CAROUSEL_BADGE_HEIGHT_66D < 15.dp)
        assertTrue(NUVIO_C_CAROUSEL_BADGE_HEIGHT_66D >= 10.dp)
    }

    @Test fun dolbyAndDtsStayLogosWordsDoNot() {
        assertTrue(nuvioCIsLogoLabel("DOLBY VISION"))
        assertTrue(nuvioCIsLogoLabel("ATMOS"))
        assertTrue(nuvioCIsLogoLabel("DTS:X"))
        assertFalse(nuvioCIsLogoLabel("4K"))
        assertFalse(nuvioCIsLogoLabel("WEB-DL"))
    }
}
