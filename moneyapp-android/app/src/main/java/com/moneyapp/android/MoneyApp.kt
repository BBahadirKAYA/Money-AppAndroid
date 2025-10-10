package com.moneyapp.android
// imports
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

import android.app.Application
import android.util.Log
import com.moneyapp.android.data.Prefs
import com.moneyapp.android.data.db.AppDatabase
import com.moneyapp.android.data.net.ApiClient
import com.moneyapp.android.data.net.ConfigService
import com.moneyapp.android.data.repository.TransactionRepository
import com.moneyapp.android.ui.MainViewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class MoneyApp : Application() {

    private val database by lazy { AppDatabase.getInstance(this) }
    val transactionRepository by lazy { TransactionRepository(database.transactionDao()) }
    val mainViewModelFactory by lazy { MainViewModelFactory(transactionRepository) }

    override fun onCreate() {
        super.onCreate()

        val prefs = Prefs(this)

        // 0) Başlangıç URL'i: önce cihazdaki cache, yoksa BuildConfig fallback
        val startUrl = prefs.getBackendUrl() ?: BuildConfig.BASE_URL
        ApiClient.updateBaseUrl(startUrl)  // mevcut ApiClient’inle uyumlu kalalım
        Log.d("MoneyApp", "Startup base URL: $startUrl")

        // 1) Açılışta Google Apps Script’ten güncel base_url’i çek (arka planda)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Bu Retrofit SADECE config (Sheets/Apps Script) için.
                // Not: baseUrl trailing '/' ile bitmeli; ConfigService @GET ile yol ekler.
                val moshi = Moshi.Builder()
                    .add(KotlinJsonAdapterFactory())
                    .build()

                val configRetrofit = Retrofit.Builder()
                    .baseUrl("https://script.google.com/macros/s/AKfycbxq1xRouJm2HjpXZTLJaTUqmSbYsNMI4icOdKxgNFNTMR01yC9nlQj4rgpIWMf737f1LA/") // sonu / olmalı
                    .addConverterFactory(MoshiConverterFactory.create(moshi))
                    .client(OkHttpClient())
                    .build()



                val configService = configRetrofit.create(ConfigService::class.java)
                val resp = configService.getBaseUrlConfig()
                val newBaseUrl = resp.base_url?.trim()

                if (!newBaseUrl.isNullOrEmpty() && newBaseUrl.startsWith("http")) {
                    if (newBaseUrl != startUrl) {
                        Log.d("MoneyApp", "Config base_url (fresh): $newBaseUrl")
                        // 2) Cache’e yaz + ApiClient tabanını güncelle
                        prefs.setBackendUrl(newBaseUrl)
                        ApiClient.updateBaseUrl(newBaseUrl)
                    } else {
                        Log.d("MoneyApp", "Config base_url aynı: $newBaseUrl")
                    }
                } else {
                    Log.w("MoneyApp", "Config base_url boş/geçersiz; startUrl ile devam.")
                }
            } catch (e: Exception) {
                Log.e("MoneyApp", "Config servisinden base_url çekilemedi: ${e.message}", e)
            }

            // 3) (Opsiyonel ama faydalı) uygulama açılışında veriyi tazele
            try {
                transactionRepository.refreshTransactions()
            } catch (e: Exception) {
                Log.e("MoneyApp", "İlk senkronizasyon başarısız: ${e.message}", e)
            }
        }
    }

}

