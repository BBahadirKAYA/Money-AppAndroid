package com.moneyapp.android.data.net.sync

import com.squareup.moshi.Json

data class AccountNetworkModel(
    val id: Long,
    val name: String,
    val type: String?,
    @Json(name = "currency_code") val currencyCode: String? = "TRY",
    @Json(name = "is_active") val isActive: Int? = 1,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null
)
