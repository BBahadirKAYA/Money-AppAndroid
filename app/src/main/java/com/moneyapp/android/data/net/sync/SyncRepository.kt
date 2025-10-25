package com.moneyapp.android.data.net.sync

import android.util.Log
import com.moneyapp.android.data.db.dao.TransactionDao
import com.moneyapp.android.data.db.entities.CategoryType
import com.moneyapp.android.data.db.entities.TransactionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import com.moneyapp.android.data.db.entities.toDto

class SyncRepository(
    private val dao: TransactionDao,
    private val api: TransactionApi // Artık getDeletedUuids metodunu içeriyor
) {
    companion object { private const val TAG = "SyncRepository" }

    // ────────────────────────────────────────────────
    // 1️⃣ Sunucudan listeyi çek ve local DB’ye uygula
    // ────────────────────────────────────────────────
    suspend fun pullFromServer() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "pullFromServer: başlatılıyor...")
            // Not: dao.getAllNow() metodunun bu kısımdaki hata ayıklamada kullanılması için
            // TransactionDao'da var olduğu varsayılmıştır.
            val remote = api.getAll().data
            Log.d(TAG, "🌐 Sunucudan ${remote.size} kayıt geldi")

            if (remote.isEmpty()) {
                Log.w(TAG, "⚠️ Sunucu boş döndü — işlem yapılmadı.")
                return@withContext
            }

            val formatter = DateTimeFormatter
                .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US)
                .withZone(ZoneId.of("UTC"))

            val localDirtyUuids = dao.getDirtyTransactions().mapNotNull { it.uuid }.toSet()

            // NOT: dao.getByUuid(dto.uuid) metodunun TransactionDao'da tanımlı olması gerekir.
            val entities = remote.mapNotNull { dto ->
                if (dto.uuid == null) return@mapNotNull null

                val existing = dao.getByUuid(dto.uuid)
                val dateMillis = try {
                    dto.occurred_at?.let { Instant.from(formatter.parse(it)).toEpochMilli() }
                        ?: System.currentTimeMillis()
                } catch (e: DateTimeParseException) {
                    Log.w(TAG, "Tarih parse hatası: ${dto.occurred_at}", e)
                    System.currentTimeMillis()
                }

                // dirty kayıtları ezme
                if (localDirtyUuids.contains(dto.uuid)) {
                    Log.d(TAG, "⏭️ Local dirty kayıt atlandı: ${dto.uuid}")
                    return@mapNotNull null
                }

                // PaidSum koruma mantığı (Ödeme işlemleri localde yapılabilir)
                val remotePaidSum = dto.paid_sum ?: 0.0
                val localPaidSum = existing?.paidSum ?: 0.0

                // Eğer sunucudan gelen değer yereldeki değerden KÜÇÜKSE, yereldeki değeri koru.
                val finalPaidSum = if (localPaidSum > remotePaidSum) {
                    Log.d(TAG, "🔒 PaidSum korundu: Local $localPaidSum > Remote $remotePaidSum")
                    localPaidSum
                } else {
                    remotePaidSum
                }

                TransactionEntity(
                    uuid = dto.uuid,
                    amount = dto.amount ?: 0.0,
                    currency = dto.currency ?: "TRY",
                    type = when (dto.type?.lowercase(Locale.getDefault())) {
                        "income" -> CategoryType.INCOME
                        else -> CategoryType.EXPENSE
                    },
                    description = dto.note,
                    accountId = dto.account_id,
                    categoryId = dto.category_id,
                    date = dateMillis,
                    dirty = false,
                    paidSum = finalPaidSum
                )
            }


            dao.upsertAll(entities)
            Log.d(TAG, "✅ pullFromServer: ${entities.size} kayıt güncellendi (dirty kayıtlar korunarak).")
            // ⚠️ Not: dao.getAllNow() metodunun var olduğunu varsayarak log bırakıyorum.
            // val afterSync = dao.getAllNow()
            // Log.d("SyncDebug", "📊 Local DB'de paidSum değerleri: ${afterSync.map { it.uuid to it.paidSum }}")

        } catch (e: Exception) {
            Log.e(TAG, "pullFromServer hata: ${e.message}", e)
        }
    }


    // ────────────────────────────────────────────────
    // 2️⃣ Local dirty kayıtları Laravel’e gönder
    // ────────────────────────────────────────────────
    suspend fun pushDirtyToServer() = withContext(Dispatchers.IO) {
        try {
            val dirtyList = dao.getDirtyTransactions()
            if (dirtyList.isEmpty()) {
                Log.d(TAG, "pushDirtyToServer: dirty kayıt yok")
                return@withContext
            }

            val dtoList = dirtyList.map { it.toDto() }
            val resp = api.bulkUpsert(dtoList)

            if (resp.isSuccessful) {
                dao.markAllClean(dirtyList.mapNotNull { it.uuid })
                Log.d(TAG, "✅ pushDirtyToServer: ${dtoList.size} kayıt gönderildi.")
            } else {
                Log.e(TAG, "❌ pushDirtyToServer: HTTP ${resp.code()}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "pushDirtyToServer hata: ${e.message}", e)
        }
    }

    // ────────────────────────────────────────────────
    // 3️⃣ Tek kaydı doğrudan remote+local sil (hard delete)
    // ────────────────────────────────────────────────
    suspend fun deleteRemote(uuid: String) = withContext(Dispatchers.IO) {
        try {
            val resp = api.delete(uuid)
            if (resp.isSuccessful) {
                dao.deleteByUuid(uuid)
                Log.d(TAG, "🗑️ deleteRemote: $uuid sunucudan ve localden silindi.")
            } else {
                Log.e(TAG, "❌ deleteRemote hata: HTTP ${resp.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "deleteRemote hata: ${e.message}", e)
        }
    }

    // ────────────────────────────────────────────────
    // ✅ YENİ METOT: Sunucudan silinen kayıtları local DB'den sil
    // ────────────────────────────────────────────────
    suspend fun syncDeletions() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "syncDeletions: başlatılıyor...")

            // API'den silinen UUID listesini çek
            val response = api.getDeletedUuids()

            if (response.isSuccessful) {
                val deletedUuids = response.body()?.data ?: emptyList()

                if (deletedUuids.isNotEmpty()) {
                    // Local DB'den toplu silme işlemini yap
                    val deletedCount = dao.deleteByUuids(deletedUuids)
                    Log.d(TAG, "✅ syncDeletions: ${deletedCount} kayıt localden silindi.")

                    // Not: Buradan sonra Laravel'e "Bu UUID'leri işledim, DeletedRecords tablosundan silebilirsin"
                    // isteği göndermek en iyi uygulamadır, ancak şimdilik bunu atlıyoruz.
                } else {
                    Log.d(TAG, "syncDeletions: Silinecek kayıt bulunamadı.")
                }
            } else {
                Log.e(TAG, "❌ syncDeletions: API HTTP ${response.code()}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "syncDeletions hata: ${e.message}", e)
        }
    }
}