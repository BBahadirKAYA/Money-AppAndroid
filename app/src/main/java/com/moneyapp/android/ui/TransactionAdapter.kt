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

        fun bind(tx: TransactionEntity) {
            if (tx.amountCents == 0L) {
                descriptionTextView.text = "📝 TASLAK: ${tx.description.orEmpty()}"
            }

            descriptionTextView.text = tx.description.orEmpty()

            val isIncome = tx.type == CategoryType.INCOME
            val ctx = amountTextView.context
            val colorRes = if (isIncome) R.color.amountPositive else R.color.amountNegative
            amountTextView.setTextColor(ContextCompat.getColor(ctx, colorRes))
            amountTextView.fontFeatureSettings = "tnum"

            // 🔹 İşlem listesi gider odaklı olduğu için "−" işareti kaldırıldı
            val sign = if (isIncome) "+ " else ""  // giderlerde işaret yok
            val formattedAmount = formatAmountTL(tx.amountCents)
            amountTextView.text = "$sign$formattedAmount"

            dateTextView?.text = formatDate(tx.date)
        }

        private fun formatAmountTL(amountCents: Long): String {
            val locale = Locale.forLanguageTag("tr-TR")
            val nf = NumberFormat.getInstance(locale).apply {
                maximumFractionDigits = 0
                isGroupingUsed = true
            }
            val tlValue = txToLiras(amountCents)
            return nf.format(tlValue) + " ₺"
        }

        private fun txToLiras(amountCents: Long): Long {
            return kotlin.math.abs(amountCents) / 100L
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

        holder.itemView.setOnClickListener { onItemClick?.invoke(item) }

        // ✅ Uzun basma (Düzenle / Sil menüsü)
        holder.itemView.setOnLongClickListener {
            onItemLongClick?.invoke(item)
            true
        }
    }
}

class TransactionDiffCallback : DiffUtil.ItemCallback<TransactionEntity>() {
    override fun areItemsTheSame(oldItem: TransactionEntity, newItem: TransactionEntity) =
        oldItem.localId == newItem.localId

    override fun areContentsTheSame(oldItem: TransactionEntity, newItem: TransactionEntity) =
        oldItem == newItem
}
