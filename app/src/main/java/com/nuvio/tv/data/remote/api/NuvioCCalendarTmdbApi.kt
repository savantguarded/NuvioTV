package com.nuvio.tv.data.remote.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

// [fork] library_calendar: the two TMDB calls the Calendar needs fields for that official's TmdbApi doesn't
// read (next / last episode to air, episode type). Kept in its own interface so TmdbApi.kt (part of the
// optional filmography patch) stays untouched; it uses the same "tmdb" Retrofit and API key.
interface NuvioCCalendarTmdbApi {
    @GET("tv/{tv_id}")
    suspend fun getTvDetails(
        @Path("tv_id") tvId: Int,
        @Query("api_key") apiKey: String
    ): Response<NuvioCCalendarTmdbShow>

    @GET("movie/{movie_id}/release_dates")
    suspend fun getMovieReleaseDates(
        @Path("movie_id") movieId: Int,
        @Query("api_key") apiKey: String
    ): Response<NuvioCCalendarReleaseDates>

    @GET("tv/{tv_id}/season/{season_number}")
    suspend fun getTvSeasonDetails(
        @Path("tv_id") tvId: Int,
        @Path("season_number") seasonNumber: Int,
        @Query("api_key") apiKey: String
    ): Response<NuvioCCalendarTmdbSeason>

    // [fork] calendar_tmdb_soon: the two discover calls behind the coming-soon list
    @GET("discover/movie")
    suspend fun discoverSoonMovies(
        @Query("api_key") apiKey: String,
        @Query("region") region: String,
        @Query("with_release_type") releaseTypes: String,
        @Query("release_date.gte") from: String,
        @Query("release_date.lte") to: String,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("include_adult") includeAdult: Boolean = false,
        @Query("language") language: String = "en-US",
        @Query("page") page: Int = 1
    ): Response<NuvioCCalendarDiscover>

    @GET("discover/tv")
    suspend fun discoverSoonShows(
        @Query("api_key") apiKey: String,
        @Query("first_air_date.gte") firstFrom: String? = null,
        @Query("first_air_date.lte") firstTo: String? = null,
        @Query("air_date.gte") airFrom: String? = null,
        @Query("air_date.lte") airTo: String? = null,
        @Query("with_original_language") language: String = "en",
        @Query("without_genres") withoutGenres: String,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("include_adult") includeAdult: Boolean = false,
        @Query("page") page: Int = 1
    ): Response<NuvioCCalendarDiscover>
}

@JsonClass(generateAdapter = true)
data class NuvioCCalendarDiscover(
    @Json(name = "results") val results: List<NuvioCCalendarDiscoverItem>? = null
)

@JsonClass(generateAdapter = true)
data class NuvioCCalendarDiscoverItem(
    @Json(name = "id") val id: Int? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "popularity") val popularity: Double? = null,
    @Json(name = "original_language") val originalLanguage: String? = null,
    @Json(name = "vote_count") val voteCount: Int? = null,
    @Json(name = "adult") val adult: Boolean? = null,
    @Json(name = "release_date") val releaseDate: String? = null
)

@JsonClass(generateAdapter = true)
data class NuvioCCalendarTmdbShow(
    @Json(name = "name") val name: String? = null,
    @Json(name = "status") val status: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "networks") val networks: List<TmdbNetwork>? = null,
    @Json(name = "last_episode_to_air") val lastEpisodeToAir: TmdbEpisodeToAir? = null,
    @Json(name = "next_episode_to_air") val nextEpisodeToAir: TmdbEpisodeToAir? = null
)

@JsonClass(generateAdapter = true)
data class NuvioCCalendarTmdbSeason(
    @Json(name = "season_number") val seasonNumber: Int? = null,
    @Json(name = "episodes") val episodes: List<TmdbEpisodeToAir>? = null
)

/** Same shape as ysosrs123's TmdbEpisodeToAir (TmdbApi.kt there), so the ported calendar code is unchanged. */
@JsonClass(generateAdapter = true)
data class TmdbEpisodeToAir(
    @Json(name = "name") val name: String? = null,
    @Json(name = "air_date") val airDate: String? = null,
    @Json(name = "season_number") val seasonNumber: Int? = null,
    @Json(name = "episode_number") val episodeNumber: Int? = null,
    @Json(name = "episode_type") val episodeType: String? = null
)

/** Official's release-date model reads certifications only; the Calendar needs each release's type and date. */
@JsonClass(generateAdapter = true)
data class NuvioCCalendarReleaseDates(
    @Json(name = "results") val results: List<NuvioCCalendarReleaseCountry>? = null
)

@JsonClass(generateAdapter = true)
data class NuvioCCalendarReleaseCountry(
    @Json(name = "iso_3166_1") val iso31661: String? = null,
    @Json(name = "release_dates") val releaseDates: List<NuvioCCalendarReleaseDate>? = null
)

@JsonClass(generateAdapter = true)
data class NuvioCCalendarReleaseDate(
    @Json(name = "type") val type: Int? = null,
    @Json(name = "release_date") val releaseDate: String? = null
)
