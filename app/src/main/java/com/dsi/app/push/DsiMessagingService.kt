package com.dsi.app.push

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.ContentResolver
import android.net.Uri
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.dsi.app.DsiApplication
import com.dsi.app.MainActivity
import com.dsi.app.R
import com.dsi.app.data.repo.AuthRepository
import com.dsi.app.data.repo.ChatRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The push payload never contains message content. The server has already decided on the
 * body — "Alter", "Wating", or "Notification" — and sends only that, plus the message id so this
 * device can confirm delivery.
 */
class DsiMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch { AuthRepository.updatePushToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val body = message.data["body"]?.takeIf { it.isNotBlank() }
            ?: message.notification?.body
            ?: DEFAULT_BODY
        val sound = message.data["sound"] ?: message.notification?.sound
        val messageId = message.data["messageId"]

        // Delivery receipt: written when the payload actually reaches this device, which is
        // stricter than "the push service accepted it".
        if (messageId != null && AuthRepository.currentUid != null) {
            scope.launch { ChatRepository.markDelivered(messageId) }
        }

        // Do not post push notifications when the chat is currently open in the foreground.
        if (com.dsi.app.core.LockManager.isChatOpen) return

        showNotification(body, sound)
    }

    private fun showNotification(body: String, sound: String? = null) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val isCircular = body.equals("Circular", ignoreCase = true) ||
            body.equals("Waiting", ignoreCase = true) ||
            body.equals("Wating", ignoreCase = true) ||
            sound == "shrine"

        val channelId = if (isCircular) DsiApplication.CHANNEL_WAITING_ID else DsiApplication.CHANNEL_ID
        val soundUri = Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://$packageName/${R.raw.shrine}")

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pending)

        if (isCircular) {
            builder.setSound(soundUri)
            builder.setDefaults(NotificationCompat.DEFAULT_VIBRATE or NotificationCompat.DEFAULT_LIGHTS)
        } else {
            builder.setDefaults(NotificationCompat.DEFAULT_ALL)
        }

        // Every push replaces the previous one, so the tray never accumulates a
        // count that hints at how much was said.
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, builder.build())
    }

    private companion object {
        const val NOTIFICATION_ID = 1001
        const val DEFAULT_BODY = "Notification"
    }
}
