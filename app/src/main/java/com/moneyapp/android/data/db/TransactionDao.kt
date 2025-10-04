package com.moneyapp.android.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Query("""
        SELECT * FROM transactions 
        WHERE deleted = 0 
          AND type = :expenseType 
          AND date BETWEEN :startMillis AND :endMillis
        ORDER BY date DESC, localId DESC
    """)
    fun getExpensesInRange(
        startMillis: Long,
        endMillis: Long,
        expenseType: CategoryType = CategoryType.EXPENSE
    ): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE deleted = 0 ORDER BY date DESC, localId DESC")
    fun getAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE deleted = 0 AND date = :day ORDER BY localId DESC")
    fun getByDay(day: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE dirty = 1")
    suspend fun getDirty(): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(tx: TransactionEntity): Long


    @Query("UPDATE transactions SET deleted = 1, dirty = 1 WHERE localId = :id")
    suspend fun softDelete(id: Long)
}

