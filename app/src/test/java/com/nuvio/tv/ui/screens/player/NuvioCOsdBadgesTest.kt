package com.nuvio.tv.ui.screens.player

import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// [fork] Nuvio C: OSD badge label rules.
class NuvioCOsdBadgesTest {

    private val b = NuvioCOsdBadges

    @Test
    fun resolutionUsesWidthForCroppedFilms() {
        assertEquals("4K", b.resolutionLabel(3840, 2160))
        assertEquals("4K", b.resolutionLabel(3840, 1600))
        assertEquals("1440p", b.resolutionLabel(2560, 1440))
        assertEquals("1080p", b.resolutionLabel(1920, 800))
        assertEquals("720p", b.resolutionLabel(1280, 536))
        assertEquals("576p", b.resolutionLabel(720, 576))
        assertEquals("480p", b.resolutionLabel(720, 480))
        assertEquals("SD", b.resolutionLabel(640, 360))
        assertNull(b.resolutionLabel(0, 0))
        assertEquals("4K", b.resolutionFromName("Movie.2160p.WEB-DL"))
        assertNull(b.resolutionFromName("Movie WEB-DL"))
    }

    @Test
    fun visualTagReflectsWhatIsDecoded() {
        fun v(
            mime: String? = MimeTypes.VIDEO_H265,
            codecs: String? = null,
            transfer: Int? = null,
            known: Boolean = true,
            stripped: Boolean = false,
            converted: Boolean = false,
            names: String = ""
        ) = b.visualLabel(mime, codecs, transfer, known, stripped, converted, names)

        assertEquals("DV", v(mime = MimeTypes.VIDEO_DOLBY_VISION, codecs = "dvhe.08.06"))
        assertEquals("HDR10", v(mime = MimeTypes.VIDEO_DOLBY_VISION, transfer = C.COLOR_TRANSFER_ST2084, stripped = true))
        assertEquals("DV", v(transfer = C.COLOR_TRANSFER_ST2084, converted = true))
        assertEquals("HDR10", v(transfer = C.COLOR_TRANSFER_ST2084, names = "Movie DV HDR10"))
        assertEquals("HDR10+", v(transfer = C.COLOR_TRANSFER_ST2084, names = "Movie.HDR10+.2160p"))
        assertEquals("HLG", v(transfer = C.COLOR_TRANSFER_HLG))
        assertEquals("SDR", v(transfer = C.COLOR_TRANSFER_SDR))
        assertEquals("SDR", v(mime = MimeTypes.VIDEO_H264))
        // mpv: nothing decoded by ExoPlayer, the stream title decides
        assertEquals("DV", v(mime = null, known = false, names = "Movie 2160p DV HEVC"))
        assertEquals("HDR10", v(mime = null, known = false, names = "Movie.2160p.HDR.x265"))
        assertEquals("SDR", v(mime = null, known = false, names = "Movie 1080p"))
        // "dv" inside a word is not Dolby Vision
        assertEquals("SDR", v(mime = null, known = false, names = "Advent DVD Rip"))
    }

    @Test
    fun audioLabels() {
        assertEquals("DD+ 5.1", b.audioLabel("E-AC-3", 6))
        assertEquals("Atmos 5.1", b.audioLabel("E-AC-3-JOC", 6))
        assertEquals("DD 5.1", b.audioLabel("ac3", 6))
        assertEquals("TrueHD 7.1", b.audioLabel("truehd", 8))
        assertEquals("Atmos 7.1", b.audioLabel("truehd", 8, "Movie.2160p.BluRay.REMUX.TrueHD.Atmos.7.1"))
        assertEquals("AAC 2.0", b.audioLabel("aac", 2))
        assertEquals("DTS-HD 7.1", b.audioLabel("DTS-HD", 8))
        assertNull(b.audioLabel(null, null))
    }

    @Test
    fun sourceFromName() {
        assertEquals("REMUX", b.sourceFromName("Movie.2160p.BluRay.REMUX.HEVC.DV"))
        assertEquals("REMUX", b.sourceFromName("Movie 2160p BDRemux"))
        assertEquals("BLURAY", b.sourceFromName("Movie.1080p.BluRay.x264"))
        assertEquals("BLURAY", b.sourceFromName("Movie 1080p BDRip"))
        assertEquals("BLURAY", b.sourceFromName("Movie.720p.BRRip"))
        assertEquals("WEB-DL", b.sourceFromName("Show.S01E01.2160p.WEB-DL.DDP5.1"))
        assertEquals("WEB-DL", b.sourceFromName("Show S01E01 1080p WEBDL"))
        assertEquals("WEBRIP", b.sourceFromName("Movie.1080p.WEBRip.x265"))
        assertEquals("WEB", b.sourceFromName("Show.S02E03.1080p.WEB.h264"))
        assertEquals("HDTV", b.sourceFromName("Show.S01E01.720p.HDTV"))
        assertEquals("DVD", b.sourceFromName("Movie.DVDRip.XviD"))
        assertEquals("CAM", b.sourceFromName("Movie 2024 HDCAM"))
        assertNull(b.sourceFromName("Cam 2018 1080p"))
        assertNull(b.sourceFromName("Movie 2160p"))
        assertNull(b.sourceFromName("Webster 1080p"))
    }

    @Test
    fun sizeAndJoin() {
        assertEquals("9.0 GB", b.sizeLabel(9L * 1024 * 1024 * 1024))
        assertEquals("850 MB", b.sizeLabel(850L * 1024 * 1024))
        assertNull(b.sizeLabel(null))
        assertEquals("4K · DV · E-AC-3 5.1 · 9.0 GB", b.join("4K", "DV", "E-AC-3 5.1", "9.0 GB"))
        assertEquals("1080p · SDR", b.join("1080p", "SDR", null, ""))
    }
}
