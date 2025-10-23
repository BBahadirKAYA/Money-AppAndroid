package com.moneyapp.android.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.datepicker.MaterialDatePicker
import com.moneyapp.android.databinding.BottomsheetTransactionEditBinding
import com.moneyapp.android.data.db.entities.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class TransactionEditBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomsheetTransactionEditBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    private lateinit var categoryAdapter: ArrayAdapter<String>
    private lateinit var accountAdapter: ArrayAdapter<String>

    private var selectedDateMillis: Long = System.currentTimeMillis()
    private var editingUuid: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        editingUuid = arguments?.getString("uuid")  // 📌 düzenleme modunda uuid al
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomsheetTransactionEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDropdowns()
        setupDatePicker()
        setupButtons()

        // 🔹 Dropdown verilerini dinle
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.categories.collectLatest { list -> updateCategoryList(list) }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.accounts.collectLatest { list -> updateAccountList(list) }
        }

        // 🧠 Eğer düzenleme modundaysa mevcut veriyi yükle
        editingUuid?.let { uuid ->
            val existing = viewModel.transactionsByMonth.value.find { it.uuid == uuid }
            if (existing != null) fillExistingTransaction(existing)
        }
    }

    private fun setupDropdowns() {
        categoryAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, mutableListOf())
        accountAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, mutableListOf())

        binding.etCategory.setAdapter(categoryAdapter)
        binding.etAccount.setAdapter(accountAdapter)

        binding.etCategory.setOnItemClickListener { _, _, position, _ ->
            viewModel.selectedCategory.value = viewModel.categories.value.getOrNull(position)
        }
        binding.etAccount.setOnItemClickListener { _, _, position, _ ->
            viewModel.selectedAccount.value = viewModel.accounts.value.getOrNull(position)
        }
    }

    private fun setupDatePicker() {
        val formatter = SimpleDateFormat("dd.MM.yyyy", Locale("tr"))
        binding.etDate.setText(formatter.format(Date(selectedDateMillis)))

        binding.etDate.setOnClickListener {
            val picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Tarih seç")
                .setSelection(selectedDateMillis)
                .build()

            picker.addOnPositiveButtonClickListener { millis ->
                selectedDateMillis = millis
                binding.etDate.setText(formatter.format(Date(millis)))
            }

            picker.show(parentFragmentManager, "date_picker")
        }
    }

    private fun setupButtons() {
        binding.btnCancel.setOnClickListener { dismiss() }

        binding.btnSave.setOnClickListener {
            val amountText = binding.etAmount.text?.toString()?.trim()
            val description = binding.etDesc.text?.toString()?.trim()
            val category = viewModel.selectedCategory.value
            val account = viewModel.selectedAccount.value
            val isIncome = binding.rbIncome.isChecked

            if (amountText.isNullOrEmpty()) {
                binding.etAmount.error = "Tutar girin"
                return@setOnClickListener
            }
            if (category == null) {
                binding.etCategory.error = "Kategori seçin"
                return@setOnClickListener
            }
            if (account == null) {
                binding.etAccount.error = "Hesap seçin"
                return@setOnClickListener
            }

            // ✅ DÜZELTME: Tutar artık Double olarak tutuluyor, çarpma ve Long'a çevirme kaldırıldı.
            val amount = amountText.toDoubleOrNull() ?: 0.0

            if (editingUuid == null) {
                // 🆕 Yeni kayıt
                val transaction = TransactionEntity(
                    uuid = UUID.randomUUID().toString(),
                    // ✅ DÜZELTME: amountCents yerine amount kullanıldı.
                    amount = amount,
                    currency = "TRY",
                    type = if (isIncome) CategoryType.INCOME else CategoryType.EXPENSE,
                    description = description,
                    accountId = account.localId,
                    categoryId = category.localId,
                    date = selectedDateMillis,
                    dirty = true
                )
                viewModel.insertTransaction(transaction)
            } else {
                // ✏️ Düzenleme modu
                viewModel.updateTransactionFields(
                    uuid = editingUuid!!,
                    // ✅ DÜZELTME: amountCents yerine amount kullanıldı.
                    amount = amount,
                    description = description,
                    categoryId = category.localId,
                    accountId = account.localId,
                    date = selectedDateMillis,
                    type = if (isIncome) CategoryType.INCOME else CategoryType.EXPENSE
                )
            }

            dismiss()
        }
    }

    // TransactionEditBottomSheet.kt - fillExistingTransaction() metodu

    // TransactionEditBottomSheet.kt - fillExistingTransaction() metodu

    private fun fillExistingTransaction(tx: TransactionEntity) {
        val formatter = SimpleDateFormat("dd.MM.yyyy", Locale("tr"))
        binding.etDesc.setText(tx.description ?: "")

        // 🌟 KESİN DÜZELTME: Tutar formatlama mantığı
        val amountToDisplay: String = if (tx.amount % 1.0 == 0.0) {
            // Eğer tutar tam sayı ise (örn: 10072.0), sadece tam sayıyı göster ("10072")
            // Bu, 10072,0 sorununu çözecektir.
            tx.amount.toLong().toString()
        } else {
            // Eğer tutar ondalık içeriyorsa (örn: 10072.5), TR formatında (virgüllü) 2 basamak göster.
            String.format(Locale("tr", "TR"), "%.2f", tx.amount)
        }

        binding.etAmount.setText(amountToDisplay) // Artık 10072,0 yerine 10072 görünmeli

        binding.etDate.setText(formatter.format(Date(tx.date)))
        selectedDateMillis = tx.date

        // ✅ Tür
        if (tx.type == CategoryType.INCOME) binding.rbIncome.isChecked = true
        else binding.rbExpense.isChecked = true

        // ✅ Kategori ve hesap dropdown’larını doldur ve seç
        val category = viewModel.categories.value.find { it.localId == tx.categoryId }
        val account = viewModel.accounts.value.find { it.localId == tx.accountId }

        category?.let {
            binding.etCategory.setText(it.name, false)
            viewModel.selectedCategory.value = it
        }

        account?.let {
            binding.etAccount.setText(it.name, false)
            viewModel.selectedAccount.value = it
        }
    }


    private fun updateCategoryList(list: List<CategoryEntity>) {
        categoryAdapter.clear()
        categoryAdapter.addAll(list.map { it.name })
        categoryAdapter.notifyDataSetChanged()
    }

    private fun updateAccountList(list: List<AccountEntity>) {
        accountAdapter.clear()
        accountAdapter.addAll(list.map { it.name })
        accountAdapter.notifyDataSetChanged()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(uuid: String? = null): TransactionEditBottomSheet {
            val fragment = TransactionEditBottomSheet()
            val args = Bundle()
            if (uuid != null) args.putString("uuid", uuid)
            fragment.arguments = args
            return fragment
        }
    }
}