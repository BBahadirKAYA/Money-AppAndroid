package com.moneyapp.android

import android.app.Application
import androidx.room.Room
import com.moneyapp.android.data.db.AppDatabase

class MoneyApp : Application() {

    companion object {
        lateinit var db: AppDatabase
            private set
    }

    override fun onCreate() {
        super.onCreate()

        db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "moneyapp.db"
        )
            .fallbackToDestructiveMigration(dropAllTables = true)  // <-- yeni API
            .build()
    }
}
