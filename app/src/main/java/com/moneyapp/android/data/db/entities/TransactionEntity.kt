package com.moneyapp.android.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.moneyapp.android.data.net.sync.TransactionNetworkModel
import java.text.SimpleDateFormat
import java.util.*
import com.moneyapp.android.data.net.sync.TransactionDto



@Entity(
    tableName = "transactions",
    indices = [
        Index("date"),
        Index("accountId"),
        Index("categoryId"),
        Index(value = ["uuid"], unique = true) // 🔒 benzersiz uuid
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val localId: Long = 0L,

    val uuid: String, // ✅ artık zorunlu — backend her kayıtta gönderiyor

    val amountCents: Long,                  // kuruş cinsinden tutar (₺12.50 = 1250)
    val currency: String = "TRY",
    val type: CategoryType = CategoryType.EXPENSE, // gelir/gider tipi

    val description: String? = null,        // not veya açıklama
    val accountId: Long? = null,
    val categoryId: Long? = null,

    val date: Long,                         // epoch millis (occurred_at)
    val deleted: Boolean = false,           // soft delete flag
    val dirty: Boolean = true,              // senkron bekleyen değişiklik

    val createdAtLocal: Long = System.currentTimeMillis(),
    val updatedAtLocal: Long = System.currentTimeMillis(),
    val updatedAtServer: Long? = null       // server timestamp
)

fun TransactionEntity.toNetworkModel(): TransactionNetworkModel {
    // Laravel ISO formatı: yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'
    val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
    val isoDate = dateFormat.format(Date(this.date))

    return TransactionNetworkModel(
        uuid = this.uuid,
        account_id = this.accountId,
        category_id = this.categoryId,
        type = this.type.name.lowercase(),  // "income" / "expense"
        amount = this.amountCents / 100.0,  // kuruş → TL
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
        currency = this.currency,
        deleted = this.deleted,
        note = this.description,
        occurred_at = isoDate,
        updated_at = isoDate
    )
}

