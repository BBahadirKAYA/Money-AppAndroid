package com.moneyapp.android

import android.app.Application
import androidx.room.Room
import com.moneyapp.android.data.db.AppDatabase
import com.moneyapp.android.data.net.ApiClient
import com.moneyapp.android.data.net.sync.TransactionApi
import com.moneyapp.android.data.net.sync.AccountApi
import com.moneyapp.android.data.net.sync.CategoryApi
import com.moneyapp.android.data.net.sync.SyncRepository
import com.moneyapp.android.data.repository.TransactionRepository
import com.moneyapp.android.data.repository.AccountRepository
import com.moneyapp.android.data.repository.CategoryRepository

class MoneyApp : Application() {

    lateinit var db: AppDatabase
    lateinit var transactionRepository: TransactionRepository
    lateinit var accountRepository: AccountRepository
    lateinit var categoryRepository: CategoryRepository
    lateinit var syncRepository: SyncRepository

    override fun onCreate() {
        super.onCreate()

        // 🗄️ Room DB
        db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "moneyapp.db"
        ).fallbackToDestructiveMigration().build()

        // 🌐 API servisleri
        val retrofit = ApiClient.getRetrofit()
        val transactionApi = retrofit.create(TransactionApi::class.java)
        val accountApi = retrofit.create(AccountApi::class.java)
        val categoryApi = retrofit.create(CategoryApi::class.java)

        // 🧩 Repositories
        val transactionDao = db.transactionDao()
        val accountDao = db.accountDao()
        val syncRepo = SyncRepository(transactionDao, transactionApi)

        transactionRepository = TransactionRepository(transactionDao, transactionApi, syncRepo)
        accountRepository = AccountRepository(accountDao)
        categoryRepository = CategoryRepository(db.categoryDao())
        syncRepository = syncRepo
    }
}
