package com.moneyapp.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyapp.android.data.db.entities.TransactionEntity
import com.moneyapp.android.data.repository.TransactionRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.YearMonth

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val repository: TransactionRepository
) : ViewModel() {

    private val currentMonth = MutableStateFlow(YearMonth.now())

    // Bu ayın giderleri
    val monthExpenses: StateFlow<List<TransactionEntity>> =
        currentMonth
            .flatMapLatest { ym ->
                repository.getMonthlyExpenses(ym.year, ym.monthValue)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Açılışta uzak verileri güncelle
        viewModelScope.launch { repository.refreshTransactions() }
    }

    fun setMonth(ym: YearMonth) {
        currentMonth.value = ym
    }

    // Opsiyonel quality-of-life yardımcıları:
    fun nextMonth() { currentMonth.value = currentMonth.value.plusMonths(1) }
    fun previousMonth() { currentMonth.value = currentMonth.value.minusMonths(1) }
}
