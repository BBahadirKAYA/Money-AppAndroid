package com.moneyapp.android.ui.payments

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.moneyapp.android.databinding.BottomsheetPaymentBinding
import com.moneyapp.android.data.db.AppDatabase
import com.moneyapp.android.data.db.entities.PaymentEntity
import com.moneyapp.android.data.repository.TransactionRepository
import com.moneyapp.android.data.net.ApiClient
import com.moneyapp.android.data.net.sync.SyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class PaymentBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomsheetPaymentBinding? = null
    private val binding get() = _binding!!

    private lateinit var transactionUuid: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        transactionUuid = requireArguments().getString("uuid") ?: error("uuid yok")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomsheetPaymentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val context = requireContext()

        // 📅 Tarih varsayılan: bugünün tarihi
        val cal = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale("tr", "TR"))
        binding.etDate.setText(dateFormat.format(cal.time))

        binding.etDate.setOnClickListener {
            DatePickerDialog(
                context,
                { _, y, m, d ->
                    cal.set(y, m, d)
                    binding.etDate.setText(dateFormat.format(cal.time))
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
        // 🟢 Varsayılan tutar: kalan miktar (veya toplam tutar)
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getInstance(context)
            val tx = db.transactionDao().getByUuid(transactionUuid)
            val remaining = (tx?.amountCents ?: 0L) - (tx?.paidSum ?: 0L)
            val remainingTl = (remaining / 100.0)

            // ana thread’e dön
            CoroutineScope(Dispatchers.Main).launch {
                if (remainingTl > 0) {
                    binding.etAmount.setText("%.2f".format(remainingTl))
                }
            }
        }
        // 💾 Kaydet
        binding.btnSave.setOnClickListener {
            val amountText = binding.etAmount.text.toString().trim()
            val amountCents = (amountText.toDoubleOrNull()?.times(100))?.toLong() ?: 0L
            if (amountCents <= 0) {
                binding.etAmount.error = "Geçerli bir tutar girin"
                return@setOnClickListener
            }

            val payment = PaymentEntity(
                transactionUuid = transactionUuid,
                amountCents = amountCents,
                date = cal.timeInMillis,
                createdAtLocal = System.currentTimeMillis(),
                dirty = true
            )

            CoroutineScope(Dispatchers.IO).launch {
                val db = AppDatabase.getInstance(context)
                val api = ApiClient.getRetrofit()
                val syncRepo = SyncRepository(db.transactionDao(), api.create(com.moneyapp.android.data.net.sync.TransactionApi::class.java))
                val repo = TransactionRepository(db.transactionDao(), api.create(com.moneyapp.android.data.net.sync.TransactionApi::class.java), syncRepo)

                repo.addPayment(payment) // ➕ yeni method
                dismiss()
            }
        }

        binding.btnCancel.setOnClickListener { dismiss() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(uuid: String) = PaymentBottomSheet().apply {
            arguments = Bundle().apply { putString("uuid", uuid) }
        }
    }
}
