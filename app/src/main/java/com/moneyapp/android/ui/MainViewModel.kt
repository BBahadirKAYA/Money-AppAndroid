package com.moneyapp.android.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyapp.android.data.db.entities.TransactionEntity
import com.moneyapp.android.data.db.entities.AccountEntity
import com.moneyapp.android.data.repository.TransactionRepository
import com.moneyapp.android.data.repository.CategoryRepository
import com.moneyapp.android.data.repository.AccountRepository
import com.moneyapp.android.data.net.sync.SyncRepository
import com.moneyapp.android.data.net.ApiClient
import com.moneyapp.android.data.net.sync.AccountApi
import com.moneyapp.android.data.net.sync.CategoryApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.util.*

class MainViewModel(
    private val repository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val syncRepository: SyncRepository
) : ViewModel() {

    // -----------------------------------------------------------
    // 📆 Ay akışı (tek kaynak)
    // -----------------------------------------------------------
    private val _currentMonth = MutableStateFlow(YearMonth.now())
    val currentMonth: StateFlow<YearMonth> = _currentMonth.asStateFlow()

    // 🔸 Bu aya ait işlemler
    val transactionsByMonth: StateFlow<List<TransactionEntity>> =
        _currentMonth
            .flatMapLatest { ym ->
                val yearStr = ym.year.toString()
                val monthStr = String.format("%02d", ym.monthValue)
                repository.getTransactionsByMonth(yearStr, monthStr)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun nextMonth() {
        _currentMonth.value = _currentMonth.value.plusMonths(1)
    }

    fun previousMonth() {
        _currentMonth.value = _currentMonth.value.minusMonths(1)
    }
    // -----------------------------------------------------------
// 🔽 Dropdown seçimleri (Kategori / Hesap Spinner'ları için)
// -----------------------------------------------------------
    val selectedCategory = MutableStateFlow<com.moneyapp.android.data.db.entities.CategoryEntity?>(null)
    val selectedAccount = MutableStateFlow<com.moneyapp.android.data.db.entities.AccountEntity?>(null)
    // ✏️ Varolan kaydı düzenlemek için (uuid ile)
    // MainViewModel.kt

    // -----------------------------------------------------------
// ✏️ Varolan kaydı düzenlemek için (uuid ile)
// -----------------------------------------------------------
    fun updateTransactionFields(
        uuid: String,
        // DÜZELTME: amountCents yerine amount: Double kullanın.
        amount: Double,
        description: String?,
        categoryId: Long,
        accountId: Long,
        date: Long,
        type: com.moneyapp.android.data.db.entities.CategoryType
    ) {
        viewModelScope.launch {
            try {
                val existing = repository.getTransactionByUuid(uuid)
                if (existing != null) {
                    val updated = existing.copy(
                        // DÜZELTME: amountCents = amountCents yerine amount = amount
                        amount = amount,
                        description = description?.ifBlank { existing.description } ?: existing.description,
                        categoryId = categoryId.takeIf { it > 0 } ?: existing.categoryId,
                        accountId = accountId.takeIf { it > 0 } ?: existing.accountId,
                        date = date,
                        type = type,
                        dirty = true,
                        updatedAtLocal = System.currentTimeMillis()
                    )
                    repository.update(updated)
                    Log.d("MainViewModel", "🟡 İşlem düzenlendi: ${updated.uuid}")
                } else {
                    Log.w("MainViewModel", "⚠️ updateTransactionFields: kayıt bulunamadı ($uuid)")
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "❌ updateTransactionFields hata: ${e.message}", e)
            }
        }
    }



    // -----------------------------------------------------------
    // 🧩 CRUD İşlemleri
    // -----------------------------------------------------------

    fun insertTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            repository.insert(transaction.copy(dirty = true))
            Log.d("MainViewModel", "🟢 Yeni işlem eklendi: ${transaction.description}")
        }
    }

    fun updateTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            repository.update(transaction.copy(dirty = true))
            Log.d("MainViewModel", "🟡 İşlem güncellendi: ${transaction.localId}")
        }
    }

    fun deleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            repository.delete(transaction)
            Log.d("MainViewModel", "🗑️ İşlem silindi: ${transaction.uuid}")
        }
    }

    // -----------------------------------------------------------
    // 🔄 Senkronizasyon
    // -----------------------------------------------------------

    fun syncWithServer() {
        viewModelScope.launch {
            Log.d("MainViewModel", "🌐 Sunucuyla senkron başlatıldı…")
            syncRepository.pushDirtyToServer()
            syncRepository.pullFromServer()
            Log.d("MainViewModel", "✅ Senkron tamamlandı.")
        }
    }

    // -----------------------------------------------------------
    // ☁️ API'den hesap & kategori çekme
    // -----------------------------------------------------------

    private val _accounts = MutableStateFlow<List<AccountEntity>>(emptyList())
    val accounts: StateFlow<List<AccountEntity>> = _accounts.asStateFlow()

    val categories = categoryRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun fetchAccountsFromServer() {
        viewModelScope.launch {
            try {
                val api = ApiClient.getRetrofit().create(AccountApi::class.java)
                val response = api.getAccounts()
                if (response.isSuccessful) {
                    val list = response.body() ?: emptyList()
                    val mapped = list.map {
                        AccountEntity(
                            localId = it.id?.toLong() ?: 0L,
                            name = it.name ?: "Bilinmeyen",
                            deleted = false,
                            dirty = false
                        )
                    }
                    mapped.forEach { accountRepository.insert(it) }
                    _accounts.value = mapped
                    Log.d("MainViewModel", "✅ ${mapped.size} hesap yüklendi (sunucudan)")
                } else {
                    Log.e("MainViewModel", "❌ Hesap API hata: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "⚠️ Hesap API hatası: ${e.message}", e)
            }
        }
    }

    fun fetchCategoriesFromServer() {
        viewModelScope.launch {
            try {
                val api = ApiClient.getRetrofit().create(CategoryApi::class.java)
                val response = api.getCategories()
                if (response.isSuccessful) {
                    val list = response.body() ?: emptyList()
                    val mapped = list.map {
                        com.moneyapp.android.data.db.entities.CategoryEntity(
                            localId = it.id?.toLong() ?: 0L,
                            name = it.name ?: "Bilinmeyen",
                            type = when (it.type?.lowercase()) {
                                "income" -> com.moneyapp.android.data.db.entities.CategoryType.INCOME
                                else -> com.moneyapp.android.data.db.entities.CategoryType.EXPENSE
                            },
                            deleted = false,
                            dirty = false
                        )
                    }
                    Log.d("MainViewModel", "✅ ${mapped.size} kategori yüklendi (sunucudan)")
                    mapped.forEach { categoryRepository.insert(it) }
                } else {
                    Log.e("MainViewModel", "❌ Kategori API hata: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "⚠️ Kategori API hatası: ${e.message}", e)
            }
        }
    }

    // -----------------------------------------------------------
    // 💰 Ödenen / Kalan Toplamları
    // -----------------------------------------------------------
// MainViewModel.kt

// -----------------------------------------------------------
// 💰 Ödenen / Kalan Toplamları (DÜZELTİLMİŞ)
// -----------------------------------------------------------

    val totalPaid: StateFlow<Double> = transactionsByMonth
        .map { list ->
            list.filter { it.paid }
                // ✅ amountCents yerine amount kullanıldı ve / 100.0 kaldırıldı.
                .sumOf { it.amount }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalUnpaid: StateFlow<Double> = transactionsByMonth
        .map { list ->
            list.filter { !it.paid }
                // ✅ amountCents yerine amount kullanıldı ve / 100.0 kaldırıldı.
                .sumOf { it.amount }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)
}