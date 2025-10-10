# Ngrok URL’sini Google Sheets’e Otomatik Yazdırma

Bu doküman, her bilgisayar açılışında **ngrok public URL**’nin otomatik olarak bir Google Sheet’e yazdırılmasını anlatır. Böylece Android uygulaması veya başka cihazlardan backend’e bağlanırken güncel URL’yi kolayca alabilirsiniz.

---

## 1. Google Sheets tarafı (Apps Script)

1. İlgili Google Sheet’i aç.  
2. **Extensions → Apps Script** menüsünden yeni bir proje aç.  
3. Aşağıdaki kodu yapıştır:

```javascript
function doGet(e) {
  const url = (e.parameter.url || '').toString();
  const host = (e.parameter.host || '').toString(); // opsiyonel
  const ss = SpreadsheetApp.openById('YOUR_SHEET_ID');
  const sh = ss.getSheets()[0];

  // Tek satıra yazmak
  sh.getRange('B2').setValue(url || 'URL yok');
  sh.getRange('C2').setValue(new Date()); // Zaman damgası

  return ContentService.createTextOutput('ok');
}
```

4. **Deploy → New deployment → Web app** ile yayınla.  
   - Execute as: **Me**  
   - Who has access: **Anyone with the link** (veya uygun erişim)  
5. Çıkan **Web App URL**’yi not et.

---

## 2. Linux tarafı: Bash script

`/home/<user>/bin/ngrok2sheet.sh` dosyasını oluştur:

```bash
#!/usr/bin/env bash
set -euo pipefail

APPS_SCRIPT_URL="https://script.google.com/macros/s/DEPLOY_ID/exec"
HOSTNAME_NOW="$(hostname)"

# ngrok API hazır olana kadar bekle
for i in {1..45}; do
  if curl -sf http://127.0.0.1:4040/api/tunnels >/dev/null; then
    break
  fi
  sleep 1
done

URL="$(curl -s http://127.0.0.1:4040/api/tunnels \
  | jq -r '.tunnels[] | select(.proto=="https") | .public_url' | head -n1 || true)"

if [[ -z "${URL:-}" || "${URL}" == "null" ]]; then
  URL="$(curl -s http://127.0.0.1:4040/api/tunnels \
    | jq -r '.tunnels[] | select(.proto=="http") | .public_url' | head -n1 || true)"
fi

if [[ -z "${URL:-}" || "${URL}" == "null" ]]; then
  echo "[ngrok2sheet] URL bulunamadı." >&2
  exit 0
fi

curl -sG "$APPS_SCRIPT_URL" \
  --data-urlencode "url=$URL" \
  --data-urlencode "host=$HOSTNAME_NOW" >/dev/null || true

echo "Sheet güncellendi: $URL"
```

Çalıştırılabilir yap:
```bash
chmod +x ~/bin/ngrok2sheet.sh
```

---

## 3. systemd user service + timer

Servis dosyası: `~/.config/systemd/user/publish-ngrok-url.service`

```ini
[Unit]
Description=Publish ngrok public URL to Google Sheet
Wants=network-online.target
After=network-online.target

[Service]
Type=oneshot
Environment=APPS_SCRIPT_URL=https://script.google.com/macros/s/DEPLOY_ID/exec
ExecStart=/home/<user>/bin/ngrok2sheet.sh

[Install]
WantedBy=default.target
```

Zamanlayıcı: `~/.config/systemd/user/publish-ngrok-url.timer`

```ini
[Unit]
Description=Run publish-ngrok-url.service on boot and periodically

[Timer]
OnBootSec=30s
OnUnitActiveSec=30min
Persistent=true

[Install]
WantedBy=timers.target
```

Aktifleştir:

```bash
systemctl --user daemon-reload
systemctl --user enable --now publish-ngrok-url.timer
```

Durumu kontrol et:

```bash
systemctl --user status publish-ngrok-url.timer
systemctl --user list-timers | grep publish-ngrok-url
```

---

## 4. Android tarafı hatırlatma

- Emulator için:
  ```gradle
  buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8000\"")
  ```
- Gerçek cihaz / dış ağ için: Google Sheet’teki ngrok **https URL**’yi kullan.

---

## Güvenlik

- **ngrok authtoken**’ını gizli tut, paylaştıysan **Rotate** et.  
- Apps Script erişimini “Anyone with the link” yerine Google hesabına kısıtlamak daha güvenli olabilir.  
- Sheet’te sadece gerekli hücreleri güncelle.

---

## Önerilen commit

```bash
git add docs/ngrok-to-sheets.md
git commit -m "docs: ngrok URL’nin Google Sheets’e otomatik yazdırılması (Apps Script + systemd)"
```

