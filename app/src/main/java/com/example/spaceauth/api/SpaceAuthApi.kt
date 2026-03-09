package com.example.spaceauth.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

// Data Classes for Requests
data class EnrollmentRequest(
    val room_id: String,
    val samples: List<List<Float>>
)

data class AuthRequest(
    val room_id: String,
    val samples: List<List<Float>>
)

// Data Classes for Responses
data class EnrollmentResponse(
    val message: String,
    val room_id: String,
    val samples_used: Int
)

data class AuthResponse(
    val room_id: String,
    val decision: String, // "ACCEPT" or "REJECT"
    val positive_votes: Int,
    val predictions: List<Int>
)

data class RoomsResponse(
    val rooms: List<String>
)

data class HealthResponse(
    val status: String
)

interface SpaceAuthApi {
    @GET("/health")
    suspend fun checkHealth(): Response<HealthResponse>

    @POST("/enroll")
    suspend fun enrollRoom(@Body request: EnrollmentRequest): Response<EnrollmentResponse>

    @POST("/authenticate")
    suspend fun authenticateRoom(@Body request: AuthRequest): Response<AuthResponse>

    @GET("/rooms")
    suspend fun getEnrolledRooms(): Response<RoomsResponse>
}
