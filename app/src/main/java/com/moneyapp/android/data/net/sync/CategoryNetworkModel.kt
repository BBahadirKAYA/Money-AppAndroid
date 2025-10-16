package com.moneyapp.android.data.net.sync

data class CategoryNetworkModel(
    val id: Int?,
    val name: String?,
    val type: String?,           // örn: "expense" / "income"
    val created_at: String?,
    val updated_at: String?
)
