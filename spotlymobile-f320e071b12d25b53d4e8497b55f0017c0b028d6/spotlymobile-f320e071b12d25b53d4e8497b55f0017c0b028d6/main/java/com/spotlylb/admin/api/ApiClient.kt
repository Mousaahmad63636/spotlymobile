package com.spotlylb.admin.api

import com.spotlylb.admin.utils.Constants
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(Constants.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val apiService: ApiService = retrofit.create(ApiService::class.java)

    fun getAuthenticatedApiService(token: String): ApiService {
        val authenticatedClient = okHttpClient.newBuilder()
            .addInterceptor(TokenInterceptor(token))
            .build()

        val authenticatedRetrofit = retrofit.newBuilder()
            .client(authenticatedClient)
            .build()

        return authenticatedRetrofit.create(ApiService::class.java)
    }
}