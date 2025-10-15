package com.moneyapp.android.ui

import android.os.Bundle
import android.widget.Button
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.moneyapp.android.MoneyApp
import com.moneyapp.android.R
import com.moneyapp.android.data.db.entities.TransactionEntity
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: TransactionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val factory = (application as MoneyApp).mainViewModelFactory
        viewModel = androidx.lifecycle.ViewModelProvider(this, factory)[MainViewModel::class.java]

        adapter = TransactionAdapter()
        val recyclerView = findViewById<RecyclerView>(R.id.rv_transactions)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        lifecycleScope.launch {
            viewModel.allTransactions.collect {
                adapter.submitList(it)
            }
        }

        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener {
            val dummyTx = TransactionEntity(
                description = "Yeni işlem",
                amountCents = (1000..5000).random().toLong(),
                date = System.currentTimeMillis()
            )
            viewModel.insert(dummyTx)
        }

        findViewById<Button>(R.id.btnSyncServer)?.setOnClickListener {
            lifecycleScope.launch {
                viewModel.syncWithServer()
            }
        }
    }
}
