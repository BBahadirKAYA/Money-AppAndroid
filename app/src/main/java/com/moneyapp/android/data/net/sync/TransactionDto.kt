package com.moneyapp.android.data.net.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TransactionDto(
    @SerialName("id")
    val id: Long? = null,

    @SerialName("uuid")
    val uuid: String? = null,

    @SerialName("account_id")
    val accountId: Long? = null,

    @SerialName("category_id")
    val categoryId: Long? = null,

    @SerialName("type")
    val type: String? = null,

    @SerialName("amount")
    val amount: Double? = null,

    @SerialName("currency")
    val currency: String? = "TRY",

    @SerialName("deleted")
    val deleted: Boolean = false,

    @SerialName("note")
    val note: String? = null,

    @SerialName("occurred_at")
    val occurredAt: String? = null,

    @SerialName("updated_at")
    val updatedAt: String? = null
)
