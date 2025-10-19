package com.moneyapp.android.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.moneyapp.android.data.net.sync.TransactionNetworkModel
import com.moneyapp.android.data.net.sync.TransactionDto
import java.text.SimpleDateFormat
import java.util.*

@Entity(
    tableName = "transactions",
    indices = [
        Index("date"),
        Index("accountId"),
        Index("categoryId"),
        Index(value = ["uuid"], unique = true)
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val localId: Long = 0L,

    val uuid: String,
    val amountCents: Long,
    val currency: String = "TRY",
    val type: CategoryType = CategoryType.EXPENSE,
    val description: String? = null,
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val date: Long,

    val dirty: Boolean = true,

    // 💰 Ödeme bilgisi (kuruş cinsinden)
    val paidSum: Long = 0L,

    val createdAtLocal: Long = System.currentTimeMillis(),
    val updatedAtLocal: Long = System.currentTimeMillis(),
    val updatedAtServer: Long? = null
) {
    // 🔹 Hesaplanan alanlar (Room’a kaydedilmez)
    val paid: Boolean
        get() = paidSum > 0L

    val fullyPaid: Boolean
        get() = paidSum >= amountCents
}

// ─────────────────────────────────────────────
// 🔄 DTO ve Network dönüşümleri
// ─────────────────────────────────────────────

fun TransactionEntity.toNetworkModel(): TransactionNetworkModel {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
    val isoDate = dateFormat.format(Date(this.date))

    return TransactionNetworkModel(
        uuid = this.uuid,
        account_id = this.accountId,
        category_id = this.categoryId,
        type = this.type.name.lowercase(),
        amount = this.amountCents / 100.0,
        occurred_at = isoDate,
        note = this.description
    )
}

fun TransactionEntity.toDto(): TransactionDto {
    val isoDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date(this.date))

    return TransactionDto(
        uuid = this.uuid,
        account_id = this.accountId,
        category_id = this.categoryId,
        type = this.type.name.lowercase(),
        amount = this.amountCents / 100.0,
        paid_sum = this.paidSum / 100.0,
        currency = this.currency,
        note = this.description,
        occurred_at = isoDate,
        updated_at = isoDate
    )
}
