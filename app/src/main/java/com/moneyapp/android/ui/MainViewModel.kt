package com.moneyapp.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyapp.android.data.db.TransactionEntity
import com.moneyapp.android.data.repository.TransactionRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.ZoneId
import java.time.LocalDate

class MainViewModel(private val repository: TransactionRepository) : ViewModel() {

    private val currentMonth = MutableStateFlow(YearMonth.now())

    // Sadece bu ayın giderlerini tutacak StateFlow
    val monthExpenses: StateFlow<List<TransactionEntity>> =
        currentMonth
            .flatMapLatest { ym ->
                repository.getMonthlyExpenses(ym.year, ym.monthValue)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Açılışta uzak verileri güncelle
        viewModelScope.launch { repository.refreshTransactions() }
    }

    // Gerekirse Activity’den ay değiştirmek için:
    fun setMonth(ym: YearMonth) {
        currentMonth.value = ym
    }
}
