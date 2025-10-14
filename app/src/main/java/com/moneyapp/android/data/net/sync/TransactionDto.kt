package com.moneyapp.android.data.net.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Laravel API ile veri alışverişi için DTO (Data Transfer Object)
 *
 * Bu model TransactionEntity'nin sadeleştirilmiş bir yansımasıdır.
 * Backend tarafında JSON şu şekilde beklenir:
 * {
 *   "uuid": "tx-123",
 *   "amount": 150.50,
 *   "currency": "TRY",
 *   "deleted": false,
 *   "updated_at": "2025-10-14T08:00:00Z"
 * }
 */

@Serializable
data class TransactionDto(
    @SerialName("uuid")
    val uuid: String,

    @SerialName("amount")
    val amount: Double,

    @SerialName("currency")
    val currency: String = "TRY",

    @SerialName("deleted")
    val deleted: Boolean = false,

    @SerialName("updated_at")
    val updatedAt: String
)
