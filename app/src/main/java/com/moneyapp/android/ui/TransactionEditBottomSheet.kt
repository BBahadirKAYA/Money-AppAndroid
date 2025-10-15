/*package com.moneyapp.android.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.datepicker.MaterialDatePicker
import com.moneyapp.android.R
import com.moneyapp.android.data.db.entities.CategoryType
import com.moneyapp.android.data.db.entities.TransactionEntity
import com.moneyapp.android.databinding.BottomsheetTransactionEditBinding
import java.text.SimpleDateFormat
import java.util.*

class TransactionEditBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomsheetTransactionEditBinding? = null
    private val binding get() = _binding!!

    private val vm: MainViewModel by activityViewModels()

    private var editingId: Long? = null
    private var selectedDateMillis: Long = System.currentTimeMillis()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomsheetTransactionEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun getTheme(): Int = R.style.AppBottomSheetDialog


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        editingId = arguments?.getLong(ARG_LOCAL_ID)?.takeIf { it != 0L }

        // Tarih alanı
        binding.etDate.setText(formatDate(selectedDateMillis))
        binding.etDate.setOnClickListener { showDatePicker() }

        // Amount değişimi
        binding.etAmount.doAfterTextChanged {
            binding.tilAmount.error = null
        }

        // Düzenleme ise veriyi yükle
        if (editingId != null) {
            vm.loadTransactionById(editingId!!) { tx ->
                if (tx != null) {
                    selectedDateMillis = tx.date
                    binding.etDate.setText(formatDate(tx.date))
                    binding.etAmount.setText((tx.amountCents / 100.0).toString())
                    binding.etDesc.setText(tx.description ?: "")
                    if (tx.type == CategoryType.INCOME) {
                        binding.rbIncome.isChecked = true
                    } else {
                        binding.rbExpense.isChecked = true
                    }
                    binding.btnDelete.visibility = View.VISIBLE
                }
            }
        }

        binding.btnDelete.setOnClickListener {
            editingId?.let { id ->
                vm.deleteTransactionById(id)
                dismiss()
            }
        }

        binding.btnCancel.setOnClickListener { dismiss() }

        binding.btnSave.setOnClickListener {
            val amountText = binding.etAmount.text?.toString()?.trim().orEmpty()
            val amountCents = parseAmountToCentsOrNull(amountText)
            if (amountCents == null || amountCents <= 0L) {
                binding.tilAmount.error = "Geçerli bir tutar girin"
                return@setOnClickListener
            }

            val type = if (binding.rbIncome.isChecked) CategoryType.INCOME else CategoryType.EXPENSE
            val desc = binding.etDesc.text?.toString()?.trim().takeUnless { it.isNullOrEmpty() }

            val entity = TransactionEntity(
                localId = editingId ?: 0L,
                amountCents = amountCents,
                currency = "TRY",
                type = type,
                description = desc,
                accountId = null,
                categoryId = null,
                date = selectedDateMillis,
                deleted = false,
                dirty = true
            )

            if (editingId == null) {
                vm.insertTransaction(entity)
            } else {
                vm.updateTransaction(entity)
            }
            dismiss()
        }
    }

    private fun showDatePicker() {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Tarih seç")
            .setSelection(selectedDateMillis)
            .build()
        picker.addOnPositiveButtonClickListener { millis ->
            selectedDateMillis = millis
            binding.etDate.setText(formatDate(millis))
        }
        picker.show(parentFragmentManager, "date_picker")
    }

    private fun formatDate(millis: Long): String {
        val df = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        return df.format(Date(millis))
    }

    private fun parseAmountToCentsOrNull(text: String): Long? {
        val normalized = text.replace(" ", "").replace(',', '.')
        val value = normalized.toDoubleOrNull() ?: return null
        return (value * 100).toLong()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_LOCAL_ID = "arg_local_id"
        fun newInstance(localId: Long? = null) = TransactionEditBottomSheet().apply {
            arguments = bundleOf(ARG_LOCAL_ID to (localId ?: 0L))
        }
    }
}
*/