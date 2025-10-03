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

        // Application sınıfından ViewModelFactory'yi alıyoruz
        val factory = (application as MoneyApp).mainViewModelFactory

        // Factory'yi kullanarak ViewModel'i oluşturuyoruz
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        // RecyclerView'ı ve Adapter'ı ayarlıyoruz
        setupRecyclerView()

        // ViewModel'den gelen veri akışını dinleyip listeyi güncelliyoruz
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
        lifecycleScope.launch {
            viewModel.transactions.collect { transactionList ->
                // Adapter'a yeni listeyi gönderiyoruz, o da ekranı güncelliyor
                transactionAdapter.submitList(transactionList)
            }
        }
    }
}