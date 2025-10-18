package com.moneyapp.android.data.net.sync

import android.util.Log
import com.moneyapp.android.data.db.dao.TransactionDao
import com.moneyapp.android.data.db.entities.CategoryType
import com.moneyapp.android.data.db.entities.TransactionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeParseException
import com.moneyapp.android.data.db.entities.toDto
import java.time.format.DateTimeFormatter
import java.util.Locale

class SyncRepository(
    private val dao: TransactionDao,
    private val api: TransactionApi
) {
    companion object { private const val TAG = "SyncRepository" }

    /**
     * 1️⃣ Sunucudan listeyi çek ve local DB'ye uygula.
     *  - Local'de dirty olan kayıtlar korunur.
     *  - Sunucudan gelen deleted=true kayıtlar local'den silinir.
     */
    suspend fun pullFromServer() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "pullFromServer: başlatılıyor...")

            val remote = api.getAll().data
            Log.d(TAG, "🌐 Sunucudan ${remote.size} kayıt geldi")

            // 1️⃣ Sunucudan hiç kayıt gelmediyse çık
            if (remote.isEmpty()) {
                dao.deleteAll()
                Log.w(TAG, "⚠️ Sunucu boş döndü — tüm local kayıtlar silindi.")
                return@withContext
            }

            // 2️⃣ DTO → Entity dönüşümü
            val entities = remote.mapNotNull { dto ->
                if (dto.uuid == null || dto.deleted == true) return@mapNotNull null

                val dateMillis = try {
                    dto.occurred_at?.let {
                        val formatter = DateTimeFormatter
                            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US)
                            .withZone(ZoneId.of("UTC"))
                        Instant.from(formatter.parse(it))
                            .atZone(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli()
                    } ?: System.currentTimeMillis()
                } catch (e: DateTimeParseException) {
                    Log.w(TAG, "Tarih parse hatası: ${dto.occurred_at}", e)
                    System.currentTimeMillis()
                }

                TransactionEntity(
                    uuid = dto.uuid,
                    amountCents = ((dto.amount ?: 0.0) * 100).toLong(),
                    currency = dto.currency ?: "TRY",
                    type = when (dto.type?.lowercase()) {
                        "income" -> CategoryType.INCOME
                        else -> CategoryType.EXPENSE
                    },
                    description = dto.note,
                    accountId = dto.account_id,
                    categoryId = dto.category_id,
                    date = dateMillis,
                    deleted = false,
                    dirty = false
                )
            }

            // 3️⃣ Tüm local kayıtları sil → Sunucudan gelenleri yeniden yaz
            dao.deleteAll()
            dao.upsertAll(entities)

            Log.d(TAG, "✅ pullFromServer: Local DB temizlendi ve ${entities.size} kayıt yeniden yazıldı.")
        } catch (e: Exception) {
            Log.e(TAG, "pullFromServer hata: ${e.message}", e)
        }
    }





    /**
     * 2️⃣ Local dirty kayıtları Laravel'e gönder.
     *  - deleted=true olanlar da gönderilir.
     *  - Başarılıysa dirty=false yapılır.
     */
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
                dao.markAllClean(dirtyList.map { it.uuid })
                Log.d(TAG, "pushDirtyToServer: ${dtoList.size} kayıt gönderildi (deleted dahil)")
            } else {
                Log.e(TAG, "pushDirtyToServer: HTTP ${resp.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "pushDirtyToServer hata: ${e.message}", e)
        }
    }

    /**
     * 3️⃣ Tek bir kaydı hem remote hem local soft delete yap.
     */
    suspend fun deleteRemote(uuid: String) = withContext(Dispatchers.IO) {
        try {
            val resp = api.delete(uuid)
            if (resp.isSuccessful) {
                dao.softDelete(uuid)
                Log.d(TAG, "deleteRemote: $uuid soft silindi")
            } else {
                Log.e(TAG, "deleteRemote hata: HTTP ${resp.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "deleteRemote hata: ${e.message}", e)
        }
    }
}
