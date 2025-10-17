package com.moneyapp.android

import android.app.Application
import com.moneyapp.android.data.db.AppDatabase
import com.moneyapp.android.data.net.ApiClient
import com.moneyapp.android.data.net.sync.TransactionApi
import com.moneyapp.android.data.net.sync.SyncRepository
import com.moneyapp.android.data.repository.*

class MoneyApp : Application() {
    lateinit var viewModelFactory: com.moneyapp.android.ui.MainViewModelFactory
        private set

    override fun onCreate() {
        super.onCreate()

        val db = AppDatabase.getInstance(this)

        val transactionDao = db.transactionDao()
        val categoryDao = db.categoryDao()
        val accountDao = db.accountDao()

        val retrofit = ApiClient.getRetrofit()
        val transactionApi = retrofit.create(TransactionApi::class.java)



        val categoryRepo = CategoryRepository(categoryDao)
        val accountRepo = AccountRepository(accountDao)
        val syncRepo = SyncRepository(transactionDao, transactionApi) // ✅ EKLENDİ
        val transactionRepo = TransactionRepository(transactionDao, transactionApi, syncRepo)
        viewModelFactory = com.moneyapp.android.ui.MainViewModelFactory(
            transactionRepo,
            categoryRepo,
            accountRepo,
            syncRepo // ✅ EKLENDİ
        )
    }
}
