package com.moneyapp.android.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.moneyapp.android.MoneyApp
import com.moneyapp.android.R
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel
    private lateinit var transactionAdapter: TransactionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Application sınıfından ViewModelFactory'yi alıyoruz
        val factory = (application as MoneyApp).mainViewModelFactory

        // 2. Factory'yi kullanarak ViewModel'i oluşturuyoruz
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        // 3. RecyclerView'ı ve Adapter'ı ayarlıyoruz
        setupRecyclerView()

        // 4. ViewModel'den gelen veri akışını dinleyip listeyi güncelliyoruz
        observeTransactions()
    }

    private fun setupRecyclerView() {
        transactionAdapter = TransactionAdapter()
        val recyclerView: RecyclerView = findViewById(R.id.rv_transactions)
        recyclerView.apply {
            adapter = transactionAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }
    }

    private fun observeTransactions() {
        // lifecycleScope, bu Activity'nin yaşam döngüsüne bağlı bir Coroutine başlatır.
        // Activity yok olduğunda bu Coroutine de otomatik olarak iptal edilir.
        lifecycleScope.launch {
            viewModel.transactions.collect { transactionList ->
                // Veritabanından yeni bir liste geldiğinde, Adapter'a gönderiyoruz.
                // Adapter, en verimli şekilde listeyi günceller.
                transactionAdapter.submitList(transactionList)
            }
        }
    }
}