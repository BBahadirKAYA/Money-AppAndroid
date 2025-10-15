package com.moneyapp.android.data.net.sync

import android.util.Log
import com.moneyapp.android.data.db.dao.TransactionDao
import com.moneyapp.android.data.db.entities.CategoryType
import com.moneyapp.android.data.db.entities.TransactionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncRepository(
    private val dao: TransactionDao,
    private val api: TransactionApi
) {
    companion object { private const val TAG = "SyncRepository" }

    /**
     * 1) Sunucudan listeyi çek ve local DB'ye uygula.
     *  - Local'de dirty olan kayıtlar korunur.
     */
    suspend fun pullFromServer() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "pullFromServer: başlatılıyor...")
            val remote = api.getAll().data
            Log.d(TAG, "pullFromServer: Sunucudan ${remote.size} kayıt geldi")

            val localDirty = dao.getDirtyTransactions().map { it.uuid }

            val merged = remote.filterNot { it.uuid in localDirty }
                .map { dto ->
                    TransactionEntity(
                        uuid = dto.uuid,
                        amountCents = ((dto.amount ?: 0.0) * 100).toLong(),
                        currency = dto.currency ?: "TRY",
                        type = CategoryType.EXPENSE,
                        description = null, // Laravel’de note varsa: dto.note
                        accountId = null,
                        categoryId = null,
                        date = System.currentTimeMillis(),
                        deleted = dto.deleted,
                        dirty = false
                    )

                }

            dao.replaceAll(merged)
            Log.d(TAG, "Room’a ${merged.size} kayıt yazıldı.")
        } catch (e: Exception) {
            Log.e(TAG, "pullFromServer hata: ${e.message}", e)
        }
    }

    /**
     * 2) Local dirty kayıtları Laravel'e gönder (başarılıysa dirty=false).
     */
    suspend fun pushDirtyToServer() = withContext(Dispatchers.IO) {
        try {
            val dirtyList = dao.getDirtyTransactions()
            if (dirtyList.isEmpty()) {
                Log.d(TAG, "pushDirtyToServer: dirty kayıt yok")
                return@withContext
            }

            val dtoList = dirtyList.mapNotNull { tx ->
                val uuid = tx.uuid ?: return@mapNotNull null
                TransactionDto(
                    uuid = uuid,
                    amount = tx.amountCents / 100.0,
                    currency = tx.currency,
                    deleted = tx.deleted,
                    updatedAt = "2025-10-14T00:00:00Z" // TODO: gerçek zaman
                )
            }

            val resp = api.bulkUpsert(dtoList)
            if (resp.isSuccessful) {
                dao.markAllClean(dirtyList.mapNotNull { it.uuid })
                Log.d(TAG, "pushDirtyToServer: ${dtoList.size} kayıt gönderildi")
            } else {
                Log.e(TAG, "pushDirtyToServer: HTTP ${resp.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "pushDirtyToServer hata: ${e.message}", e)
        }
    }

    /**
     * 3) Remote soft delete + local işaretle.
     */
    suspend fun deleteRemote(uuid: String) = withContext(Dispatchers.IO) {
        try {
            val resp = api.delete(uuid)
            if (resp.isSuccessful) {
                dao.softDelete(uuid)
                Log.d(TAG, "deleteRemote: $uuid silindi")
            } else {
                Log.e(TAG, "deleteRemote hata: HTTP ${resp.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "deleteRemote hata: ${e.message}", e)
        }
    }
}
