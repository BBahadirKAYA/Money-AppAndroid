package com.moneyapp.android.data.repository

import com.moneyapp.android.data.db.CategoryType
import com.moneyapp.android.data.db.TransactionDao
import com.moneyapp.android.data.db.TransactionEntity
import com.moneyapp.android.data.net.ApiClient // ApiClient olarak güncellendi
import com.moneyapp.android.data.net.TransactionDto
import kotlinx.coroutines.flow.Flow

class TransactionRepository(private val transactionDao: TransactionDao) {

    fun getAllTransactions(): Flow<List<TransactionEntity>> {
        return transactionDao.getAll()
    }

    suspend fun refreshTransactions() {
        try {
            // 1) getApi() yerine doğrudan tekil servis:
            val api = ApiClient.api

            // 2) Tipler netleşsin:
            val remoteTransactions: List<TransactionDto> = api.getTransactions()

            // 3) Map sonucu açıkça List<TransactionEntity> olsun:
            val entities: List<TransactionEntity> = remoteTransactions.map { dto ->
                TransactionEntity(
                    uuid = dto.uuid,
                    amount = dto.amount,
                    note = dto.note,
                    date = dto.date,
                    type = if (dto.type.equals("INCOME", ignoreCase = true))
                        CategoryType.INCOME else CategoryType.EXPENSE,
                    dirty = false,
                    deleted = false
                )
            }

            // 4) DAO çağrısı: named arg kullanma; DAO imzasına uy:
            entities.forEach { entity ->
                transactionDao.upsert(entity)
                // eğer sende 'upsert' yoksa:
                // transactionDao.insert(entity)
            }
        } catch (t: Throwable) {
            // TODO: Log/handle
        }
    }
}
