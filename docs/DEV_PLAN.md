# 🧭 MoneyAppAndroid – Geliştirme Yol Haritası

## 🚀 Genel Bakış
Bu belge, MoneyAppAndroid projesinin Android–Laravel senkronizasyon süreci, CRUD akışı ve arayüz geliştirmesi için adım adım yapılacakları tanımlar.

---

## 🔹 1. Transaction Güncelleme & Silme
**Amaç:**  
Kullanıcı bir işlem satırına uzun basınca menü açılsın:
- **Düzenle:** TransactionEditBottomSheet formu dolu gelsin → güncelle.
- **Sil:** Onay dialogu → soft delete (`deleted=true, dirty=true`).

**Plan:**
- [ ] `TransactionAdapter` → long click listener
- [ ] `MainActivity` → AlertDialog: “Düzenle / Sil”
- [ ] `TransactionEditBottomSheet.newInstance(transaction)` → düzenleme modu
- [ ] `TransactionRepository.update()` → `@Update` Room metodu
- [ ] `SyncRepository.pushDirtyToServer()` → soft delete backend’e gider

---

## 🔹 2. Update Helper Düzenlemesi
**Amaç:**  
Yeni sürüm çıktığında APK güncellemesi düzgün çalışsın.

**Kontrol:**
- [ ] `UPDATE_MANIFEST_URL` testi
- [ ] `.env` sürüm kodu ↔ `update.json` eşleşmesi
- [ ] `UpdateHelper.checkForUpdates()` çağrısı
- [ ] İndirme / kurulum akışı testi

---

## 🔹 3. Kategori & Hesap Senkronizasyonu
**Amaç:**  
Uygulama açıldığında kategori ve hesapları Laravel’den çekip Room’a kaydetsin.

**Plan:**
- [x] `AccountApi` ve `CategoryApi` tanımlandı
- [x] Remote veriler Room’a insert/update ediliyor
- [ ] `SyncRepository` içine entegre edilecek (ileride)

---

## 🔹 4. UI/UX Revizyonu
**Amaç:**  
Uygulama görünümünü sadeleştirme ve kullanıcı deneyimini iyileştirme.

**Plan:**
- [x] Arka plan siyah yerine açık ton (ör. gri veya beyaz)
- [x] "-" işareti kaldırıldı (tüm liste gider olduğu için gereksiz)
- [ ] Tutarlar sağa hizalı, sabit font-feature “tnum”
- [ ] Kart tasarımı ve margin düzenlemesi

---

## 🔹 5. `toNetworkModel` JSON Dönüşümü (Sonraki Sohbet Başlangıcı)
**Amaç:**  
Android → Laravel veri formatını standartlaştırmak.

**Plan:**
- [ ] `TransactionEntity.toNetworkModel()` gözden geçirilecek
- [ ] Laravel DTO’larıyla birebir eşleştirilecek
- [ ] `SyncRepository.pushDirtyToServer()` test edilecek

---

## 🔹 6. Test & Git İşlemleri
- [ ] `adb` ile debug test
- [ ] Laravel log kontrolü (`storage/logs/laravel.log`)
- [ ] `git add . && git commit -m "feat: transaction edit/delete + sync fixes"`
- [ ] `git push` ve `tag release`

---

## 💸 TL Sembolü Kaldırma
**Durum:**  
Tüm tutar alanlarında `₺` kaldırıldı.  
Sadece sayı gösteriliyor (`12.500` gibi).  
Backend hâlâ `"currency": "TRY"` gönderiyor, ancak UI tarafında sembol yok.

**Gerekçe:**  
Uygulama tek para birimi (TRY) kullanıyor, ama sembolsüz daha sade bir görünüm sunuyor.

---

## 📘 Notlar
- Uzun basma → Düzenle / Sil menüsü + onay dialogu
- `UpdateHelper` yeniden test edilecek
- Yeni sohbet açıldığında başlanacak adım:  
  👉 **“toNetworkModel düzeltmesi”**

---
© 2025 MoneyAppAndroid Development Plan
