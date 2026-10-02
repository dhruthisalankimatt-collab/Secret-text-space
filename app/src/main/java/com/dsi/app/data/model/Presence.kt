package com.dsi.app.data.model

data class Presence(
    val online: Boolean = false,
    val lastSeen: Long = 0L
)

data class ChatPeerState(
    val presence: Presence = Presence(),
    val typing: Boolean = false
)
