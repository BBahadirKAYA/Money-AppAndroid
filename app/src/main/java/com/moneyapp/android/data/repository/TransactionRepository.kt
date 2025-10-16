package com.moneyapp.android.data.repository

import android.util.Log
import com.moneyapp.android.data.db.dao.TransactionDao
import com.moneyapp.android.data.db.entities.TransactionEntity
import com.moneyapp.android.data.db.entities.toNetworkModel  // 👈 eklendi
import com.moneyapp.android.data.net.sync.TransactionApi   // 👈 eklendi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext


class TransactionRepository(
    private val dao: TransactionDao,
    private val api: TransactionApi // 👈 eklendi
) {

    fun getAllTransactions(): Flow<List<TransactionEntity>> = dao.getAll()

    fun getTransactionsByMonth(yearStr: String, monthStr: String): Flow<List<TransactionEntity>> {
        return dao.getTransactionsByMonth(yearStr, monthStr)
    }

    suspend fun insert(transaction: TransactionEntity) = withContext(Dispatchers.IO) {
        val tx = ensureUuid(transaction) // 👈 eksikti, eklendi
        Log.d("MoneyApp", "Insert çağrıldı: date=${tx.date}")

        val now = System.currentTimeMillis()
        val finalTx = tx.copy(
            updatedAtLocal = now,
            date = if (tx.date <= 0L) now else tx.date,
            dirty = true
        )

        dao.insert(finalTx)

        try {
            // Sunucuya gönder
            val res = api.create(finalTx.toNetworkModel()) // 👈 doğru fonksiyon
            if (res.success) {
                dao.update(finalTx.copy(dirty = false))
                Log.d("Sync", "✅ Sunucuya gönderildi: ${finalTx.uuid}")
            } else {
                Log.e("Sync", "❌ Sunucu yanıtı başarısız: success=false")
            }
        } catch (e: Exception) {
            Log.e("Sync", "❌ Sunucuya gönderilemedi: ${e.message}")
        }
    }

    suspend fun update(transaction: TransactionEntity) = withContext(Dispatchers.IO) {
        dao.update(transaction.copy(updatedAtLocal = System.currentTimeMillis()))
    }

    suspend fun delete(transaction: TransactionEntity) = withContext(Dispatchers.IO) {
        dao.delete(transaction)
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        dao.deleteAll()
    }
}


// --- Yardımcı (UUID boşsa otomatik üret) ---
private fun ensureUuid(tx: TransactionEntity): TransactionEntity {
    return if (tx.uuid.isBlank()) tx.copy(uuid = java.util.UUID.randomUUID().toString()) else tx
}
