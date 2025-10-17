package com.moneyapp.android.data.net.sync

import retrofit2.Response
import retrofit2.http.*

data class TransactionNetworkModel(
    val uuid: String,
    val account_id: Long?,
    val category_id: Long?,
    val type: String,
    val amount: Double,
    val occurred_at: String,
    val note: String?,
    val deleted: Boolean = false
)

/**
 * Laravel API ile transaction senkronizasyonu için Retrofit arabirimi.
 * Base URL -> ApiClient içinde (örnek: https://ngrok-url.ngrok-free.dev/)
 */
interface TransactionApi {

    /** 🔹 Tüm kayıtları getir */
    @GET("api/transactions")
    suspend fun getAll(): ResponseWrapper<List<TransactionDto>>

    /** 🔹 Toplu ekleme veya güncelleme (bulk upsert) */
    @POST("api/transactions/bulk-upsert")
    suspend fun bulkUpsert(
        @Body items: List<TransactionDto>
    ): Response<Unit>

    /** 🔹 Tek bir kayıt oluştur veya güncelle */
    @POST("api/transactions")
    suspend fun createOrUpdate(
        @Body item: TransactionDto
    ): ResponseWrapper<TransactionDto>

    /** 🔹 Tek bir kayıt güncelle (PUT /api/transactions/{uuid}) */
    @PUT("api/transactions/{uuid}")
    suspend fun update(
        @Path("uuid") uuid: String,
        @Body item: TransactionNetworkModel
    ): ResponseWrapper<TransactionNetworkModel>

    /** 🔹 Belirtilen UUID'li kaydı soft delete yapar. */
    @DELETE("api/transactions/{uuid}")
    suspend fun delete(
        @Path("uuid") uuid: String
    ): Response<Unit>
}

/** Laravel’in "success":true,"data":[...] formatı için wrapper */
data class ResponseWrapper<T>(
    val success: Boolean = true,
    val data: T
)
