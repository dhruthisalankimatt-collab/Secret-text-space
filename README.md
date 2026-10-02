# DSI

A private Android messaging app for two people (made of couples)

One conversation, two accounts, no registration, no discovery, no feed. The app opens on a
blank white screen that only responds to a fixed three-tap sequence.

---

## What is in this repository

| Path | Contents |
|---|---|
| `app/` | Android app (Kotlin, Jetpack Compose) |
| `firebase/` | Security rules for Firestore, Storage and Realtime Database |
| `firebase/functions/` | Cloud Function: notification rule and push delivery |
| `scripts/` | One-time account provisioning |
| `docs/` | Schema, security notes, testing, deployment, branding |

Start with **[docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)** — nothing runs until the Firebase
project exists and the two accounts are provisioned.

---

## How it works

**Launch.** A solid white screen split into four equal invisible touch targets. The sequence
is bottom-right → top-left → top-right. A wrong tap does nothing at all: no vibration, no
sound, no message, no visible change. The screen is identical before and after.

**Identity.** After the first correct sequence, the user picks Queen or Mrs.Heera and enters
that identity's passphrase. Picking a name does not grant it — the server holds a `role`
custom claim on each account, and every security rule trusts the claim rather than anything
the client says. Once authenticated, the device is bound to that identity and the choice is
not offered again.

**Chat.** Text, gallery photos, camera photos, voice notes, emoji and links. Replies,
timestamps, sent/delivered/seen ticks, online status with last-seen, and typing indicators.

**Notifications.** A message whose text is exactly `Imp` or exactly `imp` produces the
notification **Alter**. Everything else produces **Notification**. The decision is made in a
Cloud Function, and the push payload carries only that word — never the message itself.

---

## Stack

- Kotlin 2.0, Jetpack Compose (Material 3), min SDK 26, target SDK 35
- Firebase Auth — two pre-provisioned accounts with server-set role claims
- Cloud Firestore — messages, with offline queueing
- Realtime Database — presence and typing (chosen for `onDisconnect`, which Firestore lacks)
- Cloud Storage — photos and voice notes, private, served via short-lived signed URLs
- Cloud Functions (Node 20) — notification classification and push
- Firebase App Check (Play Integrity) — blocks calls from anything but the signed app

## Build

```bash
# 1. Put your google-services.json in app/
# 2. Then:
./gradlew assembleDebug        # debug build
./gradlew assembleRelease      # signed release; see docs/DEPLOYMENT.md
```

Requires JDK 17 and Android Studio Ladybug or newer.

## Test the notification rule without any setup

```bash
cd firebase/functions && node test/notificationRule.test.js
```

All 13 cases from the specification pass, including `IMP`, `imp!` and `Imp please call`
correctly falling through to `Notification`.

---

## Two things to decide before release

**The app icon.** The spec asks for the DSATM logo. That mark belongs to Dayananda Sagar
Academy of Technology and Management, and using an institution's registered mark as the
launcher icon of an unaffiliated private app is a trademark problem regardless of intent.
The repository ships a neutral placeholder. See [docs/BRANDING.md](docs/BRANDING.md) for what
to do with it.

**What the tap sequence is and is not.** It keeps a casual glance out of the conversation.
It is not a security control — it is three taps with 64 possible combinations, and anyone
who watches once knows it. The real protection is the passphrase, the role claim and the
security rules. See [docs/SECURITY.md](docs/SECURITY.md).
