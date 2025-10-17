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
            Log.d(TAG, "pullFromServer: Sunucudan ${remote.size} kayıt geldi")

            val localDirty = dao.getDirtyTransactions().map { it.uuid }

            val merged = mutableListOf<TransactionEntity>()
            val deletedUuids = remote.filter { it.deleted }.mapNotNull { it.uuid }
            if (deletedUuids.isNotEmpty()) {
                deletedUuids.forEach { uuid ->
                    dao.softDelete(uuid)
                }
                Log.d(TAG, "🧹 ${deletedUuids.size} kayıt sunucuda silinmiş, localde işaretlendi.")
            }
            for (dto in remote) {
                if (dto.uuid == null) continue
                if (dto.uuid in localDirty) continue

                // 🧩 Sunucudan deleted=true geldiyse local DB'den sil
                if (dto.deleted) {
                    dao.deleteByUuid(dto.uuid)
                    Log.d(TAG, "pullFromServer: ${dto.uuid} deleted=true, localden silindi")
                    continue
                }

                // 🔹 Normal kayıtlar için entity oluştur
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

                merged += TransactionEntity(
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

            dao.replaceAll(merged)
            Log.d(TAG, "pullFromServer: ${merged.size} kayıt güncellendi.")
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
