# Architecture

## Parts of the app

### UI (`ui/`)
- **RoleSelectActivity** — the first screen. Choose Parent or Child. The choice is
  saved on the phone. On a child phone it cannot be changed back.
- **ParentActivity** — the parent dashboard: pairing code, instant buttons, live
  usage report, and the list of recent alerts.
- **GuardSettingsActivity** — where the parent sets up call filtering, safe
  places, time rules, the SOS button and the weekly report.
- **ChildSetupActivity** — enter the pairing code, then grant permissions in
  order. Also holds the SOS button.
- **AboutActivity** — contact details, app version, and the e-signature.

### Device control (`admin/`)
- **GuardianDeviceAdminReceiver** — receives the Device Admin / Device Owner
  activation from Android.
- **PolicyManager** — a wrapper around `DevicePolicyManager`: lock the screen,
  block uninstall, hide apps, set user restrictions.

### Monitoring and enforcing (`monitor/`)
- **MonitorService** — a foreground service that never stops. This is the heart of
  the app. It listens for `policy` and `commands`, and once a minute it uploads
  reports and applies every restriction.
- **UsageTracker** — reads `UsageStatsManager` to get minutes per app.
- **AppBlockService** — an `AccessibilityService`. When a blocked app opens, it
  sends the child back to the home screen.
- **GeofenceEvaluator** — decides if the child entered or left a safe place.
- **ScheduleEvaluator** — decides which time rules are active right now.
- **SosReporter** — sends the panic alert and then the location.
- **CallLogReporter / LocationReporter** — upload call and location data.
- **BootReceiver** — starts the service again after the phone restarts.

### Call filtering (`calls/`)
- **GuardianCallScreeningService** — Android hands every call to this service and
  waits for an answer. It allows or rejects the call, then reports it.
- **CallDecision** — the rules, with no Android code mixed in, so the logic is
  easy to check. Emergency numbers are allowed first, before anything else.
- **CallPolicyStore** — a copy of the call rules saved on the phone itself.
- **ContactsLookup / NumberUtils** — match a number against the address book.
- **CallerIdLookup** — get a name for a number.
- **CallScreeningRole** — ask Android for permission to filter calls.

### Alerts (`alerts/`)
- **AlertNotifier** — turns an alert from the child into a notification on the
  parent's phone, with a useful button (open the map, look up the caller).

### Background jobs (`work/`)
- **WeeklyReportWorker** — adds up seven days of usage and sends a summary.

### Internet switch (`vpn/`)
- **FilterVpnService** — a local VPN that drops all traffic when the internet is
  switched off. It does not send traffic anywhere.

### Push (`fcm/`)
- **CommandMessagingService** — wakes the service when a push arrives, so commands
  apply quickly.

### Data (`data/`)
- **Models** — `Command`, `Policy`, `GeoZone`, `ScheduleRule`, `CallRecord`, `Alert`.
- **FirebaseRepo** — every read and write under `/devices/{pairCode}`.

---

## Two design choices worth explaining

**Call rules are stored twice.** They live in the policy on Firebase, but a copy is
also saved on the child's phone. Android starts the call screening service from
nothing when a call arrives and gives it only a few seconds to answer. That is not
enough time to ask a server. So the service reads the local copy.

**Safe places do not use the Play Services geofencing API.** That API is limited to
100 areas, needs extra plumbing, and is unreliable when the phone is asleep. The
app already takes a location once a minute, so it just measures the distance
itself. It also uses a 60 metre buffer, because without one a child sitting near
the edge of a zone would trigger an alert every minute as GPS drifts.

---

## Database layout

```
/devices/{PAIRCODE}
├── members/{uid}          true       <- who may read and write this code
├── pairingOpenUntil       timestamp  <- a new device may join before this time
├── info                   { model, role, lastSeen }
├── policy                 { internetBlocked, locked, blockedApps[], limits[],
│                            blockedDomains[], callFilterEnabled,
│                            blockUnknownCalls, restrictOutgoing, allowContacts,
│                            allowedNumbers[], blockedNumbers[], zones[],
│                            schedules[], sosEnabled, weeklyReport }
├── commands/{pushId}      { type, payload, ts, done }
├── alerts/{pushId}        { type, title, body, number, zoneName, lat, lng, ts, seen }
├── state/zones/{zoneId}   true or false  <- is the child inside this zone
└── reports
    ├── usage/{yyyymmdd}/{pkg_}  -> minutes
    ├── calls/{pushId}           { number, type, ts, durationSec,
    │                              blockedByFilter, contactName }
    └── location/latest          { lat, lng, ts }
```

Firebase keys cannot contain a dot, so package names are stored with `_` instead.

### Who can read what

Rules are in [`database.rules.json`](../database.rules.json). A device can only
touch `/devices/{code}` if its Firebase user id is listed under `members`. A
device can add itself when either the code is brand new, or the parent has opened
a pairing window. So a stranger cannot guess a code and read a child's location.

---

## Ideas for later

- A map inside the parent app instead of opening Google Maps.
- Blocking single websites by name inside `FilterVpnService`.
- A screen to edit app time limits, instead of writing them into the database.
- A button in the parent app to remove supervision, so ADB is not needed.
