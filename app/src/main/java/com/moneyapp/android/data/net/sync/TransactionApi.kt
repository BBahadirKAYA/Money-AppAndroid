package com.moneyapp.android.data.net.sync

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

data class TransactionNetworkModel(
    val uuid: String,
    val account_id: Long?,
    val category_id: Long?,
    val type: String,
    val amount: Double,
    val occurred_at: String,
    val note: String?
)
/**
 * Laravel API ile transaction senkronizasyonu için Retrofit arabirimi.
 * Base URL -> ApiClient içinde (örnek: https://ngrok-url.ngrok-free.dev/)
 */
interface TransactionApi {

    /** Sunucudan tüm transaction kayıtlarını getirir. */
    @GET("api/transactions")
    suspend fun getAll(): ResponseWrapper<List<TransactionDto>>

    /** Toplu ekleme veya güncelleme işlemi (bulk upsert). */
    @POST("api/transactions/bulk-upsert")
    suspend fun bulkUpsert(
        @Body items: List<TransactionDto>
    ): Response<Unit>

    /** Belirtilen UUID'li kaydı soft delete yapar. */
    @DELETE("api/transactions/{uuid}")
    suspend fun delete(
        @Path("uuid") uuid: String
    ): Response<Unit>
    /** Tek bir transaction kaydı oluşturur. */
    @POST("api/transactions")
    suspend fun create(
        @Body item: TransactionNetworkModel
    ): ResponseWrapper<TransactionNetworkModel>

}

/** Laravel’in "success":true,"data":[...] formatı için wrapper */
data class ResponseWrapper<T>(
    val success: Boolean,
    val data: T
)
