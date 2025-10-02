package com.moneyapp.android.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE deleted = 0 ORDER BY date DESC, localId DESC")
    fun getAll(): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tx: TransactionEntity): Long

    @Query("UPDATE transactions SET deleted = 1, dirty = 1 WHERE localId = :id")
    suspend fun softDelete(id: Long)
}
