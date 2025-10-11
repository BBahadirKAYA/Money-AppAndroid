#!/usr/bin/env bash
set -euo pipefail

################################################################################
# MoneyApp Android release script (single-source versioning, no Sheets)
# Kaynak önceliği: ENV/.env  → (yedek) gradle.properties
# Akış:
#  - Gradle'a -P ile VERSION_NAME/CODE geçirir (manifest senkron)
#  - gradle.properties içindeki VERSION_NAME/CODE'u da günceller
#  - unsigned → zipalign → sign → verify → dist/ → git/tag → GitHub release
################################################################################

die()  { echo "❌ $*"; exit 1; }
need() { command -v "$1" >/dev/null 2>&1 || die "'$1' gerekli."; }

echo "ℹ️ MoneyApp release başlıyor…"

# === 0) .env yükle (izinli anahtarlar) =======================================
if [[ -f .env ]]; then
  while IFS='=' read -r k v; do
    case "$k" in
      KS_PASS|KS_FILE|KS_ALIAS|VNAME|VCODE|DIST_DIR|BRANCH)
        [[ -n "${v}" ]] && export "$k=$v"
      ;;
    esac
  done < <(grep -E '^(KS_PASS|KS_FILE|KS_ALIAS|VNAME|VCODE|DIST_DIR|BRANCH)=' .env || true)
fi

# === 0) Config (sürüm tek kaynaktan) =========================================
# 1) ENV/.env → 2) gradle.properties → 3) hata
read_prop() { grep -E "^$1=" gradle.properties 2>/dev/null | head -n1 | cut -d= -f2-; }
if [[ -z "${VNAME:-}" ]]; then VNAME="$(read_prop VERSION_NAME || true)"; fi
if [[ -z "${VCODE:-}" ]]; then VCODE="$(read_prop VERSION_CODE || true)"; fi
[[ -n "${VNAME:-}" && -n "${VCODE:-}" ]] || die "VNAME/VCODE bulunamadı (.env veya gradle.properties)."

KS_FILE="${KS_FILE:-moneyapp-release.jks}"
KS_ALIAS="${KS_ALIAS:-moneyapp}"

# KS_PASS: ENV → .env → prompt
KS_PASS="${KS_PASS:-}"
if [[ -z "${KS_PASS}" ]]; then
  read -r -s -p "Keystore parolası (KS_PASS): " KS_PASS; echo
fi
[[ -n "${KS_PASS}" ]] || die "KS_PASS boş olamaz."

DIST_DIR="${DIST_DIR:-dist}"
BRANCH="${BRANCH:-main}"

REPO_URL="$(git remote get-url origin 2>/dev/null || echo 'https://github.com/BBahadirKAYA/MoneyAppAndroid.git')"
TAG="v${VNAME}"

# === Araç kontrolleri =========================================================
need git; need awk
[[ -f "$KS_FILE" ]] || die "Keystore yok: $KS_FILE"
git status --porcelain >/dev/null || die "Git deposu değil."

BT_DIR="${ANDROID_HOME:-$HOME/Android/Sdk}/build-tools"
if command -v fd >/dev/null 2>&1; then
  ZIPALIGN="$(fd -HI '^zipalign$' "$BT_DIR" -x | head -n1 || true)"
  APKSIGNER="$(fd -HI '^apksigner$' "$BT_DIR" -x | head -n1 || true)"
else
  ZIPALIGN="$(find "$BT_DIR" -type f -name zipalign -print 2>/dev/null | head -n1 || true)"
  APKSIGNER="$(find "$BT_DIR" -type f -name apksigner -print 2>/dev/null | head -n1 || true)"
fi
[[ -x "$ZIPALIGN"  ]] || die "zipalign bulunamadı (build-tools)."
[[ -x "$APKSIGNER" ]] || die "apksigner bulunamadı (build-tools)."

# === 1) Build (unsigned) + Gradle paramları ==================================
echo "🔧 Derleme (unsigned) başlatılıyor (v${VNAME} - ${VCODE})…"
./gradlew -q \
  -PVERSION_NAME="$VNAME" -PVERSION_CODE="$VCODE" \
  :app:clean :app:assembleRelease

# Studio görünürlüğü için gradle.properties'i de senkronla
if [[ -f gradle.properties ]]; then
  if command -v gsed >/dev/null 2>&1; then SED=gsed; else SED=sed; fi
  $SED -i "s/^VERSION_NAME=.*/VERSION_NAME=${VNAME}/" gradle.properties || true
  $SED -i "s/^VERSION_CODE=.*/VERSION_CODE=${VCODE}/" gradle.properties || true
fi

# === 2) Unsigned → Zipalign → Sign → Verify ==================================
UNSIGNED_APK=""
if [[ -f "app/build/outputs/apk/release/app-release-unsigned.apk" ]]; then
  UNSIGNED_APK="app/build/outputs/apk/release/app-release-unsigned.apk"
else
  UNSIGNED_APK="$(ls app/build/outputs/apk/release/*-unsigned.apk 2>/dev/null | head -n1 || true)"
fi
[[ -n "$UNSIGNED_APK" && -f "$UNSIGNED_APK" ]] || die "unsigned APK bulunamadı."

ALIGNED_APK="app/build/outputs/apk/release/app-release-aligned.apk"
echo "📐 zipalign → $ALIGNED_APK"
"$ZIPALIGN" -p -f 4 "$UNSIGNED_APK" "$ALIGNED_APK"

SIGNED_APK="app-release.apk"
echo "🔐 apksigner sign → $SIGNED_APK"
"$APKSIGNER" sign \
  --ks "$KS_FILE" \
  --ks-key-alias "$KS_ALIAS" \
  --ks-pass "pass:${KS_PASS}" \
  --key-pass "pass:${KS_PASS}" \
  --out "$SIGNED_APK" \
  "$ALIGNED_APK"

echo "✅ İmza doğrulama…"
"$APKSIGNER" verify --verbose --print-certs "$SIGNED_APK"

# === 3) Rename & SHA256 & dist ===============================================
NEW_BASENAME="com.moneyapp.android-v${VNAME}-${VCODE}-release.apk"
echo "📦 Yeniden adlandır → ${NEW_BASENAME}"
mv -f "$SIGNED_APK" "$NEW_BASENAME"

echo "🔑 SHA-256 hesaplanıyor…"
SHA="$(sha256sum "${NEW_BASENAME}" | awk '{print $1}')"
echo "${SHA}  ${NEW_BASENAME}" > "${NEW_BASENAME}.sha256"

echo "📁 dist/ içine taşı…"
mkdir -p "$DIST_DIR"
mv -f "$NEW_BASENAME" "$NEW_BASENAME.sha256" "$DIST_DIR/"
APK_PATH="${DIST_DIR}/${NEW_BASENAME}"
APK_SHA_PATH="${APK_PATH}.sha256"

# === 4) Git (commit + tag + push) ============================================
echo "🔖 Git commit ve tag…"
git add -u || true
git add "$0" 2>/dev/null || true
git commit -m "chore(release): ${VNAME}" || echo "ℹ️ Commit atlandı (değişiklik yok)."

if git rev-parse -q --verify "refs/tags/${TAG}" >/dev/null; then
  echo "ℹ️ Tag ${TAG} zaten var."
else
  git tag -a "${TAG}" -m "Release ${TAG}"
  echo "✅ Tag ${TAG} oluşturuldu."
fi

git push origin "HEAD:${BRANCH}" || echo "⚠️ ${BRANCH} push başarısız."
git push origin "${TAG}" || echo "ℹ️ ${TAG} push atlandı/uzakta olabilir."

# === 5) GitHub Release (opsiyonel: gh varsa) ==================================
if command -v gh >/dev/null 2>&1; then
  echo "🚀 GitHub release…"
  if gh release view "${TAG}" >/dev/null 2>&1; then
    echo "ℹ️ ${TAG} var, asset'leri güncelliyorum (--clobber)."
    gh release upload "${TAG}" "${APK_PATH}" "${APK_SHA_PATH}" --clobber
  else
    echo "🆕 ${TAG} oluşturuluyor…"
    gh release create "${TAG}" "${APK_PATH}" "${APK_SHA_PATH}" \
      --title "${TAG}" --notes "Stabil ${VNAME} yayımlandı."
  fi
  echo "✅ GitHub release tamam."
else
  echo "ℹ️ 'gh' yok → GitHub release adımı atlandı."
fi

# === 6) Özet ==================================================================
echo
echo "✅ Çıktılar (${DIST_DIR}/):"
ls -lh "${DIST_DIR}/"
echo
echo "SHA256:"
cut -d' ' -f1 "${APK_SHA_PATH}"
echo
echo "🎉 ${TAG} derleme + imzalama + (opsiyonel) GitHub release tamam!"
