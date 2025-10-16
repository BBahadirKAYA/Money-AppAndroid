package com.moneyapp.android

import android.app.Application
import android.util.Log
import com.moneyapp.android.data.Prefs
import com.moneyapp.android.data.db.AppDatabase
import com.moneyapp.android.data.net.ApiClient
import com.moneyapp.android.data.net.sync.TransactionApi
import com.moneyapp.android.data.net.sync.SyncRepository
import com.moneyapp.android.data.repository.TransactionRepository
import com.moneyapp.android.ui.MainViewModelFactory
import kotlinx.coroutines.*

class MoneyApp : Application() {

    private lateinit var localRepo: TransactionRepository
    private lateinit var syncRepo: SyncRepository
    lateinit var mainViewModelFactory: MainViewModelFactory

    override fun onCreate() {
        super.onCreate()
        Log.d("MoneyApp", "🚀 Application class loaded")
        Log.d("MoneyApp", "🚀 onCreate başladı ✅")

        val prefs = Prefs(this)
        val startUrl = prefs.getBackendUrl() ?: BuildConfig.BASE_URL
        ApiClient.updateBaseUrl(startUrl)
        Log.d("MoneyApp", "🌐 Startup base URL: $startUrl")

        val database = AppDatabase.getInstance(this)
        val dao = database.transactionDao()

        val api = ApiClient.getRetrofit().create(TransactionApi::class.java)

        localRepo = TransactionRepository(dao, api)   // 👈 api parametresi eklendi
        syncRepo = SyncRepository(dao, api)
        mainViewModelFactory = MainViewModelFactory(localRepo, syncRepo)

        Log.d("MoneyApp", "🧩 Repositories hazır, coroutine başlatılacak")

        GlobalScope.launch(Dispatchers.IO) {
            Log.d("MoneyApp", "🧭 GlobalScope.launch girdi")
            delay(2000)
            try {
                Log.d("MoneyApp", "⏳ Laravel senkron başlatılıyor...")
                val retrofit = ApiClient.getRetrofit()
                Log.d("MoneyApp", "Retrofit base URL = ${retrofit.baseUrl()}")
                syncRepo.pullFromServer()
                Log.d("MoneyApp", "✅ Laravel senkron tamamlandı.")
            } catch (e: Exception) {
                Log.e("MoneyApp", "❌ İlk senkronizasyon hatası: ${e.message}", e)
            }
        }

        Log.d("MoneyApp", "🏁 onCreate sonu (main thread)")
    }
}
