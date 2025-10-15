package com.moneyapp.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyapp.android.data.db.entities.TransactionEntity
import com.moneyapp.android.data.repository.TransactionRepository
import com.moneyapp.android.data.net.sync.SyncRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class MainViewModel(
    private val repository: TransactionRepository,
    private val syncRepository: SyncRepository
) : ViewModel() {

    val allTransactions = repository.getAllTransactions()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun insert(transaction: TransactionEntity) {
        viewModelScope.launch {
            repository.insert(transaction)
        }
    }

    fun update(transaction: TransactionEntity) {
        viewModelScope.launch {
            repository.update(transaction)
        }
    }

    fun delete(transaction: TransactionEntity) {
        viewModelScope.launch {
            repository.delete(transaction)
        }
    }

    fun syncWithServer() {
        viewModelScope.launch {
            syncRepository.pullFromServer()
        }
    }
}
