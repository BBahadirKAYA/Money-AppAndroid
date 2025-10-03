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

class TransactionAdapter : ListAdapter<TransactionEntity, TransactionAdapter.TransactionViewHolder>(TransactionDiffCallback()) {

    class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val descriptionTextView: TextView = itemView.findViewById(R.id.tv_description)
        private val amountTextView: TextView = itemView.findViewById(R.id.tv_amount)

        fun bind(transaction: TransactionEntity) {
            descriptionTextView.text = transaction.note
            val context = amountTextView.context

            if (transaction.type == CategoryType.INCOME) {
                amountTextView.text = "+${transaction.amount} ₺"
                amountTextView.setTextColor(ContextCompat.getColor(context, R.color.green))
            } else {
                amountTextView.text = "-${transaction.amount} ₺"
                amountTextView.setTextColor(ContextCompat.getColor(context, R.color.red))
            }
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
    override fun areItemsTheSame(oldItem: TransactionEntity, newItem: TransactionEntity): Boolean {
        return oldItem.localId == newItem.localId
    }

    override fun areContentsTheSame(oldItem: TransactionEntity, newItem: TransactionEntity): Boolean {
        return oldItem == newItem
    }
}