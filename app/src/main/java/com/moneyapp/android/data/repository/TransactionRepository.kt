package com.moneyapp.android.data.repository

import android.util.Log
import com.moneyapp.android.data.db.dao.TransactionDao
import com.moneyapp.android.data.db.entities.TransactionEntity
import com.moneyapp.android.data.db.entities.toNetworkModel
import com.moneyapp.android.data.db.entities.toDto
import com.moneyapp.android.data.net.sync.TransactionApi
import com.moneyapp.android.data.net.sync.SyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

class TransactionRepository(
    private val dao: TransactionDao,
    private val api: TransactionApi,
    private val syncRepository: SyncRepository
) {

    // --------------------------------------------------------
    // 🔹 Listeleme
    // --------------------------------------------------------

    fun getAllTransactions(): Flow<List<TransactionEntity>> = dao.getAll()

    fun getTransactionsByMonth(yearStr: String, monthStr: String): Flow<List<TransactionEntity>> {
        val year = yearStr.toInt()
        val month = monthStr.toInt()

        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.YEAR, year)
            set(java.util.Calendar.MONTH, month - 1)
            set(java.util.Calendar.DAY_OF_MONTH, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val startMillis = cal.timeInMillis
        cal.add(java.util.Calendar.MONTH, 1)
        val endMillis = cal.timeInMillis - 1

        return dao.getTransactionsByMonth(startMillis, endMillis)
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
// 🔍 UUID ile tek kayıt getirme
// --------------------------------------------------------
    suspend fun getTransactionByUuid(uuid: String): TransactionEntity? =
        withContext(Dispatchers.IO) {
            dao.getByUuid(uuid)
        }

    // --------------------------------------------------------
    // 🗑️ Hard Delete (artık varsayılan)
    // --------------------------------------------------------
    suspend fun delete(transaction: TransactionEntity) = withContext(Dispatchers.IO) {
        try {
            // 1️⃣ Local DB'den kaldır
            dao.delete(transaction)
            Log.d("TransactionRepo", "🗑️ Local silindi: ${transaction.uuid}")

            // 2️⃣ Sunucuya bildir
            val resp = api.delete(transaction.uuid)
            if (resp.isSuccessful) {
                Log.d("TransactionRepo", "✅ Remote silindi: ${transaction.uuid}")
            } else {
                Log.e("TransactionRepo", "❌ Remote delete HTTP ${resp.code()}")
            }
        } catch (e: Exception) {
            Log.e("TransactionRepo", "⚠️ Delete hata: ${e.message}", e)
        }
    }

    // --------------------------------------------------------
    // 🧹 Tümünü sil (test için)
    // --------------------------------------------------------
    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        dao.deleteAll()
        Log.w("TransactionRepo", "🧹 Tüm işlemler localden silindi.")
    }
    // --------------------------------------------------------
// 💸 Ödeme ekleme
// --------------------------------------------------------
    suspend fun addPayment(payment: com.moneyapp.android.data.db.entities.PaymentEntity) =
        withContext(Dispatchers.IO) {
            try {
                dao.insertPayment(payment)
                dao.updatePaidSum(payment.transactionUuid)
                Log.d("TransactionRepo", "💸 Ödeme eklendi: ${payment.transactionUuid}")

                // 🔁 Senkronizasyon için dirty=true ise push işlemine bırakılır
                syncRepository.pushDirtyToServer()
            } catch (e: Exception) {
                Log.e("TransactionRepo", "⚠️ Ödeme ekleme hatası: ${e.message}", e)
            }
        }

}

// --- Yardımcı: UUID otomatik üret ---
private fun ensureUuid(tx: TransactionEntity): TransactionEntity {
    return if (tx.uuid.isBlank()) tx.copy(uuid = UUID.randomUUID().toString()) else tx
}
