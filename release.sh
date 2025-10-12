#!/usr/bin/env bash
set -euo pipefail

################################################################################
# MoneyAppAndroid - Release Script (APK focus; optional AAB)
# - Single-source versioning: ENV/.env -> fallback gradle.properties
# - Gradle gets VERSION_NAME/CODE via -P (build.gradle.kts must read them)
# - unsigned -> zipalign -> apksign -> verify -> dist/ -> git tag -> GitHub Release
################################################################################

die()  { echo "❌ $*" >&2; exit 1; }
note() { echo "ℹ️ $*"; }
ok()   { echo "✅ $*"; }
sec()  { printf "\n\033[1;34m==> %s\033[0m\n" "$*"; }

need() { command -v "$1" >/dev/null 2>&1 || die "'$1' gerekli (kurun)."; }

# 0) .env yükle (allowlist)
load_env() {
  local allow_re='^(KS_PASS|KS_FILE|KS_ALIAS|VNAME|VCODE|DIST_DIR|BRANCH|BUILD_AAB|RELEASE_NOTES)$'
# --- .env yükle (ENV > .env) ---
# --- .env yükle (ENV > .env) ---
if [[ -f .env ]]; then
  while IFS='=' read -r k v; do
    # boş veya yorum satırlarını atla
    [[ -z "${k// }" ]] && continue
    [[ "${k:0:1}" == "#" ]] && continue
    # sadece izinli anahtarlar
    case "$k" in
      KS_PASS|KS_FILE|KS_ALIAS|VNAME|VCODE|DIST_DIR|BRANCH|BUILD_AAB|RELEASE_NOTES) ;;
      *) continue ;;
    esac
    # CRLF ve çevre tırnaklarını temizle
    v="${v%$'\r'}"; v="${v%\"}"; v="${v#\"}"; v="${v%\'}"; v="${v#\'}"
    export "$k=$v"
  done < <(grep -E '^[A-Za-z0-9_]+=.*' .env)
fi


}
load_env

note "MoneyApp release başlıyor…"

# 1) Versiyon tek kaynaktan
read_prop() { grep -E "^$1=" gradle.properties 2>/dev/null | head -n1 | cut -d= -f2-; }
: "${VNAME:=$(read_prop VERSION_NAME || true)}"
: "${VCODE:=$(read_prop VERSION_CODE || true)}"
[[ -n "${VNAME:-}" && -n "${VCODE:-}" ]] || die "VNAME/VCODE bulunamadı (.env veya gradle.properties)."

# 2) Keystore ayarları
KS_FILE="${KS_FILE:-moneyapp-release.jks}"
KS_ALIAS="${KS_ALIAS:-moneyapp}"
KS_PASS="${KS_PASS:-}"
if [[ -z "$KS_PASS" ]]; then
  read -r -s -p "Keystore parolası (KS_PASS): " KS_PASS; echo
fi
[[ -n "$KS_PASS" ]] || die "KS_PASS boş olamaz."

# Eğer KS_FILE yoksa ve keystore.jks.b64 varsa decode et
if [[ ! -f "$KS_FILE" && -f keystore.jks.b64 ]]; then
  sec "Keystore decode ediliyor (keystore.jks.b64 -> ${KS_FILE})"
  import base64, sys
  # (Bu blok bash içinde değil; sadece placeholder. Decode işini dışarıda yapın.)
fi

# 3) Tool kontrolleri
need git; need awk; need sha256sum
git status --porcelain >/dev/null || die "Git deposu değil."

# Android build-tools konumu
BT_DIR="${ANDROID_HOME:-$HOME/Android/Sdk}/build-tools"
[[ -d "$BT_DIR" ]] || die "Build-tools dizini bulunamadı: $BT_DIR"
# En yeni zipalign/apksigner'i bul
find_tool() {
  local name="$1"
  local cand
  cand="$(find "$BT_DIR" -type f -name "$name" -printf "%h/%f\n" 2>/dev/null | sort -V | tail -n1)"
  [[ -x "$cand" ]] || die "$name bulunamadı (build-tools)."
  echo "$cand"
}
ZIPALIGN="$(find_tool zipalign)"
APKSIGNER="$(find_tool apksigner)"

# 4) Parametreler
DIST_DIR="${DIST_DIR:-dist}"
BRANCH="${BRANCH:-main}"
BUILD_AAB="${BUILD_AAB:-0}"
RELEASE_NOTES="${RELEASE_NOTES:-RELEASE_NOTES.md}"
TAG="v${VNAME}"

# 5) Build (unsigned) + Gradle -P parametreleri
sec "Derleme (unsigned) başlatılıyor (v${VNAME} - ${VCODE})…"
./gradlew -q \
  -PVERSION_NAME="$VNAME" -PVERSION_CODE="$VCODE" \
  :app:clean :app:assembleRelease

# Studio görünürlüğü için gradle.properties senkron
if [[ -f gradle.properties ]]; then
  if command -v gsed >/dev/null 2>&1; then SED=gsed; else SED=sed; fi
  $SED -i "s/^VERSION_NAME=.*/VERSION_NAME=${VNAME}/" gradle.properties || true
  $SED -i "s/^VERSION_CODE=.*/VERSION_CODE=${VCODE}/" gradle.properties || true
fi

# 6) unsigned APK bul
UNSIGNED_APK=""
if [[ -f "app/build/outputs/apk/release/app-release-unsigned.apk" ]]; then
  UNSIGNED_APK="app/build/outputs/apk/release/app-release-unsigned.apk"
else
  UNSIGNED_APK="$(ls app/build/outputs/apk/release/*-unsigned.apk 2>/dev/null | head -n1 || true)"
fi
[[ -n "$UNSIGNED_APK" && -f "$UNSIGNED_APK" ]] || die "unsigned APK bulunamadı."

# 7) zipalign -> sign -> verify
ALIGNED_APK="app/build/outputs/apk/release/app-release-aligned.apk"
sec "zipalign → $ALIGNED_APK"
"$ZIPALIGN" -p -f 4 "$UNSIGNED_APK" "$ALIGNED_APK"

SIGNED_APK="app-release.apk"
sec "apksigner sign → $SIGNED_APK"
"$APKSIGNER" sign \
  --ks "$KS_FILE" \
  --ks-key-alias "$KS_ALIAS" \
  --ks-pass "pass:${KS_PASS}" \
  --key-pass "pass:${KS_PASS}" \
  --out "$SIGNED_APK" \
  "$ALIGNED_APK"

ok "İmza doğrulama"
"$APKSIGNER" verify --verbose --print-certs "$SIGNED_APK"

# 8) İsimlendir, hashle, dist'e taşı
NEW_BASENAME="com.moneyapp.android-v${VNAME}-${VCODE}-release.apk"
mv -f "$SIGNED_APK" "$NEW_BASENAME"
SHA="$(sha256sum "${NEW_BASENAME}" | awk '{print $1}')"
echo "${SHA}  ${NEW_BASENAME}" > "${NEW_BASENAME}.sha256"

mkdir -p "$DIST_DIR"
mv -f "$NEW_BASENAME" "$NEW_BASENAME.sha256" "$DIST_DIR/"
APK_PATH="${DIST_DIR}/${NEW_BASENAME}"
APK_SHA_PATH="${APK_PATH}.sha256"

# 9) (Opsiyonel) AAB üret
if [[ "$BUILD_AAB" == "1" ]]; then
  sec "AAB (bundleRelease) üretiliyor…"
  ./gradlew -q :app:bundleRelease
  AAB_SRC="app/build/outputs/bundle/release/app-release.aab"
  if [[ -f "$AAB_SRC" ]]; then
    AAB_OUT="${DIST_DIR}/com.moneyapp.android-v${VNAME}-${VCODE}-release.aab"
    cp -f "$AAB_SRC" "$AAB_OUT"
    sha256sum "$AAB_OUT" | awk '{print $1}' > "${AAB_OUT}.sha256"
    ok "AAB hazır: $(basename "$AAB_OUT")"
  else
    note "AAB bulunamadı, atlanıyor."
  fi
fi

# 10) Git (commit + tag + push)
sec "Git commit ve tag…"
git add -u || true
git add "$0" 2>/dev/null || true
git commit -m "chore(release): ${VNAME}" || note "Commit atlandı (değişiklik yok)."

if git rev-parse -q --verify "refs/tags/${TAG}" >/dev/null; then
  note "Tag ${TAG} zaten var."
else
  git tag -a "${TAG}" -m "Release ${TAG}"
  ok "Tag ${TAG} oluşturuldu."
fi

git push origin "HEAD:${BRANCH}" || note "${BRANCH} push başarısız (devam)."
git push origin "${TAG}" || note "${TAG} push atlandı/uzakta olabilir."

# 11) GitHub Release (gh varsa)
if command -v gh >/dev/null 2>&1; then
  sec "GitHub release…"
  RN_ARG=()
  [[ -f "$RELEASE_NOTES" ]] && RN_ARG=(--notes-file "$RELEASE_NOTES") || RN_ARG=(--notes "Stabil ${VNAME} yayımlandı.")
  if gh release view "${TAG}" >/dev/null 2>&1; then
    note "${TAG} var, asset'ler güncelleniyor (--clobber)."
    gh release upload "${TAG}" "${APK_PATH}" "${APK_SHA_PATH}" --clobber
    for extra in "${DIST_DIR}"/com.moneyapp.android-v${VNAME}-${VCODE}-release.aab*; do
      [[ -e "$extra" ]] && gh release upload "${TAG}" "$extra" --clobber || true
    done
  else
    note "${TAG} oluşturuluyor…"
    gh release create "${TAG}" "${APK_PATH}" "${APK_SHA_PATH}" "${RN_ARG[@]}" --title "${TAG}"
    for extra in "${DIST_DIR}"/com.moneyapp.android-v${VNAME}-${VCODE}-release.aab*; do
      [[ -e "$extra" ]] && gh release upload "${TAG}" "$extra" --clobber || true
    done
  fi
  ok "GitHub release tamam."
else
  note "'gh' yok → GitHub release adımı atlandı."
fi

# 12) Özet
printf "\n"
ok "Çıktılar (${DIST_DIR}/):"
ls -lh "${DIST_DIR}/" || true
printf "\nSHA256 (APK): %s\n" "$(cut -d' ' -f1 "${APK_SHA_PATH}")"
printf "\n🎉 %s derleme + imzalama + (opsiyonel) GitHub release tamam!\n" "${TAG}"
