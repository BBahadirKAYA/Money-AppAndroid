package com.moneyapp.android.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class LegacyReleaseInfo(
    val versionCode: Int? = null,
    val versionName: String? = null,
    val apkUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class ModernManifest(
    val latest: String? = null,
    val minSupported: String? = null,
    val url: String? = null,
    val changelog: String? = null
)

sealed class UpdateResult {
    data class Available(
        val latest: String,
        val url: String,
        val changelog: String?,
        val mandatory: Boolean
    ) : UpdateResult()
    data class UpToDate(val current: String) : UpdateResult()
    data class Error(val message: String) : UpdateResult()
}

object UpdateChecker {

    // App'in BuildConfig'inden (reflection) UPDATE_MANIFEST_URL almaya çalış;
    // yoksa GitHub raw fallback'ı kullan.
    private fun defaultManifestUrl(ctx: Context): String {
        return try {
            val bc = Class.forName("${ctx.packageName}.BuildConfig")
            val f = bc.getDeclaredField("UPDATE_MANIFEST_URL")
            (f.get(null) as? String)?.takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        } ?: "https://raw.githubusercontent.com/BBahadirKAYA/Money-AppAndroid/main/update-helper/update.json"
    }

    // Uygulamanın mevcut sürümü (modül bağımsız)
    private fun currentVersion(ctx: Context): Pair<String, Int> {
        val pm = ctx.packageManager
        val pkg = ctx.packageName
        val pi = pm.getPackageInfo(pkg, 0)
        val vn = pi.versionName ?: "0.0.0"
        val vc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            (pi.longVersionCode and 0x7FFF_FFFFL).toInt()
        } else {
            @Suppress("DEPRECATION") pi.versionCode
        }
        return vn to vc
    }

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val legacyAdapter = moshi.adapter(LegacyReleaseInfo::class.java)
    private val modernAdapter = moshi.adapter(ModernManifest::class.java)

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                // release'de sessiz, debug'da temel log
                level = if (Build.TYPE == "user") HttpLoggingInterceptor.Level.NONE
                else HttpLoggingInterceptor.Level.BASIC
            })
            .followRedirects(true)
            .build()
    }

    /** UI’dan çağır:
     *  lifecycleScope.launch { UpdateChecker.checkAndPrompt(this@MainActivity) }
     */
    suspend fun checkAndPrompt(
        context: Context,
        manifestUrl: String = defaultManifestUrl(context)
    ) = withContext(Dispatchers.IO) {
        val (currentVn, currentVc) = currentVersion(context)
        Log.d("Update", "Manifest URL = $manifestUrl")

        when (val result = check(manifestUrl, currentVn, currentVc)) {
            is UpdateResult.Available -> withContext(Dispatchers.Main) {
                val title = if (result.mandatory) "Zorunlu Güncelleme" else "Yeni Sürüm Mevcut"
                val msg = buildString {
                    append("Son sürüm: ${result.latest}\n")
                    result.changelog?.takeIf { it.isNotBlank() }?.let {
                        append("\nDeğişiklikler:\n$it")
                    }
                }
                AlertDialog.Builder(context)
                    .setTitle(title)
                    .setMessage(msg)
                    .setCancelable(!result.mandatory)
                    .setPositiveButton("Güncelle") { _, _ ->
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(result.url))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                    .apply { if (!result.mandatory) setNegativeButton("Sonra", null) }
                    .show()
            }
            is UpdateResult.UpToDate -> withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "Güncel sürümü kullanıyorsun ($currentVn).",
                    Toast.LENGTH_SHORT
                ).show()
            }
            is UpdateResult.Error -> withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "Güncelleme kontrolü başarısız: ${result.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // URL’den oku → modern parse (heuristic) → legacy parse → karşılaştır
    private fun check(
        manifestUrl: String,
        currentVn: String,
        currentVc: Int
    ): UpdateResult = try {
        Log.d("Update", "GET $manifestUrl")
        val req = Request.Builder()
            .url(manifestUrl)
            .header("Accept", "application/json")
            .header("User-Agent", "MoneyAppAndroid/$currentVn")
            .build()

        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return UpdateResult.Error("HTTP ${resp.code}")
            val bodyStr = resp.body?.string().orEmpty()
            if (bodyStr.isBlank()) return UpdateResult.Error("Boş yanıt")

            // Modern format: latest/url var mı?
            modernAdapter.fromJson(bodyStr)?.let { m ->
                val latest = m.latest?.trim().orEmpty()
                val url = m.url?.trim().orEmpty()
                val looksModern = latest.isNotBlank() || url.isNotBlank()
                Log.d("Update", "looksModern=$looksModern latest='$latest' urlPresent=${url.isNotBlank()}")

                if (looksModern) {
                    val mandatory = m.minSupported?.let { isNewer(it, currentVn) } == true
                    val hasUpdate = latest.isNotBlank() && isNewer(latest, currentVn)
                    return when {
                        mandatory -> UpdateResult.Available(latest.ifBlank { "?" }, url, m.changelog, true)
                        hasUpdate -> UpdateResult.Available(latest, url, m.changelog, false)
                        else -> UpdateResult.UpToDate(currentVn)
                    }
                }
            }

            // Legacy format
            legacyAdapter.fromJson(bodyStr)?.let { r ->
                val remoteVc = r.versionCode ?: -1
                val remoteVn = r.versionName ?: ""
                val url = r.apkUrl.orEmpty()
                Log.d("Update", "legacy: remoteVc=$remoteVc localVc=$currentVc")

                val hasByCode = remoteVc > currentVc
                val hasByName = remoteVn.isNotBlank() && isNewer(remoteVn, currentVn)

                return if (hasByCode || hasByName) {
                    UpdateResult.Available(
                        latest = remoteVn.ifBlank { "v$remoteVc" },
                        url = url,
                        changelog = null,
                        mandatory = false
                    )
                } else {
                    UpdateResult.UpToDate(currentVn)
                }
            }

            UpdateResult.Error("JSON parse edilemedi")
        }
    } catch (t: Throwable) {
        UpdateResult.Error(t.message ?: "unknown")
    }

    /** Basit semver karşılaştırma: 1.2.3[-...] */
    private fun isNewer(v1: String, v2: String): Boolean {
        fun parse(v: String) = v.replace(Regex("[^0-9._-]"), "")
            .split('.', '-', '_')
            .mapNotNull { it.toIntOrNull() }
        val a = parse(v1); val b = parse(v2)
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val ai = a.getOrElse(i) { 0 }
            val bi = b.getOrElse(i) { 0 }
            if (ai != bi) return ai > bi
        }
        return false
    }
}
