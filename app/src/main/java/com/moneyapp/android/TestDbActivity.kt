package com.moneyapp.android

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.moneyapp.android.data.db.TransactionEntity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.moneyapp.android.data.db.AccountEntity
import android.widget.Toast



class TestDbActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test_db)

        val btnAdd = findViewById<Button>(R.id.btnAdd)
        val tvTransactions = findViewById<TextView>(R.id.tvTransactions)

        val dao = MoneyApp.db.transactionDao()
        val btnAddAccount = findViewById<Button>(R.id.btnAddAccount)
        val accountDao = MoneyApp.db.accountDao()
        val tvAccounts = findViewById<TextView>(R.id.tvAccounts)

        // Listeyi canlı dinle
        lifecycleScope.launch {
            accountDao.getAll().collectLatest { list ->
                val text = buildString { list.forEach { append("• ${it.name} (bal=${it.balance})\n") } }
                tvAccounts.text = text.ifEmpty { "Hesap yok" }
            }
        }

        btnAddAccount.setOnClickListener {
            lifecycleScope.launch {
                val id = accountDao.upsert(AccountEntity(name = "Hesap ${(100..999).random()}"))
                Toast.makeText(this@TestDbActivity, "Eklendi id=$id", Toast.LENGTH_SHORT).show()
            }
        }


        // Listeyi canlı dinle
        lifecycleScope.launch {
            dao.getAll().collectLatest { list ->
                val text = buildString {
                    list.forEach { append("• ${it.date} → ${it.amount}₺\n") }
                }
                tvTransactions.text = text.ifEmpty { "Henüz kayıt yok" }
            }
        }

        btnAdd.setOnClickListener {
            lifecycleScope.launch {
                val tx = TransactionEntity(
                    amount = (100..999).random().toLong(),
                    note = "Offline test",
                    date = "2025-09-29"
                )
                dao.upsert(tx)
            }
        }
    }
}
