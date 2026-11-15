# Adhan Reminder

تطبيق Android أصلي لمؤذن واحد، مبني بـ Kotlin وJetpack Compose وRoom وPreferences DataStore وAlarmManager. معرّف الحزمة هو `com.adhanreminder.app`، والحد الأدنى Android 12 / API 31.

## البناء

ثبّت JDK 17 أو أحدث، وAndroid SDK Platform 36 وBuild Tools 35.0.0، ثم شغّل:

```bash
./gradlew assembleDebug
```

سيظهر APK في `app/build/outputs/apk/debug/app-debug.apk`.

## الاختبارات

```bash
./gradlew test
```

## الإصدار والتوقيع

لا تُنشئ مفتاح الإنتاج داخل هذا المشروع. أنشئه على جهاز المالك عبر `scripts/create_keystore.sh`، ثم أنشئ ملفاً محلياً غير متتبع باسم `keystore.properties` يحتوي `storeFile` و`storePassword` و`keyAlias` و`keyPassword`. بعد ذلك شغّل:

```bash
./scripts/build_release.sh
```

للتحقق اليدوي:

```bash
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

## الخصوصية

بيانات الجدول والإعدادات محلية. الاتصال بالشبكة يستخدم فقط عند قراءة صورة الجدول. لا يوجد Firebase أو تحليلات أو إعلانات أو تسجيل دخول أو إرسال سجلات عن بُعد، ولا يُسجّل مفتاح API أو محتوى الصورة أو نص استجابة النموذج.
