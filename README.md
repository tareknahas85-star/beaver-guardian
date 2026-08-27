# Beaver Guardian

**[⬇️ حمّل الـ APK (آخر نسخة)](https://github.com/tareknahas85-star/beaver-guardian/releases/download/latest/beaver-guardian.apk)** &nbsp;|&nbsp; **[⬇️ Download latest APK](https://github.com/tareknahas85-star/beaver-guardian/releases/download/latest/beaver-guardian.apk)**

![Build APK](../../actions/workflows/build.yml/badge.svg)

---

## بالعربي

تطبيق رقابة أبوية لأندرويد. APK واحد، وضعين: **أهل** و**طفل**.

بتنصّب نفس التطبيق عالموبايلين. على موبايلك تختار "أهل"، وعلى موبايل الطفل تختار "طفل". كود من 6 حروف بيربط الجهازين.

من موبايلك تقدر:
- تشوف كل تطبيق قده استخدم اليوم
- تحظر تطبيقات، أو تحط وقت يومي محدد لأي تطبيق
- تفتح/تسكر الإنترنت عن موبايل الطفل
- تقفل موبايل الطفل
- تشوف آخر موقع للطفل
- تشوف سجل المكالمات (الأرقام والأوقات، مش الصوت)
- تحظر مكالمات من أرقام مجهولة، وتاخد تنبيه بالرقم
- تاخد تنبيه لما الطفل يطلع من مكان آمن، متل البيت أو المدرسة
- تحط قواعد حسب وقت اليوم، متل وقت النوم أو وقت الدراسة
- تاخد تنبيه SOS مع الموقع لما الطفل يدوس زر الاستغاثة

**الطفل عارف دايماً.** موبايله بيعرض إشعار ثابت إنو الموبايل تحت المراقبة. هاد التطبيق مش مصمم يختبي.

### الحالة
شغال، بس مشروع شخصي. مش على Google Play. تبني الـ APK بنفسك، أو تحمّله من [آخر إصدار](https://github.com/tareknahas85-star/beaver-guardian/releases/tag/latest).

### كيف بيشتغل
الموبايلين بيحكوا مع **قاعدة بيانات Firebase تبعك أنت**. ولا شي بيروح لأي سيرفر تاني.

```
  موبايل الأهل                Firebase                     موبايل الطفل
 ┌──────────────┐        /devices/{CODE}/              ┌──────────────────┐
 │ أوامر     ───┼──────►  commands/  ◄─────── قراءة ───┤ خدمة المراقبة    │
 │ سياسة     ───┼──────►  policy/    ◄─────── تطبيق ───┤  كل دقيقة        │
 │ تقارير    ◄──┼──────   reports/   ◄─────── كتابة ───┤                  │
 │ تنبيهات   ◄──┼──────   alerts/    ◄─────── كتابة ───┤                  │
 └──────────────┘                                      └──────────────────┘
```

الأهل بيكتبوا *سياسة*: شو محظور، شو الحدود، وين الأماكن الآمنة. تطبيق الطفل بيقرا هاي السياسة كل دقيقة، بيطبقها، وبيكتب رجوع الاستخدام والموقع وبيانات المكالمات. كمان في خدمة شغالة بالخلفية عشان تضل تشتغل بعد ما يعيد الموبايل تشغيله.

تفاصيل أكتر: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)

### الإعداد
الدليل الكامل: **[docs/SETUP.md](docs/SETUP.md)**

باختصار:
1. اعمل مشروع Firebase مجاني. حط `google-services.json` تبعه بـ `app/`.
2. بـ Firebase، فعّل تسجيل الدخول **Anonymous**.
3. انسخ [`database.rules.json`](database.rules.json) لصفحة قواعد الـ Realtime Database. **ما تتخطى هاي الخطوة.** بدونها، أي حدا يعرف عنوان قاعدة بياناتك بيقدر يقرا موقع طفلك.
4. ابني الـ APK. نصّبه على الموبايلين.
5. على موبايل الأهل، انسخ كود الربط. دوس "فتح نافذة الربط".
6. على موبايل الطفل، اكتب الكود، وبعدين أعطي الصلاحيات بالترتيب.

لمنع الطفل من حذف التطبيق فيه خطوة إضافية من كمبيوتر — موجودة بدليل الإعداد.

### حظر المكالمات
بيحظر المكالمات من أرقام مش بجهات اتصال الطفل ومش بقائمة السماح تبعك. بتاخد إشعار بالرقم يلي اتصل.

- **أرقام الطوارئ ما بتنحظر أبداً.** ما في إعداد يغيّر هاد. الفحص بيصير قبل أي قاعدة تانية.
- بده **أندرويد 10 أو أحدث**، والمستخدم لازم يوافق بنافذة نظام. تطبيق واحد بس عالموبايل يقدر يفحص المكالمات، فتفعيله هون بيعطله بـ Truecaller أو أي تطبيق مشابه.

**ما في API من Truecaller لمعرفة صاحب الرقم.** فقط بيأكد إنك صاحب الرقم يلي كتبته، كطريقة تسجيل دخول. فالأسماء جايي من جهات اتصال الموبايل نفسه. التنبيه فيه زر يفتح الرقم جوا تطبيق Truecaller لو مثبت عندك.

### شو ما بيقدر يعمله هاد التطبيق
- **تسجيل المكالمات.** أندرويد منعها من سنين. ولا تطبيق يقدر يعملها.
- **قراءة رسائل واتساب أو SMS.** مش مدعوم، ومش مخطط له.
- **يختبي عن الطفل.** هاد مقصود. شوف [docs/PRIVACY.md](docs/PRIVACY.md).
- **يمنع الحذف بدون كمبيوتر.** Device Admin لحاله ممكن الطفل يعطله. الحماية الحقيقية بدها Device Owner، يلي بده أمر وحيد من كمبيوتر على موبايل لسا مصفّر (factory reset).

### شو بدك
- موبايلين أندرويد، **أندرويد 8 أو أحدث** (أندرويد 10+ لحظر المكالمات)
- مشروع Firebase مجاني
- كمبيوتر فيه ADB، بس لخطوة Device Owner

### القانون
استخدمه لـ **طفلك تحت 18، وخبره.** تنصيبه على موبايل شخص بالغ بدون علمه غير قانوني بمعظم الدول. تفاصيل بـ [docs/PRIVACY.md](docs/PRIVACY.md).

### مبني بـ
Kotlin، View Binding، Firebase Realtime Database والمصادقة، Firebase Cloud Messaging، WorkManager، `AccessibilityService`، `DevicePolicyManager`، `CallScreeningService`، و`VpnService` محلي لمفتاح الإنترنت. GitHub Actions بيبني الـ APK بكل push.

---

## In English

A parental control app for Android. One APK, two modes: **Parent** and **Child**.

You install the same app on both phones. On your phone you choose Parent. On your child's phone you choose Child. A 6-letter pairing code links them.

From your phone you can:
- see how long each app was used today
- block apps, or give an app a daily time limit
- turn the child's internet on or off
- lock the child's phone
- see the child's last location
- see the call log (numbers and times, not the sound)
- block calls from unknown numbers, and get an alert with the number
- get an alert when the child leaves a safe place, like home or school
- set rules by time of day, like bedtime or study time
- get an SOS alert with the location when the child presses the panic button

**The child always knows.** Their phone shows a notification the whole time saying the phone is supervised. This app isn't built to hide.

### Status
It works, but it's a personal project. Not on Google Play. Build the APK yourself, or grab it from the [latest release](https://github.com/tareknahas85-star/beaver-guardian/releases/tag/latest).

### How it works
Both phones talk to **your own** Firebase Realtime Database. Nothing goes to any other company's server.

```
  Parent phone                Firebase                     Child phone
 ┌──────────────┐        /devices/{CODE}/              ┌──────────────────┐
 │ commands  ───┼──────►  commands/  ◄─────── reads ───┤ MonitorService   │
 │ policy    ───┼──────►  policy/    ◄─────── applies ─┤  every minute    │
 │ reports   ◄──┼──────   reports/   ◄─────── writes ──┤                  │
 │ alerts    ◄──┼──────   alerts/    ◄─────── writes ──┤                  │
 └──────────────┘                                      └──────────────────┘
```

The parent writes a *policy*: what's blocked, what the limits are, where the safe places are. The child app reads that policy every minute, applies it, and writes back usage, location and call data. It also keeps a background service running so it still works after a restart.

More detail: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)

### Setup
Full guide: **[docs/SETUP.md](docs/SETUP.md)**

Short version:
1. Create a free Firebase project. Put its `google-services.json` in `app/`.
2. In Firebase, turn on **Anonymous** sign-in.
3. Copy [`database.rules.json`](database.rules.json) into the Realtime Database rules page. **Don't skip this.** Without it, anyone who knows your database address can read your child's location.
4. Build the APK. Install it on both phones.
5. On the parent phone, copy the pairing code and open the pairing window.
6. On the child phone, type the code, then grant the permissions in order.

Making the app impossible to remove needs one extra step from a computer — covered in the setup guide.

### Call filtering
Blocks calls from numbers that aren't in the child's contacts and aren't on your allow list. You get a notification with the number that called.

- **Emergency numbers are never blocked.** No setting changes that — the check happens before any other rule.
- Needs **Android 10 or newer**, and the user has to approve it in a system dialog. Only one app per phone can filter calls, so turning this on turns it off in Truecaller or similar.

**There's no Truecaller API to look up who owns a number.** Their public SDK only confirms you own the number you typed, as a login method. So names come from the phone's own contacts. The alert has a button that opens the number in Truecaller, if installed.

### What this app can't do
- **Record calls.** Android blocked this years ago. No app can do it.
- **Read WhatsApp or SMS messages.** Not supported, not planned.
- **Hide from the child.** On purpose — see [docs/PRIVACY.md](docs/PRIVACY.md).
- **Stop uninstall without a computer.** Device Admin alone can be turned off by the child. Real protection needs Device Owner, which takes one command from a PC on a freshly factory-reset phone.

### What you need
- Two Android phones, **Android 8 or newer** (Android 10+ for call filtering)
- A free Firebase project
- A computer with ADB, only for the Device Owner step

### Legal
Use this for **your own child under 18, and tell them.** Installing it on an adult's phone without their knowledge is illegal in most places. Details in [docs/PRIVACY.md](docs/PRIVACY.md).

### Built with
Kotlin, View Binding, Firebase Realtime Database and Authentication, Firebase Cloud Messaging, WorkManager, `AccessibilityService`, `DevicePolicyManager`, `CallScreeningService`, and a local `VpnService` for the internet switch. GitHub Actions builds the APK on every push.
