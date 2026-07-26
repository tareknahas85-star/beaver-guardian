# Beaver Guardian

A parental control app for Android. One APK, two modes: **Parent** and **Child**.

You install the same app on both phones. On your phone you choose Parent. On your
child's phone you choose Child. A 6-letter pairing code links them.

![Build APK](../../actions/workflows/build.yml/badge.svg)

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

---

## Status

It works, but it is a personal project. It is not on Google Play. You build the
APK yourself, or download it from the
[latest release](https://github.com/tareknahas85-star/beaver-guardian/releases/tag/latest).

---

## How it works

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

---

## Setup

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

---

## About call filtering

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

---

## What this app cannot do

- **Record calls.** Android blocked this years ago. No app can do it.
- **Read WhatsApp or SMS messages.** Not supported, and not planned.
- **Hide from the child.** This is on purpose. See
  [docs/PRIVACY.md](docs/PRIVACY.md).
- **Stop uninstall without a computer.** Device Admin alone can be switched off
  by the child. Real protection needs Device Owner, which needs one command from
  a PC on a phone that was just factory reset.

---

## What you need

- Two Android phones, **Android 8 or newer** (Android 10+ for call filtering)
- A free Firebase project
- A computer with ADB, only for the Device Owner step

---

## Project layout

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

---

## Legal

Use this for **your own child under 18, and tell them.** Installing it on an
adult's phone without their knowledge is illegal in most countries. Details in
[docs/PRIVACY.md](docs/PRIVACY.md).

---

## Built with

Kotlin, View Binding, Firebase Realtime Database and Authentication, Firebase
Cloud Messaging, WorkManager, `AccessibilityService`, `DevicePolicyManager`,
`CallScreeningService`, and a local `VpnService` for the internet switch.
GitHub Actions builds the APK on every push.
