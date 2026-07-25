# 🛠️ دليل الإعداد الكامل / Full Setup Guide

خطوات مرتّبة من الصفر حتى جهاز طفل محمي بالكامل.

---

## 0) المتطلبات / Prerequisites

- جهازان بأندرويد (جهازك + جهاز الطفل)، **Android 8 (API 26)** أو أحدث.
- كمبيوتر عليه **ADB** (Platform Tools) — لخطوة Device Owner فقط.
- حساب Google (مجاني) لإنشاء مشروع Firebase.

---

## 1) إنشاء مشروع Firebase / Create the Firebase project

1. افتح <https://console.firebase.google.com> → **Add project** → سمّه مثلاً `beaver-guardian`.
2. من القائمة: **Build → Realtime Database → Create Database** → اختر أقرب منطقة → ابدأ بـ **Locked mode**.
3. **Build → Authentication** غير مطلوب للنسخة الأولى (نستخدم القاعدة مباشرة).
4. أضف تطبيق أندرويد: **Project settings → Your apps → Android**.
   - **Package name:** `com.microbeaver.guardian` (مهم أن يطابق تماماً).
   - نزّل ملف **`google-services.json`**.
5. ضع الملف مكان: `app/google-services.json` (استبدل الملف المؤقت الموجود).

### قواعد قاعدة البيانات (مبدئية للتجربة)
في **Realtime Database → Rules** الصق التالي مؤقتاً للتجربة، ثم شدّدها لاحقاً:

```json
{
  "rules": {
    "devices": {
      "$code": {
        ".read": true,
        ".write": true
      }
    }
  }
}
```

> 🔐 **للإنتاج:** فعّل Firebase Auth وقيّد القراءة/الكتابة على مستخدم وليّ الأمر فقط. راجع `docs/PRIVACY.md`.

---

## 2) بناء الـ APK / Build the APK

**الطريقة الأسهل — GitHub Actions (بدون أي إعداد محلي):**
- ادفع الكود إلى فرع `main`. تبويب **Actions** يبني الـ APK.
- بعد نجاح البناء: **Actions → آخر تشغيل → Artifacts → `beaver-guardian-debug-apk`** → نزّل وفكّ الضغط → `app-debug.apk`.

**محلياً (اختياري):** افتح المشروع بـ **Android Studio** (Giraffe+) واضغط Run، أو:
```bash
gradle assembleDebug        # يتطلب Gradle 8.7 + JDK 17 + Android SDK
```

انقل `app-debug.apk` للجهازين وثبّته (فعّل "تثبيت من مصادر غير معروفة").

---

## 3) إعداد جهازك (Parent)

1. افتح **Beaver Guardian** → اختر **أنا وليّ الأمر (Parent)**.
2. انسخ **رمز الربط (Pairing code)** المكوّن من 6 أحرف.

---

## 4) إعداد جهاز الطفل (Child)

1. افتح التطبيق → اختر **هذا جهاز الطفل (Child)**.
2. أدخل **رمز الربط** → اضغط **ربط الجهاز**.
3. فعّل الأذونات بالترتيب (الأزرار 1→6):
   1. **الوصول لإحصاءات الاستخدام** (Usage Access).
   2. **خدمة الوصول** (Accessibility) → فعّل *Beaver Guardian — App Guard*.
   3. **الظهور فوق التطبيقات** (Display over other apps).
   4. **الموقع + المكالمات + الإشعارات** → اسمح بالكل، والموقع اختر **"طوال الوقت / Allow all the time"**.
   5. **تفعيل مشرف الجهاز** (Device Admin).
   6. **تشغيل فلتر الإنترنت** (VPN) → اقبل طلب الاتصال.

---

## 5) <a name="device-owner"></a>تفعيل Device Owner (منع الحذف الكامل)

هي الخطوة الوحيدة اللي بتحتاج كمبيوتر، وبتعطي **التحكم الكامل ومنع الحذف النهائي**.

> ⚠️ الجهاز لازم يكون **بدون أي حساب Google مُضاف** (جهاز جديد أو بعد Factory Reset). هذا شرط أندرويد، مش من التطبيق.

على جهاز الطفل:
1. فعّل **خيارات المطوّر**: الإعدادات → حول الهاتف → اضغط **رقم الإصدار** 7 مرات.
2. فعّل **تصحيح USB (USB debugging)**.
3. وصّل الجهاز بالكمبيوتر، واعتمد التصحيح.

على الكمبيوتر:
```bash
adb devices           # تأكد أن الجهاز ظاهر
adb shell dpm set-device-owner com.microbeaver.guardian/.admin.GuardianDeviceAdminReceiver
```

النتيجة المتوقعة:
```
Success: Device owner set to package com.microbeaver.guardian
```

بمجرد نجاحها، يطبّق التطبيق تلقائياً:
- `setUninstallBlocked` → **لا يمكن حذف التطبيق**.
- `DISALLOW_FACTORY_RESET` → منع إعادة الضبط للهروب.
- `DISALLOW_ADD_USER` / `DISALLOW_SAFE_BOOT`.
- إخفاء التطبيقات المحظورة نهائياً (الألعاب مثلاً).

### لإلغاء الإشراف لاحقاً (وليّ الأمر فقط)
```bash
adb shell dpm remove-active-admin com.microbeaver.guardian/.admin.GuardianDeviceAdminReceiver
```
أو من داخل تطبيق وليّ الأمر (نسخة قادمة: زر "إزالة الإشراف").

---

## 6) الاستخدام اليومي / Daily use

من جهازك (Parent Dashboard):
- **🔒 قفل فوري** / **🔓 فتح**.
- **🌐 قطع/سماح الإنترنت**.
- **تقارير الاستخدام** لكل تطبيق (تتحدّث كل دقيقة).

الحدود الزمنية وحظر تطبيقات محددة تُكتب في `policy` بقاعدة البيانات (`limits`, `blockedApps`) — واجهة إدارتها ضمن التحسينات القادمة، والمحرّك جاهز ويطبّقها فوراً.

---

## استكشاف الأخطاء / Troubleshooting

| المشكلة | الحل |
|---|---|
| `set-device-owner` يفشل بـ *"already has an account"* | اعمل Factory Reset ولا تُضِف حساب Google قبل الخطوة. |
| لا تظهر تقارير | تأكد من رمز الربط نفسه على الجهازين + قواعد Firebase تسمح بالكتابة. |
| التطبيق يُقتل بالخلفية | ألغِ تحسين البطارية للتطبيق (Settings → Battery → Unrestricted). |
| فشل بناء CI | تأكد أن `google-services.json` موجود و package = `com.microbeaver.guardian`. |
