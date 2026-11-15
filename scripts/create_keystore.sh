#!/usr/bin/env bash
set -euo pipefail
KEYSTORE="${1:-adhan-release.jks}"
if [[ -e "$KEYSTORE" ]]; then echo "الملف موجود، لن يتم الكتابة فوقه: $KEYSTORE"; exit 1; fi
read -r -p "اسم صاحب المفتاح: " NAME
keytool -genkeypair -v -keystore "$KEYSTORE" -alias adhan -keyalg RSA -keysize 4096 -validity 10000 -storepass change-me -keypass change-me -dname "CN=$NAME, OU=Adhan Reminder, O=Adhan Reminder, C=SA"
echo "غيّر كلمات المرور قبل التوزيع، ثم اكتب keystore.properties حسب README."
