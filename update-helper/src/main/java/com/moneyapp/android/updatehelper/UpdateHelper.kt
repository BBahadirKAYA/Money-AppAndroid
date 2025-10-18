package com.moneyapp.android.updatehelper

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URL

object UpdateHelper {

    // 📡 update.json konumu (doğru yol)
    private const val UPDATE_JSON_URL =
        "https://raw.githubusercontent.com/BBahadirKAYA/Money-AppAndroid/main/update-helper/update.json"

    fun checkForUpdates(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val jsonText = URL(UPDATE_JSON_URL).readText()
                val json = JSONObject(jsonText)

                val latestName = json.getString("latest")
                val minSupported = json.getString("minSupported")
                val apkUrl = json.getString("url")
                val changelog = json.optString("changelog", "")

                val currentInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val currentName = currentInfo.versionName ?: "0.0.0"

                if (isNewerVersion(latestName, currentName)) {
                    CoroutineScope(Dispatchers.Main).launch {
                        val message = buildString {
                            append("Yeni sürüm mevcut: v$latestName\n")
                            append("Mevcut sürüm: v$currentName\n\n")
                            if (changelog.isNotBlank()) {
                                append("📝 Değişiklikler:\n")
                                append(changelog.take(500)) // ilk 500 karakter
                            }
                        }

                        AlertDialog.Builder(context)
                            .setTitle("Yeni sürüm bulundu")
                            .setMessage(message)
                            .setPositiveButton("İndir") { _, _ ->
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl))
                                context.startActivity(intent)
                            }
                            .setNegativeButton("Kapat", null)
                            .show()
                    }
                } else {
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(context, "✅ Uygulama zaten güncel", Toast.LENGTH_SHORT)
                            .show()
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(
                        context,
                        "Güncelleme kontrolü başarısız: ${e.message ?: "Bilinmeyen hata"}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    // 🔢 Versiyon karşılaştırma (örnek: "1.3.7" > "1.2.9")
    private fun isNewerVersion(remote: String, local: String): Boolean {
        val r = remote.split('.').mapNotNull { it.toIntOrNull() }
        val l = local.split('.').mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(r.size, l.size)) {
            val rv = r.getOrNull(i) ?: 0
            val lv = l.getOrNull(i) ?: 0
            if (rv > lv) return true
            if (rv < lv) return false
        }
        return false
    }
}
