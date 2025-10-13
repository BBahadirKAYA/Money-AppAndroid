package com.moneyapp.android.data.db.dao

import androidx.room.*
import com.moneyapp.android.data.db.entities.RecurringRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringRuleDao {

    @Query("SELECT * FROM recurring_rules WHERE deleted = 0 ORDER BY localId ASC")
    fun getAll(): Flow<List<RecurringRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: RecurringRuleEntity): Long

    @Query("UPDATE recurring_rules SET deleted = 1, dirty = 1 WHERE localId = :id")
    suspend fun softDelete(id: Long)

    @Query("SELECT * FROM recurring_rules WHERE dirty = 1")
    suspend fun getDirty(): List<RecurringRuleEntity>
}
