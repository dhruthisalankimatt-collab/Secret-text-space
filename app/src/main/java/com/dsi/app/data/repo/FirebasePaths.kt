package com.dsi.app.data.repo

/**
 * Every path in one place. There is exactly one conversation, so its id is a constant —
 * this is what keeps the app from ever becoming a multi-chat product.
 */
object FirebasePaths {
    const val CHAT_ID = "main"

    fun messagesCollection() = "chats/$CHAT_ID/messages"
    fun userDoc(uid: String) = "users/$uid"

    fun presenceNode(role: String) = "presence/$role"
    fun typingNode(role: String) = "typing/$role"

    fun imagePath(role: String, clientId: String) = "chats/$CHAT_ID/images/$role/$clientId.jpg"
    fun audioPath(role: String, clientId: String) = "chats/$CHAT_ID/audio/$role/$clientId.m4a"
}
