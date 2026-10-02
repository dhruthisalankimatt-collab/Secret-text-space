# Limitations and things not implemented

Written plainly so nothing is a surprise later.

## Not implemented, by decision

**End-to-end encryption.** Messages are encrypted in transit and at rest with Google's keys.
Google and anyone with Firebase console access can read the conversation. Implementing real
E2EE would also break the server-side notification rule, since a function cannot classify
text it cannot read. Full reasoning in [SECURITY.md](SECURITY.md).

**Local database encryption.** The Firestore cache relies on Android's file-based encryption,
which protects app-private storage while the device is locked and only if a screen lock is
set. No additional encryption layer was added on top.

**Message editing, disappearing messages, media download to gallery, message search,
forwarding, starred messages, video, documents, voice or video calls.** None were requested.
The scope-control section of the spec rules out group chats, profiles, feeds and the rest,
and those are structurally excluded: the chat id is the hard-coded literal `main`, enforced
in the security rules.

**Message pagination.** The app loads the most recent 200 messages. Older history exists in
Firestore but is not fetched. For two people this takes a long time to matter; when it does,
it is a `startAfter` query on the existing listener.

## Platform limits nobody can code around

**Background push on Chinese-OEM Android.** Xiaomi, Oppo, Vivo, Realme and OnePlus kill
background processes aggressively. Notifications will be delayed or dropped unless DSI is set
to Unrestricted battery usage, and on Xiaomi also given Autostart. Both are manual
per-phone steps — see [DEPLOYMENT.md](DEPLOYMENT.md) step 6.

**Force-stopped apps receive nothing.** If a user swipes the app away from recents on some
OEMs, or force-stops it from Settings, Android delivers no FCM messages at all until the app
is opened again. This is by OS design.

**Notification permission is refusable.** On Android 13+ the user can deny notifications.
The chat still works fully; there are simply no alerts.

**Doze mode** can delay notifications by minutes when the phone has been stationary and
unused, even with everything configured correctly. High-priority FCM mitigates but does not
eliminate this.

**Read receipts require connectivity.** Detailed in [TESTING.md](TESTING.md).

## Not verified in this build

The code has not been compiled against the Android SDK or run on a device in the environment
where it was written — there is no Android toolchain here. The notification rule is tested
and passing. Expect to spend a first build session resolving dependency versions in Android
Studio; the Compose BOM (2024.09.03) and Kotlin (2.0.20) versions are pinned and compatible,
but a newer Android Studio may prompt for upgrades.

The emoji quick-row is a convenience. Full emoji entry is the system keyboard, which is the
right answer — it gives skin tones, search and the user's own recents for free.
