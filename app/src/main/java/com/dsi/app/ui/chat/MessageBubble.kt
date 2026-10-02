package com.dsi.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.dsi.app.data.model.Message
import com.dsi.app.data.model.MessageType
import com.dsi.app.ui.theme.DsiColors

@Composable
fun MessageBubble(
    message: Message,
    isMine: Boolean,
    pending: Boolean,
    mediaUrl: String?,
    isPlaying: Boolean,
    onPlayToggle: () -> Unit,
    onReply: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bubbleColor = if (isMine) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isMine) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurfaceVariant
    val metaColor = textColor.copy(alpha = 0.65f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isMine) 16.dp else 4.dp,
                        bottomEnd = if (isMine) 4.dp else 16.dp
                    )
                )
                .background(bubbleColor)
                .clickable(onClick = onReply)
                .padding(horizontal = 10.dp, vertical = 7.dp)
        ) {
            if (message.replyToPreview != null) {
                ReplyQuote(
                    preview = message.replyToPreview,
                    authorLabel = if (message.replyToRole == message.senderRole) "You" else "Reply",
                    accent = textColor
                )
                Spacer(Modifier.height(6.dp))
            }

            when (message.messageType) {
                MessageType.TEXT -> Text(
                    text = linkifiedText(message.text, linkColor = textColor),
                    color = textColor,
                    style = MaterialTheme.typography.bodyLarge
                )

                MessageType.IMAGE -> ImageContent(message, mediaUrl)

                MessageType.AUDIO -> VoiceNoteContent(
                    durationMs = message.durationMs ?: 0L,
                    isPlaying = isPlaying,
                    enabled = mediaUrl != null,
                    tint = textColor,
                    onToggle = onPlayToggle
                )
            }

            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatClock(message.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = metaColor
                )
                if (isMine) {
                    Spacer(Modifier.width(4.dp))
                    StatusTick(message = message, pending = pending, tint = metaColor)
                }
            }
        }
    }
}

@Composable
private fun ReplyQuote(preview: String, authorLabel: String, accent: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(accent.copy(alpha = 0.12f))
            .padding(start = 8.dp, top = 5.dp, bottom = 5.dp, end = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(28.dp)
                .background(accent.copy(alpha = 0.6f))
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = authorLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = accent.copy(alpha = 0.9f)
            )
            Text(
                text = preview,
                style = MaterialTheme.typography.bodyMedium,
                color = accent.copy(alpha = 0.8f),
                maxLines = 2
            )
        }
    }
}

@Composable
private fun ImageContent(message: Message, mediaUrl: String?) {
    val width = message.mediaWidth ?: 1
    val height = message.mediaHeight ?: 1
    val ratio = (width.toFloat() / height.toFloat()).coerceIn(0.5f, 1.8f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.06f))
    ) {
        if (mediaUrl == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Loading photo",
                    style = MaterialTheme.typography.labelSmall,
                    color = DsiColors.Muted
                )
            }
        } else {
            AsyncImage(
                model = mediaUrl,
                contentDescription = "Photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio)
                    .clip(RoundedCornerShape(10.dp))
            )
        }
    }
}

@Composable
private fun VoiceNoteContent(
    durationMs: Long,
    isPlaying: Boolean,
    enabled: Boolean,
    tint: Color,
    onToggle: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(tint.copy(alpha = 0.15f))
                .clickable(enabled = enabled, onClick = onToggle),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Stop" else "Play voice note",
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = null,
            tint = tint.copy(alpha = 0.6f),
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = formatDuration(durationMs),
            style = MaterialTheme.typography.bodyMedium,
            color = tint
        )
    }
}

/**
 * Sent, delivered, seen. A pending write shows a clock — that is a message sitting in the
 * local queue because the phone is offline, not a failure.
 */
@Composable
private fun StatusTick(message: Message, pending: Boolean, tint: Color) {
    when {
        pending || message.createdAt == null -> Icon(
            Icons.Filled.Schedule, contentDescription = "Waiting to send",
            tint = tint, modifier = Modifier.size(13.dp)
        )
        message.isSeen -> Icon(
            Icons.Filled.DoneAll, contentDescription = "Seen",
            tint = DsiColors.Online, modifier = Modifier.size(15.dp)
        )
        message.isDelivered -> Icon(
            Icons.Filled.DoneAll, contentDescription = "Delivered",
            tint = tint, modifier = Modifier.size(15.dp)
        )
        else -> Icon(
            Icons.Filled.Done, contentDescription = "Sent",
            tint = tint, modifier = Modifier.size(15.dp)
        )
    }
}
