package com.moneyapp.android.data.net.sync

import retrofit2.Response
import retrofit2.http.GET

data class AccountDto(
    val id: Int?,
    val name: String?,
    val type: String?,
    val currency_code: String?,
    val is_active: Int?
)

interface AccountApi {
    @GET("api/accounts")
    suspend fun getAccounts(): Response<List<AccountDto>>
}

 