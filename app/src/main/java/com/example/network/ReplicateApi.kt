package com.example.network

import com.squareup.moshi.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

// Replicate Prediction Request Model
data class ReplicateRequest(
    val version: String,
    val input: Map<String, Any>
)

// Replicate Prediction Response Model
data class ReplicateResponse(
    val id: String,
    val version: String?,
    val status: String, // "starting", "processing", "succeeded", "failed", "canceled"
    val input: Map<String, Any>?,
    val output: Any?, // Can be String or List<String>
    val error: String?,
    @Json(name = "created_at") val createdAt: String?,
    @Json(name = "completed_at") val completedAt: String?
)

interface ReplicateService {
    @POST("v1/predictions")
    suspend fun createPrediction(
        @Header("Authorization") token: String,
        @Body request: ReplicateRequest
    ): ReplicateResponse

    @GET("v1/predictions/{id}")
    suspend fun getPrediction(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): ReplicateResponse

    @POST("v1/predictions/{id}/cancel")
    suspend fun cancelPrediction(
        @Header("Authorization") token: String,
        @Path("id") id: String
    ): ReplicateResponse
}

object ReplicateClient {
    private const val BASE_URL = "https://api.replicate.com/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val service: ReplicateService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(ReplicateService::class.java)
    }
}
