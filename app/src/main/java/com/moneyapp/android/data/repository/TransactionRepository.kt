package com.moneyapp.android.data.repository

import android.util.Log
import com.moneyapp.android.data.db.dao.TransactionDao
import com.moneyapp.android.data.db.entities.CategoryType
import com.moneyapp.android.data.db.entities.TransactionEntity
import com.moneyapp.android.data.net.ApiClient
import com.moneyapp.android.data.net.TransactionDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.*

class TransactionRepository(
    private val transactionDao: TransactionDao
) {
    // ---- READ ----
    fun getAllTransactions(): Flow<List<TransactionEntity>> = transactionDao.getAll()

    fun getMonthlyExpenses(year: Int, month: Int): Flow<List<TransactionEntity>> {
        val zone = ZoneId.systemDefault()
        val start = LocalDate.of(year, month, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = LocalDate.of(year, month, 1).plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return transactionDao.getExpensesInRange(start, end, CategoryType.EXPENSE)
    }

    suspend fun getTransactionById(localId: Long): TransactionEntity? = withContext(Dispatchers.IO) {
        transactionDao.getById(localId)
    }

    /** Gün içi akış (LocalDate bazlı kullanıma uygun) */
    fun getByDay(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Flow<List<TransactionEntity>> {
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return transactionDao.getByDay(start, end)
    }

    // ---- CREATE / UPDATE / DELETE ----
    suspend fun insertTransaction(tx: TransactionEntity): Long = withContext(Dispatchers.IO) {
        transactionDao.insert(
            tx.copy(localId = 0L, deleted = false, dirty = true)
        )
    }

    suspend fun updateTransaction(tx: TransactionEntity): Int = withContext(Dispatchers.IO) {
        require(tx.localId != 0L) { "updateTransaction: localId gerekli" }
        transactionDao.update(tx.copy(dirty = true))
    }

    suspend fun softDeleteTransaction(localId: Long): Int = withContext(Dispatchers.IO) {
        transactionDao.softDeleteById(localId)
    }

    // ---- REMOTE SYNC ----
    suspend fun refreshTransactions() = withContext(Dispatchers.IO) {
        try {
            val remote = ApiClient.api.getTransactions()
            val entities = remote.mapNotNull { it.toEntityOrNull() }
            // Upsert ile güncelle/ekle
            entities.forEach { transactionDao.insertOrUpdate(it) }
            Log.d("Repo", "Inserted/updated ${entities.size} transactions")
        } catch (t: Throwable) {
            Log.e("Repo", "refreshTransactions() failed: ${t.message}", t)
        }
    }
}

// --- Mapping yardımcıları ---
private fun TransactionDto.toEntityOrNull(): TransactionEntity? {
    // amount: "36000.00" -> 3_600_000 (kuruş)
    val amountCents = try {
        amount?.let {
            BigDecimal(it).multiply(BigDecimal(100))
                .setScale(0, RoundingMode.HALF_UP).longValueExact()
        } ?: 0L
    } catch (e: Exception) {
        Log.w("Repo", "Bad amount: $amount")
        return null
    }

    // occurred_at -> epochMillis (OffsetDateTime öncelikli)
    val epochMillis = try {
        OffsetDateTime.parse(occurredAt).toInstant().toEpochMilli()
    } catch (_: Exception) {
        try {
            LocalDate.parse(occurredAt).atStartOfDay(ZoneId.systemDefault())
                .toInstant().toEpochMilli()
        } catch (_: Exception) {
            Log.w("Repo", "Bad date: $occurredAt")
            return null
        }
    }

    val txType = if (type.equals("income", ignoreCase = true))
        CategoryType.INCOME else CategoryType.EXPENSE

    return TransactionEntity(
        uuid = id.toString(),
        amountCents = amountCents,
        description = note,
        date = epochMillis,
        type = txType,
        dirty = false,
        deleted = false
    )
}
