// app/src/main/java/com/moneyapp/android/data/net/ApiService.kt
package com.moneyapp.android.data.net

import com.squareup.moshi.Json
import retrofit2.Call
import retrofit2.http.GET

data class TransactionDto(
    @Json(name = "id") val id: Long,
    @Json(name = "type") val type: String,               // "income"/"expense" (lowercase)
    @Json(name = "amount") val amount: String?,          // "36000.00" veya null
    @Json(name = "occurred_at") val occurredAt: String,  // "2025-09-23T11:44:00.000000Z"
    @Json(name = "note") val note: String?
)

interface ApiService {
    @GET("api/transactions")
    suspend fun getTransactions(): List<TransactionDto>

    @GET("api/ping")
    fun ping(): Call<String>
}
