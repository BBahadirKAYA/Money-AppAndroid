package com.moneyapp.android.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts WHERE deleted = 0 ORDER BY name ASC")
    fun getAll(): Flow<List<AccountEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: AccountEntity): Long
    @Query("SELECT * FROM accounts WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE dirty = 1")
    suspend fun getDirty(): List<AccountEntity>

    @Query("UPDATE accounts SET deleted = 1, dirty = 1 WHERE localId = :id")
    suspend fun softDelete(id: Long)

}
