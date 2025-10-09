package com.moneyapp.android.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.moneyapp.android.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

data class ReleaseInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String
)

object UpdateChecker {
    // Apps Script JSON endpoint’in (GET):
    private const val SCRIPT_URL =
        "https://script.google.com/macros/s/AKfycby8G4L4UhT2lutb1jyV8n8fX99_tIxsdDSGCdIt1ONHObpCLI51_tHQ-PBeT4mdpcrX/exec"

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .build()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val adapter = moshi.adapter(ReleaseInfo::class.java)

    /** Kullan: lifecycleScope.launch { UpdateChecker.check(this@MainActivity) } */
    suspend fun check(context: Context) = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(SCRIPT_URL)
                .header("Accept", "application/json")
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val body = resp.body?.string() ?: error("Empty body")
                val remote = adapter.fromJson(body) ?: error("Parse error")

                val localVc = BuildConfig.VERSION_CODE
                if (remote.versionCode > localVc) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "Yeni sürüm bulundu: ${remote.versionName}",
                            Toast.LENGTH_LONG
                        ).show()
                        // APK / Release linkini aç
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(remote.apkUrl))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Güncel sürümü kullanıyorsun.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (t: Throwable) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Güncelleme kontrolü başarısız: ${t.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
