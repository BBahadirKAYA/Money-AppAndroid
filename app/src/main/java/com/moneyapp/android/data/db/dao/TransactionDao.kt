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

    // 🔹 Tüm işlemler (senkron öncesi)
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAll(): Flow<List<TransactionEntity>>

    // 🔹 Sadece görünür (silinmemiş) işlemler
    @Query("SELECT * FROM transactions WHERE deleted = 0 ORDER BY date DESC")
    fun getAllVisible(): Flow<List<TransactionEntity>>

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

    // 🔹 Soft delete: kullanıcı sildiğinde
    @Query("UPDATE transactions SET deleted = 1, dirty = 1 WHERE uuid = :uuid")
    suspend fun softDelete(uuid: String)

    // 🔹 Hard delete: sunucudan deleted=true geldiğinde
    @Query("DELETE FROM transactions WHERE uuid = :uuid")
    suspend fun deleteByUuid(uuid: String)

    // 🔹 Birden fazla kayıt silme
    @Query("DELETE FROM transactions WHERE uuid IN (:uuids)")
    suspend fun deleteByUuids(uuids: List<String>)

    // 🔹 Sunucudan gelen kayıtları replace et
    @Transaction
    suspend fun replaceAll(transactions: List<TransactionEntity>) {
        // 🪵 Log başı
        android.util.Log.d("TransactionDao", "🌀 replaceAll() çağrıldı: remote=${transactions.size}")

        // 1️⃣ Sunucuda silinmiş kayıtları tamamen kaldır
        val deletedUuids = transactions.filter { it.deleted }.mapNotNull { it.uuid }
        if (deletedUuids.isNotEmpty()) {
            android.util.Log.d("TransactionDao", "🧹 ${deletedUuids.size} kayıt sunucuda silinmiş, localden kaldırılıyor...")
            deleteByUuids(deletedUuids)
        } else {
            android.util.Log.d("TransactionDao", "✅ Sunucuda silinmiş kayıt yok.")
        }

        // 2️⃣ Silinmemiş kayıtları upsert et
        val active = transactions.filter { !it.deleted }
        if (active.isNotEmpty()) {
            android.util.Log.d("TransactionDao", "⬆️ ${active.size} aktif kayıt upsert ediliyor...")
            upsertAll(active)
        } else {
            android.util.Log.d("TransactionDao", "⚪ Sunucudan aktif kayıt gelmedi.")
        }

        // 3️⃣ Localde deleted=1 kalanları da hard delete et
        val removed = hardDeleteMarked()
        android.util.Log.d("TransactionDao", "🧽 Local deleted=1 kayıtlar temizlendi ($removed satır).")

        // 🪵 Log sonu
        android.util.Log.d("TransactionDao", "✅ replaceAll() tamamlandı.")
    }

    @Query("DELETE FROM transactions WHERE deleted = 1")
    suspend fun hardDeleteMarked(): Int


    // ---- 📅 AYLIK FİLTRE ----
    @Query("""
        SELECT * FROM transactions
        WHERE strftime('%Y', datetime(date / 1000, 'unixepoch', 'localtime')) = :yearStr
          AND strftime('%m', datetime(date / 1000, 'unixepoch', 'localtime')) = :monthStr
          AND deleted = 0
        ORDER BY date DESC
    """)
    fun getTransactionsByMonth(
        yearStr: String,
        monthStr: String
    ): Flow<List<TransactionEntity>>
}
