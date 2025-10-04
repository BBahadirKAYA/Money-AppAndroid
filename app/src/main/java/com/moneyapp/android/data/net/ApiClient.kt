package com.moneyapp.android.data.net

import com.moneyapp.android.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private fun ensureSlash(url: String) = if (url.endsWith("/")) url else "$url/"

    private val moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY)
                    )
                }
            }
            .build()
    }

    @Volatile
    private var retrofit: Retrofit = buildRetrofit(BuildConfig.BASE_URL)

    private fun buildRetrofit(base: String): Retrofit {
        return Retrofit.Builder()
            .baseUrl(ensureSlash(base))
            .client(http)
            .addConverterFactory(ScalarsConverterFactory.create())   // String (ping) için önce
            .addConverterFactory(MoshiConverterFactory.create(moshi))// JSON için sonra
            .build()
    }

    // Buradan çağrılar yapılacak
    val api: ApiService
        get() = retrofit.create(ApiService::class.java)

    // Dinamik olarak baseUrl güncelle
    fun updateBaseUrl(newUrl: String) {
        retrofit = buildRetrofit(newUrl)
    }
}
