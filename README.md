# Openly

A radar for in-person openness. Sit down in a cafe, flip your status to **open**, and anyone
else nearby who's also open shows up as a blip — direction and rough distance only, no name
until you look closer. Tap a blip to send interest; nothing unlocks until the other person
approves too. Once you're both in, chat opens up and you can decide to actually sit together.

No signup screen: opening the app signs you in anonymously so there's zero friction between
"I'm curious" and "I'm visible."

## How it's built

- Kotlin + Jetpack Compose (Material 3), matching the conventions of this machine's other
  Android projects — manual DI via an `Application` subclass holding lazily-built
  repositories, `ViewModelProvider.Factory` per screen, `StateFlow` throughout, no Hilt/Room.
- **Firebase Auth** (anonymous) for identity, **Firestore** for presence, interest requests,
  matches, and chat — this needs a backend because the radar is inherently multi-device.
- **FusedLocationProviderClient** for GPS. The radar renders real bearing + distance computed
  from both users' lat/lng (`domain/GeoUtils.kt`), not randomized placement.
- Every field mentioned below lives in `data/model/`; Firestore paths are centralized in
  `data/remote/FirestorePaths.kt`.

### Firestore shape

```
users/{uid}          displayName, avatarEmoji, bio, isOpen, lat, lng, lastActiveMillis
interests/{id}        id = "{fromUid}_{toUid}"; fromUid, toUid, status, createdAtMillis
matches/{id}           id = sorted "{uidA}_{uidB}"; uids: [uidA, uidB], createdAtMillis,
                       lastMessageText, lastMessageSenderUid, lastMessageAtMillis
matches/{id}/messages/{id}   senderUid, text, sentAtMillis
```

The `lastMessage*` fields on the match are a denormalised copy of the newest message. They let the
chat list, the unread dot and the bottom-bar badges run off a **single** `matches` listener instead
of opening a messages listener per conversation. `firestore.rules` therefore allows a participant to
update a match — but narrowly: only those three keys, and only with `lastMessageSenderUid ==
request.auth.uid`, so membership can't be edited and messages can't be attributed to someone else.

Unread state itself is **local** (DataStore, keyed per match): "have I seen this" is per-device by
nature, and keeping it off the match doc avoids a Firestore write every time a chat is opened.

The radar query (`RadarRepository`) filters `isOpen == true` and a latitude range server-side,
then finishes the longitude box and the exact-circle distance/bearing client-side — Firestore
only allows a range filter on one field per query, so a full geohash setup was skipped as
overkill for cafe-scale proximity.

## Setup (required before this builds)

1. **Create a Firebase project** at console.firebase.google.com.
2. **Add an Android app** to it with package name `com.openly.app`. Download the generated
   `google-services.json` and place it at `app/google-services.json` (there's a
   `.template` placeholder there now — replace it, don't rename it).
3. In the Firebase console, **enable Firestore** (Native mode, any region) and **enable
   Anonymous sign-in** under Authentication → Sign-in method.
4. **Apply `firestore.rules`** — paste its contents into Firestore → Rules in the console, or
   run `firebase deploy --only firestore:rules` if you have the Firebase CLI set up.
5. The radar's compound query needs a composite index. Either deploy
   `firestore.indexes.json` (`firebase deploy --only firestore:indexes`), or just run the app —
   Firestore's first failure prints a console link that creates the exact index for you.
6. Open the project root in Android Studio, let Gradle sync, run on a device or emulator.

To actually test the mutual-approval flow you need **two separate installs** (two devices, or
two emulators each signed in anonymously — anonymous auth is per-install) with location
permission granted and GPS positions within the radar radius of each other. Android emulators
let you fake GPS coordinates from the extended controls panel, which is the easiest way to
simulate "both people in the same cafe" without leaving your desk.

## Testing locally without a real Firebase project

The app also runs against the **Firebase Local Emulator Suite** — useful for trying the whole
flow (sign-in, radar, requests, chat) without creating a real Firebase project at all:

1. `app/google-services.json` just needs to exist and have `project_id: "demo-openly"` (any
   "demo-"-prefixed project id skips real backend validation) — the placeholder already committed
   works as-is.
2. Requires the Firebase CLI (`npm install -g firebase-tools`) and a **JDK 21+** on `PATH` when
   running it (the emulators refuse to start on JDK 17, which is what the Android build itself
   still uses — keep them separate, e.g. `JAVA_HOME` set only in the shell that launches
   `firebase emulators:start`).
3. From the project root: `firebase emulators:start --only auth,firestore --project demo-openly`
   — starts Auth on `:9099` and Firestore on `:8080`, UI at `http://127.0.0.1:4000`.
4. `OpenlyApp.onCreate()` points `FirebaseAuth`/`FirebaseFirestore` at `10.0.2.2` (the emulator's
   alias for the host) whenever `BuildConfig.DEBUG` is true, so a debug build on an emulator/device
   finds the local emulators automatically — no code changes needed to switch.
5. `app/src/debug/res/xml/network_security_config.xml` allows cleartext HTTP to `10.0.2.2` only
   in debug builds (release builds get no such exception).

Gotchas hit while setting this up, in case they recur:
- If the Android emulator was previously signed in against a **different** run of the Firebase
  emulators, its cached auth token won't match the new (in-memory, non-persisted) Auth emulator
  instance and every Firestore write fails with `UNAUTHENTICATED` / `INVALID_REFRESH_TOKEN`.
  Fix: `adb shell pm clear com.openly.app` and sign in fresh.
- The radar is only half a feature unless each client also *writes* its own position. `RadarViewModel`
  publishes the device's location (via `ProfileRepository.updateLocation`) on every location tick,
  which is also what keeps `lastActiveMillis` fresh — presence goes stale after 5 minutes. Removing
  that write makes every radar look permanently empty while still appearing to work, because reads
  succeed and simply return nobody. Only a genuine two-device test catches it.
- Kotlin properties named `isXxx: Boolean` are a known Firestore POJO-mapping trap: the Java
  mapper strips the `is` prefix when inferring a field name from the getter (`isOpen()` → field
  `open`), which doesn't match the literal `"isOpen"` key this app writes elsewhere, so the value
  silently reads back as its default. `UserProfile.isOpen` is pinned with `@get:PropertyName`/
  `@set:PropertyName` for this reason — worth the same treatment for any future `isXxx` field.

## Privacy notes (read before treating this as production-ready)

- Firestore security rules can't run "is this doc within N meters" logic — the current rules
  let any signed-in user *read* any `users/{uid}` document (needed for the client-side radius
  query), which means an authenticated client could technically enumerate everyone's live
  coordinates, not just people currently in range. For a real launch, move the proximity query
  behind a Cloud Function or Cloud Run service that has service-account access to Firestore
  and only returns already-filtered, coarsened results to clients.
- Location is written verbatim (`lat`/`lng`) rather than geohash-truncated. Fine for a local
  MVP; worth truncating precision server-side before any public release.
- There's no block/report flow and no rate limiting on `sendInterest`.

## Notifications

New messages raise a local notification (channel `openly_messages`) when they land in a conversation
you aren't currently looking at — the open thread is suppressed via `BadgeViewModel.setOpenChat`, and
opening a thread cancels its notification. Notifications are de-duped on `lastMessageAtMillis`
because Firestore re-emits the whole match list on any change, so a naive listener would re-notify
for the same message every time anything else updated.

This works while the app process is alive: foreground on another tab, or recently backgrounded.
**Waking a fully killed app still needs FCM plus a Cloud Function trigger** — that part is genuinely
not built (see below). `POST_NOTIFICATIONS` is requested at launch on Android 13+.

## What's intentionally not built yet

- True push delivery when the app has been killed (needs FCM + a Cloud Function on message create).
  The in-process notifier above covers the running-app case only.
- Notifications for incoming *interest* requests (the Requests badge covers this in-app).
- Editing/deleting sent messages, typing indicators, read receipts.
- Any moderation tooling.
