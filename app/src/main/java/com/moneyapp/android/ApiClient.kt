// app/src/main/java/com/moneyapp/android/net/ApiClient.kt
package com.moneyapp.android.net

import com.moneyapp.android.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private fun ensureTrailingSlash(url: String) =
        if (url.endsWith("/")) url else "$url/"

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .apply {
                val log = HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
                addInterceptor(log)
            }
            .build()
    }

    val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(ensureTrailingSlash(BuildConfig.BASE_URL)) // örn: http://10.0.2.2:8001/
            .client(http)
            .addConverterFactory(ScalarsConverterFactory.create()) // "pong" gibi düz text
            .addConverterFactory(MoshiConverterFactory.create())   // JSON endpoint’ler
            .build()
    }

    val api: ApiService by lazy { retrofit.create(ApiService::class.java) }
}
