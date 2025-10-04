package com.moneyapp.android.data.repository

import android.util.Log
import com.moneyapp.android.data.db.CategoryType
import com.moneyapp.android.data.db.TransactionDao
import com.moneyapp.android.data.db.TransactionEntity
import com.moneyapp.android.data.net.ApiClient
import com.moneyapp.android.data.net.TransactionDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class TransactionRepository(
    private val transactionDao: TransactionDao
) {

    fun getAllTransactions(): Flow<List<TransactionEntity>> = transactionDao.getAll()

    suspend fun refreshTransactions() = withContext(Dispatchers.IO) {
        try {
            val remote: List<TransactionDto> = ApiClient.api.getTransactions()
            val entities = remote.mapNotNull { it.toEntityOrNull() }

            // Room versiyonuna göre toplu insert yerine tek tek yazıyoruz
            entities.forEach { entity ->
                transactionDao.insertOrUpdate(entity)
            }

            Log.d("Repo", "Inserted/updated ${entities.size} transactions")
        } catch (t: Throwable) {
            Log.e("Repo", "refreshTransactions() failed: ${t.message}", t)
        }
    }

    /** Verilen yıl/ay içindeki yalnızca GİDER kayıtları */
    fun getMonthlyExpenses(year: Int, month: Int): Flow<List<TransactionEntity>> {
        val zone = ZoneId.systemDefault()

        val startMillis = LocalDate.of(year, month, 1)
            .atStartOfDay(zone).toInstant().toEpochMilli()

        val endMillis = LocalDate.of(year, month, 1)
            .plusMonths(1)
            .atStartOfDay(zone).toInstant().toEpochMilli() - 1

        return transactionDao.getExpensesInRange(
            startMillis = startMillis,
            endMillis = endMillis,
            expenseType = CategoryType.EXPENSE
        )
    }
}

/* -------------------- DTO → Entity Dönüşümü -------------------- */

private fun TransactionDto.toEntityOrNull(): TransactionEntity? {
    // amount "36000.00" -> kuruş
    val amountCents = try {
        amount?.let {
            BigDecimal(it).multiply(BigDecimal(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
        } ?: 0L
    } catch (e: Exception) {
        Log.w("Repo", "Bad amount: $amount", e)
        return null
    }

    // occurred_at ISO-8601 -> epochMillis
    val epochMillis = try {
        OffsetDateTime.parse(occurredAt, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            .toInstant().toEpochMilli()
    } catch (e: Exception) {
        Log.w("Repo", "Bad date: $occurredAt", e)
        return null
    }

    val txType = if (type.equals("income", ignoreCase = true))
        CategoryType.INCOME else CategoryType.EXPENSE

    // <-- BURASI DÜZELDİ: uuid yerine id kullanıyoruz
    val uid = (id ?: return null).toString()

    return TransactionEntity(
        uuid = uid,
        amount = amountCents,
        note = note,
        date = epochMillis,
        type = txType,
        dirty = false,
        deleted = false
    )
}