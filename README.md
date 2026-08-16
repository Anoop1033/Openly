# Openly

A radar for in-person openness. Sit down in a cafe, flip your status to **open**, and anyone
else nearby who's also open shows up as a blip — direction and rough distance only, no name
until you look closer. Tap a blip to send interest; nothing unlocks until the other person
approves too. Once you're both in, chat opens up and you can decide to actually sit together.

No signup screen: opening the app signs you in anonymously so there's zero friction between
"I'm curious" and "I'm visible."

## How it's built

- **Kotlin Multiplatform + Compose Multiplatform.** `shared/` holds ~90% of the app —
  models, repositories, ViewModels, and every screen — in `commonMain`, so Android and iOS
  run the same UI and business logic. `androidApp/` and `iosApp/` are thin hosts that just
  wire platform-specific pieces (location, notifications, prefs storage) and launch the
  shared `AppRoot` composable. Manual DI via `AppContainer` (`di/AppContainer.kt`) holding
  lazily-built repositories, `viewModelFactory { initializer { } }` per screen (the
  multiplatform-safe replacement for `ViewModelProvider.Factory`, which is JVM-only),
  `StateFlow` throughout, no Hilt/Room.
- **Firebase Auth** (anonymous) for identity, **Firestore** for presence, interest requests,
  matches, and chat — this needs a backend because the radar is inherently multi-device.
  Firebase has no official Kotlin Multiplatform SDK, so this uses
  [GitLive's `firebase-kotlin-sdk`](https://github.com/GitLiveApp/firebase-kotlin-sdk), a
  community wrapper around the native Android and iOS SDKs behind one common API.
- **Location + notifications** are `expect`/`actual`: `FusedLocationProviderClient` on
  Android, `CoreLocation` on iOS (`data/location/LocationTracker.*.kt`); local notifications
  via `NotificationCompat` on Android, `UNUserNotificationCenter` on iOS
  (`notification/MessageNotifier.*.kt`). The radar renders real bearing + distance computed
  from both users' lat/lng (`domain/GeoUtils.kt`), not randomized placement.
- Every field mentioned below lives in `data/model/`; Firestore paths are centralized in
  `data/remote/FirestorePaths.kt`.

### Firestore shape

```
users/{uid}          displayName, avatarEmoji, bio, isOpen, lat, lng, lastActiveMillis,
                       fcmToken
interests/{id}        id = "{fromUid}_{toUid}"; fromUid, toUid, status, createdAtMillis
matches/{id}           id = sorted "{uidA}_{uidB}"; uids: [uidA, uidB], createdAtMillis,
                       lastMessageText, lastMessageSenderUid, lastMessageAtMillis,
                       lastMessageType, typingUid, typingAtMillis
matches/{id}/messages/{id}   senderUid, text, sentAtMillis, readAtMillis, editedAtMillis,
                       deletedForEveryone, deletedFor[], reactions{uid: emoji},
                       replyToId, replyToText, replyToSenderUid,
                       type (TEXT|IMAGE|VOICE), mediaUrl, mediaDurationSeconds
```

Cloud Storage holds chat attachments at `chat/{matchId}/{senderUid}-{millis}.{jpg|m4a}`.

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
   `google-services.json` and place it at `androidApp/google-services.json` (there's a
   `.template` placeholder there now — replace it, don't rename it).
3. In the Firebase console, **enable Firestore** (Native mode, any region) and **enable
   Anonymous sign-in** under Authentication → Sign-in method.
4. **Apply `firestore.rules`** — paste its contents into Firestore → Rules in the console, or
   run `firebase deploy --only firestore:rules` if you have the Firebase CLI set up.
5. The radar's compound query needs a composite index. Either deploy
   `firestore.indexes.json` (`firebase deploy --only firestore:indexes`), or just run the app —
   Firestore's first failure prints a console link that creates the exact index for you.
6. Open the project root in Android Studio, let Gradle sync, run `androidApp` on a device or
   emulator. For iOS, see the **iOS** section below — it needs a separate, Mac-only setup step.

To actually test the mutual-approval flow you need **two separate installs** (two devices, or
two emulators each signed in anonymously — anonymous auth is per-install) with location
permission granted and GPS positions within the radar radius of each other. Android emulators
let you fake GPS coordinates from the extended controls panel, which is the easiest way to
simulate "both people in the same cafe" without leaving your desk.

## Testing locally without a real Firebase project

The app also runs against the **Firebase Local Emulator Suite** — useful for trying the whole
flow (sign-in, radar, requests, chat) without creating a real Firebase project at all:

1. `androidApp/google-services.json` just needs to exist and have `project_id: "demo-openly"`
   (any "demo-"-prefixed project id skips real backend validation) — the placeholder already
   committed works as-is.
2. Requires the Firebase CLI (`npm install -g firebase-tools`) and a **JDK 21+** on `PATH` when
   running it (the emulators refuse to start on JDK 17, which is what the Android build itself
   still uses — keep them separate, e.g. `JAVA_HOME` set only in the shell that launches
   `firebase emulators:start`).
3. From the project root:
   `firebase emulators:start --only auth,firestore,storage --project demo-openly`
   — starts Auth on `:9099`, Firestore on `:8080`, Storage on `:9199`, UI at
   `http://127.0.0.1:4000`. Storage is needed for photo/voice messages; without it those uploads
   fail while the rest of the app works fine. Add
   `--import ./emulator-data --export-on-exit ./emulator-data` to keep accounts and chats across
   restarts (the emulators are otherwise purely in-memory).
4. `OpenlyApp.onCreate()` (`androidApp/src/main/java/com/openly/app/OpenlyApp.kt`) points
   `AppContainer.useEmulators()` at `10.0.2.2` (the Android emulator's alias for the host) or
   `BuildConfig.EMULATOR_LAN_HOST` (a physical phone on the same Wi-Fi) whenever
   `BuildConfig.DEBUG` is true, so a debug build finds the local emulators automatically — no
   code changes needed to switch. A physical phone additionally needs the host machine's
   firewall to allow inbound TCP on 8080/9099/4000 from the LAN, since Windows Firewall blocks
   those by default on a "Public" network profile.
5. `androidApp/src/debug/res/xml/network_security_config.xml` allows cleartext HTTP to
   `10.0.2.2` and the LAN host only in debug builds (release builds get no such exception).

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
- Kotlin properties named `isXxx: Boolean` used to be a Firestore POJO-mapping trap: the Firebase
  *Java* mapper strips the `is` prefix when inferring a field name from the getter (`isOpen()` →
  field `open`), which didn't match the literal `"isOpen"` key written elsewhere, so the value
  silently read back as its default. Since the move to kotlinx.serialization (which uses the
  property name verbatim) this no longer applies — but it's worth knowing if any code ever drops
  back to the Java SDK.
- Cloud Storage uploads without an explicit content type arrive as `application/octet-stream` and
  are rejected by `storage.rules`, which surfaces as a bare `403 / -13021` with no hint about the
  cause. If attachments start failing, check the metadata before the rules.

## iOS

**Building this requires a Mac with Xcode and CocoaPods — there is no way around that, and
none of the iOS code below has ever been compiled or run.** It was written on Windows, which
can compile the Android app fine but cannot build for iOS at all (Kotlin/Native's iOS targets
and Xcode itself are Apple-only tooling). Every iOS-specific file has a `NOTE: never
compiled/run — verify on a Mac` comment for this reason. Treat all of this as a first draft to
build and debug on a Mac, not as tested code.

What's there:

- `shared/src/iosMain/` — `expect`/`actual` implementations of `LocationTracker` (CoreLocation),
  `MessageNotifier` (`UNUserNotificationCenter`), `LocationPermissionRequester`, and the prefs
  directory lookup (NSDocumentDirectory), plus `shared/src/iosMain/.../MainViewController.kt`,
  which builds the `AppContainer` and returns the root `UIViewController` for Swift to host.
- `iosApp/iosApp/` — a SwiftUI shell: `iOSApp.swift` (calls `FirebaseApp.configure()` at
  launch), `ContentView.swift` (wraps `MainViewController()` via
  `UIViewControllerRepresentable`), `Info.plist` (declares
  `NSLocationWhenInUseUsageDescription` — CoreLocation stays silent without it), and a
  `GoogleService-Info.plist.template` (same idea as the Android `.template`).
- `iosApp/Podfile` — GitLive's Firebase SDK wraps the native Firebase iOS SDK rather than
  bundling it, so it has to be linked via CocoaPods.
- `shared/build.gradle.kts` has a `cocoapods { }` block (`FirebaseCore`, `FirebaseAuth`,
  `FirebaseFirestore`) that generates `shared/shared.podspec`, which the Podfile then pulls in
  via `pod 'shared', :path => '../shared'`.

**What there is *not*** is a `.xcodeproj` — hand-writing one on Windows without being able to
open or validate it in Xcode would be more likely to produce a broken project file than a
working one. Create it for real on a Mac instead:

1. Install Xcode (App Store) and CocoaPods (`sudo gem install cocoapods`, or `brew install
   cocoapods`).
2. In Xcode: **File → New → Project → iOS → App**. Product name `iosApp`, interface **SwiftUI**,
   language **Swift**, bundle identifier `com.openly.app`, save it *into* the existing
   `iosApp/` folder (let it merge with the `iosApp/iosApp/` sources already there — replace the
   Xcode-generated `iOSApp.swift`/`ContentView.swift`/`Info.plist` with the ones already in this
   repo, or add the repo's files to the target and delete Xcode's generated versions).
3. Download `GoogleService-Info.plist` from the Firebase console (iOS app, same project as
   Android — add an iOS app to the Firebase project first if there isn't one, bundle ID
   `com.openly.app`) and drop it in `iosApp/iosApp/`, then add it to the Xcode target
   (uncheck "Copy items if needed" if it's already inside the target folder).
4. From the `iosApp/` directory: `pod install`. This reads `shared/shared.podspec` (regenerate
   it first with `./gradlew :shared:podspec` if it doesn't exist yet) and produces
   `iosApp.xcworkspace` — **open the `.xcworkspace`, not the `.xcodeproj`,** from here on.
5. Build once for a simulator to force Kotlin/Native to compile the `Shared` framework for that
   architecture (CocoaPods' `syncFramework` build phase, added automatically by the Gradle
   `cocoapods` plugin, does this on every build).
6. For local-emulator-suite testing, note the iOS Simulator shares the host Mac's network stack,
   so `container.useEmulators("localhost")` works directly — no `10.0.2.2`-style alias needed.
   `MainViewController.kt` doesn't call this yet; add it there (guarded however you prefer for
   debug builds) if you want simulator ↔ local-emulator testing like the Android side has.
7. Delivering notifications while the app is in the foreground needs a
   `UNUserNotificationCenterDelegate` set on the Swift side (`UNUserNotificationCenter`
   suppresses foreground notifications by default) — not yet added; do this in `iOSApp.swift`'s
   `init()` if foreground delivery turns out to matter.

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

## Chat features

The thread supports most of what people expect from a modern messenger:

- **Timestamps and day separators** — every bubble carries its send time; `Today` / `Yesterday` /
  `5 Aug 2026` pills mark day boundaries. Formatting is hand-rolled in `platform/TimeFormat.kt`
  with kotlinx-datetime rather than platform date formatters, which differ between Android and iOS
  and would otherwise render the same thread differently on each.
- **Read receipts** — a single ✓ when sent, a blue ✓✓ once the recipient has the thread open.
  `readAtMillis` is stamped by the *reader*, which is why `firestore.rules` lets a participant
  update that one field on someone else's message and nothing else.
- **Typing indicator** — `typingUid`/`typingAtMillis` on the match doc, so the existing single
  `matches` listener picks it up with no extra reads. The signal is re-pinged every ~2s while
  typing and treated as stale after 5s **on read**, so a client that dies mid-typing can't leave
  the indicator stuck on. Shows in the chat header and in the chat list row.
- **Online / last seen** — derived from `lastActiveMillis`, using the same 5-minute freshness
  window the radar already applies to presence.
- **Reply / quote** — the quoted text is denormalised onto the reply, so rendering never needs a
  second fetch and still reads correctly if the original is later deleted.
- **Emoji reactions** — one per person (`reactions: {uid: emoji}`), re-tapping the same emoji
  clears it. The rules allow a participant to touch only their own key in that map.
- **Edit and delete** — edit rewrites `text` and stamps `editedAtMillis` (author only). "Delete for
  everyone" keeps the document as a tombstone rather than removing it, so the thread doesn't
  silently reshuffle for the other person; "delete for me" appends your uid to `deletedFor`.
- **Photos and voice notes** — see below.
- **Copy**, and a long-press menu that gathers all of the above.

### Attachments

Photos go through the platform photo picker, get downscaled to 1600px on the long edge and
re-encoded as JPEG (`MediaPicker.android.kt`) before upload — phone cameras produce multi-megabyte
files and a chat bubble never needs that. Voice notes record to AAC/m4a, the format both platforms
encode and play natively. Both upload to Cloud Storage via `MediaRepository` and travel as byte
arrays, the one representation that means the same thing on Android and iOS.

Uploads **must** carry an explicit content type — without it Firebase stores them as
`application/octet-stream` and `storage.rules` (which pins uploads to `image/*` or `audio/*`, max
10 MB) rejects them.

Images render with [Coil 3](https://coil-kt.github.io/coil/), the image loader that supports
Compose Multiplatform rather than Android only.

Attachments are deliberately immutable once sent: "delete for everyone" clears the Firestore
pointer but leaves the Storage object. That means **orphaned files accumulate** — a real deployment
wants a lifecycle rule or a cleanup function on message delete.

## Notifications

New messages raise a local notification (channel `openly_messages`) when they land in a conversation
you aren't currently looking at — the open thread is suppressed via `BadgeViewModel.setOpenChat`, and
opening a thread cancels its notification. Notifications are de-duped on `lastMessageAtMillis`
because Firestore re-emits the whole match list on any change, so a naive listener would re-notify
for the same message every time anything else updated.

That path only works while the app process is alive. For a backgrounded or killed app, the
`notifyNewMessage` Cloud Function in `functions/index.js` fires on message create, looks up the
recipient's `fcmToken`, and pushes through FCM. `POST_NOTIFICATIONS` is requested at launch on
Android 13+.

The push is sent **data-only** on purpose. With a `notification` payload the OS posts its own tray
entry, which the app can't de-duplicate against the in-process notifier — so someone with the app
open on another tab would see the same message twice. `OpenlyMessagingService` builds the
notification itself, reusing the same channel and notification id so the two paths collapse into a
single tray entry instead of stacking.

**Push cannot be tested against the Local Emulator Suite** — there is no FCM emulator, and the
function needs a deployed project. To try it for real:

1. Switch off `demo-openly` onto a real Firebase project (Setup section above).
2. Upgrade the project to the **Blaze** plan — Cloud Functions requires it.
3. `cd functions && npm install`, then `firebase deploy --only functions`.
4. For iOS, additionally upload an APNs auth key under Project Settings → Cloud Messaging, and add
   the Push Notifications + Background Modes (remote notifications) capabilities in Xcode. The
   iOS side of push is **written but never compiled or run** like the rest of the iOS code.

## What's intentionally not built yet

- Notifications for incoming *interest* requests (the Requests badge covers this in-app).
- Cleanup of orphaned Storage objects after "delete for everyone" (see Attachments above).
- Voice-note playback progress/scrubbing — playback is start-and-stop only.
- Group chats, forwarding, disappearing messages, starred messages, chat search.
- Voice/video calling, and end-to-end encryption. Both are large enough to be their own projects:
  calling needs WebRTC plus a signalling server, and E2E would mean giving up the server-side
  Cloud Function push preview along with anything else that reads message content.
- Any moderation tooling.
