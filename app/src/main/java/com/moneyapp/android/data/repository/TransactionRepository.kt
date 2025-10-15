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
        dao.insert(transaction)
    }

    suspend fun update(transaction: TransactionEntity) = withContext(Dispatchers.IO) {
        dao.update(transaction)
    }

    suspend fun delete(transaction: TransactionEntity) = withContext(Dispatchers.IO) {
        dao.delete(transaction)
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        dao.deleteAll()
    }
}
