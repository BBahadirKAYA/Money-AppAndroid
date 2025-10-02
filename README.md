# MoneyApp Android

MoneyApp Android, mevcut [MoneyApp (Laravel)](https://github.com/BBahadirKAYA/MoneyApp) backend’ini **mobil cihazlarda** kullanabilmek için geliştirilmiş **native Android uygulamasıdır**.  
Proje Kotlin ile yazılmıştır ve `Retrofit`, `Room`, `WorkManager` gibi modern Android kütüphanelerini kullanır.

---

## 🚀 Başlangıç

### 1. Depoyu Klonla
```bash
git clone https://github.com/BBahadirKAYA/MoneyAppAndroid.git
cd MoneyAppAndroid

2. Android Studio ile Aç

    Android Studio Hedgehog/Koala (veya üstü) versiyonu önerilir.

    Projeyi açınca Gradle bağımlılıkları otomatik olarak senkronize edilir.

3. Çalıştır

    Emülatörde ya da fiziksel cihazda ▶️ Run tuşuna basarak uygulamayı başlatabilirsin.

    Backend için varsayılan BASE_URL:

http://10.0.2.2:8000

Fiziksel cihazda çalıştıracaksan ya yerel IP adresini kullan ya da:

adb reverse tcp:8000 tcp:8000

📦 Özellikler

    ✅ Laravel backend ile REST API üzerinden haberleşme

    ✅ Room veritabanı ile offline kayıt desteği

    ✅ WorkManager ile arka plan senkronizasyon işleri

    ✅ Retrofit + Moshi + OkHttp ile modern network katmanı

    ✅ ViewBinding ile güvenli UI erişimi

🛠️ Geliştirme
Build Komutları

# Debug APK oluştur
./gradlew assembleDebug

# Release APK oluştur
./gradlew assembleRelease

Testler

./gradlew test
./gradlew connectedAndroidTest

📌 Katkı

Pull Request’ler ve öneriler her zaman açıktır.
📄 Lisans

Bu proje MIT lisansı ile lisanslanmıştır. Ayrıntılar için LICENSE

dosyasına bakınız.


---
