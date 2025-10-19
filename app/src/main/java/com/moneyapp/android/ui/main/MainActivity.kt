package com.moneyapp.android.ui.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.moneyapp.android.R
import com.moneyapp.android.ui.home.HomeFragment
import com.moneyapp.android.ui.settings.SettingsFragment
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 🔹 BottomNavigationView referansı
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottomNavigation)

        // 🔹 Varsayılan olarak HomeFragment göster
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, HomeFragment())
                .commit()
        }

        // 🔹 Sekme geçişleri
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainer, HomeFragment())
                        .commit()
                    true
                }
                R.id.nav_settings -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainer, SettingsFragment())
                        .commit()
                    true
                }
                else -> false
            }
        }

        // 🟢 FAB (Yeni işlem ekle)
        val fabAdd = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabAdd)
        fabAdd.setOnClickListener {
            com.moneyapp.android.ui.TransactionEditBottomSheet.newInstance()
                .show(supportFragmentManager, "transaction_edit")
        }

        // 🟢 ViewModel oluştur
        val app = application as com.moneyapp.android.MoneyApp
        val viewModelFactory = com.moneyapp.android.ui.MainViewModelFactory(
            app.transactionRepository,
            app.categoryRepository,
            app.accountRepository,
            app.syncRepository
        )
        val viewModel = androidx.lifecycle.ViewModelProvider(this, viewModelFactory)
            .get(com.moneyapp.android.ui.MainViewModel::class.java)

        // 🔄 Kategori ve hesapları çek
        viewModel.fetchCategoriesFromServer()
        viewModel.fetchAccountsFromServer()

        // 🆕 🔄 Transaction verilerini (işlemleri) sunucudan çek
        lifecycleScope.launch {
            app.syncRepository.pullFromServer()
        }
    }
}
