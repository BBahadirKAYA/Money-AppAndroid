package com.moneyapp.android.data.net

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Sheets Apps Script doGet JSON beklenen biçim:
 * { "base_url": "https://....ngrok-.../ " }
 */
class DiscoveryClient(
    private val sheetEndpoint: String
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun fetchLatestUrl(): String? {
        val req = Request.Builder().url(sheetEndpoint).get().build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string() ?: return null
            val url = JSONObject(body).optString("base_url", "").trim()
            // Demo için basit doğrulama:
            return if (url.startsWith("http")) url else null
        }
    }
}
