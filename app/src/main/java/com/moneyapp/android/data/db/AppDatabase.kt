package com.moneyapp.android.data.db
import androidx.room.TypeConverters
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TransactionEntity::class,
        AccountEntity::class,
        CategoryEntity::class,
        RecurringRuleEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)   // 👈 ekle
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun recurringRuleDao(): RecurringRuleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "moneyapp.db"
                )
                    .fallbackToDestructiveMigration() // ✅ test/dev için güvenli
                    //.addMigrations(MIGRATION_1_2)   // ➡️ ileride migration eklenebilir
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
