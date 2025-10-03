// app/src/main/java/com/moneyapp/android/net/ApiClient.kt
package com.moneyapp.android.net

import com.moneyapp.android.BuildConfig
import com.moneyapp.android.data.net.ApiService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object ApiClient {

    private fun ensureTrailingSlash(url: String) =
        if (url.endsWith("/")) url else "$url/"

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private val http: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        if (BuildConfig.DEBUG) {
            val log = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            builder.addInterceptor(log)
        }

        builder.build()
    }

    val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(ensureTrailingSlash(BuildConfig.BASE_URL))
            .client(http)
            .addConverterFactory(ScalarsConverterFactory.create())      // düz metin
            .addConverterFactory(MoshiConverterFactory.create(moshi))   // JSON
            .build()
    }

    val api: ApiService by lazy { retrofit.create(ApiService::class.java) }
}
