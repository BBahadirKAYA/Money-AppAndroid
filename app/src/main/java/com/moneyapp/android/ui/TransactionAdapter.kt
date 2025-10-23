package com.moneyapp.android.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.moneyapp.android.R
import com.moneyapp.android.data.db.entities.TransactionEntity
import com.moneyapp.android.data.db.entities.CategoryType
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class TransactionAdapter :
    ListAdapter<TransactionEntity, TransactionAdapter.TransactionViewHolder>(TransactionDiffCallback()) {

    // ✅ Tek tıklama ve uzun basma callback'leri
    var onItemClick: ((TransactionEntity) -> Unit)? = null
    var onItemLongClick: ((TransactionEntity) -> Unit)? = null

    class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val descriptionTextView: TextView = itemView.findViewById(R.id.tv_description)
        private val amountTextView: TextView = itemView.findViewById(R.id.tv_amount)
        private val dateTextView: TextView? = itemView.findViewById(R.id.tv_date)
        private val paidSumTextView: TextView = itemView.findViewById(R.id.tv_paid_sum)

        fun bind(tx: TransactionEntity) {
            val ctx = itemView.context
            val locale = Locale("tr", "TR")

            // 🌟 DÜZELTME: NumberFormat ayarları
            val numberFormat = NumberFormat.getInstance(locale).apply {
                maximumFractionDigits = 2
                // ✅ DÜZELTME BURADA: Minimum ondalık basamağı SIFIR yapın.
                // Bu, 2.000,00 gibi tam sayıların 2.000 görünmesini sağlar.
                minimumFractionDigits = 0
                isGroupingUsed = true
            }



            // 🏷 Açıklama
            descriptionTextView.text = tx.description?.ifBlank { "(Açıklama yok)" }

            // 💸 Ana tutar
            // ✅ DÜZELTME: amount direkt formatlanır ve ' ₺' sembolü manuel eklenir.
            val formattedAmount = numberFormat.format(tx.amount) + " ₺"
            amountTextView.text = formattedAmount
            amountTextView.fontFeatureSettings = "tnum"

            // 🎨 Renk: gelir / tamamen ödenmiş / ödenmemiş
            val colorRes = when {
                tx.type == CategoryType.INCOME -> R.color.amountPositive
                tx.fullyPaid -> R.color.amountPositive
                else -> R.color.amountNegative
            }
            amountTextView.setTextColor(ContextCompat.getColor(ctx, colorRes))

            // 💰 Alt satırda ödenen tutar varsa göster
            val paidSum = tx.paidSum

            if (paidSum > 0.0) {
                // ✅ DÜZELTME: Formatlama ve manuel '₺' ekleme.
                val paidText = "💸 Ödenen: ${numberFormat.format(paidSum)} ₺"
                paidSumTextView.text = paidText
                paidSumTextView.visibility = View.VISIBLE
            } else {
                paidSumTextView.visibility = View.GONE
            }

            // 📅 Tarih
            dateTextView?.text = SimpleDateFormat("dd.MM.yyyy", locale).format(Date(tx.date))
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_transaction, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
        holder.itemView.setOnClickListener { onItemClick?.invoke(item) }
        holder.itemView.setOnLongClickListener {
            onItemLongClick?.invoke(item)
            true
        }
    }
}

class TransactionDiffCallback : DiffUtil.ItemCallback<TransactionEntity>() {
    override fun areItemsTheSame(oldItem: TransactionEntity, newItem: TransactionEntity): Boolean {
        return oldItem.uuid == newItem.uuid || oldItem.localId == newItem.localId
    }

    override fun areContentsTheSame(oldItem: TransactionEntity, newItem: TransactionEntity): Boolean {
        return oldItem == newItem
    }
}