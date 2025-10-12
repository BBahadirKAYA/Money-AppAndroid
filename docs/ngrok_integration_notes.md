# Proje Notları – Ngrok URL → Google Sheets → Android

## Amaç
- Her sistem açıldığında ngrok’un ürettiği **public URL** otomatik olarak Google Sheets’e yazılsın.
- Android uygulaması açıldığında Google Sheets’ten bu URL’yi okuyup güncel backend adresi olarak kullansın.
- Böylece manuel URL girme ihtiyacı kalmasın.

---

## Bileşenler
1. **Google Sheets + Apps Script**
    - Tek satırda (ör. `A2`) güncel ngrok URL tutuluyor.
    - `doPost` ile yazma, `doGet` ile okuma uçları mevcut.
    - Android uygulaması `doGet` ile JSON formatında `{"base_url": "..."}` cevabı alıyor.

2. **Linux tarafı**
    - ngrok başlatıldığında API’den public URL çekiliyor.
    - Küçük bir bash script bu URL’yi `doPost` ile Sheets’e gönderiyor.
    - `systemd --user` service + timer sayesinde boot sonrası otomatik ve periyodik çalışıyor.

3. **Android tarafı**
    - Açılışta **fallback URL** (ör. `http://10.0.2.2:8000`) ile başlıyor.
    - Arka planda Sheet’ten güncel URL okunuyor.
    - Geçerli `https://…ngrok…` adresi varsa backend adresi güncelleniyor ve cihazda saklanıyor.
    - Böylece sonraki açılışlarda hızlı başlıyor.

---

## Çalışma Akışı
1. **Boot** → ngrok çalışıyor → bash script URL’yi Sheet’e POST ediyor.
2. **Uygulama Açılışı** → önce fallback veya kayıtlı URL ile başlıyor.
3. **Discovery** → Sheet’ten güncel URL GET isteği ile alınır.
4. **Geçerli URL** bulunduysa uygulama bu adresi backend olarak günceller.
5. **Cache** → adres cihazda saklanır (bir dahaki açılış için).

---

## Demo Kriterleri
- Uygulama açıldığında **elle URL girmeden** çalışabiliyor.
- Sheet ulaşılamazsa uygulama fallback ile sorunsuz devam ediyor.
- Bir kez başarılı keşiften sonra uygulama internetsiz açılışta bile son bilinen URL’yi kullanabiliyor.
- Sheet içinde güncel URL görünür.

---

## Sonraki Adımlar (ileriye not)
- Güvenlik: Web App erişimini kısıtlama, token doğrulama.
- Sheet log yapısı: zaman damgası + host bilgisi saklama.
- Android tarafında periyodik URL tazeleme (WorkManager).
- Çoklu makine desteği: en güncel satırı seçme.  
