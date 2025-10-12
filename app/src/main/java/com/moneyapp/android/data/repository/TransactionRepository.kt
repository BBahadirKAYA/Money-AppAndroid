package com.moneyapp.android.data.repository

import android.util.Log
import com.moneyapp.android.data.db.*
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
    fun getAllTransactions(): Flow<List<TransactionEntity>> = transactionDao.getAll()

    suspend fun refreshTransactions() = withContext(Dispatchers.IO) {
        try {
            val remote = ApiClient.api.getTransactions()
            val entities = remote.mapNotNull { it.toEntityOrNull() }
            entities.forEach { transactionDao.insertOrUpdate(it) }
            Log.d("Repo", "Inserted/updated ${entities.size} transactions")
        } catch (t: Throwable) {
            Log.e("Repo", "refreshTransactions() failed: ${t.message}", t)
        }
    }

    // Ay bazlı giderler
    fun getMonthlyExpenses(year: Int, month: Int): Flow<List<TransactionEntity>> {
        val zone = ZoneId.systemDefault()
        val start = LocalDate.of(year, month, 1)
            .atStartOfDay(zone).toInstant().toEpochMilli()
        val endExclusive = LocalDate.of(year, month, 1).plusMonths(1)
            .atStartOfDay(zone).toInstant().toEpochMilli()
        val end = endExclusive - 1
        return transactionDao.getExpensesInRange(start, end, CategoryType.EXPENSE)
    }
}

// --- Mapping yardımcıları ---
private fun TransactionDto.toEntityOrNull(): TransactionEntity? {
    // amount: "36000.00" → 3600000 (kuruş); null ise 0
    val amountCents = try {
        amount?.let {
            BigDecimal(it).multiply(BigDecimal(100))
                .setScale(0, RoundingMode.HALF_UP).longValueExact()
        } ?: 0L
    } catch (e: Exception) {
        Log.w("Repo", "Bad amount: $amount")
        return null
    }

    // occurred_at: ISO-8601 → epochMillis
    val epochMillis = try {
        // Saat içermiyorsa LocalDate.parse ile de ele alınabilir,
        // ancak ISO_OFFSET_DATE_TIME çoğu durumda yeterli.
        OffsetDateTime.parse(occurredAt).toInstant().toEpochMilli()
    } catch (e: Exception) {
        // Bazı kayıtlar sadece tarih (YYYY-MM-DD) olabilir:
        try {
            LocalDate.parse(occurredAt).atStartOfDay(ZoneId.systemDefault())
                .toInstant().toEpochMilli()
        } catch (e2: Exception) {
            Log.w("Repo", "Bad date: $occurredAt")
            return null
        }
    }

    val txType = if (type.equals("income", ignoreCase = true))
        CategoryType.INCOME else CategoryType.EXPENSE

    return TransactionEntity(
        uuid = id.toString(),
        amount = amountCents,
        note = note,
        date = epochMillis,
        type = txType,
        dirty = false,
        deleted = false
    )
}
