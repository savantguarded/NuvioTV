package com.nuvio.tv.ui.screens.detail

import com.nuvio.tv.domain.model.MetaCastMember
import org.junit.Assert.assertEquals
import org.junit.Test

class NuvioCCreditsTest {
    private val benDirector = MetaCastMember(name = "Ben Affleck", character = "Director", tmdbId = 880)
    private val benWriter = MetaCastMember(name = "Ben Affleck", character = "Writer", tmdbId = 880)
    private val benActor = MetaCastMember(name = "Ben Affleck", character = "Nick", tmdbId = 880)
    private val other = MetaCastMember(name = "Kerry Washington", character = "Jane", tmdbId = 11703)

    @Test
    fun directorWhoActsIsInCrewAndCast() {
        val out = nuvioCPeopleCredits(listOf(benDirector), emptyList(), listOf(benActor, other))
        assertEquals(listOf(benDirector, benActor, other), out)
    }

    @Test
    fun directorWhoWritesIsOneCrewCard() {
        val out = nuvioCPeopleCredits(listOf(benDirector), listOf(benWriter), emptyList())
        assertEquals(listOf(benDirector), out)
    }

    @Test
    fun duplicateCastAndBlankNamesDropped() {
        val out = nuvioCPeopleCredits(emptyList(), emptyList(), listOf(other, other, MetaCastMember(name = " ")))
        assertEquals(listOf(other), out)
    }
}
