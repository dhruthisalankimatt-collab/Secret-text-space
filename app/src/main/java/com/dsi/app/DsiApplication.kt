package com.dsi.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.content.ContentResolver
import android.media.AudioAttributes
import android.net.Uri
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.dsi.app.core.SecureStore
import com.dsi.app.data.repo.PresenceRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.ktx.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.database.ktx.database
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
class DsiApplication : Application() {

    lateinit var secureStore: SecureStore
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        FirebaseApp.initializeApp(this)

        // App Check blocks calls to the backend from anything that is not this signed app.
        Firebase.appCheck.installAppCheckProviderFactory(
            if (BuildConfig.DEBUG) DebugAppCheckProviderFactory.getInstance()
            else PlayIntegrityAppCheckProviderFactory.getInstance()
        )

        // Offline persistence: messages queue locally and flush when connectivity returns.
        Firebase.database(com.dsi.app.data.repo.PresenceRepository.RTDB_URL).setPersistenceEnabled(true)
        Firebase.firestore // Firestore persistence is on by default.

        secureStore = SecureStore(this)
        createNotificationChannels()
        observeProcessLifecycle()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)

        val messageChannel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
            enableVibration(true)
            enableLights(true)
        }
        manager.createNotificationChannel(messageChannel)

        val soundUri = Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://$packageName/${R.raw.shrine}")
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .build()

        val waitingChannel = NotificationChannel(
            CHANNEL_WAITING_ID,
            getString(R.string.channel_waiting_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setSound(soundUri, audioAttributes)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
            enableVibration(true)
            enableLights(true)
        }
        manager.createNotificationChannel(waitingChannel)
    }

    /**
     * Presence follows the *process* lifecycle, not the chat screen, so the user is marked
     * offline as soon as the app is backgrounded. A Realtime Database onDisconnect handler
     * covers the case where the process is killed or the network drops without warning.
     */
    private fun observeProcessLifecycle() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                PresenceRepository.goOnline()
            }

            override fun onStop(owner: LifecycleOwner) {
                PresenceRepository.goOffline()
                PresenceRepository.setTyping(false)
            }
        })
    }

    companion object {
        lateinit var instance: DsiApplication
            private set
        const val CHANNEL_ID = "dsi_messages"
        const val CHANNEL_WAITING_ID = "dsi_waiting"
    }
}
