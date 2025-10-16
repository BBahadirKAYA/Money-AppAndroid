package com.moneyapp.android.data.repository

import com.moneyapp.android.data.db.dao.AccountDao
import com.moneyapp.android.data.db.entities.AccountEntity
import kotlinx.coroutines.flow.Flow

class AccountRepository(private val dao: AccountDao) {

    // Tüm aktif hesaplar (deleted = 0)
    fun getAll(): Flow<List<AccountEntity>> = dao.getAll()

    // Ekle veya güncelle (Room REPLACE stratejisiyle)
    suspend fun upsert(account: AccountEntity): Long = dao.upsert(account)

    // Tekil UUID ile hesap bul
    suspend fun getByUuid(uuid: String): AccountEntity? = dao.getByUuid(uuid)

    // Soft delete (deleted=1, dirty=1)
    suspend fun softDelete(id: Long) = dao.softDelete(id)

    // Sunucuya gönderilmeyi bekleyen (dirty=1) hesaplar
    suspend fun getDirty(): List<AccountEntity> = dao.getDirty()
}
