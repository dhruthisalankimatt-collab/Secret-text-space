package com.dsi.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dsi.app.data.model.Message
import com.dsi.app.ui.theme.DsiColors

/** A short row of frequently used emoji. The system keyboard covers everything else. */
private val QUICK_EMOJI = listOf("❤️", "😊", "😂", "🙏", "👍", "😘", "🥺", "🔥")

@Composable
fun Composer(
    draft: String,
    replyTo: Message?,
    recording: Boolean,
    recordingMs: Long,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onPickPhoto: () -> Unit,
    onTakePhoto: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onClearReply: () -> Unit,
    modifier: Modifier = Modifier
) {
    var attachmentsOpen by remember { mutableStateOf(false) }
    var emojiOpen by remember { mutableStateOf(false) }

    Column(modifier = modifier.background(MaterialTheme.colorScheme.background)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        if (replyTo != null) {
            ReplyBanner(replyTo = replyTo, onClear = onClearReply)
        }

        if (recording) {
            RecordingBar(
                elapsedMs = recordingMs,
                onCancel = onCancelRecording,
                onSend = onStopRecording
            )
            return@Column
        }

        if (attachmentsOpen) {
            AttachmentRow(
                onPickPhoto = { attachmentsOpen = false; onPickPhoto() },
                onTakePhoto = { attachmentsOpen = false; onTakePhoto() }
            )
        }

        if (emojiOpen) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                QUICK_EMOJI.forEach { emoji ->
                    Text(
                        text = emoji,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onDraftChange(draft + emoji) }
                            .padding(6.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(onClick = { attachmentsOpen = !attachmentsOpen; emojiOpen = false }) {
                Icon(
                    Icons.Filled.AttachFile,
                    contentDescription = "Attach",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            TextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp, max = 140.dp),
                placeholder = { Text("Message", color = DsiColors.Muted) },
                maxLines = 5,
                shape = RoundedCornerShape(22.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                trailingIcon = {
                    Text(
                        text = "☺",
                        style = MaterialTheme.typography.titleMedium,
                        color = DsiColors.Muted,
                        modifier = Modifier
                            .clickable { emojiOpen = !emojiOpen; attachmentsOpen = false }
                            .padding(8.dp)
                    )
                }
            )

            Spacer(Modifier.width(4.dp))

            if (draft.isBlank()) {
                IconButton(onClick = onStartRecording) {
                    Icon(
                        Icons.Filled.Mic,
                        contentDescription = "Record a voice note",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(23.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onSend),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Send,
                        contentDescription = "Send",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ReplyBanner(replyTo: Message, onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(34.dp)
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Replying to",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = when (replyTo.messageType) {
                    com.dsi.app.data.model.MessageType.TEXT -> replyTo.text
                    com.dsi.app.data.model.MessageType.IMAGE -> "Photo"
                    com.dsi.app.data.model.MessageType.AUDIO -> "Voice note"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = DsiColors.Muted,
                maxLines = 1
            )
        }
        IconButton(onClick = onClear) {
            Icon(Icons.Filled.Close, contentDescription = "Cancel reply", tint = DsiColors.Muted)
        }
    }
}

@Composable
private fun AttachmentRow(onPickPhoto: () -> Unit, onTakePhoto: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        AttachmentAction(Icons.Filled.PhotoLibrary, "Gallery", onPickPhoto)
        AttachmentAction(Icons.Filled.PhotoCamera, "Camera", onTakePhoto)
    }
}

@Composable
private fun AttachmentAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = DsiColors.Muted)
    }
}

@Composable
private fun RecordingBar(elapsedMs: Long, onCancel: () -> Unit, onSend: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.error)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "Recording  ${formatDuration(elapsedMs)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "Cancel",
            style = MaterialTheme.typography.bodyMedium,
            color = DsiColors.Muted,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onCancel)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(23.dp))
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onSend),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Send,
                contentDescription = "Send voice note",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
