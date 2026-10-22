# نور TV — Noor TV

تطبيق Android TV لشاشة Haier LE55B8600TUN (أندرويد 6، رام 1GB):

- **القرآن الكريم:** جميع القرّاء والروايات من mp3quran.net، بحث، فلترة بالحرف، مفضّلة، تشغيل متواصل بالخلفية، ومتابعة الاستماع.
- **أوقات الصلاة:** من aladhan.com لكل مدن الأردن، طريقة وزارة الأوقاف الأردنية، عدّاد للصلاة القادمة، تعديل بالدقائق، فتح إذاعة القرآن عند الأذان، وتخزين الشهر كامل للعمل بدون إنترنت.
- **راديو الأردن:** كل الإذاعات الأردنية من radio-browser.info مع إذاعات القرآن أولاً، بحث، مفضّلة، وآخر إذاعة.
- **كرتون زمان:** قوائم تشغيل القنوات الرسمية (مثل سبيستون) عبر YouTube Data API وتُفتح في SmartTube، ومكتبة فيديوهاتك من الفلاشة أو الهارد مع استكمال المشاهدة وترجمة .srt.

## البناء

### الطريقة 1: GitHub Actions (بدون تثبيت أي إشي)
1. ارفع المجلد على مستودع GitHub (خاص أو عام).
2. (اختياري) ضيف secret اسمه `YOUTUBE_API_KEY`.
3. بعد كل push، افتح تبويب **Actions** ← آخر تشغيل ← نزّل `NoorTV-apk`.

### الطريقة 2: Android Studio
1. افتح المجلد في Android Studio (Ladybug أو أحدث). إذا طلب Gradle wrapper، وافق.
2. (اختياري) ضيف في `local.properties`: `youtube.apiKey=YOUR_KEY`
3. Build ← Build APK(s)، أو من الطرفية: `gradle assembleRelease`
4. الملف: `app/build/outputs/apk/release/app-release.apk`

## التثبيت على الشاشة
- فعّل خيارات المطوّر وADB، ثم: `adb connect <TV_IP>:5555` و `adb install -r app-release.apk`
- أو انسخ الـ APK على فلاشة وثبّته من مدير الملفات بعد تفعيل "مصادر غير معروفة".

## التخصيص
- **إذاعات إضافية:** `app/src/main/assets/stations_jo.json` — ضع رابط البث في `url` (الإدخالات الفارغة تُتجاهل).
- **قنوات كرتون رسمية:** `app/src/main/assets/classic_channels.json` — `handle` (مثل `@spacetoonyoutube`) أو `channelId`، مع `displayName` و `category`.
- **الأذان:** عند دخول الوقت يفتح بث إذاعة القرآن الكريم (تختارها من الإعدادات) لمدة 5 دقائق افتراضياً، ثم يرجع التشغيل اللي كان شغّال.
- **مفتاح يوتيوب:** من الإعدادات داخل التطبيق، أو من `local.properties`.

## ملاحظات تقنية
- `minSdk 23`، Compose for TV، Media3 (ExoPlayer + MediaSession)، OkHttp، kotlinx.serialization، Coil.
- شهادة ISRG Root X1 مضمّنة لأن أندرويد 6 لا يثق بشهادات Let's Encrypt.
- `java.time` عبر core library desugaring.
- المفضّلة والإعدادات في SharedPreferences، وردود الـ API مخزنة على القرص للعمل بدون إنترنت.
- التوقيع: نسخة الـ release موقّعة بمفتاح debug لتسهيل التثبيت المباشر. غيّره قبل أي نشر.
