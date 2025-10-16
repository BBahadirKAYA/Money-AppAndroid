package com.moneyapp.android.data.repository

import com.moneyapp.android.data.db.dao.CategoryDao
import com.moneyapp.android.data.db.entities.CategoryEntity
import kotlinx.coroutines.flow.Flow

class CategoryRepository(private val dao: CategoryDao) {

    fun getAll(): Flow<List<CategoryEntity>> = dao.getAll()

    suspend fun insert(category: CategoryEntity): Long = dao.upsert(category) // ✅ eklendi

    suspend fun upsert(category: CategoryEntity): Long = dao.upsert(category)

    suspend fun softDelete(id: Long) = dao.softDelete(id)

    suspend fun getDirty(): List<CategoryEntity> = dao.getDirty()
}
