package com.moneyapp.android.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.moneyapp.android.databinding.FragmentHomeBinding
import com.moneyapp.android.ui.TransactionAdapter
import com.moneyapp.android.ui.MainViewModel
import com.moneyapp.android.ui.MainViewModelFactory
import com.moneyapp.android.ui.TransactionEditBottomSheet
import com.moneyapp.android.data.net.ApiClient
import com.moneyapp.android.data.net.sync.*
import com.moneyapp.android.data.repository.*
import com.moneyapp.android.data.db.AppDatabase
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: TransactionAdapter

    private val viewModel: MainViewModel by activityViewModels {
        val context = requireContext().applicationContext
        val db = AppDatabase.getInstance(context)
        val api = ApiClient.getRetrofit()

        val syncRepo = SyncRepository(
            db.transactionDao(),
            api.create(TransactionApi::class.java)
        )

        val transactionRepo = TransactionRepository(
            db.transactionDao(),
            api.create(TransactionApi::class.java),
            syncRepo
        )
        val categoryRepo = CategoryRepository(db.categoryDao())
        val accountRepo = AccountRepository(db.accountDao())

        MainViewModelFactory(transactionRepo, categoryRepo, accountRepo, syncRepo)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 📋 RecyclerView bağlama
        adapter = TransactionAdapter()
        binding.rvTransactions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTransactions.adapter = adapter

        // 🧩 Uzun basınca menü aç
// 🧩 Uzun basınca menü aç
        adapter.onItemLongClick = { transaction ->
            // 💡 Eğer işlem zaten tam ödenmişse, "Ödeme Yap" seçeneğini çıkarıyoruz
            val options = if (transaction.fullyPaid) {
                arrayOf("Düzenle", "Sil")
            } else {
                arrayOf("Düzenle", "Sil", "Ödeme Yap")
            }

            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(transaction.description ?: "İşlem")
                .setItems(options) { _, which ->
                    when (options[which]) {
                        "Düzenle" -> {
                            // ✏️ Düzenleme formu aç
                            TransactionEditBottomSheet
                                .newInstance(transaction.uuid)
                                .show(parentFragmentManager, "edit_transaction")
                        }
                        "Sil" -> {
                            // 🗑 Silme onayı
                            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                .setMessage("Bu işlemi silmek istiyor musun?")
                                .setPositiveButton("Evet") { _, _ ->
                                    viewModel.deleteTransaction(transaction)
                                }
                                .setNegativeButton("Vazgeç", null)
                                .show()
                        }
                        "Ödeme Yap" -> {
                            // 💸 Ödeme Yap — PaymentBottomSheet aç
                            com.moneyapp.android.ui.payments.PaymentBottomSheet
                                .newInstance(transaction.uuid)
                                .show(parentFragmentManager, "payment_sheet")
                        }
                    }
                }
                .show()
        }



        // 🔹 Flow bağlantısı — liste
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.transactionsByMonth.collectLatest { list ->
                adapter.submitList(list)
            }
        }

        // 🔹 Ödenen toplam
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.totalPaid.collectLatest { paid ->
                binding.tvPaidTotal.text = "Ödenen: ₺${"%,.2f".format(paid)}"
            }
        }

        // 🔹 Kalan toplam
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.totalUnpaid.collectLatest { unpaid ->
                binding.tvRemainingTotal.text = "Kalan: ₺${"%,.2f".format(unpaid)}"
            }
        }

        // ➕ Yeni işlem ekleme (FAB)
        binding.fabAdd.setOnClickListener {
            TransactionEditBottomSheet.newInstance()
                .show(parentFragmentManager, "transaction_edit")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
