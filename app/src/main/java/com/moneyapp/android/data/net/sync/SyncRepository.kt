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
    private val api: TransactionApi
) {
    companion object { private const val TAG = "SyncRepository" }

    // ────────────────────────────────────────────────
    // 1️⃣ Sunucudan listeyi çek ve local DB’ye uygula
    // ────────────────────────────────────────────────
    suspend fun pullFromServer() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "pullFromServer: başlatılıyor...")
            val remote = api.getAll().data
            Log.d(TAG, "🌐 Sunucudan ${remote.size} kayıt geldi")

            // 🧹 Local DB’yi tamamen sıfırla (hard reset)
            dao.deleteAll()

            if (remote.isEmpty()) {
                Log.w(TAG, "⚠️ Sunucu boş döndü — local tamamen temizlendi.")
                return@withContext
            }

            val formatter = DateTimeFormatter
                .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US)
                .withZone(ZoneId.of("UTC"))

            val entities = remote.mapNotNull { dto ->
                if (dto.uuid == null) return@mapNotNull null

                val dateMillis = try {
                    dto.occurred_at?.let { Instant.from(formatter.parse(it)).toEpochMilli() }
                        ?: System.currentTimeMillis()
                } catch (e: DateTimeParseException) {
                    Log.w(TAG, "Tarih parse hatası: ${dto.occurred_at}", e)
                    System.currentTimeMillis()
                }

                TransactionEntity(
                    uuid = dto.uuid,
                    amountCents = ((dto.amount ?: 0.0) * 100).toLong(),
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
                    paidSum = ((dto.paid_sum ?: 0.0) * 100).toLong()
                )
            }

            dao.upsertAll(entities)
            Log.d(TAG, "✅ pullFromServer: ${entities.size} kayıt local DB’ye yazıldı.")

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
}
