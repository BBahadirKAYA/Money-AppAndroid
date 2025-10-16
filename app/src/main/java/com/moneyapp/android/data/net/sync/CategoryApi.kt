package com.moneyapp.android.data.net.sync

import retrofit2.Response
import retrofit2.http.GET

interface CategoryApi {
    @GET("ajax/categories")
    suspend fun getCategories(): Response<List<CategoryNetworkModel>>
}
