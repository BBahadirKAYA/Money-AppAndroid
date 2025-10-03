package com.moneyapp.android.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recurring_rules")
data class RecurringRuleEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0L,
    val uuid: String? = null,
    val title: String,
    val amount: Long?, // null = boş tutarlı plan
    val schedule: String, // örn: MONTHLY_DAY=25
    val nextRunDate: String,
    val holidayPolicy: String? = null, // NEXT_BUSINESS_DAY vb.
    val dirty: Boolean = true,
    val deleted: Boolean = false,
    val updatedAtLocal: Long = System.currentTimeMillis(),
    val updatedAtServer: Long? = null
)
