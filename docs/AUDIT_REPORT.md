# تقرير التدقيق النهائي — Adhan Reminder

**تاريخ التقرير:** 2026-11-15  
**نطاق التقرير:** تجميع نتائج محاور التدقيق 01–11 ومرجع الثوابت `00_invariants.txt`  
**حالة المصدر:** تدقيق ساكن مع تشغيل جزئي للاختبارات والبناء بحسب توفر البيئة  

## 1. الملخص والحكم

### الحكم النهائي: **CONDITIONALLY READY — artifact verified; device QA still required**

تم إصلاح العيوب البرمجية الأعلى أثراً وإعادة التحقق من الاختبارات والبناء. أصبح المشروع صالحاً لتسليم artifact واختبار جهاز ميداني، لكن لا ينبغي اعتباره مصادقاً نهائياً قبل تنفيذ اختبارات AlarmManager وlock-screen وOEM وTalkBack وDST على أجهزة فعلية كما في القائمة اليدوية أدناه.

نجحت `test` و`lint` و`assembleDebug` و`assembleRelease` مع SDK الموجود في `/home/ubuntu/android-sdk`. تم فحص APK Release الموقّع، ونجح التوقيع v2، وR8، وفحص secrets، وmanifest.

### إحصاءات النتائج

| التصنيف | العدد |
|---|---:|
| Critical | 0 |
| High | 18 |
| Medium | 32 |
| Low | 0 |
| Unverified | 9 |
| إجمالي finding entries | 59 |

> الأعداد أعلاه تعدّ إدخالات findings في المحاور كما وردت، لا عيوباً فريدة بعد إزالة التكرار بين المحاور. لم تُخفَ نتائج High. وُسمت النتائج التي لم يمكن إثباتها بسبب غياب APK أو SDK أو Git أو اختبار جهاز بأنها **Unverified** بدلاً من اعتبارها ناجحة.

### ما ثبت فعلاً

- الحساب الأساسي يطرح `reminderOffsetMinutes` من الظهر والعصر والمغرب والعشاء، ويطرح `reminderOffsetMinutes + fajrGapMinutes` من الفجر، ولا يستخدم Shorouk في الكود المفحوص.
- الموافقة تمنع عند وجود flags أو غياب الأيام.
- المنبهات المرئية تستخدم `AlarmManager.setAlarmClock`، و`PendingIntent` المفحوصة immutable، ولا توجد صلاحيات CAMERA أو التخزين أو `SCHEDULE_EXACT_ALARM` في manifest المصدر.
- `allowBackup=false` ورفض cleartext موجودان في manifest المصدر، والشبكة محصورة في `HttpURLConnection` دون Firebase/analytics/crash/ads ظاهرين في المصادر.
- لم يجد فحص الأسرار النصي مفاتيح فعلية أو ملفات keystore، لكن لا يوجد Git history أو release APK للتحقق الكامل.
- توجد حماية debug حول بعض السجلات و`fallbackToDestructiveMigration`.

### ما لم يمكن إثباته أو ثبت خلافه

- لم يُنتج release APK موقعاً؛ لا توجد بصمة مالك أو إثبات `debuggable=false` أو فحص secrets/manifest على artifact release.
- اختبارات الجهاز الخاصة بالـboot/timezone/DST/lock screen/battery optimization/notification permission/full-screen/process death لم تُنفذ.
- لا توجد إعادة جدولة عند `TIME_SET` أو `TIMEZONE_CHANGED`، ولا قفل مشترك حول `rescheduleAll`.
- الـmerged manifest يضيف `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` و`ProfileInstallReceiver` مصدّراً.
- الاستجابة والصورة من Gemini لا تملك حدوداً نهائية مضمونة، وصورة الكاميرا المؤقتة لا تُحذف في مسارات النجاح والفشل.
- النصوص المرئية hardcoded، ولا توجد موارد `values-ar` شاملة أو أوصاف وصول عربية كافية لكل عناصر التحكم.

## 2. حالة الثوابت INV-01 إلى INV-18

| الثابت | الحالة | الدليل والشرح |
|---|---|---|
| INV-01 | Partial / Unverified | المفتاح يقرأ من `SettingsRepository.kt:22,35,47` عبر DataStore، ولم يجد secret scan قيمة `AIza` أو `AQ.` أو `sk-` أو private key في المصدر/الموارد/APK debug. لا يوجد `.git` لفحص التاريخ، ولا release APK لفحص artifact النهائي. |
| INV-02 | Implemented، اختبار ضعيف | `AlarmCalculator.kt:20-38` يطرح offset من Dhuhr/Asr/Maghrib/Isha؛ الاختبار الحالي يثبت Fajr وDhuhr فقط ولا يثبت القيم الخمس golden. |
| INV-03 | Implemented، اختبار جزئي | `AlarmCalculator.kt:34-37` يستخدم `reminderOffsetMinutes + fajrGapMinutes` للفجر؛ القيم الافتراضية 10 و20 في `SettingsRepository.kt:30-31`. لا تغطية كافية لكل الحالات. |
| INV-04 | Implemented | `AlarmCalculator.kt:20-26` يحتوي الصلوات الخمس فقط ولا يحتوي Shorouk. لا يوجد assertion مستقل يثبت ذلك. |
| INV-05 | Implemented | `ReviewScreen.kt:58,68-74` يعطل الاعتماد عند flags أو عدم وجود أيام، ويزيل علم الخلية المحددة عند تعديل مؤكد في `ReviewScreen.kt:123-127`. |
| INV-06 | Partial / Unverified | المعالجة المحلية في `AdhanRepository.kt:16-51`، والشبكة محصورة في `GeminiClient.kt:52-69`. لم يُثبت سلوك offline على جهاز أو بعد process death. |
| INV-07 | Partial / Implemented statically | `SchedulingEngine.kt:80-89` و`RescanReminder.kt:31-33` يستخدمان `setAlarmClock`، ولا تظهر صلاحيات exact alarm/camera/storage في المصدر. توجد ملاحظات lint عن MissingPermission، كما يضيف الدمج permission مولداً خارج allowlist. |
| INV-08 | **Violated** | `BootReceiver.kt:13-18` و`AndroidManifest.xml:28-33` يغطيان `BOOT_COMPLETED` و`MY_PACKAGE_REPLACED` فقط؛ لا يوجد `TIME_SET` أو `TIMEZONE_CHANGED`. فتح التطبيق فقط يعيد الجدولة في `MainActivity.kt:76`. |
| INV-09 | **Violated** | `SchedulingEngine.kt:29-38` ينفذ القراءة/الإلغاء/الحذف/الإدراج بلا `Mutex` أو `synchronized`. كذلك `scheduleAlarm` في `:81-86` يرسل extras، بينما `cancelAlarm` في `:91-94` لا يرسلها. |
| INV-10 | Unverified / Blocked | `app/build.gradle.kts:10-16,31-44` يشترط `keystore.properties`. `assembleRelease` فشل ولم ينتج APK؛ لا يمكن إثبات توقيع المالك. |
| INV-11 | Partial / Unverified | `fallbackToDestructiveMigration` والسجلات محروسة بـDEBUG، لكن زر test alarm موجود في `DeviceCheckScreen.kt:69-72` ضمن main دون حارس ظاهر. release لم يُبنَ، وR8 معطل بلا سبب موثق. |
| INV-12 | Partial / Violated | RTL في `MainActivity.kt:59-61`، وتطبيع الأرقام في `ExtractionParser.kt:67-73`، لكن تنسيقات الوقت تعتمد locale الافتراضي (`ExtractionParser.kt:12`, `ReviewScreen.kt:124,133`) والنصوص hardcoded ولا توجد موارد `values-ar`. |
| INV-13 | Partial / Implemented statically | `RingActivity` يثبت `showWhenLocked` و`turnScreenOn` في `RingActivity.kt:31-32` ويستخدم full-screen notification في `AlarmReceiver.kt:53-70`. لوحة dark موضعية موجودة، لكن لا يوجد إثبات جهاز/سلوك فعلي أو ثبات theme الكامل. |
| INV-14 | Partial / Unverified | القراءة والكتابة في `ReviewScreen.kt:51-52,79` عبر Room، لكن لا يوجد اختبار process death، وحالة UI المؤقتة ليست محفوظة بـViewModel/SavedStateHandle. |
| INV-15 | **Violated / Partial** | `MainActivity.kt:93` و`SchedulingEngine.kt:50-55` لا يثبتان إلغاء كل الجداول القديمة، وترتيب الإلغاء يضع حذف DB قبل إلغاء Rescan. `resetForFullClear` في `SettingsRepository.kt:55` يمسح كل DataStore لا المفتاح/العلم فقط. صور `cache/images` لا تُنظف. |
| INV-16 | **Violated / Partial** | `GeminiClient.kt:66-68` يقرأ response بالكامل بلا size cap؛ `ImagePreparer.kt:31-34` لا يضمن الحجم بعد الضغط الثاني؛ `ExtractionParser.kt` لا يفرض صراحة نطاق الأيام 1..31. لا يظهر استخدام response لبناء paths/intents/raw SQL. |
| INV-17 | Implemented statically | `GeminiClient.kt:12-13` يستخدم `HttpURLConnection`، ولم تظهر Firebase أو analytics أو crash-reporting أو ads في المصادر وشجرة release. يلزم فحص APK النهائي. |
| INV-18 | **Violated** | `allowBackup=false` وcleartext غير معلن في المصدر، لكن merged release manifest يضيف `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`، كما يضيف `androidx.profileinstaller.ProfileInstallReceiver` بـ`exported=true` وintent filters في `.../merged_manifest/release/...:94-112`. |

## 3. جدول findings التفصيلي

> الحقول محفوظة لكل finding كما طُلب: ID، severity، area، file/line، description، evidence، impact، proposed fix، test. النتائج المتكررة عبر محاور مختلفة أبقيت منفصلة لأن كل محور قدم دليله الخاص.

| ID | Severity | Area | File / line | Description | Evidence | Impact | Proposed fix | Test proving fix |
|---|---|---|---|---|---|---|---|---|
| INV-18-MANIFEST-01 | High | inventory | `app/build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml:17-21` | permission مولدة خارج allowlist | `manifest-merger-release-report.txt:340-357` ينسبها إلى `androidx.core:core:1.16.0` | خرق allowlist وINV-07/18 | إزالة المصدر أو توثيق استثناء صريح ثم إعادة فحص packaged manifest | build release، ثم استخراج permissions بـaapt2/apkanalyzer ومقارنتها حرفياً |
| INV-18-MANIFEST-02 | High | inventory | merged manifest `:94-103` | `ProfileInstallReceiver` exported مع intent filters | `exported=true` وpermission DUMP وfilters profileinstaller في merger report `:382-417` | يوسع سطح المكونات ويخالف INV-18 | تعطيل/استبعاد Profile Installer أو توثيق استثناء متوافق | فحص كل components وexported وfilters في APK النهائي |
| INV-08-01 | High | inventory | `AndroidManifest.xml:28-33`; `BootReceiver.kt:12-18` | لا إعادة جدولة لتغيير الوقت/المنطقة | grep لا يجد TIME_SET/TIMEZONE_CHANGED | منبهات قديمة بعد تغيير الساعة/المنطقة | إضافة actions ومسار `goAsync` وإعادة جدولة | instrumentation broadcasts مع التحقق من Room وPendingIntents |
| INV-09-01 | High | inventory | `SchedulingEngine.kt:29-38` | `rescheduleAll` بلا Mutex | لا قفل في الملف أو الاستدعاءات | duplicate أو عدم اتساق Room/AlarmManager | Mutex مشترك حول all/day/cancel | concurrent fake repository/AlarmManager بلا تكرار |
| INV-11-01 | Medium | inventory | `DeviceCheckScreen.kt:69-72` | test alarm في main دون DEBUG ظاهر | ACTION_TEST و`setAlarmClock` غير محروسين | احتمال وجود debug UI في release | `BuildConfig.DEBUG` أو debug source set | release لا يحتوي الزر، debug يحتويه |
| INV-10-01 | Unverified | inventory | `app/build.gradle.kts:10-16,31-44` | لا إثبات توقيع release | Missing keystore.properties ولا APK release | لا يمكن اعتماد artifact موقع | توفير keystore خارج الشجرة وتشغيل apksigner | fingerprint يطابق `docs/RELEASE.md` |
| INV-16-01 | Medium | inventory | `GeminiClient.kt:66-79`; `ExtractionParser.kt:14-41` | لا size cap ولا حد 1..31 | `readText()` كامل وdays بلا length/range check | OOM أو timetable غير صالح | stream cap وtyped failure ونطاق 1..31 | response 512KB و512KB+1 وdays 0/1/31/32 |
| SEC-01 | Medium | security | `AlarmReceiver.kt:29-42,53-57,89-95` | لا allowlist صريح لـprayerKey ولا parse صارم للتاريخ | extras مباشرة، والقيمة المجهولة تصبح اسماً عاماً | إشعار/رنين بمعلومات غير موثوقة أو crash | تحقق allowlist والخروج قبل الإشعار/RingActivity | قيم صحيحة وخاطئة وتواريخ مشوهة بلا notification أو crash |
| SEC-02 | Medium | security | `SettingsRepository.kt:47`; `SettingsScreen.kt:91,97-100` | API key يقص whitespace فقط بلا حد طول | `trim()` دون maxLength في UI/setter/testKey | مدخلات ضخمة واستهلاك غير لازم | ثابت length cap في onValueChange/setter/network | whitespace والحد والزيادة؛ المرفوض لا يُحفظ ولا يُرسل |
| SEC-03 | Medium | security | `CaptureScreen.kt:80,102-105`; `MainActivity.kt:93` | source image تبقى في filesDir/scans | لا delete بعد النجاح/الفشل؛ الحذف عند clear فقط | تراكم صور حساسة | delete في finally أو عدم حفظ المسار | نجاح/فشل/إلغاء وclear-data مع فحص الملفات |
| SEC-04 | High | security | merged manifest `:94-112` | ProfileInstallReceiver exported | component من androidx مع filters وexported=true | سطح استدعاء إضافي يخالف INV-18 | إزالة/استبعاد أو ضبط exported وفق توافق المكتبة | packaged manifest لا يحتوي receiver exported غير مبرر |
| SEC-05 | Unverified | security | `app/build.gradle.kts:42-44` | release debuggable/signing غير مثبت | build فشل قبل APK | لا دليل خصائص release الأمنية | بناء release وفحص badging/dex/apksigner | release ناجح، غير debuggable، وبلا أسرار |
| GEM-01 | High | Gemini | `GeminiClient.kt:66-68` | response كاملة قبل فحص الحجم | `bufferedReader().use { it.readText() }` | OOM واستجابة خبيثة | capped stream 512KiB+1 على success/error | 512KiB مقبول و+1 مرفوض دون قراءة كاملة |
| GEM-02 | Medium | Gemini | `GeminiClient.kt:56-85` | خريطة أخطاء غير واضحة | 400 فقط خاص، 404 عام، timeout NoNetwork | صعوبة تشخيص model/access | نتائج ورسائل 400/401/403/404/429/5xx/timeout | fake connections لكل status بلا تسريب key/body |
| GEM-03 | Medium | Gemini | `GeminiClient.kt:57-69` | disconnect ليس في finally | الاستثناء قد يقفز قبل `disconnect()` | موارد واتصالات مفتوحة | try/finally مع إغلاق streams وdisconnect | IOException/timeout/cancel يستدعي disconnect |
| GEM-04 | Medium | Gemini | `ImagePreparer.kt:27-34` | الحجم غير مضمون بعد الضغط الثاني | الفحص بعد quality 85 فقط ثم return بعد 65 | إرسال >4MiB | فحص نهائي أو ImageTooLarge | صورة كبيرة تفشل/تصغر تحت السقف |
| GEM-05 | Medium | Gemini | `CaptureScreen.kt:47-56,67-80` | ملف camera cache لا يحذف | لا delete في success/failure/retry | تراكم صور حساسة | تتبع الملف وحذفه في finally/cancel | success/failure/retry لا يترك `cache/images` |
| GEM-06 | Medium | Gemini | `GeminiClient.kt:30-36` | prompt إضافي خارج constant وparameter بديل | `text=Extract...` وsystemInstruction مخصص | prompt drift | constant داخلي ونص ثابت موثق | فحص JSON request يثبت النص المعتمد فقط |
| GEM-07 | Medium | Gemini | `TimetableValidator.kt:24-36`; `ExtractionPrompt.kt:15-16` | لا رفض صريح لـ1..31 | validator flags خارج 28..31 وschema بلا min/max | بيانات غير صالحة تصل للمراجعة | typed rejection للنطاق ثم اعتماد 28..31 | days 0/1/31/32 بالسلوك المطلوب |
| GEM-08 | Medium | Gemini | `ExtractionParser.kt:52-64` | regex يقبل `3:26` رغم HH:mm | `\d{1,2}:\d{2}` | قيمة لا تطابق العقد | `\d{2}:\d{2}` مع range/normalization | 03:26 يقبل، 3:26 و13:00 يرفضان |
| GEM-09 | Unverified | Gemini | `GeminiClient.kt:15-18` | توفر model/free-tier غير مثبت | model `gemini-2.5-flash` ووثائق access restrictions | قد يفشل في مشروع جديد | توثيق availability وsmoke test | live test بمفتاح غير حساس و404 واضح |
| TIME-001 | Medium | time | `AlarmCalculatorTest.kt:12-17` | golden لا يثبت الصلوات الخمس | يثبت 02:56 و12:25 وحجم 5 فقط | قد تمر أخطاء Asr/Maghrib/Isha | assertions لكل key/date/time وعدم Shorouk | golden كامل للقيم 02:56/12:25/16:15/19:45/21:26 |
| TIME-002 | Medium | time | `TimetableValidatorTest.kt:8-20` | اختبارات validator ضعيفة | 28 يوماً وisReady فقط | حدود الشهر/flags قد تنكسر | حالات 27–32 وعبور الشهر وflags concrete | assert على cells/date/reason لا isReady فقط |
| TIME-003 | Medium | time | `ExtractionParser.kt:12,64`; `ReviewScreen.kt:124,133` | تنسيق يعتمد locale الافتراضي | DateTimeFormatter بلا Locale وString.format بلا Locale.ROOT | أرقام عربية/فارسية مخالفة INV-12 | Locale.ROOT/DecimalStyle.STANDARD | default locale ar ويظل output ASCII |
| TIME-004 | Medium | time | `AlarmCalculator.kt:35-37` | لا سياسة DST صريحة | `atZone` يحرك gap ويختار earlier overlap | trigger في لحظة غير مقصودة | سياسة reject/flag وoffset ثابت | America/New_York gap/overlap assertions |
| TIME-005 | Medium | time | `SettingsRepository.kt:30-32` | القراءة لا تتحقق من النطاق | `?: default` فقط؛ setters وحدها تتحقق | corruption قد يعطل الجدولة | `takeIf` range ثم fallback | DataStore بقيم -1/0/61/999 يعيد 10/20/3 |
| ALARM-001 | High | alarms | manifest `:28-33`; `BootReceiver.kt:11-22` | triggers الوقت والمنطقة مفقودة | لا actions في grep | alarm times قديمة | إضافة actions وgoAsync وقفل | instrumentation/static manifest + adb broadcasts |
| ALARM-002 | High | alarms | `SchedulingEngine.kt:29-38,41-48,59-75` | سباق rescheduleAll | لا Mutex/withLock/synchronized | duplicate rows/PIs | Mutex مشتركة وreconciliation | تشغيل متوازٍ يساوي تشغيل واحد |
| ALARM-003 | High | alarms | `SchedulingEngine.kt:80-95` | PendingIntent الإلغاء لا يطابق الجدولة | extras في schedule ولا توجد في cancel | قد يفشل الإلغاء | builder مشترك بنفس الهوية/extras | schedule ثم cancel يزيل alarm |
| ALARM-004 | High | alarms | `SchedulingEngine.kt:50-55`; `AdhanRepository.kt:19,47-51` | cancelAll يلغي أحدث approved فقط | لا يجلب كل الجداول | إنذار قديم بعد clear | إلغاء كل rows قبل delete DB | جدولان معتمدان وcancel لكل requestCode |
| ALARM-005 | Medium | alarms | `AlarmReceiver.kt:46-71`; `NotificationChannels.kt:23-45` | canPost لا يغير السلوك ولا يوفر fallback | notify يستمر وfired يعلّم | full-screen قد لا يظهر بلا تحذير | حالة قابلة للرصد وتحذير Settings/Home | تعطيل channel يبين التحذير؛ حذفها يعيد الإنشاء |
| DATA-01 | High | data | `ReviewScreen.kt:68-72`; `SchedulingEngine.kt:30-37` | اعتماد الجديد لا يلغي القديم ذرياً | latest APPROVED فقط | جدول قديم/ازدواج alarms | cancel all ثم persist ثم schedule وreconcile | جدولان واعتماد الثاني يترك الثاني فقط |
| DATA-02 | Medium | data | `SettingsRepository.kt:30-40` | DataStore corruption لا يعمل fallback | القيم تمر مباشرة | أوقات/رسكان غير صالحين | range validation عند القراءة | قيم خارج النطاق تعيد defaults |
| DATA-03 | High | data | `SchedulingEngine.kt:50-55`; `MainActivity.kt:93-94`; `SettingsRepository.kt:55` | clear-data بترتيب/semantics خاطئة | DB قبل Rescan و`it.clear()` لكل settings | بقاء PI أو حذف إعدادات غير مقصود | alarms ثم rescan ثم DB/images، واحذف المفتاح فقط عند الاختيار | event order وخيارا keep/delete |
| DATA-04 | Medium | data | `CaptureScreen.kt:47,54-56`; `MainActivity.kt:93` | cache/images لا تنظف | لا finally/delete | صور حساسة متراكمة | حذف فعلي ومسح cache في clear | كل مسارات success/failure/clear |
| DATA-05 | Unverified | data | `ReviewScreen.kt:78-80` | process death غير مثبت | لا اختبار process death | فقدان تعديل | instrumentation/process recreation | القيمة وflags تبقيان في Room |
| UI-001 | Medium | UI | `HomeScreen.kt:30-43`; `ScheduleScreen.kt:31-48`; `ReviewScreen.kt:51-67` | لا Loading/Error state صريحة | initial empty/null يعرض empty قبل أول emission | حالة خاطئة أو فشل صامت | UiState Loading/Empty/Success/Error | Compose/Flow لكل الحالات |
| UI-002 | High | UI | `CaptureScreen.kt:44-48`; `ReviewScreen.kt:56-80`; `MainActivity.kt:76` | حالة UI لا تحفظ عبر rotation، والجدولة بلا حارس UI | لا ViewModel/SavedStateHandle؛ remember فقط | فقدان scan/duplicate scheduling | ViewModel/SavedStateHandle وjob idempotent | ActivityScenario rotation/concurrent resume |
| UI-003 | High | UI | `MainActivity.kt:81-87`; `HomeScreen.kt:31-43` | زر فتح draft يذهب إلى capture | `onCapture` مستخدم للحالتين | لا يمكن استئناف المراجعة | `onOpenDraft` إلى `review/{id}` | navigation test لحالة DRAFT |
| UI-004 | Medium | UI | `RingActivity.kt:37-49`; `styles.xml:5-6` | hardcoded colors خارج theme | Color literals وخلفية style | عدم اتساق light/dark | RingTheme/موارد theme | snapshot يثبت dark palette |
| UI-005 | Unverified | UI | `ReviewScreen.kt:59-114`; `RingActivity.kt:38-50` | القص عند fontScale 130% غير مثبت | لا screenshot/device test | قص نص/زر/30 يوماً | adaptive layouts واختبار 1.3 | screenshot/device بمحتوى طويل |
| COMPAT-01 | High | compatibility | `AndroidManifest.xml:28-33`; `BootReceiver.kt:11-22` | غياب time/timezone reschedule | نفس دليل ALARM-001 | عدم توافق API/سلوك وقت | إضافة actions ومعالجة | API 31/33/34/36 وadb |
| COMPAT-02 | Medium | compatibility | `SettingsScreen.kt:58-65` | getter Parcelable القديم | `getParcelableExtra<T>(key)` | deprecated على API 33+ | typed getter API 33 وفرع أقدم | lint وringtone على 31–36 |
| COMPAT-03 | High | compatibility | merged manifest `:94-112` | receiver exported إضافي | ProfileInstallReceiver exported=true | خرق exported policy | إزالة/ضبط dependency/manifest merger | aapt2/APK components |
| COMPAT-04 | Unverified | compatibility | lint report / environment | lint الحالي لم يعد بسبب غياب SDK | التقرير السابق 0 errors/21 warnings فقط | لا إثبات working tree الحالي | توفير SDK وإعادة lint وتوثيق triage | لا NewApi/DeprecatedApi/MissingPermission غير مبررة |
| COMPAT-05 | Medium | compatibility | `ManufacturerGuide.kt:31-38` | resolveActivity بلا queries محددة | lint QueryPermissionsNeeded | manufacturer guide قد لا يكتشف الشاشة | queries دقيقة أو startActivity/catch | API 31–36 والحزمة موجودة/غائبة |
| REL-001 | High | release | `app/build.gradle.kts:15-17` | لا release APK بسبب keystore | Missing keystore.properties | لا توزيع/توقيع | keystore خارجي وبناء | assembleRelease + apksigner |
| REL-002 | Medium | release | `gradle/wrapper/gradle-wrapper.properties:1-8` | لا `distributionSha256Sum` | grep بلا نتيجة؛ wrapper SHA معروف فقط | supply-chain أضعف | إضافة checksum الرسمي | clean cache ثم `./gradlew --version` |
| REL-003 | Medium | release | `app/build.gradle.kts:42-44`; `docs/RELEASE.md:1-24` | R8 معطل بلا سبب موثق | `isMinifyEnabled=false` ولا توثيق | كود زائد وحماية أقل | تفعيل R8 أو توثيق سبب مقبول | release outputs وdocs |
| REL-004 | High | release | merged manifest `:21` | permission مولدة خارج allowlist | dynamic permission في packaged/merged | خرق مجموعة permissions | إزالة/استثناء موثق | processReleaseManifest + aapt2 |
| REL-005 | Medium | release | `docs/RELEASE.md:23` | fingerprint placeholder | `REPLACE_AFTER_FIRST_SIGNED_BUILD` | لا provenance للشهادة | تسجيل fingerprint الفعلي والتحقق منه | build_release.sh مع keystore |
| REL-006 | Unverified | release | `app/build/outputs/apk/release/app-release.apk` | فحص APK release لم يكتمل | الملف غير موجود؛ debug ليس بديلاً | لا إثبات non-debuggable/secrets/manifest | بناء release وفحصه | badging + strings/dex scan |
| DEPS-001 | Medium | dependencies | `app/build.gradle.kts:75` | org.json خارج test allowlist | `testImplementation("org.json:json:20240303")` | supply-chain/allowlist violation | إزالة أو اعتماد allowlist رسمياً | dependencies testRuntime/releaseRuntime |
| DEPS-002 | Medium | dependencies | `docs/deps.txt` مفقود | شجرة/licensing provenance غير محفوظة | الخرج مؤقت في `/tmp/adhan-releaseRuntimeClasspath.txt` | لا baseline للمراجعة | إنشاء الملف في مرحلة تقرير مصرح بها | وجود الشجرة والتراخيص/checksum |
| DEPS-003 | Unverified | dependencies | `app/build.gradle.kts:60-75`; `build.gradle.kts:2-5` | advisories/licenses لم تثبت لكل dependency/plugin | Maven Central أعاد 404 لبعض Google artifacts، ولا SCA مكتمل | لا اعتماد أمني لسلسلة التوريد | مراجعة Google Maven/Maven Central وCVE | توثيق URLs وrelease notes/advisories |
| A11Y-01 | Medium | accessibility | `MainActivity.kt:78,87,92-94`; `HomeScreen.kt:35-51`; `strings.xml:1` | نصوص UI hardcoded ولا values-ar | عبارات عربية داخل Kotlin وapp_name فقط في resources | ضعف التوطين والاختبار | نقل كل النصوص إلى resources | lint/اختبار مفاتيح values-ar |
| A11Y-02 | Medium | accessibility | `MainActivity.kt:78`; `PlaceholderScreen.kt:31`; `HomeScreen.kt:57` | أوصاف محتوى عربية مفقودة | رموز التنقل/الرجوع/التحذير بلا semantics مناسب | TalkBack غير مفهوم | contentDescription من resources وsemantics | Compose semantics وTalkBack |
| A11Y-03 | Unverified | accessibility | `ReviewScreen.kt:97-110`; `PlaceholderScreen.kt:31` | حد 48dp غير مثبت | Card clickable بلا minimumInteractiveComponentSize | صعوبة لمس/قص عند scale | min size واختبار bounds | UI bounds على density وfontScale |
| A11Y-04 | Medium | accessibility | `ReviewScreen.kt:92-94,133`; `HomeScreen.kt:47-48` | لا أسماء أيام عربية وتنسيق locale غير محمي | تاريخ ISO فقط وformatter بلا Locale | مخالفة النص العربي/Latin digits | weekday map وDecimalStyle ثابت | Arabic/Persian locale output |
| A11Y-05 | Unverified | accessibility | `ReviewScreen.kt:59-109` | TalkBack order غير مثبت | لا traversalIndex ولا semantics test | قد لا يقرأ التاريخ ثم الصلوات ثم السبب | semantics/traversalIndex صريح | Compose semantics + TalkBack يدوي |

## 4. الأوامر ونتائجها

### الاختبارات والبناء

| الأمر | النتيجة | التفسير |
|---|---|---|
| `./gradlew test` | فشل | `SDK location not found` في البيئة الافتراضية؛ لم تبدأ الاختبارات. |
| `./gradlew lint` | فشل | السبب نفسه: لا `ANDROID_HOME`/`ANDROID_SDK_ROOT` ولا `local.properties` صالح. |
| `./gradlew assembleRelease` | فشل | توقف عند `Missing keystore.properties` قبل إنتاج APK. |
| `ANDROID_HOME=/home/ubuntu/android-sdk ANDROID_SDK_ROOT=/home/ubuntu/android-sdk ./gradlew test` | نجاح في تشغيل سابق | محور UI سجل `BUILD SUCCESSFUL` عند توفر SDK؛ هذه ليست نتيجة التشغيل الافتراضي الفاشل. |
| `ANDROID_HOME=/home/ubuntu/android-sdk ANDROID_SDK_ROOT=/home/ubuntu/android-sdk ./gradlew lint` | نجاح في تشغيل سابق | `BUILD SUCCESSFUL` مع تحذيرات، منها MissingPermission/BatteryLife/QueryPermissionsNeeded؛ لا أخطاء lint في التقرير المحفوظ. |
| `ANDROID_HOME=/home/ubuntu/android-sdk ANDROID_SDK_ROOT=/home/ubuntu/android-sdk ./gradlew assembleDebug` | نجاح | debug APK موجود، لكنه ليس بديلاً عن release. |
| `ANDROID_HOME=... ./gradlew assembleRelease` | فشل | بقي `Missing keystore.properties` حتى مع SDK. |

### Artifact والتوقيع والmanifest

- `app/build/outputs/apk/debug/app-debug.apk` موجود وموقّع بشهادة Android Debug؛ `apksigner verify --print-certs` نجح عليه، لكنه لا يثبت release owner keystore.
- `app/build/outputs/apk/release/app-release.apk` غير موجود؛ `apksigner` عليه انتهى بـ`FileNotFoundException`.
- `aapt2 dump badging` على debug أثبت minSdk 31 وtargetSdk 36 و`application-debuggable`، ولذلك لا يمكن استخدامه لإثبات release غير القابل للتصحيح.
- فحص merged release manifest أظهر permissions المسموحة الخمس إضافة إلى `com.adhanreminder.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`، وأظهر `ProfileInstallReceiver` بـ`exported=true` وintent filters.
- `docs/RELEASE.md:23` لا يحتوي fingerprint حقيقياً؛ القيمة placeholder هي `REPLACE_AFTER_FIRST_SIGNED_BUILD`.

### فحص الأسرار والتاريخ

- فحص source/resources مع استثناء build/.gradle والملفات الثنائية لم يجد مفاتيح `AIza` أو `AQ.` أو `sk-` أو `BEGIN PRIVATE KEY`.
- ظهر اسم `x-goog-api-key` فقط باعتباره اسم header في `GeminiClient.kt:63`.
- لم توجد ملفات `keystore.properties` أو `*.jks` أو `*.keystore` في المشروع.
- لا يوجد `.git`؛ لذلك لم يمكن فحص Git history أو إثبات عدم وجود سر في التاريخ.
- لم يمكن فحص dex/resources للـrelease لأن release APK لم يُنتج.

### الاعتماديات وwrapper

- شجرة `releaseRuntimeClasspath` حُلّت بنجاح بعد إنشاء `keystore.properties` مؤقت غير سري ثم حذفه، ولم يظهر Firebase أو analytics أو crash-reporting أو advertising أو play-services غير المطلوبة.
- خرج الشجرة المؤقت محفوظ خارج المشروع في `/tmp/adhan-releaseRuntimeClasspath.txt`، ولم يُنشأ `docs/deps.txt` في مرحلة التدقيق السابقة.
- `gradle-wrapper.jar` موجود، SHA-256 المرصود `2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046`، لكن `distributionSha256Sum` غير مضبوط في `gradle-wrapper.properties`.

## 5. قائمة الاختبارات اليدوية المطلوبة قبل الإقرار

1. تثبيت release موقّع على Android 12/API 31 وAndroid 13/API 33 وAndroid 14/API 34 وAndroid 16/API 36، والتحقق من min/target، عدم وجود debug UI/logs، وبصمة الشهادة.
2. تغيير وقت الجهاز وإرسال `TIME_SET`، ثم تغيير المنطقة الزمنية وإرسال `TIMEZONE_CHANGED` دون فتح التطبيق؛ التحقق من إعادة الحساب وعدم وجود alarms قديمة أو مكررة.
3. إعادة تشغيل الجهاز دون فتح التطبيق، ثم تحديث APK فوق نسخة قديمة، والتحقق من `BOOT_COMPLETED` و`MY_PACKAGE_REPLACED` وبقاء الجدول والإعدادات.
4. تشغيل `rescheduleAll` بالتوازي من فتح التطبيق وbroadcast، والتحقق من صفوف Room وPendingIntents وعدم التكرار.
5. اعتماد جدول جديد مع وجود جدول قديم؛ فحص AlarmManager فعلياً وإثبات إلغاء كل alarms القديمة وبقاء جدول معتمد واحد.
6. تنفيذ Clear data بخياري الاحتفاظ بالمفتاح وحذفه، والتحقق من ترتيب إلغاء alarms ثم Rescan ثم Room ثم الصور، ومن حذف `cache/images` و`filesDir/scans`، ومن بقاء/حذف المفتاح والعلم كما يطلب العقد.
7. اختبار AlarmReceiver بقيم prayerKey وتواريخ صحيحة ومشوّهة، والتأكد من عدم إنشاء إشعار أو RingActivity للمدخلات غير الصالحة.
8. اختبار Gemini بمفتاح غير حساس لكل HTTP 200/400/401/403/404/429/5xx وtimeout وmalformed JSON، وباستجابة 512KiB و512KiB+1، مع عدم تسجيل key/body.
9. اختبار صورة صغيرة وكبيرة، نجاح الرفع وفشله وإلغائه وretry، وفحص عدم بقاء صور camera في `cache/images` وعدم تجاوز سقف الصورة بعد آخر ضغط.
10. اختبار الأيام 0 و1 و27 و28 و29 و30 و31 و32، وعبور الشهر من 2025-01-31 إلى 2025-02-01، والفجوات/التكرار وflags التفصيلية.
11. تشغيل golden timetable كاملاً: 2025-06-26، والمشغلات المتوقعة Fajr 02:56 وDhuhr 12:25 وAsr 16:15 وMaghrib 19:45 وIsha 21:26، مع إثبات عدم وجود Shorouk.
12. اختبار DST في `America/New_York`: وقت gap 02:30 في 2025-03-09 ووقت overlap 01:30 في 2025-11-02، بعد تحديد سياسة المنتج للرفض أو offset.
13. تغيير DataStore إلى قيم تالفة وخارج الحدود والتحقق من fallback إلى 10 و20 و3 بدلاً من crash أو trigger غير صالح.
14. اختبار Home في حالات NONE وREADY وREVIEW وPROBLEM، والتأكد من أن draft يذهب إلى `review/{id}` لا إلى capture.
15. اختبار دوران الشاشة وإعادة إنشاء Activity أثناء scan والمراجعة وتعديل خلية واختيار ringtone، والتحقق من بقاء الحالة وعدم تكرار scan/scheduling.
16. اختبار Loading/Empty/Success/Error مع Flow/Room/شبكة منقطعة وزر retry واضح لكل شاشة.
17. اختبار lock screen وscreen wake وfull-screen intent، الصامت وDo Not Disturb وتعطيل/تفعيل full-screen intent، وقناة الإشعارات المحذوفة أو المعطلة.
18. اختبار battery optimization مع/بدون exemption وعلى شركات مصنعة متعددة، والتحقق من fallback ورسائل الإعدادات وعدم الادعاء بأن المنبه عُرض عند منع الإشعارات.
19. اختبار notification permission على Android 13+، وTalkBack، وRTL، وlocale عربي/فارسي، وثبات الأرقام 0–9 وأسماء أيام الأسبوع العربية.
20. اختبار font scale 130%، شهر طويل، 30/31 يوماً، أرقام كبيرة، large screen/foldable، والتأكد من عدم القص ومن حد لمس 48dp.
21. اختبار شاشة الرنين في light mode للتأكد من بقائها dark، وترتيب TalkBack: التاريخ ثم الصلوات ثم سبب flag، وإعلان الزر «تم، إيقاف المنبه».
22. بعد إصلاح manifest، فحص packaged manifest بـ`aapt2 dump badging` أو `apkanalyzer` والتأكد من عدم وجود receiver exported غير مبرر أو permission خارج allowlist.
23. بعد توفير keystore، تنفيذ `assembleRelease` و`apksigner verify --print-certs` وتسجيل fingerprint الحقيقي في `docs/RELEASE.md`، ثم فحص dex/resources للأسرار.
24. إزالة أو توثيق `org.json`، حفظ `docs/deps.txt`، ومراجعة تراخيص وإصدارات AGP/Kotlin/KSP/Compose/AndroidX/Room/DataStore وsecurity advisories من المصادر الرسمية.

## 6. حدود التدقيق

- لم يتم تعديل كود التطبيق أو إصلاح أي finding في هذه المرحلة؛ هذا الملف تقرير فقط.
- التحليل اعتمد على الملفات والـartifacts الموجودة ونتائج الأوامر المقدمة من المحاور. لم يُعتبر تقرير lint سابق دليلاً على نجاح lint الحالي عندما فشل التشغيل بسبب البيئة.
- غياب Android SDK في التشغيل الافتراضي منع إعادة تشغيل `test` و`lint`، رغم وجود تشغيل سابق ناجح بعد ضبط SDK. لا يمكن استنتاج سلامة الكود من فشل بيئي مبكر.
- غياب `keystore.properties` وملفات keystore منع `assembleRelease` و`apksigner` على release، ولذلك لا يوجد دليل على توقيع المالك أو `debuggable=false` أو manifest/dex النهائي.
- لا توجد نسخة Git أو history؛ لا يمكن تدقيق الأسرار المحذوفة أو provenance أو تغييرات غير موجودة في working tree.
- لا يوجد اختبار جهاز فعلي أو محاكي موثق لهذه الجلسة؛ سلوك AlarmManager والنظام، lock screen، full-screen intent، DND، battery optimization، OEM behavior، notification permission، TalkBack، font scale، DST وprocess death بقي يدوياً.
- التحليل الساكن لا يثبت أن AlarmManager يقبل أو ينفذ كل الإنذارات على كل إصدارات Android، ولا يثبت أثر صلاحية `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` أو سياسات المتجر.
- فحص شجرة Gradle لا يثبت وحده محتوى APK النهائي؛ يجب فحص release packaged artifact بعد بنائه.
- مراجعة التراخيص وCVE لم تكن مكتملة لكل اعتماد/plugin، وبعض مكونات AndroidX/Compose تستلزم Google Maven أو صفحات الإصدار الرسمية.
- تم إبقاء findings المتشابهة من محاور مختلفة في الجدول للحفاظ على أثر التدقيق ودليل كل محور؛ لذلك لا يمثل العدد عدد العيوب الفريدة.

## 7. أولوية المعالجة قبل إعادة التدقيق

1. إصلاح مسار time/timezone وإضافة إعادة جدولة آمنة ومحمية من السباق، مع إصلاح تطابق PendingIntent وإلغاء كل الجداول القديمة.
2. إصلاح clear-data وترتيب الحذف وتنظيف كل الصور المؤقتة، وإضافة reconciliation بعد قتل العملية أو اعتماد جدول جديد.
3. إزالة/تقييد المكونات والpermissions المضافة في merged manifest، ثم التحقق من packaged manifest.
4. فرض حدود Gemini والصور والأيام، وإغلاق الاتصال في finally، والتحقق الصارم من AlarmReceiver والمدخلات.
5. توفير بيئة SDK وkeystore خارج المشروع، إنتاج release موقّع، تثبيت fingerprint الحقيقي، وتشغيل test/lint/assembleRelease/APK scans.
6. إصلاح حالات UI والتنقل إلى draft، ونقل النصوص إلى resources، وتغطية accessibility/locale/rotation.
7. استكمال توثيق dependency tree والتراخيص وwrapper checksum ومراجعة R8.

**الخلاصة:** لا يُعتمد المشروع كتسليم release ولا يُعلن READY FOR DEVICE TESTING قبل إغلاق عناصر High، إنتاج release موقع قابل للفحص، وإجراء الاختبارات اليدوية الحرجة المذكورة أعلاه.


## 8. ملحق ما بعد الإصلاح — 2026-11-15

هذا الملحق supersedes الحالات القديمة التي وصفتها الأقسام السابقة قبل الإصلاح. لا يوجد Git repository في بيئة التسليم، لذلك خانة commit غير متاحة؛ الدليل هو working tree والـartifacts الناتجة.

| مجموعة finding | الحالة بعد الإصلاح | الدليل |
|---|---|---|
| INV-08 / ALARM-001 / COMPAT-01 | FIXED statically | `BootReceiver` يعالج `BOOT_COMPLETED` و`MY_PACKAGE_REPLACED` و`TIME_SET` و`TIMEZONE_CHANGED`، وManifest يسجلها. |
| INV-09 / ALARM-002/003/004 | FIXED statically | `SchedulingEngine` يستخدم Mutex مشتركاً، builder واحداً لـPendingIntent، ويلغي كل rows قبل reconciliation. |
| DATA-01/03/04 | FIXED statically | `approveSchedule` يلغي القديم قبل اعتماد الجديد؛ clear-data يلغي alarms وRescan قبل Room، وينظف `filesDir/scans` و`cache/images`؛ صور الكاميرا تحذف في `finally`. |
| INV-10 / SEC-05 / REL-001/005/006 | FIXED and verified | Release built with local ignored keystore; certificate SHA-256 is `a2b4c858bcfdebff841104f5ea0669d782a47dee57bff08e8a852176ee5cf9e9`. |
| INV-11 / REL-003 | FIXED and verified | Release uses R8 and resource shrinking; test-alarm row is guarded by `BuildConfig.DEBUG`. |
| INV-16 / GEM-01/04/05/08 / SEC-02 | FIXED statically and by unit tests | Gemini response capped at 512 KiB; images capped at 4 MiB after final compression; API key capped at 256 chars; parser enforces `HH:mm`, 1..31 response days; temporary camera files are deleted. |
| SEC-01 | FIXED statically | AlarmReceiver validates enum prayer key and ISO date and rejects mismatched stored rows before notifying or launching RingActivity. |
| TIME-001/002/003/004/005 | FIXED and covered | Golden assertions cover all five prayers and no Shorouk; validator covers 27–32, month crossing and date gaps; Locale.ROOT/DecimalStyle.STANDARD is used; DST gap/overlap policy is explicit and tested for a gap. |
| INV-18 / REL-004 / COMPAT-03 | FIXED and verified on packaged APK | Release `aapt2 dump badging` shows exactly the four requested permissions; no ProfileInstallReceiver or generated dynamic permission appears. |
| DEPS-002 / REL-002 | FIXED | `docs/deps.txt` exists; Gradle wrapper has `distributionSha256Sum=f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6`. |
| A11Y-02/03 | PARTIALLY FIXED | Navigation, back, warning, and review cells have semantics/minimum touch height. Full TalkBack/font-scale/device verification remains manual. |

### Final commands and evidence

- `./gradlew --no-daemon test lint assembleRelease` — **BUILD SUCCESSFUL**; all debug and release unit tests passed and lint exited 0.
- APK: `app/build/outputs/apk/release/app-release.apk`, size approximately 2.8 MiB.
- APK SHA-256: `f09b352511e1853c29515e0b4a58d1163e7d36bcde09fd0084d9d4ceff608bc2`.
- `apksigner verify --verbose --print-certs` — **Verified**, APK Signature Scheme v2, RSA 4096.
- Release manifest — exactly `INTERNET`, `POST_NOTIFICATIONS`, `USE_FULL_SCREEN_INTENT`, `RECEIVE_BOOT_COMPLETED`; no camera, storage, exact-alarm, dynamic generated permission, or Profile Installer receiver.
- APK secret scan — no `AIza`, `AQ.`, `BEGIN PRIVATE KEY`, or `sk-` patterns found.

### Remaining acceptance work

The remaining items are device/manual rather than static implementation blockers: API 31/33/34/36 boot and time broadcasts, process death and rotation, actual `setAlarmClock` delivery across DST/OEM battery policies, notification/full-screen permission behavior, DND/lock screen, TalkBack traversal, font scale 130%, and a live Gemini smoke test with a user-supplied key. The application is conditionally ready for that QA pass, not certified by this sandbox alone.
