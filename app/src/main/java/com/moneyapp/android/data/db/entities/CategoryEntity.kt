package com.moneyapp.android.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "categories",
    indices = [Index(value = ["uuid"], unique = true)]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0L,
    val uuid: String? = null,
    val name: String,
    val type: CategoryType = CategoryType.EXPENSE,  // enum kullanıyoruz
    val dirty: Boolean = true,
    val deleted: Boolean = false,
    val updatedAtLocal: Long = System.currentTimeMillis(),
    val updatedAtServer: Long? = null
)
