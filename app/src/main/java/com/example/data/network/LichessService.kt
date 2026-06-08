package com.example.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class LichessUserPerf(
    @Json(name = "rating") val rating: Int = 1500,
    @Json(name = "prog") val prog: Int = 0
)

@JsonClass(generateAdapter = true)
data class LichessUserPerfs(
    @Json(name = "blitz") val blitz: LichessUserPerf? = null,
    @Json(name = "bullet") val bullet: LichessUserPerf? = null,
    @Json(name = "rapid") val rapid: LichessUserPerf? = null,
    @Json(name = "classical") val classical: LichessUserPerf? = null,
    @Json(name = "puzzle") val puzzle: LichessUserPerf? = null
)

@JsonClass(generateAdapter = true)
data class LichessUserCount(
    @Json(name = "all") val all: Int = 0,
    @Json(name = "win") val win: Int = 0,
    @Json(name = "loss") val loss: Int = 0,
    @Json(name = "draw") val draw: Int = 0
)

@JsonClass(generateAdapter = true)
data class LichessUserProfileResponse(
    @Json(name = "id") val id: String,
    @Json(name = "username") val username: String,
    @Json(name = "perfs") val perfs: LichessUserPerfs? = null,
    @Json(name = "count") val count: LichessUserCount? = null
)

@JsonClass(generateAdapter = true)
data class CloudEvalPv(
    @Json(name = "moves") val moves: String = "",
    @Json(name = "cp") val cp: Int? = null,
    @Json(name = "mate") val mate: Int? = null
)

@JsonClass(generateAdapter = true)
data class LichessCloudEvalResponse(
    @Json(name = "fen") val fen: String,
    @Json(name = "depth") val depth: Int = 0,
    @Json(name = "knodes") val knodes: Int = 0,
    @Json(name = "pvs") val pvs: List<CloudEvalPv> = emptyList()
)

interface LichessApiService {
    @GET("api/user/{username}")
    suspend fun getUserProfile(
        @Path("username") username: String
    ): LichessUserProfileResponse

    /**
     * Downloads recent games for user. Calling this endpoint with Header "Accept: application/x-ndjson"
     * returns games list in simple newline-delimited JSON format.
     */
    @GET("api/games/user/{username}")
    suspend fun getUserGamesNdjson(
        @Path("username") username: String,
        @Query("max") max: Int,
        @Query("perfType") perfType: String = "bullet,blitz,rapid,classical",
        @Query("moves") moves: Boolean = true,
        @Query("opening") opening: Boolean = true,
        @Query("clock") clock: Boolean = false,
        @Header("Accept") accept: String = "application/x-ndjson"
    ): ResponseBody

    @GET("api/cloud-eval")
    suspend fun getCloudEvaluation(
        @Query("fen") fen: String
    ): LichessCloudEvalResponse

    @GET("api/puzzle/daily")
    suspend fun getDailyPuzzle(): ResponseBody // Returns daily puzzle JSON
}

object LichessClient {
    private const val BASE_URL = "https://lichess.org/"

    private val moshi = com.squareup.moshi.Moshi.Builder()
        .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
        .build()

    val service: LichessApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        retrofit.create(LichessApiService::class.java)
    }
}
