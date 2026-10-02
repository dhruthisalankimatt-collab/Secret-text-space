# Testing

## Automated

```bash
cd firebase/functions && node test/notificationRule.test.js
```

Covers every row of the specification's table plus the near-misses: `iMp`, `impp`, leading
and trailing whitespace, empty string, and media messages. All pass.

Rules can be tested against the emulator:

```bash
firebase emulators:start --only firestore,database,storage
```

## Manual checklist

Run this on both phones before handing them over.

### Tap sequence

- [ ] Bottom-right → top-left → top-right opens the next screen
- [ ] Bottom-left at any position: nothing happens
- [ ] Wrong sequence produces **no** vibration, sound, message or visual change
- [ ] After a wrong tap, the correct sequence still works on the next attempt
- [ ] A wrong first tap that happens to be bottom-right counts as a fresh start
- [ ] All four quadrants are exactly a quarter of the screen in portrait and landscape
- [ ] Sequence works on a tall phone and a tablet-sized screen
- [ ] Backgrounding and reopening returns to the white screen, not the chat
- [ ] The recents thumbnail shows white, not the conversation

### Identity

- [ ] Queen's passphrase under "Queen" → chat
- [ ] Queen's passphrase under "Mrs.Heera" → refused, signed out, no access
- [ ] Wrong passphrase → clear error, field cleared
- [ ] Before provisioning → "This account has not been set up yet"
- [ ] After first sign-in, reopening goes straight from the tap sequence to the chat
- [ ] Airplane mode with a valid cached session → still reaches the chat

### Messaging

- [ ] Text sends and arrives on the other phone
- [ ] Gallery photo: picker needs no storage permission, uploads, displays both sides
- [ ] Camera photo: permission prompt, capture, send, display
- [ ] Denying camera permission → banner, no crash
- [ ] Voice note records, shows a live timer, uploads, plays on both phones
- [ ] A voice note under 0.7s is refused rather than sent broken
- [ ] Leaving the screen mid-recording discards the take
- [ ] Playing a second voice note stops the first
- [ ] Emoji from the quick row and from the system keyboard both render
- [ ] A pasted URL becomes tappable and opens in the browser
- [ ] Tapping a bubble sets it as the reply target; the quote shows in the sent message
- [ ] Timestamps are correct; day headings show Today / Yesterday / date
- [ ] Airplane mode: message shows a clock icon, sends on reconnect, appears once only
- [ ] Sending the same message twice does not produce duplicates

### Receipts, presence, typing

- [ ] One tick on send, double tick on delivery, green double tick when opened
- [ ] Nothing is marked seen while the app is backgrounded
- [ ] Opening the app marks everything unread as seen
- [ ] "Online" appears within a second or two of the other app opening
- [ ] Force-closing the other app flips it to "Last seen …" — this is the `onDisconnect` path
- [ ] Airplane mode on the other phone flips it offline within ~60s
- [ ] Typing shows within a second and clears 3s after the last keystroke
- [ ] Typing clears on send, on leaving the screen and on disconnect
- [ ] Nobody ever shows as typing while offline

### Notifications

Test each with the app **foreground**, **background** and **force-closed**:

- [ ] `Imp` → notification reads **Alter**
- [ ] `imp` → notification reads **Alter**
- [ ] `IMP`, `imp!`, `Imp please call`, `Important`, `Hello` → **Notification**
- [ ] A photo or voice note → **Notification**
- [ ] The notification never contains the message text
- [ ] The sender never gets their own notification
- [ ] Denying notification permission leaves the chat fully working

### Security

- [ ] A third Firebase account with no role claim can read nothing
- [ ] A signed-out client reading `chats/main/messages` is denied
- [ ] A Storage object URL pasted into a browser without auth is denied
- [ ] Attempting to mark your own message seen is denied
- [ ] Attempting to edit a sent message's text is denied
- [ ] `adb logcat` during a release build shows no message content

## Known behaviours worth knowing before you are surprised by them

**Read receipts offline.** Opening the app without internet does not mark anything seen —
`seenAt` must be the server timestamp, so the write waits in the queue and lands on
reconnect. The sender's ticks turn green late rather than never. This is deliberate: it
means a green tick always corresponds to a real read event the server witnessed.

**Delivered vs seen.** `deliveredAt` is written when the push payload reaches the device, or
by the function as a fallback when the app is closed. Treat it as "it arrived at the phone",
not "the phone showed it".

**Whitespace and the Imp rule.** The composer trims leading and trailing whitespace before
sending, as every messaging app does. So typing `" imp "` sends `imp` and produces **Alter**.
The server rule itself does not trim — it matches the stored text exactly. If you want the
untrimmed string preserved so that `" imp "` yields **Notification**, remove the `.trim()` in
`ChatRepository.sendText`. Flagging it because it is the one place the specification's intent
is genuinely ambiguous.

**Notification stacking.** Each push replaces the previous one rather than stacking, so the
tray never shows a count that hints at how much was said.
