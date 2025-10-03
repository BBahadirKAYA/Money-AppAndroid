# MoneyApp Android ↔ Laravel Backend Entegrasyon Özeti

Bu doküman Android uygulaması ile Laravel backend arasında Retrofit üzerinden API bağlantısı kurmak için yapılan adımları özetler.

---

## 1. Laravel tarafı
- `routes/api.php` içine test endpointi eklendi:
  ```php
  Route::get('/ping', function () {
      return response('pong');
  });
  ```
- `php artisan serve --host=127.0.0.1 --port=8001` ile backend çalıştırıldı.
- Test:
  ```bash
  curl http://127.0.0.1:8001/api/ping
  # çıktı: pong
  ```

---

## 2. Android tarafı – Gradle ayarları
- `app/build.gradle.kts`:
  ```kotlin
  implementation("com.squareup.retrofit2:retrofit:2.11.0")
  implementation("com.squareup.retrofit2:converter-scalars:2.11.0") // düz text için
  implementation("com.squareup.retrofit2:converter-moshi:2.11.0")   // JSON için
  implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
  ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.1")
  ```

- `gradle.properties`:
  ```
  BASE_URL=http://10.0.2.2:8001/
  ```

- `build.gradle.kts` defaultConfig:
  ```kotlin
  val backendUrl = (project.findProperty("BASE_URL") as String? ?: "http://10.0.2.2:8001/")
  buildConfigField("String", "BASE_URL", "\"$backendUrl\"")
  ```

---

## 3. Network Security Config
- `app/src/debug/res/xml/network_security_config.xml`:
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
- `AndroidManifest.xml` application tag:
  ```xml
  <application
      android:usesCleartextTraffic="true"
      android:networkSecurityConfig="@xml/network_security_config"
      ...>
  ```

---

## 4. Retrofit yapılandırması
- `ApiService.kt`:
  ```kotlin
  package com.moneyapp.android.net

  import retrofit2.Call
  import retrofit2.http.GET

  interface ApiService {
      @GET("api/ping")
      fun ping(): Call<String>

      @GET("api/transactions")
      fun transactions(): Call<String>

      @GET("api/accounts")
      fun accounts(): Call<String>
  }
  ```

- `ApiClient.kt`:
  ```kotlin
  package com.moneyapp.android.net

  import com.squareup.moshi.Moshi
  import retrofit2.Retrofit
  import retrofit2.converter.moshi.MoshiConverterFactory
  import retrofit2.converter.scalars.ScalarsConverterFactory

  object ApiClient {
      private val moshi = Moshi.Builder().build()

      private val retrofit = Retrofit.Builder()
          .baseUrl(BuildConfig.BASE_URL)
          .addConverterFactory(ScalarsConverterFactory.create())
          .addConverterFactory(MoshiConverterFactory.create(moshi))
          .build()

      val api: ApiService = retrofit.create(ApiService::class.java)
  }
  ```

---

## 5. Activity içinde kullanım
- `TestDbActivity.kt` örneği:
  ```kotlin
  ApiClient.api.ping().enqueue(object : Callback<String> {
      override fun onResponse(call: Call<String>, resp: Response<String>) {
          val body = resp.body()?.trim()
          showSnack("Ping OK: $body")
      }

      override fun onFailure(call: Call<String>, t: Throwable) {
          showSnack("Ping ERR: ${t.message}")
      }
  })
  ```

---

## 6. Sonuç
✅ Android uygulaması `ping` endpointine bağlanıp **"pong"** cevabını aldı.

Sonraki adımlar:
- Transactions ve Accounts endpointlerini test etme
- JSON modellerini (Moshi ile) tanımlayıp liste olarak parse etme
- RecyclerView ile ekranda gösterme

