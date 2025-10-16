package com.moneyapp.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyapp.android.data.db.entities.TransactionEntity
import com.moneyapp.android.data.repository.TransactionRepository
import com.moneyapp.android.data.net.sync.SyncRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

class MainViewModel(
    private val repository: TransactionRepository,
    private val syncRepository: SyncRepository
) : ViewModel() {

    // 🔹 Seçili yıl/ay
    private val _selectedYearMonth = MutableStateFlow(currentYearMonth())
    val selectedYearMonth: StateFlow<Pair<Int, Int>> = _selectedYearMonth.asStateFlow()

    // 🔹 Mevcut aya göre filtreli işlemler
    val transactionsByMonth: StateFlow<List<TransactionEntity>> =
        _selectedYearMonth
            .flatMapLatest { (year, month) ->
                val yearStr = year.toString()
                val monthStr = String.format("%02d", month)
                repository.getTransactionsByMonth(yearStr, monthStr)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 🔹 Eski davranış (tüm işlemler)
    val allTransactions = repository.getAllTransactions()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // 🔹 CRUD işlemleri
    fun insert(transaction: TransactionEntity) {
        viewModelScope.launch { repository.insert(transaction) }
    }

    fun update(transaction: TransactionEntity) {
        viewModelScope.launch { repository.update(transaction) }
    }

    fun delete(transaction: TransactionEntity) {
        viewModelScope.launch { repository.delete(transaction) }
    }

    fun syncWithServer() {
        viewModelScope.launch { syncRepository.pullFromServer() }
    }

    // 🔹 Ay geçişleri
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

    companion object {
        private fun currentYearMonth(): Pair<Int, Int> {
            val cal = Calendar.getInstance()
            return cal.get(Calendar.YEAR) to (cal.get(Calendar.MONTH) + 1)
        }
    }
}
