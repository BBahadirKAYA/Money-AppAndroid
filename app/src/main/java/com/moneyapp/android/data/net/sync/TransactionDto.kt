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
    val account_id: Long? = null,

    @SerialName("category_id")
    val category_id: Long? = null,

    @SerialName("type")
    val type: String? = null,

    @SerialName("amount")
    val amount: Double? = null,

    @SerialName("paid_sum")
    val paid_sum: Double? = null,

    @SerialName("currency")
    val currency: String? = "TRY",

    @SerialName("deleted")
    val deleted: Boolean = false,

    @SerialName("note")
    val note: String? = null,

    @SerialName("occurred_at")
    val occurred_at: String? = null,

    @SerialName("updated_at")
    val updated_at: String? = null
)
