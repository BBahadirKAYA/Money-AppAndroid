package com.moneyapp.android

import android.app.Application
import com.moneyapp.android.data.db.AppDatabase
import com.moneyapp.android.data.repository.TransactionRepository
import com.moneyapp.android.ui.MainViewModelFactory

class MoneyApp : Application() {

    // Veritabanını sadece ihtiyaç olduğunda bir kere oluşturmak için 'lazy' kullanıyoruz.
    private val database by lazy { AppDatabase.getInstance(this) } // getInstance metodunu kullanıyoruz.



}