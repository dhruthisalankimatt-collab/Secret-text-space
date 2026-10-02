package com.dsi.app.ui.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dsi.app.audio.VoicePlayer
import com.dsi.app.audio.VoiceRecorder
import com.dsi.app.data.model.MessageType
import com.dsi.app.data.repo.MediaRepository
import com.dsi.app.ui.theme.DsiColors
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val recorder = remember { VoiceRecorder(context) }
    val player = remember { VoicePlayer() }
    var playingId by remember { mutableStateOf<String?>(null) }

    // Signed URLs are resolved once per media message and cached for the session.
    val mediaUrls = remember { mutableStateMapOf<String, String>() }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var permissionNotice by remember { mutableStateOf<String?>(null) }

    // ---- launchers -----------------------------------------------------------

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        com.dsi.app.core.LockManager.isPickerActive = false
        uri?.let { viewModel.sendImage(context, it) }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        com.dsi.app.core.LockManager.isPickerActive = false
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (success && uri != null) viewModel.sendImage(context, uri)
    }

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            com.dsi.app.core.LockManager.isPickerActive = true
            pendingCameraUri = newCaptureUri(context).also { cameraLauncher.launch(it) }
        } else {
            com.dsi.app.core.LockManager.isPickerActive = false
            permissionNotice = "Camera access is off. Turn it on in Settings to send photos."
        }
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (recorder.start()) viewModel.onRecordingStarted()
        } else {
            permissionNotice = "Microphone access is off. Turn it on in Settings to send voice notes."
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Declining only means no alerts; the chat still works. */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // ---- lifecycle -----------------------------------------------------------

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    com.dsi.app.core.LockManager.isChatOpen = true
                    viewModel.markVisibleAsSeen()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    com.dsi.app.core.LockManager.isChatOpen = false
                    viewModel.stopTyping()
                    player.stop()
                    playingId = null
                    if (recorder.isRecording) {
                        // Leaving mid-recording discards the take rather than sending a
                        // half-finished note.
                        recorder.cancel()
                        viewModel.onRecordingStopped()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            com.dsi.app.core.LockManager.isChatOpen = false
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.stop()
            recorder.cancel()
        }
    }

    // New messages arriving while the screen is open are marked seen immediately.
    LaunchedEffect(state.messages) {
        viewModel.markVisibleAsSeen()
        if (state.messages.isNotEmpty()) listState.scrollToItem(0)
    }

    // Resolve download URLs for any media that does not have one yet.
    LaunchedEffect(state.messages) {
        state.messages.forEach { item ->
            val path = item.message.mediaPath ?: return@forEach
            if (mediaUrls.containsKey(path)) return@forEach
            scope.launch {
                runCatching { MediaRepository.downloadUrl(path) }
                    .onSuccess { mediaUrls[path] = it }
            }
        }
    }

    // ---- layout --------------------------------------------------------------

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        ChatHeader(
            peerName = state.peer.displayName,
            online = state.peerOnline,
            typing = state.peerTyping,
            lastSeen = state.peerLastSeen,
            onWaitingClick = viewModel::sendWaiting
        )

        state.failed.forEach { failure ->
            FailureBanner(
                text = failure.reason,
                onRetry = { viewModel.retry(context, failure) },
                onDiscard = { viewModel.discard(failure) }
            )
        }

        permissionNotice?.let { notice ->
            FailureBanner(text = notice, onRetry = null, onDiscard = { permissionNotice = null })
        }

        state.transientError?.let { error ->
            FailureBanner(text = error, onRetry = null, onDiscard = { viewModel.clearError() })
        }

        // reverseLayout draws index 0 at the bottom, so the list is newest-first.
        val ordered = state.messages.reversed()

        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Top,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
            ) {
                itemsIndexed(
                    items = ordered,
                    key = { _, item -> item.message.clientId.ifEmpty { item.message.id } }
                ) { index, item ->
                    val message = item.message
                    val previous = ordered.getOrNull(index + 1)?.message
                    val showDayHeading = message.createdAt != null &&
                        (previous?.createdAt == null ||
                            !sameDay(previous.createdAt.toDate(), message.createdAt.toDate()))

                    MessageBubble(
                        message = message,
                        isMine = message.senderRole == state.me.key,
                        pending = item.pending,
                        mediaUrl = message.mediaPath?.let { mediaUrls[it] },
                        isPlaying = playingId == message.id,
                        onPlayToggle = {
                            val url = message.mediaPath?.let { mediaUrls[it] } ?: return@MessageBubble
                            player.toggle(message.id, url) { playingId = it }
                        },
                        onReply = { viewModel.setReplyTo(message) }
                    )

                    if (showDayHeading) {
                        DayHeading(text = formatDayHeading(message.createdAt!!.toDate()))
                    }
                }
            }

            if (state.uploading.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (state.uploading.size == 1) "Sending attachment"
                        else "Sending ${state.uploading.size} attachments",
                        style = MaterialTheme.typography.labelSmall,
                        color = DsiColors.Muted
                    )
                }
            }
        }

        Composer(
            draft = state.draft,
            replyTo = state.replyTo,
            recording = state.recording,
            recordingMs = state.recordingMs,
            onDraftChange = viewModel::onDraftChange,
            onSend = viewModel::sendText,
            onPickPhoto = {
                com.dsi.app.core.LockManager.isPickerActive = true
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onTakePhoto = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    com.dsi.app.core.LockManager.isPickerActive = true
                    pendingCameraUri = newCaptureUri(context).also { cameraLauncher.launch(it) }
                } else {
                    cameraPermission.launch(Manifest.permission.CAMERA)
                }
            },
            onStartRecording = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    if (recorder.start()) viewModel.onRecordingStarted()
                    else permissionNotice = "Recording could not start. Try again."
                } else {
                    micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            onStopRecording = {
                val result = recorder.stop()
                viewModel.onRecordingStopped()
                if (result == null) {
                    permissionNotice = "That recording was too short to send."
                } else {
                    viewModel.sendVoiceNote(result.first, result.second)
                }
            },
            onCancelRecording = {
                recorder.cancel()
                viewModel.onRecordingStopped()
            },
            onClearReply = { viewModel.setReplyTo(null) },
            modifier = Modifier.navigationBarsPadding()
        )
    }
}

@Composable
private fun ChatHeader(
    peerName: String,
    online: Boolean,
    typing: Boolean,
    lastSeen: Long,
    onWaitingClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = peerName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = when {
                        typing -> "typing…"
                        online -> "Online"
                        else -> formatLastSeen(lastSeen)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (online || typing) DsiColors.Online else DsiColors.Muted
                )
            }
            if (online) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(DsiColors.Online)
                )
                Spacer(Modifier.width(12.dp))
            }
            IconButton(
                onClick = onWaitingClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = "Circular Alert",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun DayHeading(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = DsiColors.Muted,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun FailureBanner(text: String, onRetry: (() -> Unit)?, onDiscard: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.08f))
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f)
        )
        if (onRetry != null) {
            TextButton(onClick = onRetry) { Text("Retry") }
        }
        TextButton(onClick = onDiscard) { Text("Dismiss") }
    }
}

/** Creates a private cache file and a FileProvider URI for the camera to write into. */
private fun newCaptureUri(context: Context): Uri {
    val dir = File(context.cacheDir, "captures").apply { mkdirs() }
    val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

private fun sameDay(a: java.util.Date, b: java.util.Date): Boolean {
    val ca = java.util.Calendar.getInstance().apply { time = a }
    val cb = java.util.Calendar.getInstance().apply { time = b }
    return ca.get(java.util.Calendar.YEAR) == cb.get(java.util.Calendar.YEAR) &&
        ca.get(java.util.Calendar.DAY_OF_YEAR) == cb.get(java.util.Calendar.DAY_OF_YEAR)
}
