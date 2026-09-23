# Setup guide

From nothing to a working, protected child phone.

---

## 0) What you need

- Two Android phones (yours and the child's), **Android 8 or newer**.
  Call filtering needs **Android 10 or newer**.
- A Google account, to make a free Firebase project.
- A computer with **ADB** — only for the Device Owner step at the end.

---

## 1) Make the Firebase project

1. Open <https://console.firebase.google.com> then **Add project**. Name it
   `beaver-guardian`.
2. Go to **Build → Realtime Database → Create Database**. Pick the region closest
   to you. Start in **Locked mode**.
3. Go to **Build → Authentication → Get started → Anonymous → Enable → Save**.
   This is required. The database rules will not work without it.
4. Add an Android app: **Project settings → Your apps → Android**.
   - **Package name:** `com.microbeaver.guardian` — it must match exactly.
   - Download **`google-services.json`**.
5. Put that file at `app/google-services.json`, replacing the one in the repo.

### 1b) Set the database rules — do not skip this

Open **Realtime Database → Rules**. Delete what is there. Paste the contents of
[`database.rules.json`](../database.rules.json) from this repo. Press **Publish**.

> **Why this matters.** An earlier version of this guide told you to paste
> `".read": true, ".write": true` "just for testing". That makes the whole
> database public: anyone who knows the address can read every child's location
> and call log, with no password. If you ever used those rules, replace them now.

The correct rules work like this. Each pairing code has a `members` list. A phone
can only read or write a code if its Firebase user id is in that list. A phone can
add itself only when the code is brand new, or when the parent has pressed
**Open pairing window** in the app. That window lasts 15 minutes.

---

## 2) Build the APK

**Easiest — GitHub Actions.** Push to the `main` branch. The build runs by itself
and puts the finished APK on the
[latest release](https://github.com/tareknahas85-star/beaver-guardian/releases/tag/latest)
page. Always download from there.

**On your own computer (optional):** open the project in Android Studio and press
Run, or:

```bash
gradle assembleDebug        # needs Gradle 8.7, JDK 17, Android SDK
```

Copy the APK to both phones and install it. You will have to allow
"install from unknown sources".

> **Check the version after installing.** Open the About screen. It shows the
> version number. If it is not the version you just installed, an old copy is
> still on the phone. Uninstall it completely first.

---

## 3) The parent phone

1. Open **Beaver Guardian** and choose **Parent**.
2. Write down the 6-letter **pairing code**.
3. Press **Open pairing window**. You now have 15 minutes to pair the child phone.

---

## 4) The child phone

1. Open the app and choose **Child**.
2. Type the pairing code and press **Pair device**.
   If it fails, the pairing window on the parent phone has closed. Press
   **Open pairing window** again.
3. Grant the permissions in order, buttons 1 to 7:
   1. **Usage access** — to measure app time.
   2. **Accessibility** — turn on *Beaver Guardian — App Guard*. This closes
      blocked apps.
   3. **Display over other apps.**
   4. **Location, calls, contacts, notifications** — allow all. For location
      choose **Allow all the time**.
   5. **Device admin.**
   6. **Internet filter (VPN)** — accept the connection request.
   7. **Call filtering** — Android shows its own dialog. Android 10+ only. Note
      that only one app can filter calls, so this replaces Truecaller or similar.
4. Turn off battery optimisation for the app:
   **Settings → Apps → Beaver Guardian → Battery → Unrestricted.**
   Without this Android will eventually kill the background service.

---

## 5) Device Owner — stopping uninstall for real

This is the only step that needs a computer. Without it the child can remove the
app by turning off Device Admin first.

> The phone must have **no Google account added yet**. That means a new phone, or
> one right after a factory reset. This is an Android rule, not an app rule.

On the child phone:
1. Turn on **Developer options**: Settings → About phone → tap **Build number**
   seven times.
2. Turn on **USB debugging**.
3. Connect it to the computer and accept the debugging prompt.

On the computer:

```bash
adb devices           # check the phone is listed
adb shell dpm set-device-owner com.microbeaver.guardian/.admin.GuardianDeviceAdminReceiver
```

You should see:

```
Success: Device owner set to package com.microbeaver.guardian
```

After that the app automatically:
- blocks its own uninstall
- blocks factory reset, so the child cannot wipe their way out
- blocks adding users and safe boot
- can hide blocked apps completely, so they vanish from the app list

The child setup screen tells you which level of protection is active, so you can
check it worked.

### To remove supervision later

```bash
adb shell dpm remove-active-admin com.microbeaver.guardian/.admin.GuardianDeviceAdminReceiver
```

---

## 6) Daily use

On the parent dashboard:

- **Lock** and **Unlock** the phone.
- **Cut** or **allow** the internet.
- **Usage report**, refreshed every minute.
- **Recent alerts**: unknown callers, safe zone events, SOS.

In **Settings** you set up:

- **Call filtering** — allow contacts, add your own allowed numbers, block
  specific numbers, and choose whether outgoing calls are limited too.
- **Safe places** — stand where the place is, give it a name and a radius, and
  press add. You get an alert when the child arrives or leaves.
- **Time rules** — bedtime and study time presets.
- **SOS button** and the **weekly report**.

---

## Problems and fixes

| Problem | Fix |
|---|---|
| Installing the APK changes nothing | An old copy is still installed. Check the version on the About screen. Uninstall fully, then install again. |
| `set-device-owner` says "already has an account" | Factory reset the phone and do not add a Google account before this step. |
| Pairing fails on the child phone | Press **Open pairing window** on the parent phone, then try again within 15 minutes. |
| No reports appear | Check both phones use the same code, that Anonymous sign-in is on, and that the database rules were published. |
| The app stops working in the background | Turn off battery optimisation for the app. |
| Call filtering does nothing | It needs Android 10 or newer, and the permission in step 7. The child setup screen shows whether it is active. |
| The build fails | Check `google-services.json` exists and its package name is `com.microbeaver.guardian`. |
