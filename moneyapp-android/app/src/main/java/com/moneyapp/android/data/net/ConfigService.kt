package com.moneyapp.android.data.net

import retrofit2.http.GET

// JSON: { "base_url": "https://xxx.ngrok-free.dev" }
data class ConfigResponse(val base_url: String?)

interface ConfigService {
    @GET("exec")
    suspend fun getBaseUrlConfig(): ConfigResponse
}
