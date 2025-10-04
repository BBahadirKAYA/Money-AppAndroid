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
import com.moneyapp.android.data.db.TransactionEntity
import com.moneyapp.android.data.db.CategoryType
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class TransactionAdapter :
    ListAdapter<TransactionEntity, TransactionAdapter.TransactionViewHolder>(TransactionDiffCallback()) {

    class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val descriptionTextView: TextView = itemView.findViewById(R.id.tv_description)
        private val amountTextView: TextView = itemView.findViewById(R.id.tv_amount)
        private val dateTextView: TextView? = itemView.findViewById(R.id.tv_date) // opsiyonel

        fun bind(tx: TransactionEntity) {
            descriptionTextView.text = tx.note ?: ""

            // Tutar (kuruş -> TL, 1.234.567 ₺; kuruş yok)
            val formattedAmount = formatAmountTL(tx.amount)

            val ctx = amountTextView.context
            if (tx.type == CategoryType.INCOME) {
                amountTextView.text = "+$formattedAmount"
                amountTextView.setTextColor(ContextCompat.getColor(ctx, R.color.green))
            } else {
                amountTextView.text = "-$formattedAmount"
                amountTextView.setTextColor(ContextCompat.getColor(ctx, R.color.red))
            }

            // Tarih (saat yok) — layout'ta tv_date varsa doldur
            dateTextView?.text = formatDate(tx.date)
        }

        private fun formatAmountTL(amountCents: Long): String {
            val tl = amountCents / 100L
            val nf = NumberFormat.getInstance(Locale("tr", "TR")).apply {
                maximumFractionDigits = 0
                isGroupingUsed = true
            }
            return nf.format(tl) + " ₺"
        }

        private fun formatDate(epochMillis: Long): String {
            val sdf = SimpleDateFormat("dd.MM.yyyy", Locale("tr", "TR"))
            return sdf.format(Date(epochMillis))
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_transaction, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

class TransactionDiffCallback : DiffUtil.ItemCallback<TransactionEntity>() {
    override fun areItemsTheSame(oldItem: TransactionEntity, newItem: TransactionEntity) =
        oldItem.localId == newItem.localId

    override fun areContentsTheSame(oldItem: TransactionEntity, newItem: TransactionEntity) =
        oldItem == newItem
}
