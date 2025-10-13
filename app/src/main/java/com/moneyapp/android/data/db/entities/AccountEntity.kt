package com.moneyapp.android.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "accounts",
    indices = [Index(value = ["uuid"], unique = true)]
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0L,
    val uuid: String? = null,
    val name: String,
    val balance: Long = 0L,            // kuruş cinsinden (₺12.50 -> 1250)
    val dirty: Boolean = true,         // senkron bekleyen kayıt
    val deleted: Boolean = false,      // soft delete
    val updatedAtLocal: Long = System.currentTimeMillis(),
    val updatedAtServer: Long? = null  // senkron sonrası sunucu timestamp
)
