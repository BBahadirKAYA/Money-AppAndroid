package com.moneyapp.android.data.db.dao

import androidx.room.*
import com.moneyapp.android.data.db.entities.TransactionEntity
import com.moneyapp.android.data.db.entities.CategoryType
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    // ---- CUD ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(tx: TransactionEntity): Long

    @Update
    suspend fun update(tx: TransactionEntity)

    @Query("UPDATE transactions SET deleted = 1, dirty = 1 WHERE localId = :id")
    suspend fun softDelete(id: Long)

    // ---- Queries ----
    @Query("SELECT * FROM transactions WHERE localId = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("""
        SELECT * FROM transactions 
        WHERE deleted = 0 
        ORDER BY date DESC, localId DESC
    """)
    fun getAll(): Flow<List<TransactionEntity>>

    @Query("""
        SELECT * FROM transactions 
        WHERE deleted = 0 
          AND date = :day 
        ORDER BY localId DESC
    """)
    fun getByDay(day: Long): Flow<List<TransactionEntity>>

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

    @Query("""
        SELECT * FROM transactions
        WHERE deleted = 0
          AND date BETWEEN :fromMillis AND :toMillis
        ORDER BY date DESC, localId DESC
    """)
    fun streamByDate(
        fromMillis: Long,
        toMillis: Long
    ): Flow<List<TransactionEntity>>

    @Query("""
        SELECT * FROM transactions
        WHERE deleted = 0
          AND (:q IS NULL OR description LIKE '%' || :q || '%')
        ORDER BY date DESC, localId DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun search(q: String?, limit: Int, offset: Int): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE dirty = 1")
    suspend fun getDirty(): List<TransactionEntity>
    @Transaction
    suspend fun replaceAll(transactions: List<TransactionEntity>) {
        transactions.forEach { insertOrUpdate(it) }
    }

}
