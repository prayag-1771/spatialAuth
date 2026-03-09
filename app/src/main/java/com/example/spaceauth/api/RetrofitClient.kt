package com.example.spaceauth.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    /**
     * For Physical Devices (USB): Use "http://127.0.0.1:8000/" AND run "adb reverse tcp:8000 tcp:8000"
     * For Emulator: Use "http://10.0.2.2:8000/"
     */
    private const val BASE_URL = "http://127.0.0.1:8000/"

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(logging)
        .build()

    val api: SpaceAuthApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
            .create(SpaceAuthApi::class.java)
    }
}
