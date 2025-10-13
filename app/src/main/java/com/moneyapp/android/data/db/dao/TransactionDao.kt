package com.moneyapp.android.data.db.dao

import androidx.room.*
import com.moneyapp.android.data.db.entities.TransactionEntity
import com.moneyapp.android.data.db.entities.CategoryType
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    // ---- CREATE / UPDATE / DELETE ----

    /** UI tabanlı “sadece ekle” — kasti yinelenmelerde hata versin */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(tx: TransactionEntity): Long

    /** Uzak senkron / upsert */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(tx: TransactionEntity): Long

    /** Tam satır güncelleme — etkilenen satır sayısı döner */
    @Update
    suspend fun update(tx: TransactionEntity): Int

    /** Soft delete — etkilenen satır sayısı döner */
    @Query("UPDATE transactions SET deleted = 1, dirty = 1 WHERE localId = :id")
    suspend fun softDeleteById(id: Long): Int

    // ---- Queries ----

    @Query("SELECT * FROM transactions WHERE localId = :id LIMIT 1")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("""
        SELECT * FROM transactions 
        WHERE deleted = 0 
        ORDER BY date DESC, localId DESC
    """)
    fun getAll(): Flow<List<TransactionEntity>>

    /** Gün içi tüm kayıtlar (start–end) */
    @Query("""
        SELECT * FROM transactions 
        WHERE deleted = 0 
          AND date BETWEEN :dayStart AND :dayEnd
        ORDER BY localId DESC
    """)
    fun getByDay(dayStart: Long, dayEnd: Long): Flow<List<TransactionEntity>>

    @Query("""
        SELECT * FROM transactions 
        WHERE deleted = 0 
          AND type = :expenseType 
          AND date BETWEEN :startMillis AND :endMillis
        ORDER BY date DESC, localId DESC
    """)
    fun getExpensesInRange(
        startMillis: Long,
        endMillis: Long,
        expenseType: CategoryType = CategoryType.EXPENSE
    ): Flow<List<TransactionEntity>>

    @Query("""
        SELECT * FROM transactions
        WHERE deleted = 0
          AND date BETWEEN :fromMillis AND :toMillis
        ORDER BY date DESC, localId DESC
    """)
    fun streamByDate(
        fromMillis: Long,
        toMillis: Long
    ): Flow<List<TransactionEntity>>

    /** Basit arama — ileride FTS’e taşıyabiliriz */
    @Query("""
        SELECT * FROM transactions
        WHERE deleted = 0
          AND (:q IS NULL OR description LIKE '%' || :q || '%')
        ORDER BY date DESC, localId DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun search(q: String?, limit: Int, offset: Int): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE dirty = 1")
    suspend fun getDirty(): List<TransactionEntity>

    @Transaction
    suspend fun replaceAll(transactions: List<TransactionEntity>) {
        transactions.forEach { insertOrUpdate(it) }
    }

    // (Opsiyonel) Net bakiye akışı (gelir – gider), deleted=0 filtreli
    @Query("""
        SELECT COALESCE(SUM(
            CASE 
                WHEN type = 'INCOME' THEN amountCents
                WHEN type = 'EXPENSE' THEN -amountCents
                ELSE 0
            END
        ), 0) FROM transactions WHERE deleted = 0
    """)
    fun observeNetBalanceCents(): Flow<Long>
}
