package com.moneyapp.android

import android.app.Application
import com.moneyapp.android.data.db.AppDatabase
import com.moneyapp.android.data.repository.TransactionRepository
import com.moneyapp.android.ui.MainViewModelFactory

class MoneyApp : Application() {

    private val database by lazy { AppDatabase.getInstance(this) }

    val transactionRepository by lazy { TransactionRepository(database.transactionDao()) }

    val mainViewModelFactory by lazy { MainViewModelFactory(transactionRepository) }
}