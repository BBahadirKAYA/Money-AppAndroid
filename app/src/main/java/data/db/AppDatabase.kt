package com.moneyapp.android.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.moneyapp.android.data.db.TransactionEntity
import com.moneyapp.android.data.db.AccountEntity
import com.moneyapp.android.data.db.CategoryEntity
import com.moneyapp.android.data.db.RecurringRuleEntity


@Database(
    entities = [
        TransactionEntity::class,
        AccountEntity::class,         // <-- EKLE
        CategoryEntity::class,        // (varsa)
        RecurringRuleEntity::class    // (varsa)
    ],
    version = 2,                      // <-- 1'den 2'ye YÜKSELT
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun accountDao(): AccountDao
    // varsa diğer DAO’lar...
}

