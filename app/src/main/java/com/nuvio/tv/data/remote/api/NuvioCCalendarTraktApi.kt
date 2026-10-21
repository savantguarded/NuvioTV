package com.nuvio.tv.data.remote.api

import com.nuvio.tv.data.remote.dto.trakt.TraktCalendarEpisodeDto
import com.nuvio.tv.data.remote.dto.trakt.TraktCalendarShowDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

// [fork] library_calendar: Trakt's calendar and next-episode calls (in TraktApi in ysosrs123's fork). Own
// interface on the same "trakt" Retrofit, so official's TraktApi.kt is untouched.
interface NuvioCCalendarTraktApi {
    @GET("calendars/my/shows/{start_date}/{days}")
    suspend fun getMyShowsCalendar(
        @Header("Authorization") authorization: String,
        @Path("start_date") startDate: String,
        @Path("days") days: Int,
        @Query("extended") extended: String = "full"
    ): Response<List<TraktCalendarShowDto>>

    @GET("shows/{id}/next_episode")
    suspend fun getShowNextEpisode(
        @Path("id") id: String,
        @Query("extended") extended: String = "full"
    ): Response<TraktCalendarEpisodeDto>
}
