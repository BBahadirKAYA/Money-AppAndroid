package com.moneyapp.android.data.repository

import android.util.Log
import com.moneyapp.android.data.db.dao.TransactionDao
import com.moneyapp.android.data.db.entities.TransactionEntity
import com.moneyapp.android.data.db.entities.toNetworkModel
import com.moneyapp.android.data.net.sync.TransactionApi
import com.moneyapp.android.data.net.sync.SyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID
import com.moneyapp.android.data.db.entities.toDto


class TransactionRepository(
    private val dao: TransactionDao,
    private val api: TransactionApi,
    private val syncRepository: SyncRepository
) {

    // --------------------------------------------------------
    // 🔹 Listeleme
    // --------------------------------------------------------

    fun getAllTransactions(): Flow<List<TransactionEntity>> = dao.getAll()
    fun getAllVisible(): Flow<List<TransactionEntity>> = dao.getAllVisible()

    fun getTransactionsByMonth(yearStr: String, monthStr: String): Flow<List<TransactionEntity>> {
        return dao.getTransactionsByMonth(yearStr, monthStr)
    }

    // --------------------------------------------------------
    // 🟢 Ekleme
    // --------------------------------------------------------
    suspend fun insert(transaction: TransactionEntity) = withContext(Dispatchers.IO) {
        val tx = ensureUuid(transaction)
        val now = System.currentTimeMillis()
        val finalTx = tx.copy(
            updatedAtLocal = now,
            date = if (tx.date <= 0L) now else tx.date,
            dirty = true
        )

        dao.insert(finalTx)
        Log.d("TransactionRepo", "🟢 Insert edildi (local): ${finalTx.uuid}")

        try {
            val res = api.createOrUpdate(finalTx.toDto())


            if (res.success) {
                dao.update(finalTx.copy(dirty = false))
                Log.d("TransactionRepo", "✅ Sunucuya gönderildi: ${finalTx.uuid}")
            } else {
                Log.e("TransactionRepo", "❌ Sunucu create başarısız (success=false)")
            }
        } catch (e: Exception) {
            Log.e("TransactionRepo", "⚠️ Insert sunucu hatası: ${e.message}")
        }
    }

    // --------------------------------------------------------
    // 🟡 Güncelleme
    // --------------------------------------------------------
    suspend fun update(transaction: TransactionEntity) = withContext(Dispatchers.IO) {
        val updated = transaction.copy(
            updatedAtLocal = System.currentTimeMillis(),
            dirty = true
        )
        dao.update(updated)
        Log.d("TransactionRepo", "🟡 Güncellendi (local): ${updated.uuid}")

        try {
            val res = api.update(updated.uuid, updated.toNetworkModel())
            if (res.success) {
                dao.update(updated.copy(dirty = false))
                Log.d("TransactionRepo", "✅ Güncelleme sunucuya gönderildi: ${updated.uuid}")
            } else {
                Log.e("TransactionRepo", "❌ Güncelleme sunucu hatası: success=false")
            }
        } catch (e: Exception) {
            Log.e("TransactionRepo", "⚠️ Güncelleme API hatası: ${e.message}")
        }
    }

    // --------------------------------------------------------
    // 🔴 Soft Delete
    // --------------------------------------------------------
    suspend fun softDelete(transaction: TransactionEntity) = withContext(Dispatchers.IO) {
        try {
            // 1️⃣ Localde işaretle (deleted = 1, dirty = 1)
            dao.softDelete(transaction.uuid)
            Log.d("TransactionRepo", "🔴 Soft delete (local): ${transaction.uuid}")

            // 2️⃣ Sunucuya bildir
            syncRepository.deleteRemote(transaction.uuid)

            // 3️⃣ Başarılıysa temizle
            dao.markAllClean(listOf(transaction.uuid))
            Log.d("TransactionRepo", "✅ Soft delete senkron tamamlandı: ${transaction.uuid}")
        } catch (e: Exception) {
            Log.e("TransactionRepo", "⚠️ Soft delete hata: ${e.message}", e)
        }
    }

    // --------------------------------------------------------
    // 🧹 Hard Delete / Tümünü sil
    // --------------------------------------------------------
    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        dao.deleteAll()
        Log.w("TransactionRepo", "🧹 Tüm işlemler localden silindi.")
    }
}

// --- Yardımcı: UUID otomatik üret ---
private fun ensureUuid(tx: TransactionEntity): TransactionEntity {
    return if (tx.uuid.isBlank()) tx.copy(uuid = UUID.randomUUID().toString()) else tx
}
