package com.moneyapp.android.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["uuid"], unique = true)]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0L,
    val uuid: String? = null,
    val amount: Long,
    val note: String? = null,
    val date: Long, // ✅ Kotlin tip düzeltildi
    val type: CategoryType,
    val dirty: Boolean = true,
    val deleted: Boolean = false,
    val updatedAtLocal: Long = System.currentTimeMillis()
)
