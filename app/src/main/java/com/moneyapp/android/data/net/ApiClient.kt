package com.moneyapp.android.data.net

import android.util.Log
import com.moneyapp.android.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val TAG_HTTP = "HTTP"
    private const val TAG_API = "ApiClient"

    private fun ensureSlash(url: String) = if (url.endsWith("/")) url else "$url/"

    private val moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    // --- Logging interceptor (URL + headers + body) ---
    private val httpLogger = HttpLoggingInterceptor { msg ->
        Log.d(TAG_HTTP, msg)
    }.apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
        else HttpLoggingInterceptor.Level.BASIC
    }

    // Her isteğe küçük bir debug header ekleyelim (ngrok inspect’te kolay seçersin)
    private val debugHeaderInterceptor = Interceptor { chain ->
        val req = chain.request().newBuilder()
            .header("X-Debug-Client", "MoneyAppAndroid")
            .build()
        chain.proceed(req)
    }

    private val http by lazy {
        OkHttpClient.Builder()
            .addInterceptor(debugHeaderInterceptor)
            .addInterceptor(httpLogger)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Volatile
    private var retrofit: Retrofit = buildRetrofit(BuildConfig.BASE_URL)

    @Volatile
    private var currentBaseUrl: String = ensureSlash(BuildConfig.BASE_URL)

    private fun buildRetrofit(base: String): Retrofit {
        // Base URL doğrudan dışarıdan gelen URL olsun (Laravel zaten /api içeriyor)
        val normalized = ensureSlash(base)
        Log.i(TAG_API, "Using BASE_URL = $normalized") // 👈 kontrol için Logcat’te göreceğiz

        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(http)
            // String uçlar için
            .addConverterFactory(ScalarsConverterFactory.create())
            // JSON uçlar için
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }



    // Dışarıya servis
    val api: ApiService
        get() = retrofit.create(ApiService::class.java)

    /** Aktif baseUrl’i hızlıca kontrol etmek için */
    fun currentBaseUrl(): String = currentBaseUrl

    /**
     * Dinamik olarak baseUrl güncelle.
     * - Aynı URL gelirse hiçbir şey yapmaz.
     * - Farklıysa thread-safe şekilde Retrofit'i yeniden kurar.
     */
    @Synchronized
    fun updateBaseUrl(newUrl: String) {
        val normalized = ensureSlash(newUrl)
        if (normalized == currentBaseUrl) {
            Log.i(TAG_API, "updateBaseUrl: same URL, skipping ($normalized)")
            return
        }
        retrofit = buildRetrofit(normalized)
        currentBaseUrl = normalized
        Log.i(TAG_API, "updateBaseUrl: switched to $currentBaseUrl")
    }
    fun getRetrofit(): Retrofit = retrofit
}
