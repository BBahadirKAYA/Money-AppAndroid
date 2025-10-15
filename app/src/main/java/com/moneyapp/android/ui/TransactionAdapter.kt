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

    // ✅ EKLENDİ: Liste öğesi tıklama callback’i
    var onItemClick: ((TransactionEntity) -> Unit)? = null

    class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val descriptionTextView: TextView = itemView.findViewById(R.id.tv_description)
        private val amountTextView: TextView = itemView.findViewById(R.id.tv_amount)
        private val dateTextView: TextView? = itemView.findViewById(R.id.tv_date) // opsiyonel

        fun bind(tx: TransactionEntity) {
            // Açıklama
            descriptionTextView.text = tx.description.orEmpty()

            // TL formatı (kuruş -> TL, 1.234.567 ₺)
            val formattedAbs = formatAmountTL(absTL(tx.amountCents))

            // Renk ve işaret
            val ctx = amountTextView.context
            val isIncome = tx.type == CategoryType.INCOME
            val sign = if (isIncome) "+" else "-"
            val colorRes = if (isIncome) R.color.amountPositive else R.color.amountNegative
            amountTextView.setTextColor(ContextCompat.getColor(ctx, colorRes))

            // Tabular rakamlar ile hizalama
            amountTextView.fontFeatureSettings = "tnum"
            amountTextView.text = "$sign$formattedAbs"

            // Tarih (epoch millis)
            dateTextView?.text = formatDate(tx.date)
        }

        /** kuruş -> TL (mutlak değer, tam sayı TL) */
        private fun absTL(amountCents: Long): Long = kotlin.math.abs(amountCents) / 100L

        private fun formatAmountTL(tl: Long): String {
            val locale = Locale.forLanguageTag("tr-TR")
            val nf = NumberFormat.getInstance(locale).apply {
                maximumFractionDigits = 0
                isGroupingUsed = true
            }
            return nf.format(tl) + " ₺"
        }

        private fun formatDate(epochMillis: Long): String {
            val locale = Locale.forLanguageTag("tr-TR")
            val sdf = SimpleDateFormat("dd.MM.yyyy", locale)
            return sdf.format(Date(epochMillis))
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

        // ✅ EKLENDİ: öğe tıklaması
        holder.itemView.setOnClickListener { onItemClick?.invoke(item) }
    }
}

class TransactionDiffCallback : DiffUtil.ItemCallback<TransactionEntity>() {
    override fun areItemsTheSame(oldItem: TransactionEntity, newItem: TransactionEntity) =
        oldItem.localId == newItem.localId

    override fun areContentsTheSame(oldItem: TransactionEntity, newItem: TransactionEntity) =
        oldItem == newItem
}