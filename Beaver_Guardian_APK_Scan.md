# Beaver Guardian — Debug APK Scan Report

**File:** `beaver-guardian-debug-apk.zip` → `app-debug.apk` (10.5 MB)
**Package:** `com.microbeaver.guardian`
**Version:** 1.0.0 (versionCode 1)
**Build:** AGP 8.5.2 · compileSdk 34 · minSdk 24 (Android 7.0) · targetSdk 34 (Android 14)
**Signing:** APK Signature Scheme v2/v3 present, no legacy v1 (JAR) signature — standard debug-keystore signing, no native `lib/` folder (pure Kotlin/Java), no `assets/` folder.

Method: unzipped the APK, wrote a small binary-AXML parser to decode `AndroidManifest.xml` in full (package, permissions, components, intent filters), and ran a string scan across all 12 `classes*.dex` files + `resources.arsc` for endpoints/keys.

---

## 1. What the app is

A parental-control / device-supervision app with a classic parent↔child architecture:

| Role screen | Purpose |
|---|---|
| `RoleSelectActivity` | Launcher — picks Parent vs Child mode |
| `ParentActivity` | Parent dashboard |
| `ChildSetupActivity` | Enrolls the child device |
| `GuardSettingsActivity` / `AboutActivity` | Settings & about (localized EN + AR — "الإعدادات", "حول") |

Bilingual (Arabic/English) UI confirmed directly in the manifest strings.

## 2. Core supervision components

| Component | Type | Exported | Guard permission | Role |
|---|---|---|---|---|
| `GuardianDeviceAdminReceiver` | Device Admin | Yes | `BIND_DEVICE_ADMIN` (system-checked) | Anti-uninstall / policy enforcement |
| `AppBlockService` | Accessibility Service | Yes | `BIND_ACCESSIBILITY_SERVICE` (system-checked) | Detects & blocks apps in real time |
| `FilterVpnService` | VpnService, FGS type `special_use` | No | `BIND_VPN_SERVICE` | Local VPN for web/content filtering — declares itself to Play as `parental_control_filter` |
| `MonitorService` | Foreground Service, FGS type `special_use` | No | — | Background monitoring — declares itself as `parental_control_monitoring` |
| `GuardianCallScreeningService` | CallScreeningService | Yes | `BIND_SCREENING_SERVICE` (system-checked) | Call blocking/allow-listing |
| `CommandMessagingService` | FirebaseMessagingService subclass | No | — | Receives remote commands from parent via FCM |
| `BootReceiver` | BroadcastReceiver | **Yes, no permission** | — | Restarts monitoring after reboot |

All the "exported=True" components other than `BootReceiver` are protected by OS-enforced `BIND_*` signature permissions, so third-party apps can't invoke them directly even though they're exported — that's expected/required Android plumbing, not a bug. `BootReceiver` being exported with no permission is a common lint flag, but low real risk since `BOOT_COMPLETED`/`LOCKED_BOOT_COMPLETED` are protected broadcasts only the system can send.

The two Android-14 `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` declarations (`parental_control_filter`, `parental_control_monitoring`) are exactly the justification strings Google expects for this app category — good, deliberate compliance work already in place.

## 3. Permissions requested (grouped)

**Location:** `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `ACCESS_BACKGROUND_LOCATION` — continuous child-location tracking.

**Communications:** `READ_CALL_LOG`, `READ_CONTACTS`, `READ_PHONE_STATE`, `BIND_SCREENING_SERVICE`.

**Device control:** `BIND_DEVICE_ADMIN`, `BIND_ACCESSIBILITY_SERVICE`, `BIND_VPN_SERVICE`, `SYSTEM_ALERT_WINDOW` (overlay/blocking screen), `PACKAGE_USAGE_STATS` (special, granted via Settings), `QUERY_ALL_PACKAGES` (Play-restricted, needs a declaration form).

**Infra:** `INTERNET`, `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE(_DATA_SYNC/_LOCATION/_SPECIAL_USE)`.

**Ads/measurement (pulled in by Play Services/Firebase, not app-declared intent):** `com.google.android.gms.permission.AD_ID`, `ACCESS_ADSERVICES_AD_ID`, `ACCESS_ADSERVICES_ATTRIBUTION`.

⚠️ Worth a look: since this targets families/children, `AD_ID`/AdServices permissions and `QUERY_ALL_PACKAGES` both draw extra scrutiny under Play's **Families Policy** and the **Permissions declaration form**. If there's no ads/attribution use case, stripping the AD_ID permission (`tools:node="remove"` in the merged manifest) avoids a Play Console policy flag.

## 4. Backend & third-party integrations (from string scan)

- **Firebase:** Auth (incl. `RecaptchaActivity` / `GenericIdpActivity` — reCAPTCHA-backed auth flow), Realtime Database at `beaver-guardian-default-rtdb.firebaseio.com`, Cloud Messaging (FCM — parent→child commands), Analytics, Installations.
- **Google Sign-In / Credentials API** (`androidx.credentials`, `GoogleApiActivity`, `SignInHubActivity`) — parent login.
- **Google API key** `[REDACTED_FIREBASE_API_KEY]` embedded in `resources.arsc` (standard `google-services.json` → `strings.xml` injection, not a leak by itself). **Action item:** confirm in Google Cloud Console that this key is *restricted* to the app's package name + release/debug signing SHA-1s, and that Firebase Realtime Database rules are locked down (not left in test/open mode) — this DB holds child location, call logs, and filter data, so it's the highest-value target here.
- **apilayer.net** (`/api/validate?access_key=…`) — third-party phone-number validation (Numverify-style). No access key found hardcoded in the strings dump; likely injected at runtime/remote config — good.
- **Truecaller** — app explicitly queries `com.truecaller` package visibility plus generic VIEW/DIAL intent queries, suggesting optional caller-ID enrichment during call screening.
- **Room** (local DB), **WorkManager** (scheduled background work) — standard Jetpack, no findings.

## 5. Build hygiene notes

- `debuggable="True"` and `allowBackup="False"` — correct combination for a debug artifact (debug flag expected here; backups correctly disabled given the sensitive data the app collects).
- 12-way multidex with two ~10 MB dex files (`classes.dex`, `classes12.dex`) — normal for an unshrunk **debug** build (no R8/minify), not a red flag on its own; a release build should be checked separately for shrink/obfuscation status.
- No native libraries and no `assets/` — smaller/simpler attack surface, nothing bundled to inspect there.

## 6. Suggested follow-ups

1. Verify Firebase RTDB security rules restrict read/write to authenticated, matched parent↔child UIDs only (this is the biggest real risk surface given the API key is embedded client-side by design).
2. Confirm the Google API key has package+SHA-1 restrictions in GCP.
3. Re-run this same scan against the **release** APK/AAB to confirm R8 shrinking/obfuscation is active and `debuggable` is `false`.
4. Consider removing `AD_ID`/AdServices permissions if there's no ads use case, to simplify the Play Families Policy review.
5. Double-check whether `BootReceiver` needs to be exported at all, or can be `exported="false"` (Android still delivers protected system broadcasts to non-exported receivers registered for them).
