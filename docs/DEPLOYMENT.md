# Deployment

Roughly 45 minutes end to end. Do these in order.

## 1. Firebase project

1. Create a project at console.firebase.google.com. Name it anything; the name is not shown
   in the app.
2. Upgrade to the **Blaze** plan. Cloud Functions require it. Two users will not leave the
   free allowance — see Costs below.
3. Enable, in this order:
   - **Authentication** → Sign-in method → Email/Password
   - **Firestore Database** → production mode → region `asia-south1` (Mumbai)
   - **Realtime Database** → locked mode → region `asia-southeast1`
   - **Storage** → production mode
   - **App Check** → register the app with Play Integrity

## 2. Register the Android app

1. Project settings → Add app → Android, package name `com.dsi.app`.
2. Add the debug SHA-1 (`./gradlew signingReport`) and, later, the release SHA-1.
3. Download `google-services.json` into `app/`. It is gitignored deliberately.

## 3. Deploy rules and the function

```bash
npm install -g firebase-tools
firebase login
cd firebase
firebase use --add            # pick your project

cd functions && npm install && cd ..
firebase deploy --only firestore:rules,storage:rules,database,functions
```

The function deploys to `asia-south1`. If you chose a different Firestore region, change
`setGlobalOptions` in `functions/index.js` to match before deploying.

## 4. Provision the two accounts

Project settings → Service accounts → Generate new private key. Save it as
`scripts/service-account.json` (gitignored).

```bash
cd scripts && npm install

GOOGLE_APPLICATION_CREDENTIALS=./service-account.json \
QUEEN_PASSPHRASE='choose-a-long-one' \
HEERA_PASSPHRASE='choose-a-different-long-one' \
node provision_users.js
```

This creates both accounts and sets the `role` claim on each. Nothing works before this step:
without a claim, every security rule denies and the app refuses the sign-in with "This
account has not been set up yet."

Give each passphrase to its owner in person. Not over this chat, not over SMS.

## 5. Build and install

```bash
# Debug, for testing on both phones:
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk

# Release:
keytool -genkey -v -keystore dsi.jks -keyalg RSA -keysize 2048 -validity 10000 -alias dsi
./gradlew assembleRelease
```

For release, add the keystore to `app/build.gradle.kts` under `signingConfigs`, add the
release SHA-1 to Firebase, and register the release build with App Check. A release build
whose SHA-1 is not registered will fail attestation and every backend call will be denied.

Installing outside the Play Store means enabling "Install unknown apps" for the file manager
on each phone.

## 6. Per-phone setup

On each phone, after first launch:

1. Allow notifications when prompted.
2. Settings → Apps → DSI → Battery → **Unrestricted**.

Step 2 is not optional on Xiaomi, Oppo, Vivo, Realme and OnePlus devices. Their battery
managers kill background processes aggressively and will silently drop pushes otherwise.
On Xiaomi also enable Autostart for DSI. This is an OS behaviour; no amount of app code
works around it.

## Costs

For two people at normal volume, everything sits inside the Firebase free allowance:
50k Firestore reads and 20k writes per day, 5 GB storage, 2M function invocations per month.
Expect ₹0. Blaze requires a card on file but bills only past those limits. Set a budget alert
of ₹100 in Google Cloud Billing for peace of mind.

## Keys and services required

| Item | Where it lives | In the app? |
|---|---|---|
| `google-services.json` | `app/` | Yes — it is not a secret; rules do the enforcing |
| Service account key | `scripts/`, your machine only | **Never** |
| Passphrases | The two people | Never stored, only entered |
| FCM | Automatic with the Firebase project | — |
