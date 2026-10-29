# Geofence Tracker

An Android app where people create **tracking groups**, the group owner draws a **geofence** on a map,
and when any member **leaves that area, every other member gets a push notification**, even if
their app is closed.

Built with Kotlin, Jetpack Compose, MVVM, Hilt, Coroutines/Flow, WorkManager, the Android
Geofencing API, MapLibre (OpenStreetMap), Firebase (Auth, Firestore, Cloud Messaging) and a small
Cloudflare Worker that sends the pushes.

**Runs entirely on free tiers, and no billing account or credit card is needed.**

---

## Features

- Email/password sign-up and sign-in
- Create a group; invite others with a 6-character code (shared via the Android share sheet)
- Join a group with an invite code
- The owner sets the geofence on a map: tap to place the centre, slider for the radius (100 m – 2 km)
- Members' devices monitor the geofence in the background
- On exit: the other members get a push notification; tapping it opens the group
- The group screen shows members and a live feed of recent exits
- Guided permission setup (precise location → "Allow all the time" → notifications → location on)

## How it works

```
 Member A's phone                              Backend                              Members B, C…
 ────────────────                              ───────                              ─────────────
 Play services geofence ──EXIT──▶ GeofenceBroadcastReceiver
                                   │ filter duplicates (DataStore)
                                   ▼
                                  GeofenceEventWorker  (WorkManager: waits for network, retries)
                                   │ 1. write event ───────────▶ Firestore groups/{id}/events/{eventId}
                                   │ 2. POST /notify-exit ─────▶ Exit-alert Worker (Cloudflare)
                                   │    (Firebase ID token)       │ verify caller + membership
                                   │                              │ mark event notified (once)
                                   │                              └──FCM HTTP v1──────────────────▶ notification
```

1. **Geofence registration.** `GeofenceSyncWorker` reads the user's groups and registers one
   Play-services geofence per group (request id = group id). It runs on sign-in, whenever a
   group's geofence changes in Firestore, after permissions are granted, and after reboot / app update.
2. **Transition.** Play services wakes `GeofenceBroadcastReceiver`, even if the app process is dead.
3. **Duplicate filtering.** `TransitionDeduplicator` (pure Kotlin, unit tested) ignores repeated
   transitions and suppresses a second EXIT within 60 s (GPS jumping back and forth at the boundary).
   State is kept in DataStore so it survives process death.
4. **Report.** `GeofenceEventWorker` saves the event to Firestore, then calls the exit-alert service.
   Both steps are idempotent: the WorkManager request id is the Firestore document id, and the service
   marks the event `notifiedAt` exactly once. So the whole job can be retried safely, including when
   the phone was offline at the moment the user left.
5. **Fan-out.** The Worker verifies the caller's Firebase ID token, checks the event belongs to them
   and that they are a member, claims the event with a conditional write (optimistic locking), then
   sends a push to every other member's FCM token. Tokens of uninstalled apps are removed.
6. **Display.** In the background the system shows the notification; in the foreground
   `TrackerMessagingService` shows it. Tapping opens `MainActivity`, which navigates to the group.

### Why a separate service sends the push

Sending an FCM message needs Google service-account credentials. Putting them in the APK would let
anyone extract them and push to every user. The exit-alert service holds the credential as a
secret and also enforces the business rules: only real members, only their own events, never the sender.

Cloud Functions would be the default choice, but deploying them requires Firebase's Blaze (billing)
plan. A Cloudflare Worker does the same job on a free plan without a card, and calls the same Google
APIs: Firestore REST and FCM HTTP v1.

### Joining without a backend

`inviteCodes/{code}` documents map a code to its group. A signed-in user can **get** a code they
know but cannot **list** codes. Security rules then let a non-member update a group only by adding
exactly themselves to `memberIds`/`memberNames`. A group id is only discoverable through its code,
so knowing it means you were invited. Codes are unique because they are created in the same batch as
the group, and the rules reject a code document that already exists.

## Architecture

MVVM, in a single `app` module organised by layer:

```
app/src/main/java/com/rahul/assignmentrahul/
├─ GeofenceApp.kt            @HiltAndroidApp, Hilt WorkManager factory, notification channels
├─ MainActivity.kt           Compose host, handles notification deep link
├─ di/AppModule.kt           Firebase, Play services, WorkManager, OkHttp
├─ domain/                   Pure Kotlin rules, no Android imports (unit tested)
│   ├─ TransitionDeduplicator
│   ├─ RadiusPolicy
│   ├─ InviteCodeGenerator
│   └─ GeoMath               circle polygon for drawing the geofence, haversine distance
├─ data/
│   ├─ model/                Group, GeofenceArea, ExitEvent, Coordinates
│   ├─ repository/           AuthRepository, UserRepository, GroupRepository, LocationRepository
│   └─ remote/               ExitAlertClient (calls the exit-alert service)
├─ geofence/                 GeofenceManager, GeofenceBroadcastReceiver, GeofenceStateStore,
│                            GeofenceEventWorker, GeofenceSyncWorker, BootReceiver
├─ notification/             NotificationHelper, TrackerMessagingService (FCM)
├─ permission/               AppPermissions (permission + location-services checks)
├─ util/                     runSuspendCatching (runCatching that respects cancellation)
└─ ui/
    ├─ AppRoot + SessionViewModel   signed-in vs signed-out, session-wide side effects
    ├─ navigation/                  type-safe Navigation Compose routes
    ├─ auth/  groups/  groupdetail/  geofence/   screens + ViewModels (geofence/GeofenceMap = MapLibre)
    └─ components/  theme/
notifier/                    Exit-alert service (Cloudflare Worker, TypeScript)
firestore.rules              Security rules
```

- **UI → ViewModel → Repository.** Screens observe a `StateFlow<UiState>` using
  `collectAsStateWithLifecycle`. One-off events (navigate, show snackbar) go through a `Channel`, so
  they are not replayed on rotation.
- **Real-time data.** Repositories expose Firestore snapshot listeners as `Flow`s, so new members,
  geofence edits and exits appear without refreshing. Firestore's offline cache provides offline reads.
- **`SessionViewModel` is activity-scoped.** It decides whether the auth screen or the nav graph is
  shown, refreshes the FCM token on sign-in, re-syncs geofences when any group's area changes, and
  cleans up on sign-out (removes the token, removes geofences, clears local state).
- **Map.** MapLibre with free OpenFreeMap (OpenStreetMap) tiles. No API key is needed. The ViewModels
  use a plain `Coordinates` type, so the map library is only referenced in `GeofenceMap.kt`.
- **Dependency choices.** Hilt for DI (`@HiltWorker` for workers, an `@EntryPoint` for the
  BroadcastReceiver). No Room: Firestore already caches data, and WorkManager already persists pending
  uploads. DataStore holds the small per-group geofence state. OkHttp for the single HTTP call
  (MapLibre already depends on it).

### Firestore data model

| Path | Fields | Who writes |
|---|---|---|
| `users/{uid}` | `displayName`, `email`, `fcmToken` | the user only |
| `groups/{groupId}` | `name`, `ownerId`, `memberIds[]`, `memberNames{uid: name}`, `inviteCode`, `geofence{latitude, longitude, radiusMeters}` | owner (name/geofence); a non-member may only add themselves |
| `groups/{groupId}/events/{eventId}` | `userId`, `userName`, `type: "EXIT"`, `clientTimeMillis`, `createdAt`, `notifiedAt` | members (only their own exits); `notifiedAt` by the exit-alert service |
| `inviteCodes/{code}` | `groupId`, `ownerId` | the group owner, once, at creation |

## Setup

> Everything runs on **your own** accounts. No credentials are in this repository:
> `google-services.json`, `local.properties` and service-account keys are git-ignored.

### Requirements

- Android Studio (AGP 9.2 / Gradle 9.4), JDK 17+, Android SDK 37 platform installed
- Node.js 20+
- An Android device or emulator **with Google Play services** (a "Google Play" system image)

### 1. Firebase project (Spark / free plan)

1. Go to [console.firebase.google.com](https://console.firebase.google.com) → **Create a project** (Analytics not needed).
2. **Add an Android app**
   - package `com.rahul.assignmentrahul`
   - add your debug SHA-1: `./gradlew signingReport`, or
     `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android`
3. Download **`google-services.json`** into `app/`.
4. **Authentication** → Sign-in method → enable **Email/Password**.
5. **Firestore Database** → Create database (production mode, any region).
6. **Firestore → Rules**: replace the default deny-all rules with the contents of
   [`firestore.rules`](firestore.rules), then **Publish**. Alternatively, with the Firebase CLI:
   `firebase deploy --only firestore:rules`.

### 2. Exit-alert service (Cloudflare Worker, free plan)

1. **Service-account key.** In the Firebase console, go to ⚙ Project settings → **Service accounts**
   → **Generate new private key**. This downloads a JSON file. **Keep it outside the project folder,
   never commit it and never share it.** It grants admin access to your Firebase project.
2. Create a free account at [dash.cloudflare.com](https://dash.cloudflare.com/sign-up). No card is needed.
3. Deploy (PowerShell):
   ```powershell
   cd notifier
   npm install
   npx wrangler login                 # opens the browser
   npx wrangler deploy                # first time: choose a workers.dev subdomain
   Get-Content "C:\path\to\your-key.json" -Raw | npx wrangler secret put FIREBASE_SERVICE_ACCOUNT
   ```
   `deploy` prints the URL, e.g. `https://geofence-exit-alerts.<you>.workers.dev`.
   If you use a different Firebase project, change `FIREBASE_PROJECT_ID` in `notifier/wrangler.toml`.
4. You can delete the downloaded key file afterwards, since Cloudflare stores it encrypted. If a
   key ever leaks, delete it under Service accounts → Manage keys and generate a new one.

### 3. App configuration

Add the Worker URL to `local.properties` in the project root (git-ignored):

```properties
EXIT_ALERT_URL=https://geofence-exit-alerts.<you>.workers.dev
```

It is not a secret (every request needs a valid Firebase ID token), but it is specific to each environment.

### 4. Run

Open the project in Android Studio and run the `app` configuration, or `./gradlew installDebug`.

## Cost

| Service | Plan | Cost |
|---|---|---|
| Firebase Auth (email), Firestore, Cloud Messaging | Spark (free) | Free within the free quotas |
| Cloudflare Workers | Free | 100,000 requests/day, no card |
| Map tiles (OpenFreeMap) | Free, no key | Free; please keep usage reasonable |

## Permissions

| Permission | Why |
|---|---|
| `ACCESS_FINE_LOCATION` | Geofencing requires precise location |
| `ACCESS_BACKGROUND_LOCATION` | Android 10+: needed for transitions while the app is closed. Requested after foreground location; on Android 11+ the user grants it in Settings ("Allow all the time") |
| `POST_NOTIFICATIONS` | Android 13+: to show alerts |
| `RECEIVE_BOOT_COMPLETED` | Geofences are cleared on reboot; re-register them |
| `INTERNET` | Firebase, map tiles, exit-alert service |

The permission card on the groups screen requests these step by step, re-checks when the user
returns from Settings, and links to app settings if a dialog can no longer be shown.

## Testing

### Unit tests

```bash
./gradlew testDebugUnitTest
```

These cover duplicate filtering and the cooldown (`TransitionDeduplicatorTest`), the circle geometry
(`GeoMathTest`), radius limits (`RadiusPolicyTest`) and invite codes (`InviteCodeGeneratorTest`).

### End-to-end with two devices

Recommended: **an emulator as the member who moves** (you control its location) and **a real phone
as the member who receives the alert**. Two emulators also work.

1. Phone (B): create account **B**, then create a group. Note the invite code.
2. Emulator (A): create account **A**, then **Join group** with the code.
3. On **both** devices, complete the permission card (background location = "Allow all the time").
4. Emulator A: set its location first (**Extended controls (⋯) → Location** → search a place → *Set location*).
5. Phone B (owner): open the group → **Set geofence** → place it on A's location → Save.
6. Emulator A: set a location **outside** the circle (e.g. 1 km away) → *Set location*.
7. Within about a minute, B gets **"A left the zone"**, A gets a quiet "You left … zone" confirmation,
   and the exit appears under **Recent exits** on both devices.

Tips for reliable transitions:
- Use an emulator image **with Google Play**, and turn on Google Location Accuracy in Settings → Location.
- On the emulator, open Google Maps once before moving. Play services only evaluates geofences
  when it receives location updates, and an app that actively requests location makes updates flow.
- Move clearly outside the circle (for example 2× the radius away), not just past the edge.
- App logs: `adb logcat -s GeofenceReceiver GeofenceEventWorker GeofenceSyncWorker GeofenceManager`
- Worker logs: `cd notifier && npx wrangler tail`

## Known limitations

- **Exit latency.** Android geofence transitions typically arrive within 1–3 minutes and can be
  slower with Doze or poor signal. This is a platform behaviour, not a bug.
- **One device per account.** `users/{uid}` stores a single FCM token, so if one account signs in on
  two phones, only the most recent one receives alerts. Fix: a `tokens` subcollection.
- **Exit only.** ENTER is used for local state only; "back in zone" alerts are not sent.
- **Geofence updates on other devices** are applied when that device's app is opened (live listener)
  or after reboot. A data-only push "resync" message would make this instant.
- **No leave/delete/transfer-ownership flows yet.**
- **Max 100 geofences per device** (Play services limit), so at most 100 groups are monitored.
- If location services are switched off, the system drops all geofences. They are registered again
  on the next app start or reboot.
- Force-stopping the app (Settings → Force stop) blocks geofence and FCM delivery until the app is
  opened again. This is Android behaviour.

## Security notes

- Firestore rules: users can only read their own profile and groups they belong to; only the owner can
  change the geofence (coordinates and radius are validated); a non-member can only add themselves;
  members can only post exit events as themselves.
- The exit-alert service accepts only valid Firebase ID tokens for this project, and re-checks event
  ownership and membership on the server.
- The service-account key exists only as an encrypted Cloudflare secret.
- `google-services.json` is not a secret (it ships inside every APK) but is git-ignored anyway.

## Library versions

Versions were chosen to build together on AGP 9.2 with Kotlin 2.3 (see `gradle/libs.versions.toml`).
Lint reports some newer versions. Upgrading, for example to Kotlin 2.4, should be done together and re-tested.
