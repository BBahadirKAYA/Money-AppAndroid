package com.moneyapp.android.data.net.sync

import retrofit2.Response
import retrofit2.http.GET

interface AccountApi {
    @GET("api/accounts")
    suspend fun getAccounts(): Response<List<AccountNetworkModel>>
}
