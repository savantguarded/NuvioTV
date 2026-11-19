package com.nuvio.tv.data.remote.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

@JsonClass(generateAdapter = true)
data class SeriesGraphEpisodeRatingDto(
    @field:Json(name = "season_number") val seasonNumber: Int? = null,
    @field:Json(name = "episode_number") val episodeNumber: Int? = null,
    @field:Json(name = "vote_average") val voteAverage: Double? = null,
    // [fork] round 50 imdb_ratings_lean: name / tconst (and the overview, still etc. the server also sends) are not
    // read at all: Moshi skips unknown keys, so only season / episode / rating / date are built per episode.
    @field:Json(name = "air_date") val airDate: String? = null
)

@JsonClass(generateAdapter = true)
data class SeriesGraphSeasonRatingsDto(
    @field:Json(name = "episodes") val episodes: List<SeriesGraphEpisodeRatingDto>? = null
)

interface SeriesGraphApi {
    @GET("api/shows/{tmdbId}/season-ratings")
    suspend fun getSeasonRatings(
        @Path("tmdbId") tmdbId: Int
    ): Response<List<SeriesGraphSeasonRatingsDto>>
}

interface ImdbTapframeApi {
    @GET("api/shows/{imdbId}/season-ratings")
    suspend fun getSeasonRatings(
        @Path("imdbId") imdbId: String
    ): Response<List<SeriesGraphSeasonRatingsDto>>
}
