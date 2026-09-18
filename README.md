# Beaver Guardian

**[⬇️ Download latest APK](https://github.com/tareknahas85-star/beaver-guardian/releases/download/latest/beaver-guardian.apk)** &nbsp;|&nbsp; **[⬇️ حمّل آخر نسخة APK](https://github.com/tareknahas85-star/beaver-guardian/releases/download/latest/beaver-guardian.apk)**

![Build APK](../../actions/workflows/build.yml/badge.svg)

---

## In English

A parental control app for Android. One app, two modes: Parent and Child.

You install the same app on both phones. On your phone you pick Parent. On your child's phone you pick Child. A code of 6 letters links the two phones together.

### What you can do from your phone

- See how long your child used each app today
- Block an app, or give it a daily time limit
- Turn the internet on or off on your child's phone
- Lock your child's phone
- See your child's last location
- See the call list (numbers and times only, not the sound)
- Block calls from unknown numbers, and get the number in a notification
- Get an alert when your child leaves a safe place, like home or school
- Set rules by time of day, like sleep time or study time
- Get an alert with the location when your child presses the SOS button

**Your child always knows.** Their phone shows a notice all the time that says this phone is watched. This app does not hide itself.

### Status

It works, but it is a personal project. It is not on Google Play. You build the app yourself, or you download it from the [latest release](https://github.com/tareknahas85-star/beaver-guardian/releases/tag/latest).

### How it works

Both phones talk to your own free Firebase database. Nothing goes to any other company.

From the parent phone you set the rules: what is blocked, what the limits are, and where the safe places are. The child phone reads these rules every minute, applies them, and sends back the app usage, the location and the call list. It also keeps working after the phone restarts.

More detail: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)

### Setup

Full guide: **[docs/SETUP.md](docs/SETUP.md)**

Short steps:

1. Make a free Firebase project. Put its `google-services.json` file inside the `app/` folder.
2. In Firebase, turn on Anonymous sign in.
3. Copy [`database.rules.json`](database.rules.json) into the database rules page. **Do not skip this step.** Without it, anyone who knows your database address can read your child's location.
4. Build the app. Install it on both phones.
5. On the parent phone, copy the code and open the pairing window.
6. On the child phone, type the code, then give the permissions one by one.

To stop your child from deleting the app, there is one more step you do from a computer. It is in the setup guide.

### Call blocking

The app blocks calls from numbers that are not in your child's contacts and not in your allowed list. You get a notification with the number that called.

- **Emergency numbers are never blocked.** No setting can change this. The app checks for them before any other rule.
- It needs Android 10 or newer, and the user must accept it in a system window. Only one app on the phone can check calls, so if you turn it on here, it turns off in Truecaller or any app like it.

Caller names come from the phone contacts only. There is no service that tells you who owns a number you do not have.

### What this app cannot do

- **Record calls.** Android stopped this years ago. No app can do it.
- **Read WhatsApp or SMS messages.** Not supported, and not planned.
- **Hide from your child.** This is on purpose. See [docs/PRIVACY.md](docs/PRIVACY.md).
- **Stop the child from deleting it without a computer.** The simple protection can be turned off by the child. Real protection needs one command from a computer, on a phone that was just reset.

### What you need

- Two Android phones, Android 8 or newer (Android 10 or newer for call blocking)
- A free Firebase account
- A computer with ADB, only for the anti delete step

### Legal

Use this for **your own child under 18, and tell them about it.** Installing it on the phone of an adult without their knowledge is against the law in most countries. Details in [docs/PRIVACY.md](docs/PRIVACY.md).

### Built with

Kotlin, Firebase (database, sign in and notifications), WorkManager, and the Android services for accessibility, device policy and call screening, plus a local VPN service for the internet switch. GitHub Actions builds the app on every push.

---

## بالعربي

تطبيق رقابة أبوية لأندرويد. تطبيق واحد، وضعان: الأهل والطفل.

تثبّت نفس التطبيق على الهاتفين. على هاتفك تختار "أهل"، وعلى هاتف طفلك تختار "طفل". كود من 6 أحرف يربط الهاتفين ببعضهما.

### ماذا تستطيع أن تفعل من هاتفك

- ترى كم استخدم طفلك كل تطبيق اليوم
- تحظر تطبيقاً، أو تعطيه وقتاً محدداً في اليوم
- تفتح أو تغلق الإنترنت على هاتف طفلك
- تقفل هاتف طفلك
- ترى آخر موقع لطفلك
- ترى قائمة المكالمات (الأرقام والأوقات فقط، وليس الصوت)
- تحظر المكالمات من الأرقام المجهولة، ويصلك الرقم في إشعار
- يصلك تنبيه عندما يخرج طفلك من مكان آمن، مثل البيت أو المدرسة
- تضع قواعد حسب وقت اليوم، مثل وقت النوم أو وقت الدراسة
- يصلك تنبيه مع الموقع عندما يضغط طفلك زر الاستغاثة

**طفلك يعرف دائماً.** هاتفه يعرض إشعاراً طوال الوقت يقول إن هذا الهاتف مُراقَب. هذا التطبيق لا يخفي نفسه.

### الحالة

يعمل، لكنه مشروع شخصي. غير موجود على Google Play. تبني التطبيق بنفسك، أو تحمّله من [آخر إصدار](https://github.com/tareknahas85-star/beaver-guardian/releases/tag/latest).

### كيف يعمل

الهاتفان يتصلان بقاعدة بيانات Firebase المجانية الخاصة بك أنت. لا شيء يذهب إلى أي شركة أخرى.

من هاتف الأهل تضع القواعد: ما هو محظور، وما هي الحدود، وأين الأماكن الآمنة. هاتف الطفل يقرأ هذه القواعد كل دقيقة، ويطبّقها، ويرسل لك استخدام التطبيقات والموقع وقائمة المكالمات. كما أنه يستمر بالعمل بعد إعادة تشغيل الهاتف.

تفاصيل أكثر: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)

### طريقة الإعداد

الدليل الكامل: **[docs/SETUP.md](docs/SETUP.md)**

خطوات مختصرة:

1. أنشئ مشروع Firebase مجاني. ضع ملف `google-services.json` داخل مجلد `app/`.
2. في Firebase، فعّل تسجيل الدخول Anonymous.
3. انسخ [`database.rules.json`](database.rules.json) إلى صفحة قواعد قاعدة البيانات. **لا تتخطَّ هذه الخطوة.** بدونها، أي شخص يعرف عنوان قاعدة بياناتك يستطيع أن يقرأ موقع طفلك.
4. ابنِ التطبيق. ثبّته على الهاتفين.
5. على هاتف الأهل، انسخ الكود وافتح نافذة الربط.
6. على هاتف الطفل، اكتب الكود، ثم أعطِ الصلاحيات واحدة بعد الأخرى.

لمنع طفلك من حذف التطبيق هناك خطوة إضافية تنفّذها من كمبيوتر. تجدها في دليل الإعداد.

### حظر المكالمات

التطبيق يحظر المكالمات من الأرقام غير الموجودة في جهات اتصال طفلك وغير الموجودة في قائمة السماح عندك. ويصلك إشعار بالرقم الذي اتصل.

- **أرقام الطوارئ لا تُحظر أبداً.** لا يوجد إعداد يغيّر هذا. التطبيق يتحقق منها قبل أي قاعدة أخرى.
- يحتاج أندرويد 10 أو أحدث، ويجب أن يوافق المستخدم في نافذة من النظام. تطبيق واحد فقط على الهاتف يستطيع فحص المكالمات، فإذا فعّلته هنا سيتوقف في Truecaller أو أي تطبيق مشابه.

أسماء المتصلين تأتي من جهات اتصال الهاتف فقط. لا توجد خدمة تخبرك بصاحب رقم غير محفوظ عندك.

### ما لا يستطيع هذا التطبيق فعله

- **تسجيل المكالمات.** أندرويد منع هذا منذ سنوات. لا يوجد تطبيق يستطيع فعله.
- **قراءة رسائل واتساب أو الرسائل النصية.** غير مدعوم، وغير مخطط له.
- **الاختباء عن الطفل.** هذا مقصود. انظر [docs/PRIVACY.md](docs/PRIVACY.md).
- **منع الطفل من حذفه بدون كمبيوتر.** الحماية البسيطة يستطيع الطفل إيقافها. الحماية الحقيقية تحتاج أمراً واحداً من كمبيوتر، على هاتف تمت تهيئته للتو.

### ما الذي تحتاجه

- هاتفان أندرويد، أندرويد 8 أو أحدث (أندرويد 10 أو أحدث لحظر المكالمات)
- حساب Firebase مجاني
- كمبيوتر عليه ADB، لخطوة منع الحذف فقط

### الجانب القانوني

استخدمه من أجل **طفلك أنت، تحت 18 سنة، وأخبره بذلك.** تثبيته على هاتف شخص بالغ بدون علمه مخالف للقانون في معظم الدول. التفاصيل في [docs/PRIVACY.md](docs/PRIVACY.md).

### مبني بـ

Kotlin، و Firebase (قاعدة البيانات وتسجيل الدخول والإشعارات)، و WorkManager، وخدمات أندرويد الخاصة بإمكانية الوصول وسياسة الجهاز وفحص المكالمات، بالإضافة إلى خدمة VPN محلية لمفتاح الإنترنت. و GitHub Actions يبني التطبيق مع كل تحديث.
