package com.nuvio.tv.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class PersonDetail(
    val tmdbId: Int,
    val name: String,
    val biography: String?,
    val birthday: String?,
    val deathday: String?,
    val placeOfBirth: String?,
    val profilePhoto: String?,
    val knownFor: String?,
    val movieCredits: List<MetaPreview>,
    val tvCredits: List<MetaPreview>,
    /** [fork] Nuvio C: line under each title (character or "Director · Writer"), keyed "movie:<id>" / "tv:<id>". */
    val nuvioCRoleLines: Map<String, String> = emptyMap()
)
