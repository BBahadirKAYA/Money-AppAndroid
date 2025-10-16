package com.moneyapp.android.ui

import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.moneyapp.android.MoneyApp
import com.moneyapp.android.R
import com.moneyapp.android.data.db.entities.TransactionEntity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.DateFormatSymbols
import java.util.UUID


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

        // 📅 Ay başlığı ve butonlar
        val txtMonth = findViewById<TextView>(R.id.txtMonthTitle)
        val btnPrev = findViewById<ImageButton>(R.id.btnPrevMonth)
        val btnNext = findViewById<ImageButton>(R.id.btnNextMonth)

        btnPrev.setOnClickListener { viewModel.prevMonth() }
        btnNext.setOnClickListener { viewModel.nextMonth() }

        // 🔹 Seçili ay başlığı güncelle
        lifecycleScope.launch {
            viewModel.selectedYearMonth.collectLatest { (year, month) ->
                val monthName = DateFormatSymbols().months[month - 1].replaceFirstChar { it.uppercase() }
                txtMonth.text = "$monthName $year"
            }
        }

        // 🔹 Aylık işlemleri listele
        lifecycleScope.launch {
            viewModel.transactionsByMonth.collectLatest { list ->
                adapter.submitList(list)
            }
        }

        // ✅ Açılışta senkronizasyon
        lifecycleScope.launch {
            Snackbar.make(findViewById(android.R.id.content), "⏳ Senkronize ediliyor...", Snackbar.LENGTH_SHORT).show()
            viewModel.syncWithServer()
            Snackbar.make(findViewById(android.R.id.content), "✅ Güncellendi", Snackbar.LENGTH_SHORT).show()
        }

        // ➕ Hızlı ekleme
        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener {
            val dummyTx = TransactionEntity(
                uuid = UUID.randomUUID().toString(), // ✅ benzersiz uuid ekle
                description = "Yeni işlem",
                amountCents = (1000..5000).random().toLong(),
                date = System.currentTimeMillis()
            )
            viewModel.insert(dummyTx)
        }

        // 🌐 Manuel senkron butonu
        findViewById<Button>(R.id.btnSyncServer)?.setOnClickListener {
            lifecycleScope.launch { viewModel.syncWithServer() }
        }
    }
}
