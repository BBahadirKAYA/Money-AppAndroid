# MoneyAppAndroid

Android (Kotlin) ile kişisel finans uygulaması. Laravel backend’e Retrofit üzerinden bağlanır, verileri Room ile yerelde tutar.

## ✨ Özellikler
- Retrofit + OkHttp (logging) ile REST istemcisi
- Room (DAO + KSP) yerel veritabanı
- ViewBinding
- WorkManager ile arka plan işleri
- BuildConfig üzerinden **dinamik BASE_URL** (emülatör, ngrok vs.)

## 🧱 Teknolojiler
- Kotlin / AndroidX
- Retrofit 2.11, OkHttp 4.12
- Moshi 1.15 (opsiyonel kod üretimi)
- Room 2.8 (KSP)
- Gradle Kotlin DSL

---

## ⚙️ Gereksinimler
- Android Studio (Hedgehog/Koala+)
- JDK 17
- Android SDK 24+
- Çalışan bir backend (Laravel 10/11)

---

## 🚀 Hızlı Başlangıç

### 1) Backend (Laravel)
Lokal geliştirme için (geçici):
```bash
php artisan serve --host=127.0.0.1 --port=8000
```

Kalıcı servis (önerilen, systemd):
```
/etc/systemd/system/moneyapp.service
```
```ini
[Unit]
Description=MoneyApp Laravel server
After=network.target mariadb.service

[Service]
Type=simple
User=bahadir
WorkingDirectory=/srv/moneyapp
ExecStart=/usr/bin/php artisan serve --host=127.0.0.1 --port=8000
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```
Aktifleştir:
```bash
sudo systemctl daemon-reload
sudo systemctl enable moneyapp.service
sudo systemctl start moneyapp.service
systemctl status moneyapp.service
```

### 2) Android (BASE_URL)
- **Emülatör → host makine**:
  ```
  BASE_URL=http://10.0.2.2:8000/
  ```
- **Gerçek cihaz + ngrok (https)**:
  ```
  BASE_URL=https://<subdomain>.ngrok-free.app/
  ```

`gradle.properties`:
```properties
BASE_URL=http://10.0.2.2:8000/
```

`app/build.gradle.kts` → `defaultConfig`:
```kotlin
val backendUrl = (project.findProperty("BASE_URL") as String? ?: "http://10.0.2.2:8000/")
buildConfigField("String", "BASE_URL", ""$backendUrl"")
```

> `BASE_URL` **/ ile bitmeli**.

---

## 🔐 Network Security (debug)
HTTP (cleartext) çağrılar için sadece **debug**’da izin verilir.

`app/src/debug/AndroidManifest.xml`
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:usesCleartextTraffic="true"
        android:networkSecurityConfig="@xml/network_security_config" />
</manifest>
```

`app/src/debug/res/xml/network_security_config.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="true">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>
</network-security-config>
```

---

## 🔌 API Endpoints (örnek)
- `GET /api/ping` → `pong` (text)
- `GET /api/transactions` → JSON liste
- `GET /api/accounts` → JSON liste

Hızlı test:
```bash
curl http://127.0.0.1:8000/api/ping   # pong
```

---

## 📦 Retrofit Kurulumu

`app/build.gradle.kts`:
```kotlin
implementation("com.squareup.retrofit2:retrofit:2.11.0")
implementation("com.squareup.retrofit2:converter-scalars:2.11.0")
implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
implementation("com.squareup.okhttp3:okhttp:4.12.0")
debugImplementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.1")
```

`ApiService.kt`
```kotlin
package com.moneyapp.android.net

import retrofit2.Call
import retrofit2.http.GET

interface ApiService {
    @GET("api/ping") fun ping(): Call<String>
    @GET("api/transactions") fun transactions(): Call<String> // şimdilik String
    @GET("api/accounts") fun accounts(): Call<String>
}
```

`ApiClient.kt`
```kotlin
package com.moneyapp.android.net

import com.moneyapp.android.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private fun withSlash(u: String) = if (u.endsWith("/")) u else "$u/"

    private val http = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(withSlash(BuildConfig.BASE_URL))
        .client(http)
        .addConverterFactory(ScalarsConverterFactory.create())
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    val api: ApiService = retrofit.create(ApiService::class.java)
}
```

Activity’de basit test:
```kotlin
ApiClient.api.ping().enqueue(object: retrofit2.Callback<String> {
    override fun onResponse(c: retrofit2.Call<String>, r: retrofit2.Response<String>) {
        android.util.Log.d("API", "ping -> ${r.body()}")
    }
    override fun onFailure(c: retrofit2.Call<String>, t: Throwable) {
        android.util.Log.e("API", "ping error", t)
    }
})
```

---

## 🧩 (Opsiyonel) Tipli Modeller
```kotlin
@JsonClass(generateAdapter = true)
data class Account(
    val id: Int,
    val name: String,
    val type: String,
    val currency_code: String
)

// ApiService:
// @GET("api/accounts") fun accountsTyped(): Call<List<Account>>
```

---

## 🏗️ Mimari (özet)
- **UI/Activity/Fragment** → kullanıcı etkileşimi
- **ViewModel** → UI state & coroutine scope
- **Repository** → ağ (Retrofit) + yerel (Room) orkestrasyonu
- **Data** → DTO/Entity/Mapper

---

## 🧪 Geliştirme İpuçları
- Emülatör → host’a `10.0.2.2` ile erişir.
- Gerçek cihazda lokal backend için:
  ```bash
  adb reverse tcp:8000 tcp:8000
  BASE_URL=http://127.0.0.1:8000/
  ```
- Loglar: Logcat’te `OkHttp`/`API` filtreleri

---

## 🩺 Sorun Giderme
- **404 ama route var** → `php artisan route:list` kontrol et, nadiren `route:clear`.
- **Timeout** → BASE_URL yanlış / backend kapalı; `systemctl status moneyapp.service`.
- **CLEARTEXT hata** → debug network security config’i kontrol et.
- **ngrok** → https URL kullan, gerekirse CORS ayarlarını backend’de aç.

---

## 📄 Lisans
MIT (veya projenin seçtiği lisans)
