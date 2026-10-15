#!/usr/bin/env bash
# Release imzası için keystore üretir ve GitHub secret değerlerini yazdırır.
# Kullanım:  ./scripts/create-keystore.sh [takma-ad]
# Üretilen release.keystore dosyasını GÜVENLİ bir yerde yedekle; kaybedersen uygulamayı güncelleyemezsin.
# Dosya .gitignore ile repoya girmekten korunur (*.keystore).
set -euo pipefail

ALIAS="${1:-kripton}"
OUT="release.keystore"

command -v keytool >/dev/null 2>&1 || { echo "keytool bulunamadı. JDK 17 kur."; exit 1; }
[ -e "$OUT" ] && { echo "$OUT zaten var; üzerine yazılmadı."; exit 1; }

read -r -s -p "Keystore parolası (en az 6 karakter): " STORE_PASS; echo
read -r -s -p "Parolayı tekrar gir: " STORE_PASS2; echo
[ "$STORE_PASS" = "$STORE_PASS2" ] || { echo "Parolalar eşleşmiyor."; exit 1; }
[ "${#STORE_PASS}" -ge 6 ] || { echo "Parola en az 6 karakter olmalı."; exit 1; }

# PKCS12'de anahtar parolası keystore parolasıyla aynı olmak zorundadır.
keytool -genkeypair -v \
  -keystore "$OUT" -storetype PKCS12 \
  -alias "$ALIAS" -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass "$STORE_PASS" -keypass "$STORE_PASS" \
  -dname "CN=Kripton Cod, O=Kripton, C=TR"

echo
echo "GitHub > Settings > Secrets and variables > Actions altına şunları ekle:"
echo "  KEYSTORE_PASSWORD = (girdiğin parola)"
echo "  KEY_PASSWORD      = (aynı parola)"
echo "  KEY_ALIAS         = $ALIAS"
echo "  KEYSTORE_BASE64   = aşağıdaki çıktı"
echo
base64 < "$OUT" | tr -d '\n'
echo
