package com.moneyapp.android.ui.main

import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.moneyapp.android.MoneyApp
import com.moneyapp.android.R
import com.moneyapp.android.ui.MainViewModel
import com.moneyapp.android.ui.MainViewModelFactory
import com.moneyapp.android.ui.TransactionEditBottomSheet
import com.moneyapp.android.ui.home.HomeFragment
import com.moneyapp.android.ui.settings.SettingsFragment
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : AppCompatActivity() {

    // 🧠 ViewModel artık class seviyesinde
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 🔹 ViewModel oluştur
        val app = application as MoneyApp
        val viewModelFactory = MainViewModelFactory(
            app.transactionRepository,
            app.categoryRepository,
            app.accountRepository,
            app.syncRepository
        )
        viewModel = ViewModelProvider(this, viewModelFactory)
            .get(MainViewModel::class.java)

        // 🔹 BottomNavigationView referansı
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottomNavigation)

        // 🔹 Varsayılan olarak HomeFragment göster
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, HomeFragment(), "home")
                .commit()
        }

        // 🔹 Sekme geçişleri (fragment yeniden yaratmadan)
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    showFragment("home", HomeFragment())
                    true
                }
                R.id.nav_settings -> {
                    showFragment("settings", SettingsFragment())
                    true
                }
                else -> false
            }
        }


        // 📅 Ay seçici bağlantısı
        val btnPrev = findViewById<ImageButton>(R.id.btnPrevMonth)
        val btnNext = findViewById<ImageButton>(R.id.btnNextMonth)
        val txtTitle = findViewById<TextView>(R.id.txtMonthTitle)

        // 🔸 Başlık güncellemesi
        lifecycleScope.launch {
            viewModel.currentMonth.collect { month ->
                val formatter = DateTimeFormatter.ofPattern("LLLL yyyy", Locale("tr"))
                txtTitle.text = month.format(formatter)
            }
        }

        // 🔸 Ok butonları
        btnPrev.setOnClickListener {
            viewModel.previousMonth()
        }

        btnNext.setOnClickListener {
            viewModel.nextMonth()
        }

        // 🔄 Kategori ve hesapları çek
        viewModel.fetchCategoriesFromServer()
        viewModel.fetchAccountsFromServer()

        // 🆕 🔄 Transaction verilerini (işlemleri) sunucudan çek
        lifecycleScope.launch {
            app.syncRepository.pullFromServer()
        }
    }

    /**
     * Fragment geçişlerini tag bazlı yapar — aynı fragment varsa yeniden yaratmaz.
     */
    private fun showFragment(tag: String, fragment: androidx.fragment.app.Fragment) {
        val fm = supportFragmentManager
        val existing = fm.findFragmentByTag(tag)
        fm.beginTransaction()
            .replace(R.id.fragmentContainer, existing ?: fragment, tag)
            .setReorderingAllowed(true)
            .commit()
    }
}
