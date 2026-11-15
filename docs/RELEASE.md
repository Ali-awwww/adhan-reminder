# إصدار التطبيق / Release

## العربية

1. ارفع `versionCode` و`versionName` في `gradle.properties` فقط.
2. تأكد من وجود `keystore.properties` خارج Git.
3. شغّل `scripts/build_release.sh`.
4. افحص SHA-256 للبصمة وسجّلها في هذا الملف بعد أول إصدار.
5. وزّع `app/build/outputs/apk/release/app-release.apk`.
6. ثبّت النسخة الجديدة فوق القديمة؛ تبقى Room وDataStore والجدولة.
7. لا يمكن خفض الإصدار على Android. إلغاء التثبيت يحذف البيانات ويستلزم تصوير الجدول من جديد.

## English

1. Bump `versionCode` and `versionName` in `gradle.properties`.
2. Keep `keystore.properties` outside Git.
3. Run `scripts/build_release.sh`.
4. Verify and record the signing fingerprint below.
5. Distribute `app/build/outputs/apk/release/app-release.apk`.
6. Installing over an older APK preserves Room, DataStore, and the approved schedule.
7. Android downgrades are not supported; uninstalling deletes local data.

certificateSha256=a2b4c858bcfdebff841104f5ea0669d782a47dee57bff08e8a852176ee5cf9e9
apkSha256=e602fe05c1275b15334014a3c5958c292d52d0e868c2310a12a846fd8feee5a2

## Release verification
- Release enables R8 and resource shrinking with Android's optimized defaults to reduce shipped code and resource surface.
- `app/proguard-rules.pro` is intentionally empty because the current code has no reflective keep rules.
- Keep the owner keystore and `keystore.properties` outside version control; both are ignored by `.gitignore`.
