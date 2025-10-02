package com.moneyapp.android.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0L,
    val uuid: String? = null,
    val amount: Long,
    val note: String? = null,
    val date: String, // ISO-8601
    val dirty: Boolean = true,
    val deleted: Boolean = false,
    val updatedAtLocal: Long = System.currentTimeMillis()
)
