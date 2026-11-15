# تقرير الجودة

الحالة: مراجعة كود قبل أول بناء نهائي. لا يمكن اعتبار اختبار الأجهزة الفعلية منفذاً من بيئة التطوير وحدها.

| السيناريو | النتيجة | ملاحظات |
|---|---|---|
| قراءة جدول عينة وظهور 30 يوماً | يحتاج جهاز/مفتاح Gemini | مسار parser والتحقق مضاف |
| الرنين والهاتف مفتوح | يحتاج جهاز | AlarmReceiver وRingActivity مضافان |
| الرنين على شاشة القفل | يحتاج جهاز | setShowWhenLocked وsetTurnScreenOn مضافان |
| الصامت وعدم الإزعاج | يحتاج جهاز | قناة Usage Alarm؛ السلوك النهائي يعتمد على إعدادات النظام |
| توفير البطارية | يحتاج جهاز | فحص واستثناء مضافان |
| إعادة الجدولة بعد الإقلاع | يحتاج جهاز | BootReceiver مضاف |
| التحديث فوق نسخة قديمة | يحتاج APK موقّع | MY_PACKAGE_REPLACED مضاف |
| وضع الطيران | يحتاج جهاز | المنبهات محلية، والمسح يحتاج الشبكة |
| Force stop | يحتاج جهاز | سلوك النظام موثق |
| الإشعارات مغلقة | يحتاج جهاز | يظهر تحذير ولا تعتمد الجدولة على الإشعار |
| تعديل يوم واحد | مراجعة كود | rescheduleDay مضاف |
| الوضعان الداكن والفاتح | مراجعة كود | DataStore والثيم الديناميكي مضافان |


## تحديث التحقق الآلي — 2026-11-15

| الفحص | النتيجة | الدليل |
|---|---|---|
| Unit tests Debug/Release | ناجح | `./gradlew --no-daemon test`، كل الاختبارات نجحت، بما فيها golden timings وDST والحدود. |
| Lint | ناجح | `./gradlew --no-daemon lint`، exit code 0. |
| APK Release مع R8 | ناجح | `./gradlew --no-daemon assembleRelease`، R8 وresource shrinking مفعّلان. |
| توقيع APK | ناجح | `apksigner`: Verified باستخدام APK Signature Scheme v2، RSA 4096. |
| Manifest packaged | ناجح | أربع صلاحيات مطلوبة فقط؛ لا Profile Installer ولا dynamic permission ولا CAMERA/exact alarm. |
| Secret scan | ناجح | لا `AIza` أو `AQ.` أو private key أو `sk-` في dex/resources. |

تبقى اختبارات الجهاز الفعلية في الجدول أعلاه مطلوبة قبل الإعلان عن اعتماد نهائي، خصوصاً AlarmManager وOEM battery policies وlock screen/full-screen وTalkBack وGemini live smoke test.


## تحديث حزمة مفاتيح Gemini والتصميم — 2026-11-15

- أضيف نموذج المفتاح والفحص المحلي غير المانع للصيغة (`AIza` و`AQ.` وUNKNOWN) مع masking آمن.
- أضيف محلل أخطاء Gemini مشترك بسقف 64 KiB وتعقيم للرسائل، وأصبح عميل الجدول يستخدمه.
- أضيف `ApiKeyVerifier` بثلاث خطوات ACCESS/SERVICE/GENERATE عبر `HttpURLConnection`، مع حفظ المفتاح فقط بعد نجاح الفحص أو تحذير الحصة.
- عند فشل مفتاح جديد لا يتم استبدال مفتاح صالح محفوظ سابقاً، وتتوفر حالة FAILED وتقرير تشخيصي لا يحتوي المفتاح الكامل أو نص الاستجابة أو URL.
- أضيفت توكنز التصميم وIBM Plex Mono والمكونات الأساسية لقسم المفتاح.
- `./gradlew --no-daemon test lint assembleRelease`: **BUILD SUCCESSFUL**.
- APK Release الجديد: `app/build/outputs/apk/release/app-release.apk`.
- SHA-256: `e602fe05c1275b15334014a3c5958c292d52d0e868c2310a12a846fd8feee5a2`.
- التوقيع v2 ناجح، والصلاحيات الأربع المطلوبة فقط.
- فحص الأسرار الدقيق لم يجد مفاتيح فعلية؛ النصوص الظاهرة `AIza` و`AQ.` في APK هي أسماء صيغ وregex الخاصة بالتحقق، وليست قيماً سرية.

المتبقي يدوياً: اختبار مفتاح حقيقي على الجهاز، الحالات الشبكية والحصة، فتح AI Studio، TalkBack، والوضع الفاتح. لم يُحفظ أي مفتاح حقيقي في المشروع أو الـAPK.
