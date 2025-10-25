package com.moneyapp.android.data.net.sync

import retrofit2.Response
import retrofit2.http.*

data class TransactionNetworkModel(
    val uuid: String,
    val account_id: Long?,
    val category_id: Long?,
    val type: String,
    val amount: Double,
    val paid_sum: Double? = 0.0,
    val currency: String = "TRY",
    val occurred_at: String,
    val note: String?,
    val deleted: Boolean = false
)

// ────────────────────────────────────────────────
// ✅ YENİ MODEL: Silinen UUID'leri karşılayacak API Cevap Modeli
// Laravel'deki 'success':true,'data':[...] formatına uygun
// ────────────────────────────────────────────────
data class DeletedUuidsResponse(
    val success: Boolean,
    val data: List<String> // Sunucudan dönen silinmiş UUID listesi
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

    /** 🔹 Belirtilen UUID'li kaydı hard delete yapar. */
    @DELETE("api/transactions/{uuid}")
    suspend fun delete(
        @Path("uuid") uuid: String
    ): Response<Unit>

    // ────────────────────────────────────────────────
    // ✅ YENİ METOT: Sunucudan silinen UUID'leri çeker
    // ────────────────────────────────────────────────
    @GET("api/transactions/deleted")
    suspend fun getDeletedUuids(): Response<DeletedUuidsResponse>
}

/** Laravel’in "success":true,"data":[...] formatı için wrapper */
data class ResponseWrapper<T>(
    val success: Boolean = true,
    val data: T
)