package com.moneyapp.android.data.db.dao

import androidx.room.*
import com.moneyapp.android.data.db.entities.TransactionEntity
import kotlinx.coroutines.flow.Flow
import com.moneyapp.android.data.db.entities.PaymentEntity

@Dao
interface TransactionDao {
    // ---- CRUD ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query("SELECT * FROM transactions WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): TransactionEntity?

    // ---- QUERIES ----
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY date DESC")
    suspend fun getAllNow(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE localId = :id LIMIT 1")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE dirty = 1")
    suspend fun getDirtyTransactions(): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(transactions: List<TransactionEntity>)

    // TransactionDao.kt içinde
    // ✅ Tek versiyon (TL temelli)
// TransactionDao.kt içinde

    // ✅ Tek versiyon (TL temelli)
    @Query("""
    UPDATE transactions
    SET paidSum = (
        SELECT IFNULL(SUM(amount), 0)
        FROM payments
        WHERE transactionUuid = :uuid
    ),
    dirty = 1,
    updatedAtLocal = :timestamp
    WHERE uuid = :uuid
""")
    suspend fun updatePaidSum(uuid: String, timestamp: Long = System.currentTimeMillis())
    // ^^^ Fonksiyon imzasına "timestamp" parametresi eklendi ^^^


    @Query("DELETE FROM transactions WHERE uuid = :uuid")
    suspend fun deleteByUuid(uuid: String)

    @Query("DELETE FROM transactions WHERE uuid IN (:uuids)")
    suspend fun deleteByUuids(uuids: List<String>)
    @Query("UPDATE transactions SET dirty = 0, updatedAtLocal = :timestamp WHERE uuid IN (:uuids)")
    suspend fun markAllClean(uuids: List<String>, timestamp: Long = System.currentTimeMillis())
    @Transaction
    suspend fun replaceAll(transactions: List<TransactionEntity>) {
        android.util.Log.d("TransactionDao", "🌀 replaceAll() çağrıldı: remote=${transactions.size}")
        deleteAll()
        if (transactions.isNotEmpty()) upsertAll(transactions)
        android.util.Log.d("TransactionDao", "✅ replaceAll() tamamlandı.")
    }
    // TransactionDao.kt içinde
    @Transaction
    suspend fun insertPaymentAndUpdateSum(payment: PaymentEntity) {
        insertPayment(payment) // 1. Ödemeyi ekler
        updatePaidSum(payment.transactionUuid) // 2. paidSum'ı günceller ve dirty=1 yapar
    }

    @Query("""
        SELECT * FROM transactions
        WHERE date BETWEEN :startMillis AND :endMillis
        ORDER BY date DESC
    """)
    fun getTransactionsByMonth(
        startMillis: Long,
        endMillis: Long
    ): Flow<List<TransactionEntity>>

    @Insert
    suspend fun insertPayment(payment: PaymentEntity)
}
