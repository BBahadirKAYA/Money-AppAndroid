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
import java.util.*

class MainViewModel(
    private val repository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val syncRepository: SyncRepository
) : ViewModel() {

    private val _selectedYearMonth = MutableStateFlow(currentYearMonth())
    val selectedYearMonth: StateFlow<Pair<Int, Int>> = _selectedYearMonth.asStateFlow()

    // 🔹 Kategoriler
    val categories = categoryRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // 🔹 Hesaplar
    private val _accounts = MutableStateFlow<List<AccountEntity>>(emptyList())
    val accounts: StateFlow<List<AccountEntity>> = _accounts.asStateFlow()

    // 🔹 Dropdown seçimleri
    val selectedCategory = MutableStateFlow<com.moneyapp.android.data.db.entities.CategoryEntity?>(null)
    val selectedAccount = MutableStateFlow<AccountEntity?>(null)

    // 🔹 Aylık işlemler
    val transactionsByMonth: StateFlow<List<TransactionEntity>> =
        _selectedYearMonth
            .flatMapLatest { (year, month) ->
                val yearStr = year.toString()
                val monthStr = String.format("%02d", month)
                repository.getTransactionsByMonth(yearStr, monthStr)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    fun softDelete(transaction: TransactionEntity) {
        viewModelScope.launch {
            repository.softDelete(transaction)
            Log.d("MainViewModel", "🔴 Soft delete uygulandı: ${transaction.uuid}")
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
    // 📆 Ay geçişleri
    // -----------------------------------------------------------

    fun nextMonth() {
        val (y, m) = _selectedYearMonth.value
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, y)
            set(Calendar.MONTH, m - 1)
            add(Calendar.MONTH, 1)
        }
        _selectedYearMonth.value = cal.get(Calendar.YEAR) to (cal.get(Calendar.MONTH) + 1)
    }

    fun prevMonth() {
        val (y, m) = _selectedYearMonth.value
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, y)
            set(Calendar.MONTH, m - 1)
            add(Calendar.MONTH, -1)
        }
        _selectedYearMonth.value = cal.get(Calendar.YEAR) to (cal.get(Calendar.MONTH) + 1)
    }

    // -----------------------------------------------------------
    // ☁️ API'den hesap & kategori çekme
    // -----------------------------------------------------------

    fun fetchAccountsFromServer() {
        viewModelScope.launch {
            try {
                val api = ApiClient.getRetrofit().create(AccountApi::class.java)
                val response = api.getAccounts()
                if (response.isSuccessful) {
                    val list = response.body() ?: emptyList()
                    _accounts.value = list.map {
                        AccountEntity(
                            localId = it.id?.toLong() ?: 0L,
                            name = it.name ?: "Bilinmeyen",
                            deleted = false,
                            dirty = false
                        )
                    }
                    Log.d("MainViewModel", "✅ ${list.size} hesap yüklendi (sunucudan)")
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

    // Toplam ödenen tutar
    val totalPaid: StateFlow<Double> = transactionsByMonth
        .map { list ->
            list.filter { it.paid } // veya it.paid == true
                .sumOf { it.amountCents } / 100.0
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Toplam kalan tutar
    val totalUnpaid: StateFlow<Double> = transactionsByMonth
        .map { list ->
            list.filter { !it.paid } // veya it.paid == false
                .sumOf { it.amountCents } / 100.0
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    companion object {
        private fun currentYearMonth(): Pair<Int, Int> {
            val cal = Calendar.getInstance()
            return cal.get(Calendar.YEAR) to (cal.get(Calendar.MONTH) + 1)
        }
    }
}
