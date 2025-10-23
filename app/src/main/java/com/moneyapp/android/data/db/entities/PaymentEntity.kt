package com.moneyapp.android.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 💸 PaymentEntity
 * Bir işlemin (Transaction) kısmi ya da tam ödemesini temsil eder.
 *
 * - Her ödeme bir Transaction'a bağlıdır (foreign key = transactionUuid)
 * - Artık tutar TL (Double) cinsinden tutulur.
 * - dirty = true ⇒ henüz sunucuya gönderilmedi
 */

@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["transactionUuid"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["transactionUuid"])]
)
data class PaymentEntity(

    @PrimaryKey(autoGenerate = true)
    val localId: Long = 0,

    // 🔗 Transaction ile ilişki
    val transactionUuid: String,

    // 💰 Tutar (TL cinsinden)
    val amount: Double,

    // 📅 Ödeme tarihi (epoch millis)
    val paidAt: Long,

    // 🕓 Yerel oluşturulma zamanı
    val createdAtLocal: Long = System.currentTimeMillis(),

    // 🌍 Sunucuya gönderilmemişse true
    val dirty: Boolean = true
)
