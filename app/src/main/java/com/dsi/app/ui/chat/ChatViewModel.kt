package com.dsi.app.ui.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dsi.app.core.Identity
import com.dsi.app.data.model.Message
import com.dsi.app.data.model.MessageType
import com.dsi.app.data.repo.AuthRepository
import com.dsi.app.data.repo.ChatMessage
import com.dsi.app.data.repo.ChatRepository
import com.dsi.app.data.repo.MediaRepository
import com.dsi.app.data.repo.PresenceRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** A media send that failed before it reached Firestore, kept so the user can retry it. */
data class FailedSend(
    val clientId: String,
    val type: MessageType,
    val imageUri: Uri? = null,
    val audioFile: File? = null,
    val durationMs: Long = 0L,
    val replyTo: Message? = null,
    val reason: String
)

data class ChatUiState(
    val me: Identity,
    val peer: Identity,
    val messages: List<ChatMessage> = emptyList(),
    val peerOnline: Boolean = false,
    val peerLastSeen: Long = 0L,
    val peerTyping: Boolean = false,
    val draft: String = "",
    val replyTo: Message? = null,
    val uploading: Set<String> = emptySet(),
    val failed: List<FailedSend> = emptyList(),
    val recording: Boolean = false,
    val recordingMs: Long = 0L,
    val transientError: String? = null
)

class ChatViewModel(
    private val me: Identity
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState(me = me, peer = me.other))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var typingJob: Job? = null
    private var typingActive = false
    private var recordingTicker: Job? = null

    init {
        PresenceRepository.bind(me)
        observeConversation()
    }

    private fun observeConversation() {
        viewModelScope.launch {
            ChatRepository.observeMessages().collect { messages ->
                _state.update { it.copy(messages = messages) }
            }
        }
        viewModelScope.launch {
            combine(
                PresenceRepository.observePresence(me.other),
                PresenceRepository.observeTyping(me.other)
            ) { presence, typing -> presence to typing }
                .collect { (presence, typing) ->
                    _state.update {
                        it.copy(
                            peerOnline = presence.online,
                            peerLastSeen = presence.lastSeen,
                            // Never show "typing" for someone we believe is offline.
                            peerTyping = typing && presence.online
                        )
                    }
                }
        }
    }

    // ---------------------------------------------------------------- composing

    fun onDraftChange(value: String) {
        _state.update { it.copy(draft = value) }
        signalTyping(value.isNotBlank())
    }

    /**
     * Typing is a transient signal, not a stored record. It is set once when the user starts
     * and cleared automatically 3 seconds after the last keystroke, so a single boolean is
     * written rather than one event per character.
     */
    private fun signalTyping(typing: Boolean) {
        if (typing && !typingActive) {
            typingActive = true
            PresenceRepository.setTyping(true)
        }
        typingJob?.cancel()
        if (typing) {
            typingJob = viewModelScope.launch {
                delay(3_000)
                typingActive = false
                PresenceRepository.setTyping(false)
            }
        } else if (typingActive) {
            typingActive = false
            PresenceRepository.setTyping(false)
        }
    }

    fun stopTyping() {
        typingJob?.cancel()
        typingActive = false
        PresenceRepository.setTyping(false)
    }

    fun setReplyTo(message: Message?) = _state.update { it.copy(replyTo = message) }

    fun clearError() = _state.update { it.copy(transientError = null) }

    // ---------------------------------------------------------------- sending

    fun sendText() {
        val current = _state.value
        val body = current.draft.trim()
        if (body.isEmpty()) return
        val uid = AuthRepository.currentUid ?: run {
            _state.update { it.copy(transientError = "Session expired. Reopen the app to sign in.") }
            return
        }
        val replyTo = current.replyTo
        _state.update { it.copy(draft = "", replyTo = null) }
        stopTyping()

        viewModelScope.launch {
            // While offline, Firestore holds this write in its local queue and the message
            // shows as pending until the device reconnects. It is not lost.
            runCatching { ChatRepository.sendText(me, uid, body, replyTo = replyTo) }
                .onFailure {
                    _state.update { s -> s.copy(transientError = "That message could not be sent.") }
                }
        }
    }

    fun sendCircular() {
        val uid = AuthRepository.currentUid ?: run {
            _state.update { it.copy(transientError = "Session expired. Reopen the app to sign in.") }
            return
        }
        viewModelScope.launch {
            runCatching { ChatRepository.sendText(me, uid, "Circular", replyTo = null) }
                .onFailure {
                    _state.update { s -> s.copy(transientError = "Could not send Circular alert.") }
                }
        }
    }

    fun sendWaiting() = sendCircular()

    fun sendImage(context: Context, uri: Uri) {
        val uid = AuthRepository.currentUid ?: return
        val clientId = ChatRepository.newClientId()
        val replyTo = _state.value.replyTo
        _state.update { it.copy(uploading = it.uploading + clientId, replyTo = null) }

        viewModelScope.launch {
            runCatching {
                val uploaded = MediaRepository.uploadImage(context, uri, me.key, clientId)
                ChatRepository.sendImage(
                    me, uid, uploaded.path, uploaded.width, uploaded.height, clientId, replyTo
                )
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        failed = it.failed + FailedSend(
                            clientId = clientId,
                            type = MessageType.IMAGE,
                            imageUri = uri,
                            replyTo = replyTo,
                            reason = error.message ?: "The photo could not be sent."
                        )
                    )
                }
            }
            _state.update { it.copy(uploading = it.uploading - clientId) }
        }
    }

    fun sendVoiceNote(file: File, durationMs: Long) {
        val uid = AuthRepository.currentUid ?: return
        val clientId = ChatRepository.newClientId()
        val replyTo = _state.value.replyTo
        _state.update { it.copy(uploading = it.uploading + clientId, replyTo = null) }

        viewModelScope.launch {
            runCatching {
                val path = MediaRepository.uploadAudio(file, me.key, clientId)
                ChatRepository.sendAudio(me, uid, path, durationMs, clientId, replyTo)
                file.delete()
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        failed = it.failed + FailedSend(
                            clientId = clientId,
                            type = MessageType.AUDIO,
                            audioFile = file,
                            durationMs = durationMs,
                            replyTo = replyTo,
                            reason = error.message ?: "The voice note could not be sent."
                        )
                    )
                }
            }
            _state.update { it.copy(uploading = it.uploading - clientId) }
        }
    }

    fun retry(context: Context, failed: FailedSend) {
        _state.update { it.copy(failed = it.failed - failed) }
        when (failed.type) {
            MessageType.IMAGE -> failed.imageUri?.let { sendImage(context, it) }
            MessageType.AUDIO -> failed.audioFile?.let { sendVoiceNote(it, failed.durationMs) }
            MessageType.TEXT -> Unit
        }
    }

    fun discard(failed: FailedSend) {
        failed.audioFile?.delete()
        _state.update { it.copy(failed = it.failed - failed) }
    }

    // ---------------------------------------------------------------- recording state

    fun onRecordingStarted() {
        _state.update { it.copy(recording = true, recordingMs = 0L) }
        recordingTicker = viewModelScope.launch {
            while (true) {
                delay(100)
                _state.update { it.copy(recordingMs = it.recordingMs + 100) }
            }
        }
    }

    fun onRecordingStopped() {
        recordingTicker?.cancel()
        recordingTicker = null
        _state.update { it.copy(recording = false, recordingMs = 0L) }
    }

    // ---------------------------------------------------------------- read receipts

    /**
     * Called when the chat is actually on screen and the app is in the foreground. Messages
     * are only marked seen if they came from the other person and have not been marked yet,
     * so a read is reported once and never for our own messages.
     */
    fun markVisibleAsSeen() {
        val unseen = _state.value.messages
            .map { it.message }
            .filter { it.senderRole == me.other.key && it.seenAt == null && it.id.isNotEmpty() }
            .map { it.id }
        if (unseen.isEmpty()) return
        viewModelScope.launch { ChatRepository.markSeen(unseen) }
    }

    override fun onCleared() {
        stopTyping()
        recordingTicker?.cancel()
        super.onCleared()
    }
}
