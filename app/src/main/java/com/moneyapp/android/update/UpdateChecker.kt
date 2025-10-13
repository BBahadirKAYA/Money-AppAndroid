package com.moneyapp.android.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.moneyapp.android.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * Uzak manifest örnekleri:
 *
 * Modern:
 * {
 *   "latest": "1.2.0",
 *   "minSupported": "1.0.0",
 *   "url": "https://.../com.moneyapp.android-v1.2.0-120-release.apk",
 *   "sha256": "ab12cd34...",            // opsiyonel
 *   "changelog": "• Performans iyileştirmeleri"
 * }
 *
 * Legacy:
 * { "versionCode":120, "versionName":"1.2.0", "apkUrl":"https://.../app-release.apk" }
 */

@JsonClass(generateAdapter = true)
data class LegacyReleaseInfo(
    val versionCode: Int? = null,
    val versionName: String? = null,
    @Json(name = "apkUrl") val apkUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class ModernManifest(
    val latest: String? = null,
    val minSupported: String? = null,
    val url: String? = null,
    val changelog: String? = null,
    val sha256: String? = null
)

sealed class UpdateResult {
    data class Available(
        val latest: String,
        val url: String,
        val changelog: String?,
        val mandatory: Boolean,
        val sha256: String? // bilgi amaçlı (doğrulama istemiyorsak bile gösterebiliriz)
    ) : UpdateResult()

    data class UpToDate(val current: String) : UpdateResult()
    data class Error(val message: String) : UpdateResult()
}

object UpdateChecker {

    /** Varsayılan manifest adresi: BuildConfig üzerinden (gradle.properties veya -P ile override edilebiliyor) */
    private val DEFAULT_URL: String =
        BuildConfig.UPDATE_MANIFEST_URL.ifBlank {
            // Güvenli fallback (repo içi varsayılan)
            "https://raw.githubusercontent.com/BBahadirKAYA/Money-AppAndroid/main/update-helper/update.json"
        }

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val legacyAdapter = moshi.adapter(LegacyReleaseInfo::class.java)
    private val modernAdapter = moshi.adapter(ModernManifest::class.java)

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
            )
            .followRedirects(true)
            .build()
    }

    /** App versiyonunu Context’ten al (BuildConfig.versionCode/Name yerine PM kullanıyoruz) */
    private fun getAppVersion(context: Context): Pair<Int, String> {
        val pm = context.packageManager
        val pn = context.packageName
        val pi = pm.getPackageInfo(pn, 0)
        val vc = if (android.os.Build.VERSION.SDK_INT >= 28) pi.longVersionCode.toInt()
        else @Suppress("DEPRECATION") pi.versionCode
        val vn = pi.versionName ?: "0.0.0"
        return vc to vn
    }

    /**
     * UI’dan çağır:
     * lifecycleScope.launch { UpdateChecker.checkAndPrompt(this@MainActivity) }
     *
     * manifestUrl parametresi boş bırakılırsa BuildConfig.UPDATE_MANIFEST_URL kullanılır.
     */
    suspend fun checkAndPrompt(
        context: Context,
        manifestUrl: String? = null
    ) = withContext(Dispatchers.IO) {
        val (currentVc, currentVn) = getAppVersion(context)
        val url = manifestUrl?.ifBlank { null } ?: DEFAULT_URL

        when (val result = check(url, currentVc, currentVn)) {
            is UpdateResult.Available -> withContext(Dispatchers.Main) {
                val title = if (result.mandatory) "Zorunlu Güncelleme" else "Yeni Sürüm Mevcut"
                val msg = buildString {
                    append("Son sürüm: ${result.latest}\n")
                    result.changelog?.takeIf { it.isNotBlank() }?.let {
                        append("\nDeğişiklikler:\n$it")
                    }
                    result.sha256?.takeIf { it.isNotBlank() }?.let {
                        append("\nSHA-256:\n$it")
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
                    "Güncel sürümü kullanıyorsun (${result.current}).",
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

    /** İş mantığı: URL’den oku → modern parse → (değilse) legacy parse → karşılaştır */
    private fun check(
        manifestUrl: String,
        currentVc: Int,
        currentVn: String
    ): UpdateResult = try {
        // CDN cache-buster
        val bust = System.currentTimeMillis()
        val urlWithBust = if ('?' in manifestUrl) "$manifestUrl&ts=$bust" else "$manifestUrl?ts=$bust"
        Log.d("Update", "GET $urlWithBust")

        val req = Request.Builder()
            .url(urlWithBust)
            .header("Accept", "application/json")
            .header("Cache-Control", "no-cache")
            .header("Pragma", "no-cache")
            .header("User-Agent", "MoneyAppAndroid/$currentVn")
            .build()

        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return UpdateResult.Error("HTTP ${resp.code}")
            val body = resp.body?.string().orEmpty()
            if (body.isBlank()) return UpdateResult.Error("Boş yanıt")

            // 1) Modern dene
            modernAdapter.fromJson(body)?.let { m ->
                val latest = m.latest?.trim().orEmpty()
                val url = m.url?.trim().orEmpty()
                val looksModern = latest.isNotBlank() || url.isNotBlank()
                Log.d("Update", "modern?=$looksModern latest='$latest' urlPresent=${url.isNotBlank()}")

                if (looksModern) {
                    val mandatory = m.minSupported?.let { isNewer(it, currentVn) } == true
                    val hasUpdate = latest.isNotBlank() && isNewer(latest, currentVn)
                    return when {
                        mandatory -> UpdateResult.Available(latest.ifBlank { "?" }, url, m.changelog, true, m.sha256)
                        hasUpdate -> UpdateResult.Available(latest, url, m.changelog, false, m.sha256)
                        else -> UpdateResult.UpToDate(currentVn)
                    }
                }
            }

            // 2) Legacy fallback
            legacyAdapter.fromJson(body)?.let { r ->
                val remoteVc = r.versionCode ?: -1
                val remoteVn = r.versionName ?: ""
                val url = r.apkUrl.orEmpty()

                Log.d("Update", "legacy: remoteVc=$remoteVc localVc=$currentVc")
                val hasByCode = remoteVc > currentVc
                val hasByName = remoteVn.isNotBlank() && isNewer(remoteVn, currentVn)

                return if (hasByCode || hasByName) {
                    UpdateResult.Available(
                        latest = if (remoteVn.isNotBlank()) remoteVn else "v$remoteVc",
                        url = url,
                        changelog = null,
                        mandatory = false,
                        sha256 = null
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

    /** Basit semver karşılaştırma: 1.2.3[-...] → yalnızca sayısal kısımlar kıyaslanır */
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
