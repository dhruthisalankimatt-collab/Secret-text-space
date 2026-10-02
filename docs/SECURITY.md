# Security: what is protected, and what is not

## What the tap sequence does

It keeps the conversation off the screen when someone picks up the phone or glances over.
That is a real and useful property, and it is the only one it has.

It is **not** a security control:

- Three taps across four zones is 64 combinations, and wrong attempts are unlimited and free.
- Anyone who watches the phone being unlocked once knows the sequence permanently.
- It protects nothing on the server. A stolen passphrase gets someone into the conversation
  from any device without ever seeing the white screen.

Treat it as a privacy screen, not a lock. The passphrase and the device lock are the locks.

## What actually enforces access

**Role claims.** Each account carries a `role` custom claim set once by an administrator via
the Admin SDK. Clients cannot read-modify-write a claim; the Firebase Auth token is signed by
Google and verified server-side. Every rule in `firestore.rules`, `storage.rules` and
`database.rules.json` keys off this claim. Selecting "Queen" on the identity screen without
Queen's passphrase produces nothing at all.

**Identity mismatch is refused, not corrected.** If someone signs in successfully but the
returned claim does not match the identity they tapped, the app signs them back out rather
than quietly continuing as the real account.

**App Check.** Play Integrity attestation is required on every Firestore call. A stolen
passphrase used from `curl` or a repackaged APK fails attestation.

**Server-side notification classification.** The `Imp`/`imp` rule runs in a Cloud Function.
A modified client cannot change what the other phone's notification says.

**Immutable history.** Rules permit exactly two field updates (`seenAt`, `deliveredAt`) and
only by the recipient, only to the server clock. Nobody can rewrite a sent message or
backdate one.

## Local data on the phone

| Data | Where | Encrypted |
|---|---|---|
| Bound identity | EncryptedSharedPreferences | Yes — AES-256-GCM, key in the Android Keystore |
| Auth refresh token | Firebase SDK, app-private storage | At rest by the OS, if a device lock is set |
| Message cache | Firestore local cache, app-private storage | At rest by the OS, if a device lock is set |
| Photos, voice notes | App cache, deleted after upload | — |

**The honest version:** Android's file-based encryption protects app-private storage only
while the device is locked and only if a screen lock is set. On a phone with no PIN, or an
unlocked phone in someone's hands, the Firestore cache is readable with enough effort. The
app does not add its own database encryption layer on top.

Mitigations in place: `FLAG_SECURE` blocks screenshots and blanks the recents thumbnail;
backups are disabled so the conversation never lands in Google Drive; release builds strip
all logging via ProGuard; message text is never written to logs or analytics, on the device
or in the function.

**Set a screen lock on both phones.** It does more for this app's security than anything in
the code.

## If a phone is lost

1. Re-run `scripts/provision_users.js` with a new passphrase for that identity. It calls
   `revokeRefreshTokens`, which kills the existing session on the lost device the next time
   it tries to refresh — within an hour, and immediately on any Firestore write.
2. Clear that user's `fcmToken` in Firestore so pushes stop reaching the device.
3. The Firestore cache already on the lost phone remains readable until the app is removed.
   Remote wipe through Google's Find My Device is the only way to address that.

## End-to-end encryption

Not implemented. Messages are encrypted in transit (TLS) and at rest on Google's servers with
Google's keys, which means Google — and anyone with access to the Firebase console — can read
the conversation.

Real E2EE would need a key exchange between the two devices, key backup and recovery, and a
verification step, plus server-side media that is opaque to the Cloud Function. That last part
conflicts with the notification rule as specified: a function cannot classify text it cannot
read, so the `Imp` check would have to move back to the client, where a modified app could
change it.

Rather than claim encryption that is not there, this build says plainly what it is:
private from other people, not private from the backend operator. If E2EE matters more than
the server-side notification rule, that is a design conversation worth having.
