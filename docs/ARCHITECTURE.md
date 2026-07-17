# 🧩 Architecture / البنية التقنية

## المكوّنات / Components

### واجهة / UI (`ui/`)
- **RoleSelectActivity** — الشاشة الأولى: اختيار Parent/Child. الاختيار يُخزّن محلياً ولا يتغيّر على جهاز الطفل.
- **ParentActivity** — لوحة وليّ الأمر: رمز الربط، أوامر فورية، تقارير الاستخدام الحية.
- **ChildSetupActivity** — إدخال رمز الربط + تفعيل الأذونات الستة.

### الإشراف / Admin (`admin/`)
- **GuardianDeviceAdminReceiver** — يستقبل تفعيل Device Admin/Owner ويطبّق السياسات.
- **PolicyManager** — غلاف `DevicePolicyManager`: `lockNow`, `setUninstallBlocked`, `setApplicationHidden`, قيود المستخدم.

### المراقبة والتطبيق / Monitoring (`monitor/`)
- **MonitorService** — Foreground Service دائم. القلب: يستمع لـ `policy`+`commands`، وكل دقيقة يرفع التقارير ويطبّق الحدود.
- **UsageTracker** — يقرأ `UsageStatsManager`.
- **AppBlockService** — `AccessibilityService` يعيد الطفل للـ Home عند فتح تطبيق محظور.
- **CallLogReporter / LocationReporter** — يرفعان بيانات المكالمات والموقع.
- **BootReceiver** — يعيد تشغيل الخدمة بعد الإقلاع.

### التحكم بالنت / VPN (`vpn/`)
- **FilterVpnService** — VPN محلي يقطع الترافيك عند `internetBlocked`. جاهز لفلترة النطاقات (DNS) مستقبلاً.

### الدفع / FCM (`fcm/`)
- **CommandMessagingService** — إيقاظ الخدمة عند وصول إشعار.

### البيانات / Data (`data/`)
- **Models** — `Command`, `Policy`, `CallRecord`.
- **FirebaseRepo** — كل قراءة/كتابة تحت `/devices/{pairCode}`.

## مخطط قاعدة البيانات / DB schema
```
/devices/{PAIRCODE}
├── info        { model, role, lastSeen }
├── policy      { internetBlocked, locked, blockedApps[], limits[], blockedDomains[] }
├── commands/{pushId}  { type, payload, ts, done }
└── reports
    ├── usage/{yyyymmdd}/{pkg_}   -> minutes
    ├── calls/{pushId}           { number, type, ts, durationSec }
    └── location/latest          { lat, lng, ts }
```

## خارطة الطريق / Roadmap
- واجهة إدارة الحدود وحظر التطبيقات من جهاز وليّ الأمر (حالياً عبر DB).
- فلترة النطاقات على مستوى DNS داخل `FilterVpnService`.
- جدولة زمنية (Bedtime / school hours) للسياسات.
- تشديد قواعد Firebase + Auth.
- خريطة الموقع داخل تطبيق وليّ الأمر.
