/**
 * One-time setup: creates the two accounts and stamps each with its role claim.
 *
 * Run this once, from a trusted machine, with a service-account key that is never
 * committed and never shipped inside the app.
 *
 *   npm install
 *   GOOGLE_APPLICATION_CREDENTIALS=./service-account.json \
 *   QUEEN_PASSPHRASE='...' HEERA_PASSPHRASE='...' \
 *   node provision_users.js
 *
 * The role claim is the whole authorisation model: security rules trust it, and a client
 * cannot set or edit it. Running this again with new passphrases is also how you rotate
 * credentials if a phone is lost.
 */

const { initializeApp, applicationDefault } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");

initializeApp({ credential: applicationDefault() });

const USERS = [
  { role: "queen", email: "queen@dsi.invalid", envVar: "QUEEN_PASSPHRASE" },
  { role: "heera", email: "heera@dsi.invalid", envVar: "HEERA_PASSPHRASE" }
];

function checkPassphrase(value, envVar) {
  if (!value) throw new Error(`${envVar} is not set.`);
  if (value.length < 12) {
    throw new Error(`${envVar} is too short. Use at least 12 characters.`);
  }
}

async function provision({ role, email, envVar }) {
  const passphrase = process.env[envVar];
  checkPassphrase(passphrase, envVar);

  let user;
  try {
    user = await getAuth().getUserByEmail(email);
    await getAuth().updateUser(user.uid, { password: passphrase });
    console.log(`Updated existing account for ${role}.`);
  } catch (error) {
    if (error.code !== "auth/user-not-found") throw error;
    user = await getAuth().createUser({ email, password: passphrase, emailVerified: true });
    console.log(`Created account for ${role}.`);
  }

  await getAuth().setCustomUserClaims(user.uid, { role });
  // Invalidates existing tokens so the new claim takes effect on next sign-in.
  await getAuth().revokeRefreshTokens(user.uid);
  console.log(`  uid: ${user.uid}  role claim: ${role}`);
}

(async () => {
  try {
    for (const user of USERS) await provision(user);
    console.log("\nDone. Both identities are provisioned.");
    console.log("Deliver each passphrase to its owner out of band — not over this chat.");
  } catch (error) {
    console.error("Provisioning failed:", error.message);
    process.exit(1);
  }
})();
