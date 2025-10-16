package com.moneyapp.android

import android.app.Application
import android.util.Log
import com.moneyapp.android.data.Prefs
import com.moneyapp.android.data.db.AppDatabase
import com.moneyapp.android.data.net.ApiClient
import com.moneyapp.android.data.net.sync.TransactionApi
import com.moneyapp.android.data.net.sync.SyncRepository
import com.moneyapp.android.data.repository.*
import com.moneyapp.android.data.db.entities.*
import com.moneyapp.android.ui.MainViewModelFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull

class MoneyApp : Application() {

    private lateinit var localRepo: TransactionRepository
    private lateinit var syncRepo: SyncRepository
    private lateinit var categoryRepo: CategoryRepository
    private lateinit var accountRepo: AccountRepository

    lateinit var mainViewModelFactory: MainViewModelFactory

    override fun onCreate() {
        super.onCreate()
        Log.d("MoneyApp", "🚀 Application class loaded")

        val prefs = Prefs(this)
        val startUrl = prefs.getBackendUrl() ?: BuildConfig.BASE_URL
        ApiClient.updateBaseUrl(startUrl)

        val database = AppDatabase.getInstance(this)
        val dao = database.transactionDao()

        val api = ApiClient.getRetrofit().create(TransactionApi::class.java)

        // 🔹 Repository'ler
        localRepo = TransactionRepository(dao, api)
        syncRepo = SyncRepository(dao, api)
        categoryRepo = CategoryRepository(database.categoryDao())
        accountRepo = AccountRepository(database.accountDao())

        mainViewModelFactory = MainViewModelFactory(localRepo, categoryRepo, accountRepo, syncRepo)

        // 🔹 Örnek kategori & hesap kayıtlarını oluştur
        GlobalScope.launch(Dispatchers.IO) {
            val catDao = database.categoryDao()
            val accDao = database.accountDao()

            val cats = catDao.getAll().firstOrNull() ?: emptyList()
            val accs = accDao.getAll().firstOrNull() ?: emptyList()

            if (cats.isEmpty()) {
                catDao.upsert(CategoryEntity(name = "Genel Gider", type = CategoryType.EXPENSE))
                catDao.upsert(CategoryEntity(name = "Genel Gelir", type = CategoryType.INCOME))
                Log.d("MoneyApp", "🟢 Varsayılan kategoriler eklendi")
            }

            if (accs.isEmpty()) {
                accDao.upsert(AccountEntity(name = "Nakit"))
                accDao.upsert(AccountEntity(name = "Banka"))
                Log.d("MoneyApp", "🟢 Varsayılan hesaplar eklendi")
            }
        }

        Log.d("MoneyApp", "🏁 onCreate tamamlandı ✅")
    }
}
