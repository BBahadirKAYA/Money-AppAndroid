package com.moneyapp.android.data.net

import retrofit2.Call
import retrofit2.http.GET

// Sunucudan gelen bir transaction verisinin yapısını temsil eden DTO (Data Transfer Object)
data class TransactionDto(
    val uuid: String,
    val amount: Long,
    val note: String?,
    val date: Long,
    val type: String // "INCOME" veya "EXPENSE"
)

interface ApiService {
    @GET("api/transactions")
    suspend fun getTransactions(): List<TransactionDto>

    // Ping endpoint
    @GET("api/ping")
    fun ping(): Call<String>
}
