package com.moneyapp.updatehelper

import android.app.DownloadManager
import android.content.*
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import java.io.File

object UpdateHelper {

    private var currentDownloadId: Long = -1

    fun checkAndDownload(context: Context, apkUrl: String, fileName: String = "update.apk") {
        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle("MoneyAppAndroid Güncellemesi")
            .setDescription("Yeni sürüm indiriliyor...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir("Download", fileName)
            .setAllowedOverMetered(true)

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        currentDownloadId = manager.enqueue(request)

        // 🔹 İndirme tamamlanınca otomatik yükleme penceresini aç
        registerReceiver(context)
    }

    private fun registerReceiver(context: Context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: return
                if (id == currentDownloadId) {
                    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    val uri = manager.getUriForDownloadedFile(id)
                    if (uri != null) {
                        launchInstaller(context, uri)
                    } else {
                        Toast.makeText(context, "İndirme tamamlandı ama dosya bulunamadı.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }

    private fun launchInstaller(context: Context, uri: Uri) {
        // 🔸 Android 8.0+ için bilinmeyen kaynak izni kontrolü
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canInstall = context.packageManager.canRequestPackageInstalls()
            if (!canInstall) {
                Toast.makeText(context, "Lütfen bilinmeyen kaynaklardan yüklemeye izin verin.", Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return
            }
        }

        // 🔹 Doğrudan yükleme penceresini aç
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "APK açılamadı. Dosya yöneticisinden deneyin.", Toast.LENGTH_LONG).show()
        }
    }
}
