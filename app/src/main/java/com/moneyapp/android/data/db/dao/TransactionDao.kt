package com.moneyapp.android.data.db.dao

import androidx.room.*
import com.moneyapp.android.data.db.entities.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    // ---- CRUD ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    // ---- QUERIES ----

    // 🔹 Tüm işlemler
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAll(): Flow<List<TransactionEntity>>

    // 🔹 Tüm işlemleri anlık liste olarak döndür (Flow yerine)
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    suspend fun getAllNow(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE localId = :id LIMIT 1")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE dirty = 1")
    suspend fun getDirtyTransactions(): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(transactions: List<TransactionEntity>)

    @Query("UPDATE transactions SET dirty = 0 WHERE uuid IN (:uuids)")
    suspend fun markAllClean(uuids: List<String>)

    // 🔹 Tek veya çoklu silme işlemleri
    @Query("DELETE FROM transactions WHERE uuid = :uuid")
    suspend fun deleteByUuid(uuid: String)

    @Query("DELETE FROM transactions WHERE uuid IN (:uuids)")
    suspend fun deleteByUuids(uuids: List<String>)

    // 🔹 Sunucudan gelen kayıtları doğrudan replace et (soft delete yok)
    @Transaction
    suspend fun replaceAll(transactions: List<TransactionEntity>) {
        android.util.Log.d("TransactionDao", "🌀 replaceAll() çağrıldı: remote=${transactions.size}")

        // Local DB’yi sıfırla
        deleteAll()

        // Gelen tüm kayıtları ekle
        if (transactions.isNotEmpty()) {
            upsertAll(transactions)
            android.util.Log.d("TransactionDao", "⬆️ ${transactions.size} kayıt upsert edildi.")
        } else {
            android.util.Log.d("TransactionDao", "⚪ Sunucudan kayıt gelmedi.")
        }

        android.util.Log.d("TransactionDao", "✅ replaceAll() tamamlandı.")
    }

    // ---- 📅 AYLIK FİLTRE ----
    @Query("""
        SELECT * FROM transactions
        WHERE date BETWEEN :startMillis AND :endMillis
        ORDER BY date DESC
    """)
    fun getTransactionsByMonth(
        startMillis: Long,
        endMillis: Long
    ): Flow<List<TransactionEntity>>
}
