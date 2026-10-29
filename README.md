# Geofence Tracker

A native Android app for small groups (a family, a team on a field visit, friends on a trip) to
create a **tracking group**, draw a **zone** on the map, and get a **push notification the moment a
member leaves it**, even when the app is closed.

> 🎥 **Demo:** _Loom link here_  ·  Built with Kotlin, Jetpack Compose, MVVM, Hilt, Coroutines/Flow,
> WorkManager, the Android Geofencing API, MapLibre, Firebase and a small Cloudflare Worker.

---

## How it works for a user

1. **Sign up** with your name, email and password.
2. **Create a group**, e.g. "School trip". You get a 6-character invite code (e.g. `QJESWJ`) to share.
3. Friends tap **Join** and enter the code.
4. As the owner, tap **Set zone**, tap the map to place the centre, and pick a radius (10 m – 2 km).
5. Everyone completes a short **permission checklist**, so the phone can watch the zone in the background.
6. When anyone walks out of the zone, everyone else gets **"Rahul left the zone"**, and tapping it
   opens the group. The person who left gets a quiet "your group has been notified" confirmation,
   and the exit appears in the group's **Recent exits** list.

## Architecture

I kept it to one `app` module with clear layers (MVVM + a small pure-Kotlin domain layer), which
is the right size for this scope and easy to split into modules later.

```
UI (Compose screens)  ──observe StateFlow──▶  ViewModels  ──▶  Repositories  ──▶  Firebase / Play services
                                                   │
                                                   └──▶ domain/ (pure Kotlin rules, unit tested)

Background:  Play-services geofence ─▶ BroadcastReceiver ─▶ WorkManager worker ─▶ Firestore + alert service
```

```
app/src/main/java/com/rahul/assignmentrahul/
├─ ui/            Compose screens + ViewModels (auth, groups, group detail, zone editor), theme, components
├─ domain/        TransitionDeduplicator, RadiusPolicy, InviteCodeGenerator, GeoMath (no Android imports)
├─ data/          models, repositories (Auth, User, Group, Location), remote/ExitAlertClient
├─ geofence/      GeofenceManager, GeofenceBroadcastReceiver, GeofenceStateStore, workers, BootReceiver
├─ notification/  channels, FCM service
├─ permission/    runtime-permission + location-services checks
└─ di/            Hilt module
notifier/         Exit-alert service (Cloudflare Worker, TypeScript)
firestore.rules   Security rules (they are a real part of the logic here, not an afterthought)
```

### The exit flow, end to end

```
Member A's phone                                                        Members B, C…
Play services geofence ──EXIT──▶ GeofenceBroadcastReceiver
                                  │ duplicate filter (DataStore)
                                  ▼
                                 GeofenceEventWorker  (WorkManager: needs network, retries)
                                  │ 1. save event ─────────────▶ Firestore  groups/{id}/events/{eventId}
                                  │ 2. POST /notify-exit ──────▶ Exit-alert service (Cloudflare Worker)
                                  │    + Firebase ID token          verifies caller & membership,
                                  │                                 marks the event notified once,
                                  │                                 sends via FCM HTTP v1 ──────────▶ 🔔
```

## Key decisions

**Geofencing through Play services, not my own location polling.** The OS monitors the zone and
wakes the app on a transition, which is far better for battery than a foreground service streaming GPS.
The trade-off is latency: Android typically delivers exits within 1–3 minutes.

**Receiver → WorkManager.** A `BroadcastReceiver` gets ~10 seconds and has no network guarantee. It
only filters duplicates (fast, local) and hands the upload to a worker that waits for connectivity
and retries with backoff. So an exit that happens with no signal is still reported when the phone
reconnects. This is the same offline-first pattern I use at work.

**Every step is idempotent.** The WorkManager request id is used as the Firestore event id, so a
retry rewrites the same document. The alert service sets `notifiedAt` with a conditional write
(optimistic locking on the document's `updateTime`), so retries or concurrent calls can never send
a second notification for the same exit.

**Duplicate and "GPS jitter" filtering in pure Kotlin.** `TransitionDeduplicator` ignores repeated
transitions and suppresses a second EXIT within 60 s. That keeps the rules testable without a device,
and the state lives in DataStore because geofence events often start a fresh process.

**Push is sent from a server, never from the phone.** Sending FCM needs service-account credentials,
and shipping those in an APK would let anyone push to every user. A small server also enforces the
rules: only members, only your own events, never notify the sender.

**Joining a group without a backend.** An `inviteCodes/{code}` document maps a code to its group.
Signed-in users can *get* a code they know but can't *list* codes, and the Firestore rules let a
non-member update a group only by adding exactly themselves. Codes are unique because they're
written in the same batch as the group, and the rules reject an existing code.

**Permissions done properly.** Background location has to be requested *after* foreground location,
and on Android 11+ it can only be granted on the system Settings page ("Allow all the time"). The
permission card walks through this step by step, re-checks when the user returns from Settings,
and falls back to app settings once Android stops showing the dialog.

**Hilt for DI**, with `@HiltWorker` for workers and a Hilt `@EntryPoint` for the BroadcastReceiver.
I didn't use Room: Firestore already gives an offline cache, and WorkManager already persists pending uploads.

### Two blockers, and what I did about them

- **Google Maps.** The Maps SDK needs an API key on a Google Cloud project with billing enabled, and
  my billing/payment verification kept failing. I switched to **MapLibre** with free
  **OpenFreeMap** (OpenStreetMap) tiles: open source, no key, no billing. I had already used MapLibre
  in a personal learning project, so I knew it would work well here. The map is isolated in one
  composable (`GeofenceMap.kt`), and ViewModels use a plain `Coordinates` type, so switching back to
  Google Maps would touch a single file.
- **Cloud Functions.** Deploying them needs Firebase's paid Blaze plan, for the same billing reason.
  I kept the same server-side design but host it as a **Cloudflare Worker** (free plan). It calls the
  same Google APIs (Firestore REST + FCM HTTP v1) using a service account stored as an encrypted secret.

### Why native Android and not KMP

The brief mentioned KMP + Swift. I checked with the team, and the guidance was to use KMP if
possible, otherwise go with my expertise. I don't have a Mac, so I couldn't build or test an iOS
target, and an untested iOS app wouldn't be honest work. I built it natively, but kept the `domain`
package free of Android imports so it can move into a KMP `commonMain` module largely as-is. The
models, duplicate filter, radius policy, invite codes and geo maths would be shared; geofencing,
notifications and UI stay platform-specific (`GeofencingClient` / `CLLocationManager`, Compose / SwiftUI).

## Setup

Everything runs on free tiers, with no billing account or card needed. No credentials are committed:
`google-services.json`, `local.properties` and the service-account key are git-ignored.

**Requirements:** Android Studio (AGP 9.2), JDK 17+, Android SDK 37, Node.js 20+, and a device or
emulator with Google Play services.

### 1. Firebase (Spark plan)
1. Create a project at [console.firebase.google.com](https://console.firebase.google.com).
2. Add an Android app with package `com.rahul.assignmentrahul` and your debug SHA-1 (`./gradlew signingReport`).
   Put `google-services.json` in `app/`.
3. Authentication → enable **Email/Password**.
4. Firestore → create a database → **Rules** → paste [`firestore.rules`](firestore.rules) → Publish.

### 2. Exit-alert service (Cloudflare Workers, free)
1. Firebase console → Project settings → **Service accounts → Generate new private key**.
   Keep the file outside the repo.
2. Then:
   ```powershell
   cd notifier
   npm install
   npx wrangler login
   npx wrangler deploy        # prints https://geofence-exit-alerts.<you>.workers.dev
   Get-Content "C:\path\to\key.json" -Raw | npx wrangler secret put FIREBASE_SERVICE_ACCOUNT
   ```
   For a different Firebase project, change `FIREBASE_PROJECT_ID` in `notifier/wrangler.toml`.

### 3. App
Add the service URL to `local.properties`, then run the `app` configuration:
```properties
EXIT_ALERT_URL=https://geofence-exit-alerts.<you>.workers.dev
```

## Testing

**Unit tests:** `./gradlew testDebugUnitTest`. These cover the duplicate filter and cooldown,
the logarithmic radius slider, invite codes and the circle geometry.

**End to end:** I used an emulator as the member who moves (you can set its location) and a real
phone as the member who receives the alert.
1. Phone: sign up, create a group, copy the invite code.
2. Emulator: sign up with a second account and join with the code. Complete the permission checklist on both.
3. Emulator: Extended controls (⋯) → Location → set a point, then on the phone set the zone there.
4. Emulator: move the location well outside the zone.
5. Within about a minute the phone shows "… left the zone", and the exit appears under Recent exits.

Tips:
- Use a Google Play emulator image, and open Google Maps on it once so location updates flow.
- Move clearly outside the zone (about 2× the radius).
- App logs: `adb logcat -s GeofenceReceiver GeofenceEventWorker GeofenceSyncWorker`.
- Service logs: `npx wrangler tail`.

## Known limitations and next steps

- **Exit latency** is controlled by Android (usually 1–3 min, longer in Doze).
- **Small zones:** radius goes down to 10 m for demos, but below ~100 m normal GPS error makes
  transitions unreliable. The app shows a warning.
- **One device per account:** one FCM token per user. Next step: a `tokens` subcollection.
- **Exit-only alerts.** "Back in the zone" alerts and leave/delete group aren't built yet.
- **Zone edits** reach other members' devices the next time their app runs. A silent "resync" push would make that instant.
- **KMP:** move `domain/` and the models into a shared module and add a SwiftUI client.
