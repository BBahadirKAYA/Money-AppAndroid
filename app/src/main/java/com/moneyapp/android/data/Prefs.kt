package com.moneyapp.android.data

import android.content.Context
import androidx.core.content.edit

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("moneyapp_prefs", Context.MODE_PRIVATE)

    fun getBackendUrl(): String? = sp.getString("backend_url", null)

    fun setBackendUrl(url: String) = sp.edit { putString("backend_url", url) }
}
