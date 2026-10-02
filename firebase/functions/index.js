
const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { setGlobalOptions } = require("firebase-functions/v2");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();
setGlobalOptions({ region: "asia-south1", maxInstances: 3 });

const db = getFirestore();

const ROLES = ["queen", "heera"];

/**
 * The notification rule.
 *
 * Exactly "Imp" or exactly "imp" -> "Alter". Everything else -> "Notification".
 *
 * Case-sensitive and whole-string: "IMP", "imp!", "Imp please call" and " imp " all fall
 * through to "Notification". No trimming, no lowercasing, no partial matching — those
 * would each break one of the specified cases.
 *
 * This runs on the server so the classification cannot be altered by a modified client,
 * and so the push payload never has to carry the message text.
 */
function notificationBodyFor(message) {
  if (!message || message.type !== "TEXT") return "Notification";
  const text = message.text;
  if (typeof text !== "string") return "Notification";
  if (text === "Imp" || text === "imp") return "Alter";
  if (
    text === "Circular" ||
    text === "circular" ||
    text === "Waiting" ||
    text === "waiting" ||
    text === "Wating" ||
    text === "wating"
  ) {
    return "Circular";
  }
  return "Notification";
}

async function tokensForRole(role) {
  const snapshot = await db.collection("users").where("role", "==", role).get();
  if (snapshot.empty) return [];
  const tokens = [];
  for (const doc of snapshot.docs) {
    const data = doc.data();
    if (data && typeof data.fcmToken === "string" && data.fcmToken.trim().length > 0) {
      tokens.push(data.fcmToken.trim());
    }
  }
  return [...new Set(tokens)];
}

exports.onMessageCreated = onDocumentCreated(
  "chats/main/messages/{messageId}",
  async (event) => {
    const snapshot = event.data;
    if (!snapshot) return;

    const message = snapshot.data();
    const senderRole = message.senderRole;
    if (!ROLES.includes(senderRole)) return;

    const recipientRole = senderRole === "queen" ? "heera" : "queen";
    const body = notificationBodyFor(message);
    const isCircular = body === "Circular";
    const tokens = await tokensForRole(recipientRole);

    if (tokens.length === 0) {
      console.log(`No push token registered for recipient (${recipientRole}); skipping push.`);
      return;
    }

    try {
      const response = await getMessaging().sendEachForMulticast({
        tokens,
        data: {
          body,
          messageId: event.params.messageId,
          sound: isCircular ? "shrine" : "default"
        },
        android: {
          priority: "high"
        }
      });

      // Clear stale tokens
      if (response && response.responses) {
        for (let i = 0; i < response.responses.length; i++) {
          const res = response.responses[i];
          if (!res.success && res.error) {
            const code = res.error.code;
            if (
              code === "messaging/registration-token-not-registered" ||
              code === "messaging/invalid-argument"
            ) {
              const badToken = tokens[i];
              const staleDocs = await db.collection("users").where("fcmToken", "==", badToken).get();
              await Promise.all(staleDocs.docs.map((doc) => doc.ref.update({ fcmToken: null })));
            }
          }
        }
      }

      // Fallback delivery mark
      await snapshot.ref.update({ deliveredAt: FieldValue.serverTimestamp() });
    } catch (error) {
      console.error("Push delivery failed:", error.code || error.message || "unknown");
    }
  }
);

module.exports.notificationBodyFor = notificationBodyFor;
