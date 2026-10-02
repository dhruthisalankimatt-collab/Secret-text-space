package com.dsi.app.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dsi.app.core.Identity
import com.dsi.app.core.SecureStore
import com.dsi.app.data.repo.AuthRepository
import com.dsi.app.data.repo.PresenceRepository
import com.dsi.app.ui.chat.ChatScreen
import com.dsi.app.ui.chat.ChatViewModel
import com.dsi.app.ui.identity.IdentityScreen
import com.dsi.app.ui.identity.IdentityViewModel
import com.dsi.app.ui.lock.LockScreen

private sealed interface Stage {
    data object Locked : Stage
    data object ChoosingIdentity : Stage
    data class Chatting(val identity: Identity) : Stage
}

/**
 * The whole navigation graph, which is three steps deep by design.
 *
 * The lock screen is always the entry point — including after the app is backgrounded and
 * the process is recreated — so the conversation is never what appears on launch.
 */
@Composable
fun DsiNavHost(secureStore: SecureStore) {
    var stage by remember { mutableStateOf<Stage>(Stage.Locked) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    androidx.activity.compose.BackHandler {
        stage = Stage.Locked
        (context as? androidx.activity.ComponentActivity)?.finish()
    }

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                // Whenever the app is backgrounded or screen turns off, lock unless picking media
                if (!com.dsi.app.core.LockManager.isPickerActive) {
                    stage = Stage.Locked
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var restoredIdentity by remember { mutableStateOf<Identity?>(secureStore.boundIdentity) }

    // Ensure session and presence are refreshed in the background without ever locking out a bound device
    LaunchedEffect(Unit) {
        val session = AuthRepository.restoreSession() ?: secureStore.boundIdentity
        if (session != null) {
            restoredIdentity = session
            PresenceRepository.bind(session)
        }
    }

    when (val current = stage) {
        Stage.Locked -> Box(Modifier.fillMaxSize().background(Color.White)) {
            LockScreen(
                onUnlocked = {
                    val bound = secureStore.boundIdentity ?: restoredIdentity
                    stage = when {
                        // Once logged in on this device: never log out, go straight to chat!
                        bound != null -> {
                            PresenceRepository.bind(bound)
                            Stage.Chatting(bound)
                        }
                        // First-time setup only: authenticate once.
                        else -> Stage.ChoosingIdentity
                    }
                }
            )
        }

        Stage.ChoosingIdentity -> {
            val vm: IdentityViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T =
                        IdentityViewModel(secureStore) as T
                }
            )
            IdentityScreen(
                viewModel = vm,
                onAuthenticated = { identity ->
                    restoredIdentity = identity
                    stage = Stage.Chatting(identity)
                }
            )
        }

        is Stage.Chatting -> {
            val vm: ChatViewModel = viewModel(
                key = current.identity.key,
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T =
                        ChatViewModel(current.identity) as T
                }
            )
            ChatScreen(viewModel = vm)
        }
    }
}
