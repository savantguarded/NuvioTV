package com.nuvio.tv

import com.nuvio.tv.data.quality.CachedKind
import com.nuvio.tv.data.quality.CachedQuality
import com.nuvio.tv.data.quality.QualityEdition
import com.nuvio.tv.data.quality.QualityResolution
import com.nuvio.tv.data.quality.QualityStreamText
import com.nuvio.tv.data.quality.TitleQuality
import com.nuvio.tv.data.quality.TitleQualityParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Round 65: editions need 2+ releases and 10+ seeders, ordered by seeders; fan edits and Criterion never count. */
class NuvioCRound65EditionsTest {
    private fun s(text: String, seeders: Int = 10) = QualityStreamText("Torrentio\n1080p", "$text\n👤 $seeders 💾 2 GB", null)
    private fun fixture(name: String): List<QualityStreamText> =
        TitleQualityParser.streamsOf(javaClass.classLoader!!.getResource("quality/$name.json")!!.readText())

    @Test fun oneReleaseIsNotEnough() {
        assertTrue(TitleQualityParser.editions(listOf(s("Some.Film.2010.Extended.1080p", 500)), "Some Film", 2010).isEmpty())
        // the same release twice is still one release
        val same = s("Some.Film.2010.Extended.1080p", 50)
        assertTrue(TitleQualityParser.editions(listOf(same, same), "Some Film", 2010).isEmpty())
    }

    @Test fun twoReleasesNeedTenSeedersBetweenThem() {
        val low = listOf(s("Some.Film.2010.Extended.1080p", 4), s("Some.Film.2010.Extended.2160p", 5))
        assertTrue(TitleQualityParser.editions(low, "Some Film", 2010).isEmpty())
        val ok = listOf(s("Some.Film.2010.Extended.1080p", 4), s("Some.Film.2010.Extended.2160p", 6))
        assertEquals(listOf(QualityEdition.EXTENDED), TitleQualityParser.editions(ok, "Some Film", 2010))
    }

    @Test fun orderedBySeedersTieExtendedFirst() {
        val streams = listOf(
            s("Some.Film.2010.Directors.Cut.1080p", 40), s("Some.Film.2010.Directors.Cut.2160p", 40),
            s("Some.Film.2010.Extended.1080p", 5), s("Some.Film.2010.Extended.2160p", 6),
            s("Some.Film.2010.IMAX.1080p", 30), s("Some.Film.2010.IMAX.2160p", 30)
        )
        assertEquals(listOf(QualityEdition.DIRECTORS_CUT, QualityEdition.IMAX), TitleQualityParser.editions(streams, "Some Film", 2010))
        val tie = listOf(
            s("Some.Film.2010.Directors.Cut.1080p", 10), s("Some.Film.2010.Directors.Cut.2160p", 10),
            s("Some.Film.2010.Extended.1080p", 10), s("Some.Film.2010.Extended.2160p", 10)
        )
        assertEquals(listOf(QualityEdition.EXTENDED, QualityEdition.DIRECTORS_CUT), TitleQualityParser.editions(tie, "Some Film", 2010))
    }

    @Test fun fanEditsAndCriterionIgnored() {
        assertTrue(TitleQualityParser.editionsOf(s("Some.Film.2010.Extended.FanEdit.1080p"), "Some Film", 2010).isEmpty())
        assertTrue(TitleQualityParser.editionsOf(s("Some Film 2010 Extended Fan-Made Cut"), "Some Film", 2010).isEmpty())
        assertTrue(TitleQualityParser.editionsOf(s("Some.Film.2010.Despecialized.Edition"), "Some Film", 2010).isEmpty())
        assertTrue(TitleQualityParser.editionsOf(s("Some.Film.2010.Criterion.1080p"), "Some Film", 2010).isEmpty())
    }

    @Test fun mcuFanEditFixture() {
        // Extended only in fan edits, Criterion named twice, Director's Cut once: only IMAX survives
        assertEquals(listOf(QualityEdition.IMAX), TitleQualityParser.editions(fixture("mcu_fan_edits"), "Avengers Endgame", 2019))
    }

    @Test fun cacheEntriesBeforeRound65AreReadAgain() {
        val c = CachedQuality(CachedKind.FOUND, TitleQuality(QualityResolution.UHD, null, null, null, listOf(QualityEdition.IMAX)), 1L, 2L)
        assertEquals(c, CachedQuality.decode(c.encode()))
        assertNull(CachedQuality.decode("FOUND|UHD||||1|2|IMAX"))
    }
}
