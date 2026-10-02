# Data model and access control

## Firestore

### `users/{uid}`

| Field | Type | Notes |
|---|---|---|
| `role` | string | `queen` or `heera`. Must match the auth claim. |
| `fcmToken` | string \| null | Push token for this device. Cleared on sign-out. |
| `tokenUpdatedAt` | timestamp | Server-set. |

A user can read and write only their own document, and only these three fields.

### `chats/main/messages/{messageId}`

The document id is the client-generated `clientId`. That is what makes retries idempotent:
a resend overwrites its own document rather than creating a second copy.

| Field | Type | Notes |
|---|---|---|
| `senderUid` | string | Must equal `request.auth.uid` on create. |
| `senderRole` | string | Must equal the sender's role claim. |
| `type` | string | `TEXT`, `IMAGE` or `AUDIO`. |
| `text` | string | Body for `TEXT`. 1–5000 characters. |
| `mediaPath` | string | Storage object path. Must be inside the sender's own folder. |
| `mediaWidth`, `mediaHeight` | number | Images, so the bubble reserves the right space before the photo loads. |
| `durationMs` | number | Voice note length. |
| `replyToId`, `replyToRole`, `replyToPreview` | string | Denormalised reply context. |
| `createdAt` | timestamp | Must be `request.time`. Clients cannot backdate. |
| `deliveredAt` | timestamp \| null | Written by the recipient or the function. |
| `seenAt` | timestamp \| null | Written by the recipient only, only as `request.time`. |
| `clientId` | string | Must equal the document id. |

There is exactly one chat, and its id is the literal `main`. The rules hard-code it, so the
collection cannot grow into a multi-conversation structure.

**Message content is immutable.** The only permitted update is setting `seenAt` and
`deliveredAt`, and only by the person who did not send the message. A sender may delete
their own message; nobody can edit one.

## Realtime Database

```
presence/queen  { online: bool, lastSeen: number }
presence/heera  { online: bool, lastSeen: number }
typing/queen    bool
typing/heera    bool
```

Each user writes only their own node; both can read both. Typing is a single boolean that is
overwritten, not a log — no keystroke is ever stored.

RTDB is used here instead of Firestore for one reason: `onDisconnect()`. The RTDB server
performs the "set me offline" write itself when the connection dies, so a killed app, a flat
battery or a tunnel cannot leave someone showing as online. Firestore has no equivalent, and
a client-side-only presence system would get this wrong.

## Cloud Storage

```
chats/main/images/{role}/{clientId}.jpg
chats/main/audio/{role}/{clientId}.m4a
```

Both participants can read; each can write only into their own `{role}` folder. Images are
capped at 8 MB and must have an `image/*` content type; audio at 12 MB. Nothing is public —
the app resolves a signed download URL per object at display time.

Images are re-encoded on device before upload: downscaled to 1600px on the long edge, JPEG
quality 82, EXIF rotation applied and all other metadata dropped, which removes GPS
coordinates and device identifiers from every photo sent.
