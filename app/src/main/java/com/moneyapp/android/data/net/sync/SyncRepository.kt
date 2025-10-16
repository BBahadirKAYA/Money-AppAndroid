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
                    // 🧩 BURAYA EKLE
                    Log.d(TAG, "Remote tarih: ${dto.occurred_at}")


                    val dateMillis = try {
                        dto.occurred_at?.let {
                            val formatter = DateTimeFormatter
                                .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US)
                                .withZone(ZoneId.of("UTC"))

                            val instant = formatter.parse(it, Instant::from)
                            instant.atZone(ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli()
                        } ?: System.currentTimeMillis()
                    } catch (e: DateTimeParseException) {
                        Log.w(TAG, "Tarih parse hatası: ${dto.occurred_at}", e)
                        System.currentTimeMillis()
                    }



                    TransactionEntity(
                        uuid = dto.uuid!!, // ✅ zorunlu alan — null gelmemeli
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

            val dtoList = dirtyList.mapNotNull { tx: TransactionEntity ->
                tx.toDto()
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
