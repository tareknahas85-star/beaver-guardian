# Beaver Guardian

**[⬇️ Download latest APK](https://github.com/tareknahas85-star/beaver-guardian/releases/latest/download/beaver-guardian.apk)** &nbsp;|&nbsp; **[⬇️ حمّل آخر نسخة APK](https://github.com/tareknahas85-star/beaver-guardian/releases/latest/download/beaver-guardian.apk)**

![Build APK](../../actions/workflows/build.yml/badge.svg)

---

## In English

A parental control app for Android. One APK, two modes: **Parent** and **Child**.

You install the same app on both phones. On your phone you choose Parent. On your
child's phone you choose Child. A 6-letter pairing code links them.

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

**The child always knows.** Their phone shows a notification all the time that
says the phone is supervised. This app is not made to hide.

### Status

It works, but it is a personal project. It is not on Google Play. You build the
APK yourself, or download it from the
[latest release](https://github.com/tareknahas85-star/beaver-guardian/releases/tag/latest).

### How it works

Both phones talk to **your own** Firebase Realtime Database. Nothing goes to any
other company's server.

```
  Parent phone                Firebase                     Child phone
 ┌──────────────┐        /devices/{CODE}/              ┌──────────────────┐
 │ commands  ───┼──────►  commands/  ◄─────── reads ───┤ MonitorService   │
 │ policy    ───┼──────►  policy/    ◄─────── applies ─┤  every minute    │
 │ reports   ◄──┼──────   reports/   ◄─────── writes ──┤                  │
 │ alerts    ◄──┼──────   alerts/    ◄─────── writes ──┤                  │
 └──────────────┘                                      └──────────────────┘
```

The parent writes a *policy*: what is blocked, what the limits are, where the safe
places are. The child app reads that policy every minute, applies it, and writes
back usage, location and call data. It also keeps a service running in the
background so it still works after the phone restarts.

More detail: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)

### Setup

Full guide: **[docs/SETUP.md](docs/SETUP.md)**

Short version:

1. Create a free Firebase project. Put its `google-services.json` in `app/`.
2. In Firebase, turn on **Anonymous** sign-in.
3. Copy [`database.rules.json`](database.rules.json) into the Realtime Database
   rules page. **Do not skip this step.** Without it, anyone who knows your
   database address can read your child's location.
4. Build the APK. Install it on both phones.
5. On the parent phone, copy the pairing code. Press "Open pairing window".
6. On the child phone, type the code, then give the permissions in order.

To make the app impossible to remove you need one extra step from a computer. It
is explained in the setup guide.

### About call filtering

This blocks calls from numbers that are not in the child's contacts and not on
your allow list. You get a notification with the number that called.

Two important points:

- **Emergency numbers are never blocked.** No setting can change that. The check
  happens before any other rule, so a wrong allow list can never stop a child
  from calling for help.
- It needs **Android 10 or newer**, and the user must approve it in a system
  dialog. Android does not let an app give itself this permission. Only one app
  on a phone can filter calls, so turning it on here turns it off in Truecaller
  or any similar app.

**There is no Truecaller API to look up who owns a number.** Their public SDK only
confirms that a person owns the number they typed, as a way to log in. So names
come from the phone's own contact list. The alert has a button that opens the
number inside the Truecaller app, if you have it installed.

### What this app cannot do

- **Record calls.** Android blocked this years ago. No app can do it.
- **Read WhatsApp or SMS messages.** Not supported, and not planned.
- **Hide from the child.** This is on purpose. See
  [docs/PRIVACY.md](docs/PRIVACY.md).
- **Stop uninstall without a computer.** Device Admin alone can be switched off
  by the child. Real protection needs Device Owner, which needs one command from
  a PC on a phone that was just factory reset.

### What you need

- Two Android phones, **Android 8 or newer** (Android 10+ for call filtering)
- A free Firebase project
- A computer with ADB, only for the Device Owner step

### Project layout

```
app/src/main/java/com/microbeaver/guardian/
├── ui/         role select · parent dashboard · child setup · settings · about
├── admin/      device admin receiver · policy manager (Device Owner)
├── monitor/    background service · usage · app blocking · safe zones ·
│               schedules · SOS · call log · location · boot
├── calls/      call filtering: screening service · decision rules · caller id
├── alerts/     parent notifications
├── work/       weekly report
├── vpn/        internet on/off switch
├── fcm/        push wake-up
└── data/       models · Firebase access
```

### Legal

Use this for **your own child under 18, and tell them.** Installing it on an
adult's phone without their knowledge is illegal in most countries. Details in
[docs/PRIVACY.md](docs/PRIVACY.md).

### Built with

Kotlin, View Binding, Firebase Realtime Database and Authentication, Firebase
Cloud Messaging, WorkManager, `AccessibilityService`, `DevicePolicyManager`,
`CallScreeningService`, and a local `VpnService` for the internet switch.
GitHub Actions builds the APK on every push.

---

## بالعربي

تطبيق رقابة أبوية لأندرويد. ملف APK واحد، وضعان: **وليّ الأمر** و**الطفل**.

تُثبّت التطبيق نفسه على الهاتفين. على هاتفك تختار وليّ الأمر. وعلى هاتف طفلك
تختار الطفل. ويربط بينهما رمز اقتران من 6 أحرف.

من هاتفك تستطيع:

- رؤية كم استُخدم كل تطبيق اليوم
- حظر التطبيقات، أو إعطاء تطبيق حدًّا زمنيًّا يوميًّا
- تشغيل إنترنت الطفل أو إيقافه
- قفل هاتف الطفل
- رؤية آخر موقع للطفل
- رؤية سجل المكالمات (الأرقام والأوقات، لا الصوت)
- حظر المكالمات من الأرقام المجهولة، مع تنبيه يحمل الرقم
- الحصول على تنبيه عندما يغادر الطفل مكانًا آمنًا، كالبيت أو المدرسة
- ضبط قواعد حسب وقت اليوم، مثل وقت النوم أو وقت الدراسة
- الحصول على تنبيه استغاثة (SOS) مع الموقع عندما يضغط الطفل زر الطوارئ

**الطفل يعرف دائمًا.** هاتفه يعرض إشعارًا طوال الوقت يقول إن الهاتف تحت الإشراف.
هذا التطبيق ليس مصنوعًا للتخفّي.

### الحالة

يعمل، لكنه مشروع شخصي. ليس على Google Play. تبني ملف APK بنفسك، أو تحمّله من
[صفحة الإصدار الأخير](https://github.com/tareknahas85-star/beaver-guardian/releases/tag/latest).

### كيف يعمل

يتحدث الهاتفان مع قاعدة بيانات Firebase Realtime **الخاصة بك أنت**. لا شيء يذهب
إلى خادم أي شركة أخرى.

```
   هاتف وليّ الأمر              Firebase                     هاتف الطفل
 ┌──────────────┐        /devices/{CODE}/              ┌──────────────────┐
 │ commands  ───┼──────►  commands/  ◄─────── يقرأ ────┤ MonitorService   │
 │ policy    ───┼──────►  policy/    ◄─────── يطبّق ────┤  كل دقيقة        │
 │ reports   ◄──┼──────   reports/   ◄─────── يكتب ────┤                  │
 │ alerts    ◄──┼──────   alerts/    ◄─────── يكتب ────┤                  │
 └──────────────┘                                      └──────────────────┘
```

يكتب وليّ الأمر *سياسة*: ما المحظور، وما الحدود، وأين الأماكن الآمنة. ويقرأ تطبيق
الطفل تلك السياسة كل دقيقة، ويطبّقها، ويكتب في المقابل بيانات الاستخدام والموقع
والمكالمات. كما يبقي خدمة تعمل في الخلفية كي يستمر عمله بعد إعادة تشغيل الهاتف.

تفاصيل أكثر: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)

### الإعداد

الدليل الكامل: **[docs/SETUP.md](docs/SETUP.md)**

النسخة المختصرة:

1. أنشئ مشروع Firebase مجّاني. ضع ملف `google-services.json` الخاص به في `app/`.
2. في Firebase، فعّل تسجيل الدخول **المجهول (Anonymous)**.
3. انسخ [`database.rules.json`](database.rules.json) إلى صفحة قواعد قاعدة بيانات
   Realtime. **لا تتجاوز هذه الخطوة.** بدونها، أي شخص يعرف عنوان قاعدة بياناتك
   يستطيع قراءة موقع طفلك.
4. ابنِ ملف APK. ثبّته على الهاتفين.
5. على هاتف وليّ الأمر، انسخ رمز الاقتران. اضغط "فتح نافذة الاقتران".
6. على هاتف الطفل، اكتب الرمز، ثم امنح الأذونات بالترتيب.

ولجعل التطبيق يستحيل حذفه تحتاج خطوة إضافية واحدة من جهاز كمبيوتر. وهي مشروحة في
دليل الإعداد.

### عن فلترة المكالمات

يحظر هذا المكالمات من الأرقام غير الموجودة في جهات اتصال الطفل وغير الموجودة في
قائمة السماح عندك. وتصلك إشعار بالرقم الذي اتصل.

نقطتان مهمّتان:

- **أرقام الطوارئ لا تُحظر أبدًا.** لا يوجد إعداد يغيّر ذلك. يحدث الفحص قبل أي
  قاعدة أخرى، فلا تستطيع قائمة سماح خاطئة أن تمنع طفلًا من طلب النجدة.
- يحتاج **أندرويد 10 أو أحدث**، وعلى المستخدم الموافقة في نافذة نظام. لا يسمح
  أندرويد لأي تطبيق أن يمنح نفسه هذه الصلاحية. وتطبيق واحد فقط على الهاتف يستطيع
  فلترة المكالمات، فتشغيلها هنا يوقفها في Truecaller أو أي تطبيق مشابه.

**لا يوجد Truecaller API للبحث عن صاحب رقم.** الـ SDK العام عندهم يؤكّد فقط أن
الشخص يملك الرقم الذي كتبه، كوسيلة لتسجيل الدخول. لذا تأتي الأسماء من قائمة جهات
الاتصال في الهاتف نفسه. وفي التنبيه زر يفتح الرقم داخل تطبيق Truecaller، إن كان
مثبّتًا عندك.

### ما لا يستطيع هذا التطبيق فعله

- **تسجيل المكالمات.** منع أندرويد هذا منذ سنوات. لا يستطيع أي تطبيق فعله.
- **قراءة رسائل واتساب أو الرسائل النصية.** غير مدعوم، وغير مخطّط له.
- **التخفّي عن الطفل.** هذا مقصود. راجع [docs/PRIVACY.md](docs/PRIVACY.md).
- **منع الحذف بدون كمبيوتر.** صلاحية Device Admin وحدها يستطيع الطفل إيقافها.
  الحماية الحقيقية تحتاج Device Owner، وهي تحتاج أمرًا واحدًا من كمبيوتر على هاتف
  أُعيد ضبطه لتوّه لإعدادات المصنع.

### ما الذي تحتاجه

- هاتفا أندرويد، **أندرويد 8 أو أحدث** (أندرويد 10+ لفلترة المكالمات)
- مشروع Firebase مجّاني
- كمبيوتر عليه ADB، فقط لخطوة Device Owner

### بنية المشروع

```
app/src/main/java/com/microbeaver/guardian/
├── ui/         اختيار الدور · لوحة وليّ الأمر · إعداد الطفل · الإعدادات · حول
├── admin/      مستقبِل إدارة الجهاز · مدير السياسة (Device Owner)
├── monitor/    خدمة الخلفية · الاستخدام · حظر التطبيقات · المناطق الآمنة ·
│               الجداول · الاستغاثة · سجل المكالمات · الموقع · الإقلاع
├── calls/      فلترة المكالمات: خدمة الفحص · قواعد القرار · هوية المتصل
├── alerts/     إشعارات وليّ الأمر
├── work/       التقرير الأسبوعي
├── vpn/        مفتاح تشغيل/إيقاف الإنترنت
├── fcm/        الإيقاظ عبر الإشعارات
└── data/       النماذج · الوصول إلى Firebase
```

### قانونيًّا

استخدم هذا **لطفلك أنت دون 18 سنة، وأخبِره بذلك.** تثبيته على هاتف شخص بالغ دون
علمه غير قانوني في معظم الدول. التفاصيل في [docs/PRIVACY.md](docs/PRIVACY.md).

### مبنيّ بـ

Kotlin، وView Binding، وFirebase Realtime Database والمصادقة، وFirebase Cloud
Messaging، وWorkManager، و`AccessibilityService`، و`DevicePolicyManager`،
و`CallScreeningService`، وخدمة `VpnService` محلّية لمفتاح الإنترنت. وGitHub
Actions يبني ملف APK عند كل رفع.
