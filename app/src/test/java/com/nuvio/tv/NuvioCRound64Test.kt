package com.nuvio.tv

import com.nuvio.tv.data.quality.CachedKind
import com.nuvio.tv.data.quality.CachedQuality
import com.nuvio.tv.data.quality.QualityAudio
import com.nuvio.tv.data.quality.QualityEdition
import com.nuvio.tv.data.quality.QualityResolution
import com.nuvio.tv.data.quality.QualitySource
import com.nuvio.tv.data.quality.QualityStreamText
import com.nuvio.tv.data.quality.TitleQuality
import com.nuvio.tv.data.quality.TitleQualityParser
import com.nuvio.tv.ui.screens.detail.nuvioCDtsSuffix
import com.nuvio.tv.ui.screens.detail.nuvioCLabels64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Round 64: badge order, sound formats back, editions from any release (Torrentio answer saved 2026-11-23). */
class NuvioCRound64Test {
    private fun s(text: String, seeders: Int = 10) = QualityStreamText("Torrentio\n1080p", "$text\n👤 $seeders 💾 2 GB", null)
    private fun fixture(name: String): List<QualityStreamText> =
        TitleQualityParser.streamsOf(javaClass.classLoader!!.getResource("quality/$name.json")!!.readText())

    @Test fun labelOrderDolbyLast() {
        assertEquals(listOf("4K", "BLURAY", "DOLBY VISION", "ATMOS"), nuvioCLabels64(listOf("4K", "DOLBY VISION", "ATMOS", "REMUX")))
        assertEquals(listOf("4K", "BLURAY", "TRUEHD", "DOLBY VISION"), nuvioCLabels64(listOf("4K", "DOLBY VISION", "TRUEHD", "BLURAY")))
        assertEquals(listOf("1080P", "BLURAY", "HDR10", "DTS"), nuvioCLabels64(listOf("1080P", "HDR10", "DTS", "BLURAY")))
        assertEquals(listOf("1080P", "WEB-DL", "DOLBY DIGITAL PLUS"), nuvioCLabels64(listOf("1080P", "DOLBY DIGITAL PLUS", "WEB-DL")))
        assertEquals(listOf("1080P", "BLURAY", "HDR10+", "ATMOS"), nuvioCLabels64(listOf("1080P", "HDR10+", "ATMOS", "BLURAY")))
    }

    @Test fun soundFormatsParsed() {
        fun audio(name: String) = TitleQualityParser.parse(s(name)).quality?.audio
        assertEquals(QualityAudio.DTS, audio("Film.2010.1080p.BluRay.DTS.x264"))
        assertEquals(QualityAudio.DTS, audio("Film.2010.1080p.BluRay.DTS5.1.x264"))
        assertEquals(QualityAudio.DTS_HD, audio("Film.2010.1080p.BluRay.DTS-HD.MA.5.1"))
        assertEquals(QualityAudio.DTS_X, audio("Film.2010.2160p.BluRay.DTS-X.7.1"))
        assertEquals(QualityAudio.FLAC, audio("Film.1954.1080p.BluRay.FLAC.1.0.x264"))
        assertEquals(QualityAudio.DDP, audio("Film.2020.1080p.WEB-DL.DDP5.1.H.264"))
        assertEquals(QualityAudio.DD, audio("Film.2020.720p.WEB-DL.DD5.1.H.264"))
        assertEquals(QualityAudio.ATMOS, audio("Film.2020.2160p.WEB-DL.DDP5.1.Atmos.DV"))
        assertEquals(QualityAudio.FLAC, TitleQualityParser.fromTokens(listOf("1080P", "FLAC"))?.audio)
        assertEquals(QualityAudio.DTS, TitleQualityParser.fromTokens(listOf("1080P", "DTS"))?.audio)
        assertEquals(":X", nuvioCDtsSuffix("DTS:X"))
        assertEquals("HD", nuvioCDtsSuffix("DTS-HD"))
        assertNull(nuvioCDtsSuffix("DTS"))
    }

    @Test fun editionsFromAnyRelease() {
        val streams = listOf(
            s("Some.Film.2010.1080p.BluRay.x264"),
            s("Some.Film.2010.Directors.Cut.1080p.BluRay.x264"),
            s("Some Film (2010) Extended Edition 2160p UHD BluRay"),
            s("Some.Film.2010.IMAX.Enhanced.2160p.WEB-DL.DDP5.1"),
            s("Some.Film.2010.Open.Matte.1080p.WEB-DL")
        )
        assertEquals(listOf(QualityEdition.EXTENDED, QualityEdition.DIRECTORS_CUT), TitleQualityParser.editions(streams, "Some Film", 2010))
        assertEquals(listOf(QualityEdition.IMAX, QualityEdition.OPEN_MATTE), TitleQualityParser.editions(streams.drop(3), "Some Film", 2010))
        assertEquals(setOf(QualityEdition.DIRECTORS_CUT), TitleQualityParser.editionsOf(s("Some.Film.2010.Director's.Cut.1080p"), "Some Film", 2010))
        assertEquals(setOf(QualityEdition.BLACK_AND_WHITE), TitleQualityParser.editionsOf(s("Some Film 2010 B&W 1080p"), "Some Film", 2010))
        assertEquals(setOf(QualityEdition.SPECIAL_EDITION), TitleQualityParser.editionsOf(s("Some.Film.2010.Special.Edition.1080p"), "Some Film", 2010))
    }

    @Test fun editionsIgnoreTitleCamZeroSeedersOtherYearsAndPacks() {
        // the film's own title
        assertTrue(TitleQualityParser.editionsOf(s("The.Final.Cut.2004.1080p.BluRay"), "The Final Cut", 2004).isEmpty())
        assertEquals(setOf(QualityEdition.EXTENDED), TitleQualityParser.editionsOf(s("The.Final.Cut.2004.Extended.1080p"), "The Final Cut", 2004))
        // CAM, no seeders, another year
        assertTrue(TitleQualityParser.editionsOf(s("Some.Film.2010.Extended.HDCAM"), "Some Film", 2010).isEmpty())
        assertTrue(TitleQualityParser.editionsOf(s("Some.Film.2010.Extended.1080p", 0), "Some Film", 2010).isEmpty())
        assertTrue(TitleQualityParser.editionsOf(s("Some.Film.1985.Extended.1080p"), "Some Film", 2010).isEmpty())
        // a pack line that doesn't name the film
        val pack = QualityStreamText("Torrentio\n1080p", "Y Tu Mama Tambien (2001) Criterion 1080p\nSome.Film.2010.1080p.mkv\n👤 9", null)
        assertTrue(TitleQualityParser.editionsOf(pack, "Some Film", 2010).isEmpty())
    }

    @Test fun apocalypseNowFixture() {
        val all = TitleQualityParser.editions(fixture("apocalypse_now"), "Apocalypse Now", 1979, max = 10)
        assertTrue(QualityEdition.REDUX in all)
        assertTrue(QualityEdition.FINAL_CUT in all)
        assertTrue(QualityEdition.CRITERION !in all) // only named by a pack of another film
        // Extended releases exist in this answer, so Extended leads by priority
        assertEquals(QualityEdition.EXTENDED, TitleQualityParser.editions(fixture("apocalypse_now"), "Apocalypse Now", 1979).first())
        assertEquals(2, TitleQualityParser.editions(fixture("apocalypse_now"), "Apocalypse Now", 1979).size)
        // the live answer still gives one usable release, and the early read agrees with the full read
        val full = TitleQualityParser.best(fixture("apocalypse_now"), 1979)
        val early = TitleQualityParser.bestEarly(fixture("apocalypse_now").asSequence(), 1979)
        assertEquals(QualityResolution.UHD, full?.resolution)
        assertEquals(full, early.quality)
        assertTrue(early.read < fixture("apocalypse_now").size)
    }

    @Test fun cacheKeepsEditions() {
        val q = TitleQuality(QualityResolution.UHD, null, QualityAudio.DTS, QualitySource.BLURAY, listOf(QualityEdition.FINAL_CUT, QualityEdition.REDUX))
        val c = CachedQuality(CachedKind.FOUND, q, 1L, 2L)
        assertEquals(c, CachedQuality.decode(c.encode()))
        // a round 63 entry (7 fields) is looked up again
        assertNull(CachedQuality.decode("FOUND|UHD|||BLURAY|1|2"))
    }
}
