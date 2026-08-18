# Shipping Openly to Google Play

Everything in the repository is configured for a signed release build. What remains is the work
that needs accounts, money, or a browser — none of which can be done from the codebase.

Work through the blockers first; they are ordered by how badly they hurt if discovered late.

---

## Blockers

### 1. Create a real Firebase project

`androidApp/google-services.json` currently reads `project_id: demo-openly`. That is a
Local Emulator Suite placeholder, not a backend. **A release build compiled against it will install
and then fail at sign-in**, because there is no such project on Google's servers.

1. Create a project at console.firebase.google.com.
2. Add an Android app with package name `com.openly.app`.
3. Download the real `google-services.json` and replace `androidApp/google-services.json`.
   It stays gitignored — that is intentional.
4. Enable **Anonymous** sign-in under Authentication → Sign-in method.
5. Deploy the rules, which are written and ready:
   ```
   firebase deploy --only firestore:rules,storage
   ```
6. Add the release signing certificate's SHA-256 to the Firebase Android app once Play has issued
   it (Play Console → Setup → App signing).

### 2. Upgrade to the Blaze plan

`functions/index.js` sends the message pushes. Cloud Functions cannot be deployed on the free Spark
plan. Blaze is pay-as-you-go with a free tier that an app this size will sit inside, but it does
require a card on file. Then:

```
firebase deploy --only functions
```

Without this the app still works — messages arrive whenever it is open — but users get no
notification when it is closed, which for a chat app is close to unusable.

### 3. Generate the upload key

```
keytool -genkeypair -v -keystore openly-upload.jks -keyalg RSA -keysize 2048 \
        -validity 10000 -alias openly
```

Copy `keystore.properties.template` to `keystore.properties` and fill in the passwords.

**Back up `openly-upload.jks` and `keystore.properties` off this laptop, today.** They are
gitignored and exist nowhere else. Losing them means the listing can never be updated — the only
remedy is republishing under a new package name, stranding every existing install.

### 4. Publish a web-accessible deletion route

The in-app path exists — **Settings → Delete account**, wired to `AccountRepository`. Play also
requires a route reachable **without installing the app**, for people who have already uninstalled.
A static form, or simply a published email address that you action by hand, satisfies this; it just
has to exist and be linked from the store listing.

Note that the cascade is split deliberately. The client deletes what belongs to one person — the
profile, the interest records, the anonymous auth account — so the user disappears from everyone's
radar immediately. Matches, messages and attachments are deleted by `purgeDeletedAccount` in
`functions/index.js`, under the admin SDK. That split exists because the alternative is security
rules that let *either* participant delete a shared thread, which would let anyone wipe someone
else's history on demand.

**Consequence: if you never deploy Functions (blocker 2), messages and attachments are never
erased.** The visible part of deletion works, but the promise made in `PRIVACY.md` would not be
fully kept. Deploy the function before accepting real users.

### 5. Host the privacy policy

`PRIVACY.md` is written and describes what the code actually does, but has `[BRACKETED]`
placeholders to fill. Publish it at a public URL — GitHub Pages will serve it from this repo for
free — and paste that URL into the listing. Play will not approve an app that collects location
without a reachable policy.

---

## Building the bundle

```
./gradlew :androidApp:bundleRelease
```

Output: `androidApp/build/outputs/bundle/release/androidApp-release.aab` — upload that, not an APK.

Bump the version on every upload; Play rejects a `versionCode` it has already seen:

```
./gradlew :androidApp:bundleRelease -PopenlyVersionCode=2 -PopenlyVersionName=1.0.1
```

**Test the release build before uploading.** R8 is on now, and shrinking is exactly the kind of
change that works in debug and crashes in release. Install the release build on a real device and
walk through sign-in, radar, a match, and sending a photo and a voice note:

```
./gradlew :androidApp:installRelease
```

If something crashes only in release, the culprit is almost certainly a missing keep rule — read
`androidApp/build/outputs/mapping/release/` and add to `androidApp/proguard-rules.pro`.

---

## Play Console

One-time US$25 registration. Individual developer accounts also require identity verification and,
since 2023, a **closed test with at least 12 testers running for 14 continuous days** before
production access is granted. Plan for that: it is a two-week wall, not a formality.

### Data safety form

Declare all of this — it must match `PRIVACY.md` or the app gets rejected for inconsistency:

| Data type | Collected | Shared | Purpose |
|---|---|---|---|
| Approximate location | Yes | No | App functionality (radar) |
| Precise location | Yes | No | App functionality (radar) |
| Name (user-chosen) | Yes | No | App functionality |
| Messages | Yes | No | App functionality |
| Photos | Yes | No | App functionality |
| Voice recordings | Yes | No | App functionality |
| User IDs | Yes | No | App functionality |

Encrypted in transit: yes. Deletion route: yes (once blocker 4 is done).

### Listing assets

- App icon, 512×512 PNG
- Feature graphic, 1024×500
- At least 2 phone screenshots (16:9 or 9:16, min 320px on the short side)
- Short description (80 chars), full description (4000)

### Content rating

Answer the questionnaire honestly: Openly has **user-generated content and unmoderated
user-to-user communication**, which will push the rating up and is a common source of follow-up
questions. Expect to explain what moderation exists.

---

## Worth knowing before you submit

**Location + strangers + chat is a scrutinised combination.** Apps that connect nearby strangers
draw closer review than average, and Play's User Generated Content policy expects a way to report
and block abuse. There is none in the app today. It is not required to pass automated checks, but
it is the sort of gap that turns into a rejection or a takedown later, and it is much cheaper to
build now than after launch.

**Storage costs are unbounded.** Nothing deletes old images or voice notes. A cleanup function, or
a Storage lifecycle rule, is worth adding before real users arrive.

**`targetSdk 35` is current** as of this writing and satisfies Play's requirement for new apps.
Google raises the floor each August — expect to bump it annually.
