# 🛠️ Full Setup Guide

Everything from zero to a fully protected child device.

---

## 0) What you need

- Two Android devices (yours + the kid's), **Android 8 (API 26)** or newer.
- A computer with **ADB** (Platform Tools), only needed for the Device Owner step.
- A Google account (free) to create the Firebase project.

---

## 1) Create the Firebase project

1. Open <https://console.firebase.google.com> > **Add project** > name it something like `beaver-guardian`.
2. From the menu: **Build > Realtime Database > Create Database** > pick the closest region > start in **Locked mode**.
3. Add an Android app: **Project settings > Your apps > Android**.
   - **Package name:** `com.microbeaver.guardian` (must match exactly).
   - Download **`google-services.json`**.
4. Put the file at `app/google-services.json` (replace the placeholder that's there).

### Database rules (just for testing)
In **Realtime Database > Rules** paste this for now, tighten it later:

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

> 🔐 **For production:** turn on Firebase Auth and restrict read/write to the parent's account only.

---

## 2) Build the APK

**Easiest way, GitHub Actions (no local setup at all):**
- Push the code to `main`. The **Actions** tab builds the APK.
- When the build finishes: **Actions > latest run > Artifacts > `beaver-guardian-debug-apk`** > download, unzip > `app-debug.apk`.

**Locally (optional):** open the project in **Android Studio** and hit Run, or:
```bash
gradle assembleDebug        # needs Gradle 8.7 + JDK 17 + Android SDK
```

Copy `app-debug.apk` to both devices and install it (allow "install from unknown sources").

---

## 3) Your device (Parent)

1. Open **Beaver Guardian** > pick **Parent**.
2. Copy the 6 character **pairing code**.

---

## 4) The kid's device (Child)

1. Open the app > pick **Child**.
2. Enter the **pairing code** > tap **pair device**.
3. Turn on the permissions in order (buttons 1 to 6):
   1. **Usage Access**.
   2. **Accessibility** > enable *Beaver Guardian - App Guard*.
   3. **Display over other apps**.
   4. **Location + calls + notifications**. For location pick **"Allow all the time"**.
   5. **Device Admin**.
   6. **Internet filter (VPN)** > accept the connection request.

---

## 5) Device Owner (full uninstall protection)

The only step that needs a computer. It gives **full control and makes the app impossible to remove**.

> ⚠️ The device must have **no Google account added** (new device or right after a factory reset). That's an Android requirement, not ours.

On the kid's device:
1. Enable **Developer options**: Settings > About phone > tap **Build number** 7 times.
2. Enable **USB debugging**.
3. Plug it into the computer and approve debugging.

On the computer:
```bash
adb devices           # make sure the device shows up
adb shell dpm set-device-owner com.microbeaver.guardian/.admin.GuardianDeviceAdminReceiver
```

Expected result:
```
Success: Device owner set to package com.microbeaver.guardian
```

Once that works, the app automatically applies:
- `setUninstallBlocked`, so **the app can't be removed**.
- `DISALLOW_FACTORY_RESET`, no factory reset escape.
- `DISALLOW_ADD_USER` / `DISALLOW_SAFE_BOOT`.
- Fully hiding blocked apps (games for example).

### To remove supervision later (parent only)
```bash
adb shell dpm remove-active-admin com.microbeaver.guardian/.admin.GuardianDeviceAdminReceiver
```

---

## 6) Daily use

From your device (Parent Dashboard):
- **🔒 Instant lock** / **🔓 unlock**.
- **🌐 Cut / allow internet**.
- **Usage reports** per app (updates every minute).

Time limits and blocking specific apps are written to `policy` in the database (`limits`, `blockedApps`). A UI to manage them is on the roadmap, the engine is ready and applies them right away.

---

## Troubleshooting

| Problem | Fix |
|---|---|
| `set-device-owner` fails with *already has an account* | Factory reset and don't add a Google account before this step. |
| No reports showing | Make sure both devices use the same pairing code and the Firebase rules allow writes. |
| App gets killed in the background | Turn off battery optimization for the app (Battery > Unrestricted). |
| CI build fails | Make sure `google-services.json` exists and the package is `com.microbeaver.guardian`. |
