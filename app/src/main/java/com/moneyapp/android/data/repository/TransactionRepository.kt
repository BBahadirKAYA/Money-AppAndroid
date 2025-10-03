package com.moneyapp.android.data.repository

import com.moneyapp.android.data.db.TransactionDao
import com.moneyapp.android.data.db.TransactionEntity
import kotlinx.coroutines.flow.Flow

class TransactionRepository(private val transactionDao: TransactionDao) {

    /**
     * Kullanıcının arayüzde göreceği, silinmemiş tüm transaction'ları Flow olarak döndürür.
     */
    fun getAllTransactions(): Flow<List<TransactionEntity>> {
        return transactionDao.getAll()
    }

    /**
     * Yeni bir transaction ekler veya var olanı günceller.
     */
    suspend fun saveTransaction(transaction: TransactionEntity) {
        transactionDao.upsert(transaction)
    }

    /**
     * Belirtilen ID'ye sahip transaction'ı geçici olarak siler (soft delete).
     */
    suspend fun deleteTransaction(id: Long) {
        transactionDao.softDelete(id)
    }

    /**
     * Sunucu ile senkronize edilmesi gereken, "kirli" (dirty) olarak işaretlenmiş
     * tüm kayıtları döndürür.
     */
    suspend fun getDirtyTransactions(): List<TransactionEntity> {
        return transactionDao.getDirty()
    }
}