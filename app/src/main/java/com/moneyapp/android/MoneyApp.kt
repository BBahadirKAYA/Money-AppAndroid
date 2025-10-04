// app/src/main/java/com/moneyapp/android/MoneyApp.kt
package com.moneyapp.android

import android.app.Application
import android.util.Log
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

        // 1) Açılışta Google Apps Script’ten güncel base_url’i çek
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Bu baseUrl sabit: yalnızca config servisine erişmek için.
                val configRetrofit = Retrofit.Builder()
                    .baseUrl("https://script.google.com/macros/s/AKfycbxq1xRouJm2HjpXZTLJaTUqmSbYsNMI4icOdKxgNFNTMR01yC9nlQj4rgpIWMf737f1LA/")
                    .addConverterFactory(MoshiConverterFactory.create())
                    .client(OkHttpClient())
                    .build()

                val configService = configRetrofit.create(ConfigService::class.java)
                val resp = configService.getBaseUrlConfig()
                val newBaseUrl = resp.base_url?.trim()

                if (!newBaseUrl.isNullOrEmpty()) {
                    Log.d("MoneyApp", "Config base_url: $newBaseUrl")
                    // 2) ApiClient’i yeni URL ile güncelle
                    ApiClient.updateBaseUrl(newBaseUrl)
                } else {
                    Log.w("MoneyApp", "Config base_url boş geldi, BuildConfig.BASE_URL ile devam.")
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
