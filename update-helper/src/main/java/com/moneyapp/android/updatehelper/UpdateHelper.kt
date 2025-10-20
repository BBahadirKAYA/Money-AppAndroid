package com.moneyapp.android.updatehelper

import android.app.AlertDialog
import android.app.DownloadManager
import android.content.*
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URL

object UpdateHelper {

    private const val UPDATE_JSON_URL =
        "https://raw.githubusercontent.com/BBahadirKAYA/Money-AppAndroid/main/update-helper/update.json"

    private var currentDownloadId: Long = -1L

    fun checkForUpdates(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val jsonText = URL(UPDATE_JSON_URL).readText()
                val json = JSONObject(jsonText)

                val latestName = json.getString("latest")
                val apkUrl = json.getString("url")
                val changelog = json.optString("changelog", "")

                val currentName =
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"

                if (isNewerVersion(latestName, currentName)) {
                    CoroutineScope(Dispatchers.Main).launch {
                        val message = buildString {
                            append("Yeni sürüm mevcut: v$latestName\n")
                            append("Mevcut sürüm: v$currentName\n\n")
                            if (changelog.isNotBlank()) {
                                append("📝 Değişiklikler:\n")
                                append(changelog.take(400))
                            }
                        }

                        AlertDialog.Builder(context)
                            .setTitle("Yeni sürüm bulundu")
                            .setMessage(message)
                            .setPositiveButton("İndir ve Yükle") { _, _ ->
                                downloadAndInstall(context, apkUrl)
                            }
                            .setNegativeButton("Kapat", null)
                            .show()
                    }
                } else {
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(context, "✅ Uygulama zaten güncel", Toast.LENGTH_SHORT).show()
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

    // 🔽 APK indir ve tamamlanınca yükleme ekranını aç
    private fun downloadAndInstall(context: Context, apkUrl: String) {
        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle("MoneyAppAndroid Güncellemesi")
            .setDescription("Yeni sürüm indiriliyor…")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir("Download", "moneyapp_update.apk")
            .setAllowedOverMetered(true)

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        currentDownloadId = manager.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: return
                if (id == currentDownloadId) {
                    val uri = manager.getUriForDownloadedFile(id)
                    if (uri != null) {
                        context.unregisterReceiver(this)
                        launchInstaller(context, uri)
                    } else {
                        Toast.makeText(context, "İndirilen dosya bulunamadı.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }

    // 🔸 APK yükleme ekranını başlat (bilinmeyen kaynak izni dâhil)
    private fun launchInstaller(context: Context, uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canInstall = context.packageManager.canRequestPackageInstalls()
            if (!canInstall) {
                Toast.makeText(context, "Yükleme izni gerekli, lütfen etkinleştirin.", Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return
            }
        }

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "APK açılamadı, Dosya Yöneticisinden deneyin.", Toast.LENGTH_LONG).show()
        }
    }

    // 🔢 Versiyon karşılaştırması
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
