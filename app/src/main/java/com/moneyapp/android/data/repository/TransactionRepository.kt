package com.moneyapp.android.data.repository

import com.moneyapp.android.data.db.dao.TransactionDao
import com.moneyapp.android.data.db.entities.TransactionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class TransactionRepository(
    private val dao: TransactionDao
) {

    fun getAllTransactions(): Flow<List<TransactionEntity>> = dao.getAll()

    suspend fun insert(transaction: TransactionEntity) = withContext(Dispatchers.IO) {
        // ✅ Eğer tarih alanı boş veya 0 ise, otomatik şu anki zamanı ata
        val now = System.currentTimeMillis()
        val finalTx = if (transaction.date <= 0L) {
            transaction.copy(
                date = now,
                updatedAtLocal = now
            )
        } else {
            transaction.copy(updatedAtLocal = now)
        }

        dao.insert(finalTx)
    }

    suspend fun update(transaction: TransactionEntity) = withContext(Dispatchers.IO) {
        // ✅ Güncellemede updatedAtLocal da yenilensin
        dao.update(transaction.copy(updatedAtLocal = System.currentTimeMillis()))
    }

    suspend fun delete(transaction: TransactionEntity) = withContext(Dispatchers.IO) {
        dao.delete(transaction)
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        dao.deleteAll()
    }
}
