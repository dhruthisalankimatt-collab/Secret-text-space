package com.dsi.app.data.repo

import com.dsi.app.core.Identity
import com.dsi.app.data.model.Message
import com.dsi.app.data.model.MessageType
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID

/** A message plus the local-only information the UI needs but the server does not store. */
data class ChatMessage(
    val message: Message,
    /** True while the write is still only in the local cache. */
    val pending: Boolean
)

object ChatRepository {

    private const val PAGE_SIZE = 200L
    const val RETENTION_DAYS = 3L
    val RETENTION_MILLIS = RETENTION_DAYS * 24 * 60 * 60 * 1000

    private val db get() = Firebase.firestore

    fun newClientId(): String = UUID.randomUUID().toString()

    /**
     * Live conversation feed, newest last. Automatically filters out messages older
     * than 3 days and triggers a background purge of expired messages and media.
     */
    fun observeMessages(): Flow<List<ChatMessage>> = callbackFlow {
        // Asynchronously purge expired messages older than 3 days from database and storage
        CoroutineScope(Dispatchers.IO).launch {
            purgeExpiredMessages()
        }

        val registration = db.collection(FirebasePaths.messagesCollection())
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(PAGE_SIZE)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val cutoffTime = System.currentTimeMillis() - RETENTION_MILLIS
                val items = snapshot.documents.mapNotNull { doc ->
                    val msg = runCatching { doc.toObject<Message>() }.getOrNull() ?: return@mapNotNull null
                    // Discard messages older than 3 days from display
                    val msgTime = msg.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                    if (msgTime < cutoffTime) return@mapNotNull null

                    // Do not display "Circular" / "Waiting" alert signals in the chat list
                    val isAlertSignal = msg.text.equals("Circular", ignoreCase = true) ||
                        msg.text.equals("Waiting", ignoreCase = true) ||
                        msg.text.equals("Wating", ignoreCase = true)
                    if (isAlertSignal) return@mapNotNull null

                    ChatMessage(message = msg, pending = doc.metadata.hasPendingWrites())
                }.reversed()
                trySend(items)
            }
        awaitClose { registration.remove() }
    }

    /**
     * Purges messages older than 3 days from Firestore and deletes their associated media files
     * from Firebase Storage to automatically reclaim space.
     */
    suspend fun purgeExpiredMessages() {
        runCatching {
            val cutoff = com.google.firebase.Timestamp(
                java.util.Date(System.currentTimeMillis() - RETENTION_MILLIS)
            )
            val expired = db.collection(FirebasePaths.messagesCollection())
                .whereLessThan("createdAt", cutoff)
                .get()
                .await()

            for (doc in expired.documents) {
                val mediaPath = doc.getString("mediaPath")
                if (!mediaPath.isNullOrBlank()) {
                    MediaRepository.deleteMedia(mediaPath)
                }
                doc.reference.delete().await()
            }
        }
    }

    suspend fun sendText(
        sender: Identity,
        uid: String,
        text: String,
        clientId: String = newClientId(),
        replyTo: Message? = null
    ) {
        val trimmed = text.trim()
        require(trimmed.isNotEmpty()) { "Empty message" }
        require(trimmed.length <= 5000) { "That message is too long to send." }
        write(
            clientId,
            baseFields(sender, uid, MessageType.TEXT, clientId, replyTo) + mapOf("text" to trimmed)
        )
        CoroutineScope(Dispatchers.IO).launch {
            com.dsi.app.push.FcmSender.sendPushNotification(sender.key, trimmed, clientId)
        }
    }

    suspend fun sendImage(
        sender: Identity,
        uid: String,
        storagePath: String,
        width: Int,
        height: Int,
        clientId: String,
        replyTo: Message? = null
    ) {
        write(
            clientId,
            baseFields(sender, uid, MessageType.IMAGE, clientId, replyTo) + mapOf(
                "mediaPath" to storagePath,
                "mediaWidth" to width,
                "mediaHeight" to height
            )
        )
        CoroutineScope(Dispatchers.IO).launch {
            com.dsi.app.push.FcmSender.sendPushNotification(sender.key, "Photo", clientId)
        }
    }

    suspend fun sendAudio(
        sender: Identity,
        uid: String,
        storagePath: String,
        durationMs: Long,
        clientId: String,
        replyTo: Message? = null
    ) {
        write(
            clientId,
            baseFields(sender, uid, MessageType.AUDIO, clientId, replyTo) + mapOf(
                "mediaPath" to storagePath,
                "durationMs" to durationMs
            )
        )
        CoroutineScope(Dispatchers.IO).launch {
            com.dsi.app.push.FcmSender.sendPushNotification(sender.key, "Voice note", clientId)
        }
    }

    private fun baseFields(
        sender: Identity,
        uid: String,
        type: MessageType,
        clientId: String,
        replyTo: Message?
    ): Map<String, Any?> = mapOf(
        "senderUid" to uid,
        "senderRole" to sender.key,
        "type" to type.name,
        "clientId" to clientId,
        "createdAt" to FieldValue.serverTimestamp(),
        "replyToId" to replyTo?.id,
        "replyToRole" to replyTo?.senderRole,
        "replyToPreview" to replyTo?.let { previewOf(it) }
    )

    private fun previewOf(message: Message): String = when (message.messageType) {
        MessageType.TEXT -> message.text.take(120)
        MessageType.IMAGE -> "Photo"
        MessageType.AUDIO -> "Voice note"
    }

    private suspend fun write(clientId: String, fields: Map<String, Any?>) {
        db.collection(FirebasePaths.messagesCollection()).document(clientId).set(fields).await()
    }

    /**
     * Marks incoming messages as seen. Security rules allow the recipient to set `seenAt`
     * (and nothing else) and only to the server timestamp, so a client cannot backdate a
     * read or mark its own messages seen.
     */
    suspend fun markSeen(messageIds: List<String>) {
        if (messageIds.isEmpty()) return
        messageIds.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { id ->
                batch.update(
                    db.collection(FirebasePaths.messagesCollection()).document(id),
                    "seenAt", FieldValue.serverTimestamp()
                )
            }
            runCatching { batch.commit().await() }
        }
    }

    /** Called by the push service when a message actually lands on this device. */
    suspend fun markDelivered(messageId: String) {
        runCatching {
            db.collection(FirebasePaths.messagesCollection()).document(messageId)
                .update("deliveredAt", FieldValue.serverTimestamp()).await()
        }
    }

    suspend fun deleteMessage(messageId: String) {
        db.collection(FirebasePaths.messagesCollection()).document(messageId).delete().await()
    }
}
