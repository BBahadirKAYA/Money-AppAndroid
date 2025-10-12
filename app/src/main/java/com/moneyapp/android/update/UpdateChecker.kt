package com.moneyapp.android.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.moneyapp.android.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.TimeUnit
import android.util.Log

/**
 * Uzak manifest örnekleri:
 * 1) Eski Apps Script (sadece release info)
 *   {"versionCode":7,"versionName":"1.0.7","apkUrl":"https://.../app-release.apk"}
 *
 * 2) Yeni manifest (zorunlu güncelleme + changelog)
 *   {
 *     "latest": "1.0.7",
 *     "minSupported": "1.0.0",
 *     "url": "https://.../app-release.apk",
 *     "changelog": "Performans iyileştirmeleri"
 *   }
 */

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

    // Varsayılan: BuildConfig üzerinden; yoksa (geriye uyum için) eski Apps Script adresi
    private val DEFAULT_URL: String by lazy {
        val manifest = try { BuildConfig.UPDATE_MANIFEST_URL } catch (_: Throwable) { "" }
        if (manifest.isNullOrBlank()) {
            "https://script.google.com/macros/s/AKfycby8G4L4UhT2lutb1jyV8n8fX99_tIxsdDSGCdIt1ONHObpCLI51_tHQ-PBeT4mdpcrX/exec"
        } else manifest
    }

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val legacyAdapter = moshi.adapter(LegacyReleaseInfo::class.java)
    private val modernAdapter = moshi.adapter(ModernManifest::class.java)

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    // DEBUG’de gövdeleri gör, release’de kapalı kalsın
                    level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                    else HttpLoggingInterceptor.Level.NONE
                }
            )
            .followRedirects(true)
            .build()
    }

    /** UI’dan çağır: lifecycleScope.launch { UpdateChecker.checkAndPrompt(this@MainActivity) } */
    suspend fun checkAndPrompt(
        context: Context,
        manifestUrl: String = DEFAULT_URL
    ) = withContext(Dispatchers.IO) {
        Log.d("Update", "Manifest URL = $manifestUrl")
        when (val result = check(manifestUrl)) {
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
                Toast.makeText(context, "Güncel sürümü kullanıyorsun (${result.current}).",
                    Toast.LENGTH_SHORT).show()
            }
            is UpdateResult.Error -> withContext(Dispatchers.Main) {
                Toast.makeText(context, "Güncelleme kontrolü başarısız: ${result.message}",
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    /** İş mantığı: URL’den oku → modern parse dene → legacy parse fallback → karşılaştır */
    private fun check(manifestUrl: String): UpdateResult = try {
        val req = Request.Builder()
            .url(manifestUrl)
            .header("Accept", "application/json")
            .header("User-Agent", "MoneyAppAndroid/${BuildConfig.VERSION_NAME}")
            .build()
        Log.d("Update", "Manifest URL = $manifestUrl")
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return UpdateResult.Error("HTTP ${resp.code}")
            val body = resp.body?.string().orEmpty()
            if (body.isBlank()) return UpdateResult.Error("Boş yanıt")

            // Önce modern formatı dene
            modernAdapter.fromJson(body)?.let { m ->
                val latest = m.latest?.trim().orEmpty()
                val url = m.url?.trim().orEmpty()
                val changelog = m.changelog
                val current = BuildConfig.VERSION_NAME
                val mandatory = m.minSupported?.let { isNewer(it, current) } == true
                val hasUpdate = latest.isNotBlank() && isNewer(latest, current)

                return when {
                    mandatory -> UpdateResult.Available(latest.ifBlank { "?" }, url, changelog, true)
                    hasUpdate -> UpdateResult.Available(latest, url, changelog, false)
                    else -> UpdateResult.UpToDate(current)
                }
            }

            // Fallback: legacy format
            legacyAdapter.fromJson(body)?.let { r ->
                val remoteVc = r.versionCode ?: -1
                val remoteVn = r.versionName ?: ""
                val url = r.apkUrl.orEmpty()
                val localVc = BuildConfig.VERSION_CODE
                val localVn = BuildConfig.VERSION_NAME

                val hasByCode = remoteVc > localVc
                val hasByName = remoteVn.isNotBlank() && isNewer(remoteVn, localVn)
                return if (hasByCode || hasByName) {
                    UpdateResult.Available(
                        latest = if (remoteVn.isNotBlank()) remoteVn else "v$remoteVc",
                        url = url,
                        changelog = null,
                        mandatory = false
                    )
                } else {
                    UpdateResult.UpToDate(localVn)
                }
            }

            UpdateResult.Error("JSON parse edilemedi")
        }
    } catch (t: Throwable) {
        UpdateResult.Error(t.message ?: "unknown")
    }

    /** Basit semver karşılaştırma: 1.2.3[-...] biçimleri */
    private fun isNewer(v1: String, v2: String): Boolean {
        fun parse(v: String) = v
            .replace(Regex("[^0-9._-]"), "")
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
