package com.moneyapp.android.data.repository
import android.util.Log
import com.moneyapp.android.data.db.dao.TransactionDao
import com.moneyapp.android.data.db.entities.TransactionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext



class TransactionRepository(
private val dao: TransactionDao
) {

    fun getAllTransactions(): Flow<List<TransactionEntity>> = dao.getAll()

    // 🔹 AYLIK FİLTRELİ SORGULAMA
    fun getTransactionsByMonth(yearStr: String, monthStr: String): Flow<List<TransactionEntity>> {
        return dao.getTransactionsByMonth(yearStr, monthStr)
    }

    suspend fun insert(transaction: TransactionEntity) = withContext(Dispatchers.IO) {
        Log.d("MoneyApp", "Insert çağrıldı: date=${transaction.date}")
        if (transaction.uuid.isBlank()) {
            Log.w("MoneyApp", "⚠️ Boş UUID ile insert denemesi engellendi")
            return@withContext
        }
        dao.insert(transaction.copy(updatedAtLocal = System.currentTimeMillis()))

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
        dao.update(transaction.copy(updatedAtLocal = System.currentTimeMillis()))
    }

    suspend fun delete(transaction: TransactionEntity) = withContext(Dispatchers.IO) {
        dao.delete(transaction)
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        dao.deleteAll()
    }
}
