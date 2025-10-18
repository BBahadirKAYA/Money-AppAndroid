package com.moneyapp.android.ui

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.moneyapp.android.MoneyApp
import com.moneyapp.android.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.DateFormatSymbols

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels {
        (application as MoneyApp).viewModelFactory
    }

    private lateinit var adapter: TransactionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        adapter = TransactionAdapter()
        val recyclerView = findViewById<RecyclerView>(R.id.rv_transactions)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        val txtMonth = findViewById<TextView>(R.id.txtMonthTitle)
        val btnPrev = findViewById<ImageButton>(R.id.btnPrevMonth)
        val btnNext = findViewById<ImageButton>(R.id.btnNextMonth)

        btnPrev.setOnClickListener { viewModel.prevMonth() }
        btnNext.setOnClickListener { viewModel.nextMonth() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.selectedYearMonth.collectLatest { (year, month) ->
                        val monthName = DateFormatSymbols().months[month - 1].replaceFirstChar { it.uppercase() }
                        txtMonth.text = "$monthName $year"
                    }
                }
                launch {
                    viewModel.transactionsByMonth.collectLatest { list ->
                        adapter.submitList(list)
                    }
                }
                // 🟢 Toplam ödenen
                launch {
                    viewModel.totalPaid.collectLatest { total ->
                        findViewById<TextView>(R.id.tvPaidTotal).text =
                            "Ödenen: ${"%,.2f".format(total)} ₺"
                    }
                }

                // 🔴 Toplam kalan
                launch {
                    viewModel.totalUnpaid.collectLatest { total ->
                        findViewById<TextView>(R.id.tvRemainingTotal).text =
                            "Kalan: ${"%,.2f".format(total)} ₺"
                    }
                }

            }
        }

        lifecycleScope.launch {
            Snackbar.make(findViewById(android.R.id.content), "⏳ Sunucudan veri çekiliyor...", Snackbar.LENGTH_SHORT).show()
            viewModel.syncWithServer()
            viewModel.fetchAccountsFromServer()
            viewModel.fetchCategoriesFromServer()
            delay(500)
            Snackbar.make(findViewById(android.R.id.content), "✅ Güncellendi", Snackbar.LENGTH_SHORT).show()
        }

        // ➕ Yeni işlem ekleme
        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener {
            TransactionEditBottomSheet.newInstance()
                .show(supportFragmentManager, "transaction_edit")
        }

        // 🔁 Elle senkron butonu
        findViewById<Button>(R.id.btnSyncServer)?.setOnClickListener {
            lifecycleScope.launch { viewModel.syncWithServer() }
        }
// ⚙️ Güncelleme kontrol et butonu
        findViewById<Button>(R.id.btnCheckUpdate)?.setOnClickListener {
            try {
                // Eğer UpdateHelper companion object içinde statik metod sunuyorsa:
                com.moneyapp.android.updatehelper.UpdateHelper.checkForUpdates(this)

                // Eğer sınıf instance olarak çalışıyorsa şu satır geçerli:
                // val helper = com.moneyapp.android.updatehelper.UpdateHelper(this)
                // helper.checkForUpdates()

            } catch (e: Exception) {
                e.printStackTrace()
                Snackbar.make(findViewById(android.R.id.content),
                    "Güncelleme kontrolü başarısız: ${e.message ?: "Bilinmeyen hata"}",
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }

        // 🧩 Uzun basma: Düzenle / Sil menüsü
        adapter.onItemLongClick = { transaction ->
            AlertDialog.Builder(this)
                .setTitle("İşlem Seçenekleri")
                .setItems(arrayOf("Düzenle", "Sil")) { _, which ->
                    when (which) {
                        0 -> TransactionEditBottomSheet
                            .newInstance()
                            .show(supportFragmentManager, "transaction_edit")
                        1 -> confirmDelete(transaction)
                    }
                }
                .show()
        }
    }

    // 🗑 Silme onayı (soft delete)
    private fun confirmDelete(transaction: com.moneyapp.android.data.db.entities.TransactionEntity) {
        AlertDialog.Builder(this)
            .setTitle("İşlemi silmek istiyor musunuz?")
            .setMessage("Bu işlem silinecek ve senkronizasyonda kaldırılacak.")
            .setPositiveButton("Sil") { _, _ ->
                lifecycleScope.launch {
                    viewModel.softDelete(transaction)
                    Snackbar.make(findViewById(android.R.id.content), "🗑 Silindi", Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }
}
