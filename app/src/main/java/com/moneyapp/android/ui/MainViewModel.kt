package com.moneyapp.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyapp.android.data.db.entities.TransactionEntity
import com.moneyapp.android.data.repository.TransactionRepository
import com.moneyapp.android.data.net.sync.TransactionRepository as SyncRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.YearMonth

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val repository: TransactionRepository,      // Yerel DB repository
    private val syncRepository: SyncRepository? = null  // Laravel sync opsiyonel
) : ViewModel() {

    private val currentMonth = MutableStateFlow(YearMonth.now())

    // Bu ayın giderleri (Room)
    val monthExpenses: StateFlow<List<TransactionEntity>> =
        currentMonth
            .flatMapLatest { ym ->
                repository.getMonthlyExpenses(ym.year, ym.monthValue)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Açılışta yerel verileri yenile
        viewModelScope.launch { repository.refreshTransactions() }

        // Laravel senkron opsiyonel — eğer syncRepository verilmişse çalışır
        syncRepository?.let {
            viewModelScope.launch {
                it.pushDirtyToServer()
                it.pullFromServer()
            }
        }
    }

    fun setMonth(ym: YearMonth) {
        currentMonth.value = ym
    }

    // ------------------------------------------------------------------
    // 🧩 CRUD Fonksiyonları (Insert, Update, Delete)
    // ------------------------------------------------------------------

    fun insertTransaction(tx: TransactionEntity) = viewModelScope.launch {
        repository.insertTransaction(
            tx.copy(localId = 0L, deleted = false, dirty = true)
        )
    }

    fun updateTransaction(tx: TransactionEntity) = viewModelScope.launch {
        require(tx.localId != 0L) { "updateTransaction: localId gerekli" }
        repository.updateTransaction(tx.copy(dirty = true))
    }

    fun deleteTransactionById(localId: Long) = viewModelScope.launch {
        repository.softDeleteTransaction(localId)
    }

    fun loadTransactionById(localId: Long, onLoaded: (TransactionEntity?) -> Unit) =
        viewModelScope.launch {
            val tx = repository.getTransactionById(localId)
            onLoaded(tx)
        }

    // ------------------------------------------------------------------
    // 🌐 Laravel Senkron (manuel tetikleme)
    // ------------------------------------------------------------------

    /** Kullanıcı isteğiyle Laravel senkron başlatır (push → pull). */
    fun syncWithServer() = viewModelScope.launch {
        syncRepository?.let {
            it.pushDirtyToServer()
            it.pullFromServer()
        }
    }
}
