# 🦫 Beaver Guardian

تطبيق **مراقبة وإدارة أبوية** (Parental Control / MDM) لأندرويد — تطبيق واحد (APK واحد) فيه وضعان: **وليّ الأمر (Parent)** و**الطفل (Child)**. وليّ الأمر يتحكم عن بُعد بجهاز الطفل: حدود وقت التطبيقات والألعاب، حظر التطبيقات، قطع الإنترنت، سجل المكالمات، والموقع — مع **منع الحذف** إلا بموافقة وليّ الأمر.

A single-APK Android parental-control app with two modes — **Parent** and **Child**. The parent remotely controls the child device: app/game time limits, app blocking, internet cut-off, call-log visibility, and location — with **uninstall protection**.

> **الشفافية بالتصميم / Transparent by design:** جهاز الطفل يعرض إشعاراً دائماً بأنه تحت الإشراف. This is supervision the child is told about — not hidden spyware.

![Build APK](../../actions/workflows/build.yml/badge.svg)

---

## ✨ الميزات / Features

| Feature | كيف تعمل | مستوى الصلاحية |
|---|---|---|
| ⏱️ حدود وقت الشاشة والتطبيقات | `UsageStatsManager` + إغلاق التطبيق عند تجاوز الحد | Usage Access + Accessibility |
| 🚫 قفل/حظر التطبيقات + قفل فوري | إخفاء التطبيق (Device Owner) + إعادة للـ Home | Device Owner / Accessibility |
| 🌐 التحكم بالنت | VPN محلي يقطع كل الترافيك عند الطلب | VPN service |
| 📞 سجل المكالمات | الرقم/الاتجاه/الوقت/المدة (**بدون تسجيل صوت**) | `READ_CALL_LOG` |
| 📍 الموقع | آخر موقع للجهاز على Firebase | Location (background) |
| 🔒 منع الحذف | `setUninstallBlocked` + `DISALLOW_FACTORY_RESET` | **Device Owner** |

> ⚠️ **تسجيل المكالمات الصوتي غير ممكن** على Android 10+ لأي تطبيق طرف ثالث. نعرض بيانات المكالمة فقط (الرقم، الوقت، المدة، صادر/وارد). Call **audio** recording is not possible on modern Android.

---

## 🏗️ كيف تعمل / Architecture

```
   جهاز وليّ الأمر (Parent)                Firebase Realtime DB                 جهاز الطفل (Child)
  ┌───────────────────────┐            /devices/{PAIRCODE}/               ┌────────────────────────┐
  │ ParentActivity        │  ── أوامر ──►   commands/    ◄── يستمع ──────  │ MonitorService (FGS)   │
  │  • قفل / فتح          │  ── سياسة ──►   policy/      ◄── يطبّق ───────  │  • UsageStats + limits │
  │  • قطع/سماح الإنترنت   │            ◄── تقارير ───    reports/  ◄─ يرفع ─ │  • AppBlock (a11y)     │
  │  • عرض تقارير الاستخدام│                usage/ calls/ location/          │  • CallLog + Location  │
  └───────────────────────┘                                                │  • FilterVpnService    │
                                                                           │  PolicyManager (Owner) │
                                                                           └────────────────────────┘
```

الربط عبر **رمز مشترك (Pairing code)** يولّده جهاز وليّ الأمر ويُدخله جهاز الطفل. هذا الرمز هو مفتاح المسار في قاعدة البيانات.

---

## 🚀 التشغيل السريع / Quick start

1. **بناء الـ APK**: كل push إلى `main` يبني الـ APK تلقائياً عبر GitHub Actions → بتلاقي الملف بـ **Actions → آخر تشغيل → Artifacts → `beaver-guardian-debug-apk`**.
2. **Firebase**: اتبع [`docs/SETUP.md`](docs/SETUP.md) لإنشاء مشروع Firebase واستبدال `app/google-services.json`.
3. **التركيب**:
   - على جهازك: افتح التطبيق → اختر **Parent** → انسخ رمز الربط.
   - على جهاز بنتك: اختر **Child** → أدخل الرمز → فعّل الأذونات الستة بالترتيب.
4. **منع الحذف (Device Owner)**: نفّذ خطوة ADB الوحيدة في [`docs/SETUP.md`](docs/SETUP.md#device-owner).

الدليل الكامل خطوة بخطوة: **[docs/SETUP.md](docs/SETUP.md)**.

---

## 📁 بنية المشروع / Project layout

```
app/src/main/java/com/microbeaver/guardian/
├── ui/            RoleSelect · Parent · ChildSetup
├── admin/         DeviceAdminReceiver · PolicyManager (Device Owner)
├── monitor/       MonitorService · UsageTracker · AppBlockService · CallLog · Location · Boot
├── vpn/           FilterVpnService (internet control)
├── fcm/           CommandMessagingService (wake)
└── data/          Models · FirebaseRepo
```

---

## ⚖️ الاستخدام القانوني / Legal & ethical use

هذا التطبيق مُصمّم **حصراً** لاستخدام وليّ الأمر لمراقبة طفله القاصر مع علم الطفل. لا تُركّبه على جهاز شخص بالغ دون علمه — هذا غير قانوني في معظم الدول. راجع [`docs/PRIVACY.md`](docs/PRIVACY.md).

Designed **only** for a parent supervising their own minor child, with the child's awareness. See [`docs/PRIVACY.md`](docs/PRIVACY.md).
