const admin = require('firebase-admin');
const serviceAccount = require('./service-account.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();
const ROLES = ['queen', 'heera'];

function notificationBodyFor(message) {
  if (!message || message.type !== 'TEXT') return 'Notification';
  const text = message.text;
  if (typeof text !== 'string') return 'Notification';
  if (text === 'Imp' || text === 'imp') return 'Alter';
  if (
    text === 'Circular' ||
    text === 'circular' ||
    text === 'Waiting' ||
    text === 'waiting' ||
    text === 'Wating' ||
    text === 'wating'
  ) {
    return 'Circular';
  }
  return 'Notification';
}

async function tokensForRole(role) {
  const snapshot = await db.collection('users').where('role', '==', role).get();
  if (snapshot.empty) return [];
  const tokens = [];
  for (const doc of snapshot.docs) {
    const data = doc.data();
    if (data && typeof data.fcmToken === 'string' && data.fcmToken.trim().length > 0) {
      tokens.push(data.fcmToken.trim());
    }
  }
  return [...new Set(tokens)];
}

/**
 * Automatically purges chat history older than 3 days (both Firestore messages and Storage files)
 */
async function purgeExpiredMessages() {
  const threeDaysAgo = new Date(Date.now() - 3 * 24 * 60 * 60 * 1000);
  try {
    const expired = await db.collection('chats/main/messages')
      .where('createdAt', '<', admin.firestore.Timestamp.fromDate(threeDaysAgo))
      .get();

    if (expired.empty) return;
    console.log(`[Auto-Cleanup] Found ${expired.size} messages older than 3 days. Deleting to clear space...`);

    let storageBucket;
    try { storageBucket = admin.storage().bucket(); } catch (_) {}

    for (const doc of expired.docs) {
      const data = doc.data();
      if (data.mediaPath && storageBucket) {
        try {
          await storageBucket.file(data.mediaPath).delete();
        } catch (_) {}
      }
      await doc.ref.delete();
    }
    console.log(`[Auto-Cleanup] Successfully cleared ${expired.size} expired messages.`);
  } catch (err) {
    console.error('[Auto-Cleanup] Error during 3-day cleanup:', err.message);
  }
}

console.log('Push notification daemon & 3-day cleanup service is starting...');
console.log('Listening for new messages in chats/main/messages...');

// Run 3-day cleanup on start and every hour
purgeExpiredMessages();
setInterval(purgeExpiredMessages, 60 * 60 * 1000);

// Keep track of initialized state so we do not send duplicate pushes for old messages on startup
let isInitialLoad = true;

const unsubscribe = db.collection('chats/main/messages')
  .orderBy('createdAt', 'desc')
  .limit(20)
  .onSnapshot(async (snapshot) => {
    if (isInitialLoad) {
      isInitialLoad = false;
      console.log(`Initial snapshot loaded (${snapshot.size} recent messages). Now actively watching for new incoming messages...`);
      return;
    }

    for (const change of snapshot.docChanges()) {
      if (change.type === 'added') {
        const message = change.doc.data();
        const messageId = change.doc.id;
        const senderRole = message.senderRole;

        if (!ROLES.includes(senderRole)) continue;

        const recipientRole = senderRole === 'queen' ? 'heera' : 'queen';
        const body = notificationBodyFor(message);
        const isCircular = body === 'Circular';
        const tokens = await tokensForRole(recipientRole);

        if (tokens.length === 0) {
          console.log(`[Message ${messageId}] No push tokens found for recipient (${recipientRole}); skipping.`);
          continue;
        }

        console.log(`[Message ${messageId}] New message from ${senderRole}. Sending push to ${recipientRole} (Body: "${body}", ${tokens.length} token(s))...`);

        try {
          const response = await admin.messaging().sendEachForMulticast({
            tokens,
            data: {
              body,
              messageId: messageId,
              sound: isCircular ? 'shrine' : 'default'
            },
            android: {
              priority: 'high'
            }
          });

          console.log(`[Message ${messageId}] Push delivered: success=${response.successCount}, failure=${response.failureCount}`);

          // Mark delivered fallback
          await change.doc.ref.update({ deliveredAt: admin.firestore.FieldValue.serverTimestamp() });
        } catch (error) {
          console.error(`[Message ${messageId}] Push delivery error:`, error.message);
        }
      }
    }
  }, (err) => {
    console.error('Firestore listener error:', err);
  });
