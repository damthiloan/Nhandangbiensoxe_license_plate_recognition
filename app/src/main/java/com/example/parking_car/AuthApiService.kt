package com.example.parking_car

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApiService {
    @POST("api/v1/auth/sign-in")
    fun login(@Body request: LoginRequest): Call<LoginResponse>
}