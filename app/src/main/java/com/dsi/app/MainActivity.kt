package com.dsi.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.dsi.app.core.SecureStore
import com.dsi.app.ui.nav.DsiNavHost
import com.dsi.app.ui.theme.DsiTheme

class MainActivity : ComponentActivity() {

    private val screenLockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                // Phone is locked / screen turned off: close app immediately
                finishAndRemoveTask()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Register receiver to auto-close when the phone screen turns off / locks
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        registerReceiver(screenLockReceiver, filter)

        // FLAG_SECURE: keeps the conversation out of the recents thumbnail and blocks
        // screenshots. Also means the white lock screen is what a shoulder-surfer sees
        // in the app switcher.
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )

        setContent {
            val store = remember { SecureStore(applicationContext) }
            DsiTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
                    DsiNavHost(secureStore = store)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(screenLockReceiver) }
    }
}

