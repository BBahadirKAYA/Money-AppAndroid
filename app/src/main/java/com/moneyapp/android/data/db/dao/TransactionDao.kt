package com.moneyapp.android.data.db.dao

import androidx.room.*
import com.moneyapp.android.data.db.entities.TransactionEntity
import kotlinx.coroutines.flow.Flow

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

    // ---- QUERIES ----

    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE localId = :id LIMIT 1")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE dirty = 1")
    suspend fun getDirtyTransactions(): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(transactions: List<TransactionEntity>)

    @Query("UPDATE transactions SET dirty = 0 WHERE uuid IN (:uuids)")
    suspend fun markAllClean(uuids: List<String>)

    @Query("UPDATE transactions SET deleted = 1 WHERE uuid = :uuid")
    suspend fun softDelete(uuid: String)

    @Transaction
    suspend fun replaceAll(transactions: List<TransactionEntity>) {
        upsertAll(transactions)
    }

    // ---- 📅 AYLIK FİLTRE ----
    @Query("""
        SELECT * FROM transactions
        WHERE strftime('%Y', datetime(date / 1000, 'unixepoch', 'localtime')) = :yearStr
          AND strftime('%m', datetime(date / 1000, 'unixepoch', 'localtime')) = :monthStr
          AND deleted = 0
        ORDER BY date DESC
    """)
    fun getTransactionsByMonth(
        yearStr: String,
        monthStr: String
    ): Flow<List<TransactionEntity>>
}
