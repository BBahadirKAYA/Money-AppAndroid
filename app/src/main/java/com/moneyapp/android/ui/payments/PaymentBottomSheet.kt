package com.moneyapp.android.ui.payments

import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.moneyapp.android.databinding.BottomsheetPaymentBinding
import com.moneyapp.android.data.db.AppDatabase
import com.moneyapp.android.data.db.entities.PaymentEntity
// import'lara Repository'ler dahil değil, bu yüzden geçici olarak kaldırıyorum.
// Eğer PaymentRepository'yi kullanıyorsanız, onu uygun şekilde import etmeniz gerekir.
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
    private var selectedDate: Long = System.currentTimeMillis() // 📅 Seçilen tarihi tutmak için değişken

    // ⚠️ DİKKAT: PaymentRepository sınıfınız bu dosyada tanımlı değil.
    // Eğer bir PaymentRepository kullanıyorsanız, onu ViewModel veya Dagger/Koin aracılığıyla almanız gerekir.
    // Şimdilik kodu derlenebilir kılmak için bu kısmı yorumluyorum/düzeltiyorum.
    // Buradaki hatalı repo çağrısını, kodun geri kalanıyla uyumlu olacak şekilde düzenliyoruz.

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
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale("tr", "TR"))
        val cal = Calendar.getInstance()

        // Başlangıç tarihi olarak bugünü ayarla
        selectedDate = cal.timeInMillis

        // 📅 Tarih seçici kurulumu
        binding.etDate.setText(dateFormat.format(Date(selectedDate)))

        binding.etDate.setOnClickListener {
            DatePickerDialog(
                context,
                { _, y, m, d ->
                    cal.set(y, m, d)
                    selectedDate = cal.timeInMillis // 🌟 Seçilen tarihi kaydet
                    binding.etDate.setText(dateFormat.format(Date(selectedDate)))
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

            // ✅ DÜZELTME: amount ve paidSum artık Double olduğu için cents dönüşümü kaldırıldı.
            val remaining = (tx?.amount ?: 0.0) - (tx?.paidSum ?: 0.0)

            // ana thread’e dön
            CoroutineScope(Dispatchers.Main).launch {
                if (remaining > 0.0) {
                    // ✅ DÜZELTME: Kalan tutarı formatlama
                    val remainingToDisplay: String = if (remaining % 1.0 == 0.0) {
                        // Eğer tam sayı ise (örn: 350.0), sadece tam sayıyı göster ("350")
                        remaining.toLong().toString()
                    } else {
                        // Eğer ondalık varsa (örn: 350.50), TR formatında göster ("350,50")
                        String.format(Locale("tr", "TR"), "%.2f", remaining)
                    }

                    binding.etAmount.setText(remainingToDisplay)
                }
            }
        }

        // 💾 Kaydet
        binding.btnSave.setOnClickListener {
            Log.d("PaymentBottomSheet", "💾 Kaydet butonuna tıklandı!")
            val amountText = binding.etAmount.text.toString().trim()

            // ⚠️ DİKKAT: Bu filtreleme (sadece rakam kabul etme) ondalık ayracı (virgül/nokta) kullanmayı engeller.
            // Örneğin: "12,50" -> "1250" olur. Bu, uygulamanızda veri girişi hatasına yol açar.
            // Bu satırı Double'a dönüştürmeye uygun şekilde güncelliyorum:
            val amount = amountText.replace(',', '.').toDoubleOrNull() ?: 0.0

            if (amount <= 0.0) {
                // Tutar geçerli değilse veya sıfırsa hata göster
                binding.etAmount.error = "Geçerli bir tutar girin"
                return@setOnClickListener
            }

            // TL olarak kaydedilecek (artık *100 yok)
            val payment = PaymentEntity(
                transactionUuid = transactionUuid,
                // ✅ DÜZELTME: amount doğrudan Double olarak kullanılıyor.
                amount = amount,
                paidAt = selectedDate
            )

            // Buradaki repository ve api oluşturma mantığı ViewModel'den değil, manuel yapıldığı için
            // sadece TransactionRepository ve SyncRepository'yi kullanarak ödeme ekleme mantığını tutuyorum:
            CoroutineScope(Dispatchers.IO).launch {
                val db = AppDatabase.getInstance(context)
                val api = ApiClient.getRetrofit()
                val syncRepo = SyncRepository(
                    db.transactionDao(),
                    api.create(com.moneyapp.android.data.net.sync.TransactionApi::class.java)
                )
                val repo = TransactionRepository(
                    db.transactionDao(),
                    api.create(com.moneyapp.android.data.net.sync.TransactionApi::class.java),
                    syncRepo
                )

                try {
                    // ✅ DÜZELTME: amountCents / 100.0 yerine doğrudan payment.amount kullanıldı.
                    android.util.Log.d("PaymentBottomSheet", "💸 Ödeme gönderiliyor: ${payment.amount}₺, tx=${payment.transactionUuid}")

                    // 🌟 repo.addPayment(payment) çağrısı PaymentEntity'yi ekleyip ana TransactionEntity'yi güncellemelidir.
                    // Eğer TransactionRepository içinde Payment'i ekleyen bir metot yoksa bu satır hata verir.
                    // Veya buraya direkt PaymentDao üzerinden ekleme yapmanız gerekebilir.
                    repo.addPayment(payment)

                    android.util.Log.d("PaymentBottomSheet", "✅ Ödeme eklendi ve senkron başlatıldı.")
                } catch (e: Exception) {
                    android.util.Log.e("PaymentBottomSheet", "❌ Ödeme gönderimi hata: ${e.message}", e)
                } finally {
                    // dismiss main thread’de olmalı
                    CoroutineScope(Dispatchers.Main).launch {
                        dismiss()
                    }
                }
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