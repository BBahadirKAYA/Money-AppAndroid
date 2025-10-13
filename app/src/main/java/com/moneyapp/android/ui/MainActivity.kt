// app/src/main/java/com/moneyapp/android/ui/MainActivity.kt
package com.moneyapp.android.ui

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.moneyapp.android.MoneyApp
import com.moneyapp.android.R
import com.moneyapp.android.update.UpdateChecker
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel
    private lateinit var transactionAdapter: TransactionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val factory = (application as MoneyApp).mainViewModelFactory
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        setupRecyclerView()
        observeTransactions()

        findViewById<Button>(R.id.btnCheckUpdate)?.setOnClickListener {
            lifecycleScope.launch { UpdateChecker.checkAndPrompt(this@MainActivity) }
        }

        findViewById<FloatingActionButton>(R.id.fabAdd)?.setOnClickListener {
            TransactionEditBottomSheet.newInstance()
                .show(supportFragmentManager, "tx_edit")
        }
    }

    private fun setupRecyclerView() {
        transactionAdapter = TransactionAdapter().apply {
            onItemClick = { tx ->
                TransactionEditBottomSheet.newInstance(tx.localId)
                    .show(supportFragmentManager, "tx_edit")
            }
        }
        val rv: RecyclerView = findViewById(R.id.rv_transactions)
        rv.adapter = transactionAdapter
        rv.layoutManager = LinearLayoutManager(this)
    }

    private fun observeTransactions() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.monthExpenses.collect { list ->
                    transactionAdapter.submitList(list)
                }
            }
        }
    }
}
