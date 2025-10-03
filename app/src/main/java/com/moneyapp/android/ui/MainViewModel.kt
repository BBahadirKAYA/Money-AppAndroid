package com.moneyapp.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyapp.android.data.db.TransactionEntity
import com.moneyapp.android.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(private val repository: TransactionRepository) : ViewModel() {

    // Arayüzün dinleyeceği transaction listesi için StateFlow
    private val _transactions = MutableStateFlow<List<TransactionEntity>>(emptyList())
    val transactions: StateFlow<List<TransactionEntity>> = _transactions.asStateFlow()

    init {
        // ViewModel ilk oluşturulduğunda veritabanından verileri çekmeye başlar
        fetchTransactions()
    }

    private fun fetchTransactions() {
        // viewModelScope, bu coroutine'in ViewModel yaşadığı sürece aktif olmasını sağlar.
        // Böylece ekran kapandığında veya uygulama alta alındığında hafıza sızıntısı olmaz.
        viewModelScope.launch {
            repository.getAllTransactions().collect { transactionList ->
                _transactions.value = transactionList
            }
        }
    }
}