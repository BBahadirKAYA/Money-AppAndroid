package com.moneyapp.android.data.db.dao

import androidx.room.*
import com.moneyapp.android.data.db.entities.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories WHERE deleted = 0 ORDER BY name ASC")
    fun getAll(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(category: CategoryEntity): Long

    @Query("UPDATE categories SET deleted = 1, dirty = 1 WHERE localId = :id")
    suspend fun softDelete(id: Long)

    @Query("SELECT * FROM categories WHERE dirty = 1")
    suspend fun getDirty(): List<CategoryEntity>
}
