package com.moneyapp.android.data.net

import retrofit2.http.GET

data class ConfigResponse(val base_url: String)

interface ConfigService {
    @GET("macros/s/AKfycbxq1xRouJm2HjpXZTLJaTUqmSbYsNMI4icOdKxgNFNTMR01yC9nlQj4rgpIWMf737f1LA/exec")
    suspend fun getBaseUrlConfig(): ConfigResponse
}