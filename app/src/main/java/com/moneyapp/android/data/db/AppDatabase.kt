package com.moneyapp.android.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.moneyapp.android.data.db.converters.CategoryTypeConverter
import com.moneyapp.android.data.db.dao.AccountDao
import com.moneyapp.android.data.db.dao.CategoryDao
import com.moneyapp.android.data.db.dao.RecurringRuleDao
import com.moneyapp.android.data.db.dao.TransactionDao
import com.moneyapp.android.data.db.entities.AccountEntity
import com.moneyapp.android.data.db.entities.CategoryEntity
import com.moneyapp.android.data.db.entities.RecurringRuleEntity
import com.moneyapp.android.data.db.entities.TransactionEntity
import com.moneyapp.android.data.db.entities.PaymentEntity   // ✅ EKLENDİ

@Database(
    entities = [
        TransactionEntity::class,
        AccountEntity::class,
        CategoryEntity::class,
        RecurringRuleEntity::class,
        PaymentEntity::class        // ✅ yeni tablo eklendi
    ],
    version = 6,                    // ⬆️ versiyonu 1 artır (örnek: 5 → 6)
    exportSchema = false
)
@TypeConverters(CategoryTypeConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun recurringRuleDao(): RecurringRuleDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "moneyapp.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
