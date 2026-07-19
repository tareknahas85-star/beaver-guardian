# 🧩 Architecture

## Components

### UI (`ui/`)
- **RoleSelectActivity**: first screen, pick Parent or Child. The choice is stored locally and can't be changed on the kid's device.
- **ParentActivity**: parent dashboard, pairing code, instant commands, live usage reports.
- **ChildSetupActivity**: enter the pairing code + enable the six permissions.

### Admin (`admin/`)
- **GuardianDeviceAdminReceiver**: receives Device Admin/Owner activation and applies the policies.
- **PolicyManager**: wrapper around `DevicePolicyManager`: `lockNow`, `setUninstallBlocked`, `setApplicationHidden`, user restrictions.

### Monitoring (`monitor/`)
- **MonitorService**: permanent foreground service, the heart of the app. Listens to `policy` + `commands`, and every minute uploads reports and enforces the limits.
- **UsageTracker**: reads `UsageStatsManager`.
- **AppBlockService**: `AccessibilityService` that sends the kid back to Home when a blocked app opens.
- **CallLogReporter / LocationReporter**: upload call info and location.
- **BootReceiver**: restarts the service after boot.

### VPN (`vpn/`)
- **FilterVpnService**: local VPN that cuts traffic when `internetBlocked` is on. Ready for DNS domain filtering later.

### FCM (`fcm/`)
- **CommandMessagingService**: wakes the service when a push arrives.

### Data (`data/`)
- **Models**: `Command`, `Policy`, `CallRecord`.
- **FirebaseRepo**: every read/write lives under `/devices/{pairCode}`.

## DB schema
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

## Roadmap
- UI for managing limits and blocked apps from the parent device (right now it's through the DB).
- DNS level domain filtering inside `FilterVpnService`.
- Time schedules (bedtime / school hours) for policies.
- Tighter Firebase rules + Auth.
- Location map inside the parent app.
