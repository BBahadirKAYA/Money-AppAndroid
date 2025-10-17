#!/bin/bash
set -e

echo "🔧 MoneyApp – Derle / Kaldır / Kur başlatılıyor..."

APP_DIR="$(dirname "$0")"
APK_PATH="$APP_DIR/app/build/outputs/apk/debug"
PKG_NAME="com.moneyapp.android"

# 1️⃣ Derleme
echo "🧱 Derleme başlıyor..."
cd "$APP_DIR"
./gradlew assembleDebug

# 2️⃣ APK yolunu bul
APK_FILE=$(ls -t "$APK_PATH"/moneyapp-*-debug.apk | head -n 1)

if [ ! -f "$APK_FILE" ]; then
  echo "❌ APK bulunamadı!"
  exit 1
fi

# 3️⃣ Eski sürümü kaldır
echo "🧹 Eski sürüm kaldırılıyor ($PKG_NAME)..."
adb uninstall "$PKG_NAME" >/dev/null 2>&1 || echo "ℹ️ Önceden yüklü değil"

# 4️⃣ Yeni sürümü yükle
echo "📲 Yeni sürüm yükleniyor..."
adb install -r -t "$APK_FILE"

# 5️⃣ Sonuç
echo "✅ Yükleme tamamlandı: $(basename "$APK_FILE")"
adb shell am start -n "$PKG_NAME/.ui.MainActivity" >/dev/null 2>&1 || true
echo "🚀 Uygulama başlatıldı."
