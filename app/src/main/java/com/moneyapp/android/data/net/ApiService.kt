package com.moneyapp.android.data.net

import com.squareup.moshi.Json
import retrofit2.Call
import retrofit2.http.GET

// Sunucunun JSON’una birebir uygun DTO
data class TransactionDto(
    val id: Long,
    val type: String,                 // "income" | "expense"
    val amount: String?,              // "36000.00" ya da null
    @Json(name = "occurred_at")
    val occurredAt: String,           // ISO-8601 tarih (saat olmadan da gelebilir)
    val note: String?
)

interface ApiService {
    @GET("api/transactions")
    suspend fun getTransactions(): List<TransactionDto>

    // (opsiyonel) ping varsa kalsın:
    @GET("api/ping")
    fun ping(): Call<String>
}
