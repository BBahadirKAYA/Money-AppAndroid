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

        // 🔹 Flow collect
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.categories.collectLatest { list ->
                updateCategoryList(list)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.accounts.collectLatest { list ->
                updateAccountList(list)
            }
        }
    }

    private fun setupDropdowns() {
        categoryAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, mutableListOf())
        accountAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, mutableListOf())

        binding.etCategory.setAdapter(categoryAdapter)
        binding.etAccount.setAdapter(accountAdapter)

        binding.etCategory.setOnItemClickListener { _, _, position, _ ->
            val selected = viewModel.categories.value.getOrNull(position)
            viewModel.selectedCategory.value = selected
        }

        binding.etAccount.setOnItemClickListener { _, _, position, _ ->
            val selected = viewModel.accounts.value.getOrNull(position)
            viewModel.selectedAccount.value = selected
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

            if (amountText.isNullOrEmpty() || category == null || account == null) {
                binding.etAmount.error = if (amountText.isNullOrEmpty()) "Tutar girin" else null
                return@setOnClickListener
            }

            val amountCents = ((amountText.toDoubleOrNull() ?: 0.0) * 100).toLong()

            val transaction = TransactionEntity(
                uuid = UUID.randomUUID().toString(),
                amountCents = amountCents,
                currency = "TRY",
                type = if (isIncome) CategoryType.INCOME else CategoryType.EXPENSE,
                description = description,
                accountId = account.localId,
                categoryId = category.localId,
                date = selectedDateMillis,
                dirty = true
            )

            viewModel.insertTransaction(transaction)

            dismiss()
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
        fun newInstance() = TransactionEditBottomSheet()
    }
}
