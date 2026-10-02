package com.dsi.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class MessageType { TEXT, IMAGE, AUDIO;
    companion object { fun parse(v: String?) = entries.firstOrNull { it.name == v } ?: TEXT }
}

/** Local-only send state for a message that has not yet been acknowledged by the server. */
enum class SendState { PENDING, SENT, FAILED }

@IgnoreExtraProperties
data class Message(
    @DocumentId val id: String = "",
    /** Firebase Auth uid of the sender. */
    val senderUid: String = "",
    /** "queen" or "heera" — denormalised so the UI can style bubbles without a lookup. */
    val senderRole: String = "",
    val type: String = MessageType.TEXT.name,

    /** Body for TEXT messages. Empty for media. */
    val text: String = "",

    /** Storage object path (not a download URL) for IMAGE and AUDIO messages. */
    val mediaPath: String? = null,
    val mediaWidth: Int? = null,
    val mediaHeight: Int? = null,
    /** Voice note length in milliseconds. */
    val durationMs: Long? = null,

    val replyToId: String? = null,
    /** Short preview of the replied-to message, so the bubble renders without a second read. */
    val replyToPreview: String? = null,
    val replyToRole: String? = null,

    @ServerTimestamp val createdAt: Timestamp? = null,
    val deliveredAt: Timestamp? = null,
    val seenAt: Timestamp? = null,

    /** Client-generated id used to deduplicate retries. Doubles as the document id. */
    val clientId: String = ""
) {
    val messageType: MessageType get() = MessageType.parse(type)
    val isSeen: Boolean get() = seenAt != null
    val isDelivered: Boolean get() = deliveredAt != null
}
