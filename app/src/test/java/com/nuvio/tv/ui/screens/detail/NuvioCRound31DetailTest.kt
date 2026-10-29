package com.nuvio.tv.ui.screens.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class NuvioCRound31DetailTest {
    private val lagos = ZoneId.of("Africa/Lagos")
    private val today = LocalDate.of(2026, 10, 29)

    @Test
    fun `countdown badge wording`() {
        // 20:00 UTC = 21:00 in Lagos, same day: tonight
        val tonight = NuvioCNextEpisode(5, 4, today, Instant.parse("2026-10-29T20:00:00Z"))
        assertEquals("TONIGHT 9:00 PM", nuvioCNextEpisodeBadge(tonight, today, lagos))
        val afternoon = NuvioCNextEpisode(5, 4, today, Instant.parse("2026-10-29T14:00:00Z"))
        assertEquals("TODAY 3:00 PM", nuvioCNextEpisodeBadge(afternoon, today, lagos))
        val tomorrow = NuvioCNextEpisode(5, 4, today.plusDays(1), Instant.parse("2026-10-30T02:00:00Z"))
        assertEquals("TOMORROW 3:00 AM", nuvioCNextEpisodeBadge(tomorrow, today, lagos))
        val later = NuvioCNextEpisode(5, 4, today.plusDays(3), null)
        assertEquals("S5 E4 IN 3 DAYS", nuvioCNextEpisodeBadge(later, today, lagos))
    }

    @Test
    fun `countdown badge stays official when too far or past`() {
        assertNull(nuvioCNextEpisodeBadge(NuvioCNextEpisode(1, 1, today.plusDays(40), null), today, lagos))
        assertNull(nuvioCNextEpisodeBadge(NuvioCNextEpisode(1, 1, today.minusDays(1), null), today, lagos))
        assertNull(nuvioCNextEpisodeBadge(null, today, lagos))
    }

    @Test
    fun `money and dates`() {
        assertEquals("$190M", money(190_000_000))
        assertEquals("$714.8M", money(714_800_000))
        assertEquals("$1.2B", money(1_200_000_000))
        assertEquals("$850K", money(850_000))
        assertEquals("1 Mar 2024", nuvioCDate("2024-03-01"))
        assertEquals("1 Mar 2024", nuvioCDate("2024-03-01T00:00:00.000Z"))
        assertNull(nuvioCDate("soon"))
    }
}
