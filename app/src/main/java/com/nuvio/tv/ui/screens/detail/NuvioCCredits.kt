package com.nuvio.tv.ui.screens.detail

import com.nuvio.tv.domain.model.MetaCastMember

// [fork] Nuvio C: director in cast (switch `director_in_cast`).
//
// Official joins directors, writers and cast into one list and then removes duplicates by TMDB id,
// so a director who also acts (Ben Affleck in Animals) loses the acting credit. Here crew and cast
// are de-duplicated on their own and then joined: a person who both directs and writes still gets
// one crew card, and their role in the cast stays.
internal fun nuvioCPeopleCredits(
    directors: List<MetaCastMember>,
    writers: List<MetaCastMember>,
    cast: List<MetaCastMember>
): List<MetaCastMember> {
    fun key(member: MetaCastMember): Any =
        member.tmdbId ?: (member.name.lowercase() + "|" + (member.character ?: ""))

    val crew = (directors + writers)
        .filter { it.name.isNotBlank() }
        .distinctBy(::key)
    val actors = cast
        .filter { it.name.isNotBlank() }
        .distinctBy(::key)
    return crew + actors
}
