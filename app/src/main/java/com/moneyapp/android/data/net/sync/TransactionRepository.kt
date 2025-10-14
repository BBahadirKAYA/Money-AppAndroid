package com.moneyapp.android.data.net.sync

import android.util.Log
import com.moneyapp.android.data.db.dao.TransactionDao
import com.moneyapp.android.data.db.entities.TransactionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Laravel senkronizasyon yöneticisi.
 * Room (TransactionDao) ve Retrofit (TransactionApi) arasında köprü oluşturur.
 */
class TransactionRepository(
    private val dao: TransactionDao,
    private val api: TransactionApi
) {
    companion object {
        private const val TAG = "TransactionRepository"
    }

    /**
     * 1️⃣ Sunucudan güncel transaction listesini çek ve local DB'ye uygula.
     * - Eğer local kayıtta dirty=true ise, sunucu kaydıyla çakışma yaşanmaz (öncelik local’dedir).
     */
    suspend fun pullFromServer() = withContext(Dispatchers.IO) {
        try {
            val remote = api.getAll()
            val localDirty = dao.getDirtyTransactions().map { it.uuid }

            val merged = remote.filterNot { it.uuid in localDirty }
                .map { dto ->
                    TransactionEntity(
                        uuid = dto.uuid,
                        amountCents = (dto.amount * 100).toLong(),
                        currency = dto.currency,
                        deleted = dto.deleted,
                        dirty = false,
                        date = System.currentTimeMillis(), // Laravel'de yoksa local zaman kullan
                        description = null,
                        accountId = null,
                        categoryId = null
                    )
                }

            dao.upsertAll(merged)
            Log.d(TAG, "pullFromServer: ${merged.size} kayıt güncellendi")

        } catch (e: Exception) {
            Log.e(TAG, "pullFromServer hata: ${e.message}", e)
        }
    }

    /**
     * 2️⃣ Local'de dirty=true olan kayıtları Laravel'e gönder.
     * - Gönderim başarılıysa dirty=false yapılır.
     */
    suspend fun pushDirtyToServer() = withContext(Dispatchers.IO) {
        try {
            val dirtyList = dao.getDirtyTransactions()
            if (dirtyList.isEmpty()) {
                Log.d(TAG, "pushDirtyToServer: dirty kayıt yok")
                return@withContext
            }

            val dtoList = dirtyList.map {
                TransactionDto(
                    uuid = it.uuid ?: return@map null,
                    amount = it.amountCents / 100.0,
                    currency = it.currency,
                    deleted = it.deleted,
                    updatedAt = "2025-10-14T00:00:00Z" // TODO: gerçek updated_at eklenecek
                )
            }.filterNotNull()

            val response = api.bulkUpsert(dtoList)
            if (response.isSuccessful) {
                dao.markAllClean(dirtyList.mapNotNull { it.uuid })
                Log.d(TAG, "pushDirtyToServer: ${dtoList.size} kayıt başarıyla gönderildi")
            } else {
                Log.e(TAG, "pushDirtyToServer: HTTP ${response.code()}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "pushDirtyToServer hata: ${e.message}", e)
        }
    }

    /**
     * 3️⃣ Belirli bir kaydı soft delete yap.
     * - API DELETE çağrısı ardından local olarak deleted=true + dirty=false yapılır.
     */
    suspend fun deleteRemote(uuid: String) = withContext(Dispatchers.IO) {
        try {
            val response = api.delete(uuid)
            if (response.isSuccessful) {
                dao.softDelete(uuid)
                Log.d(TAG, "deleteRemote: $uuid silindi")
            } else {
                Log.e(TAG, "deleteRemote hata: HTTP ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "deleteRemote hata: ${e.message}", e)
        }
    }
}
