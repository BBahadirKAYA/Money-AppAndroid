package com.moneyapp.android.data.net

import retrofit2.Call
import retrofit2.http.GET

interface ApiService {
    @GET("api/ping")
    fun ping(): Call<String>

    @GET("api/transactions")
    fun transactions(): Call<String>

    @GET("api/accounts")
    fun accounts(): Call<String>
}