# 🦫 Beaver Guardian

Android parental control app. One APK, two modes: Parent and Child. You control your kid's phone from yours: app and game time limits, block apps, cut the internet, see the call log and location. And the kid can't uninstall it without your approval.

The child's phone always shows a notification that it's being supervised. This is supervision the kid knows about, not hidden spyware.

![Build APK](../../actions/workflows/build.yml/badge.svg)

## Features

- Screen time and per-app limits: uses `UsageStatsManager` and closes the app when time is up (needs Usage Access + Accessibility)
- Lock or block apps, plus instant lock that sends the kid back to the home screen (Device Owner / Accessibility)
- Internet control: a local VPN that cuts all traffic when you say so (VPN service)
- Call log: number, direction, time and duration. No audio recording (`READ_CALL_LOG`)
- Location: last known device location, synced to Firebase (background location)
- Uninstall protection: `setUninstallBlocked` + `DISALLOW_FACTORY_RESET` (needs Device Owner)

Heads up: recording call audio is simply not possible on Android 10+ for any third party app. You get call info only, no sound.

## Quick start

1. Build the APK: every push to `main` builds it automatically. Go to Actions, open the latest run, grab `beaver-guardian-debug-apk` from Artifacts.
2. Firebase: follow [docs/SETUP.md](docs/SETUP.md) to create a Firebase project and replace `app/google-services.json`.
3. Setup: on your phone pick Parent and copy the pairing code. On the kid's phone pick Child, enter the code and allow the six permissions.
4. Uninstall protection (Device Owner): run the one ADB step in [docs/SETUP.md](docs/SETUP.md).

Full guide: [docs/SETUP.md](docs/SETUP.md) / Architecture: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) / Privacy: [docs/PRIVACY.md](docs/PRIVACY.md)

## Project layout

```
app/src/main/java/com/microbeaver/guardian/
├── ui/            RoleSelect · Parent · ChildSetup
├── admin/         DeviceAdminReceiver · PolicyManager (Device Owner)
├── monitor/       MonitorService · UsageTracker · AppBlockService · CallLog · Location · Boot
├── vpn/           FilterVpnService (internet control)
├── fcm/           CommandMessagingService (wake)
└── data/          Models · FirebaseRepo
```

## Legal and ethical use

This app is made for a parent supervising their own minor kid, with the kid knowing about it. Don't install it on an adult's phone without their knowledge. See [docs/PRIVACY.md](docs/PRIVACY.md).
