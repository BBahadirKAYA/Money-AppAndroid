# MoneyApp Android (WebView Template)

Bu repo, mevcut MoneyApp (Laravel) kurulumunuzu **Android APK** olarak paketlemek için minimalist bir **WebView** şablonudur.

## Hızlı Başlangıç
1) `BASE_URL` değerini değiştirin: `app/build.gradle.kts` içinde
```kotlin
buildConfigField("String", "BASE_URL", ""https://your-moneyapp-url.example"")
```
MoneyApp'inizin **HTTPS** URL'si ile değiştirin.

2) Android Studio ile açın:
- `File > Open` ve bu klasörü seçin.
- İlk senkronizasyonda **Gradle Wrapper** eksikse
  ```bash
  gradle wrapper
  ```
  komutu ile `gradle/wrapper/gradle-wrapper.jar` oluşturun (ya da Android Studio otomatik yükler).

3) Çalıştırın:
- `Run ▶` ile emülatörde açın.
- **Build > Build APK(s)** ile APK üretin. Çıktı: `app/build/outputs/apk/...`

## Notlar
- WebView, **aynı origin** (BASE_URL) içinde uygulama içi; farklı domain linklerini cihazın tarayıcısında açar.
- `network_security_config`: HTTP engelli, yalnızca HTTPS.
- Deep link için `AndroidManifest.xml` içindeki `your-moneyapp-url.example` alanını kendi domain’inizle değiştirin.

## CI (Opsiyonel)
`.github/workflows/android.yml` dosyası ile **Debug APK** otomatik derlenir ve **artifact** olarak yüklenir.