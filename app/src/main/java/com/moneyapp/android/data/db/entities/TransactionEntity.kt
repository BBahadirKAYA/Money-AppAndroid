package com.moneyapp.android.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [
        Index("date"),
        Index("accountId"),
        Index("categoryId")
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0L,

    val uuid: String? = null,               // opsiyonel: sunucu eşleştirmesi
    val amountCents: Long,                  // kuruş cinsinden tutar (₺12.50 = 1250)
    val currency: String = "TRY",
    val type: CategoryType = CategoryType.EXPENSE, // gelir/gider tipi

    val description: String? = null,
    val accountId: Long? = null,
    val categoryId: Long? = null,

    val date: Long,                         // epoch millis
    val deleted: Boolean = false,           // soft delete
    val dirty: Boolean = true,              // senkron bekleyen değişiklik

    val createdAtLocal: Long = System.currentTimeMillis(),
    val updatedAtLocal: Long = System.currentTimeMillis(),
    val updatedAtServer: Long? = null
)
