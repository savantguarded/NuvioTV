package com.nuvio.tv.core.tmdb

import com.nuvio.tv.data.remote.api.TmdbPersonCreditCast
import com.nuvio.tv.data.remote.api.TmdbPersonCreditCrew
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// [fork] Nuvio C: cast-page filmography rules.
class NuvioCFilmographyTest {

    private val f = NuvioCFilmography

    private fun cast(id: Int, character: String?, media: String = "movie", genres: List<Int> = emptyList()) =
        TmdbPersonCreditCast(id = id, character = character, mediaType = media, genreIds = genres)

    private fun crew(id: Int, job: String?, department: String?, media: String = "movie") =
        TmdbPersonCreditCrew(id = id, job = job, department = department, mediaType = media)

    @Test
    fun actorKeepsRealRolesOnly() {
        assertTrue(f.isRealRole(cast(1, "Ethan Hunt")))
        assertTrue(f.isRealRole(cast(2, "Lightning McQueen (voice)")))
        assertFalse(f.isRealRole(cast(3, "Self")))
        assertFalse(f.isRealRole(cast(4, "Himself")))
        assertFalse(f.isRealRole(cast(5, "Herself - Guest")))
        assertFalse(f.isRealRole(cast(6, "Self - Host")))
        assertFalse(f.isRealRole(cast(7, "Maverick (archive footage)")))
        assertFalse(f.isRealRole(cast(8, "")))
        assertFalse(f.isRealRole(cast(9, null)))
        assertFalse(f.isRealRole(cast(10, "Guest", media = "tv", genres = listOf(10767))))
        assertFalse(f.isRealRole(cast(11, "Contestant", media = "tv", genres = listOf(10764))))
        assertFalse(f.isRealRole(cast(12, "Anchor", media = "tv", genres = listOf(10763))))
        assertTrue(f.isRealRole(cast(13, "Selfridge")))
    }

    @Test
    fun characterLinesMergePerTitle() {
        val lines = f.characterLines(listOf(cast(1, "A"), cast(1, "B"), cast(1, "a"), cast(1, "X", media = "tv")))
        assertEquals("A / B", lines["movie:1"])
        assertEquals("X", lines["tv:1"])
    }

    @Test
    fun crewKeepsCreatorDirectingWriting() {
        assertEquals("Creator", f.jobLabel("Creator", "Creator"))
        assertEquals("Director", f.jobLabel("Directing", "Director"))
        assertEquals("Writer", f.jobLabel("Writing", "Screenplay"))
        assertEquals("Writer", f.jobLabel("Writing", "Story"))
        assertEquals("Writer", f.jobLabel("Writing", "Characters"))
        assertEquals("Writer", f.jobLabel("Writing", "Novel"))
        assertEquals("Writer", f.jobLabel(null, "Writer"))
        assertNull(f.jobLabel("Production", "Executive Producer"))
        assertNull(f.jobLabel("Production", "Producer"))
        assertNull(f.jobLabel("Production", "Consulting Producer"))
        assertNull(f.jobLabel("Crew", "Thanks"))
        assertNull(f.jobLabel("Camera", "Director of Photography"))
        assertNull(f.jobLabel("Directing", "Assistant Director"))
        assertNull(f.jobLabel("Directing", "Second Unit Director"))
        assertNull(f.jobLabel("Writing", "Story Editor"))
        assertNull(f.jobLabel("Writing", "Script Consultant"))
        assertNull(f.jobLabel("Editing", "Editor"))
    }

    @Test
    fun jobLinesUseFixedOrder() {
        val lines = f.jobLines(
            listOf(
                crew(1, "Writer", "Writing", "tv"),
                crew(1, "Executive Producer", "Production", "tv"),
                crew(1, "Director", "Directing", "tv"),
                crew(1, "Creator", "Creator", "tv"),
                crew(2, "Screenplay", "Writing"),
                crew(2, "Director", "Directing"),
                crew(3, "Thanks", "Crew")
            )
        )
        assertEquals("Creator · Director · Writer", lines["tv:1"])
        assertEquals("Director · Writer", lines["movie:2"])
        assertNull(lines["movie:3"])
    }
}
