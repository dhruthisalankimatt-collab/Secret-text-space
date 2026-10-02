package com.dsi.app.ui.identity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dsi.app.core.Identity
import com.dsi.app.core.SecureStore
import com.dsi.app.data.repo.AuthResult
import com.dsi.app.data.repo.AuthRepository
import com.dsi.app.data.repo.PresenceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class IdentityUiState(
    val selected: Identity? = null,
    val passphrase: String = "",
    val busy: Boolean = false,
    val error: String? = null
)

class IdentityViewModel(private val secureStore: SecureStore) : ViewModel() {

    private val _state = MutableStateFlow(IdentityUiState())
    val state: StateFlow<IdentityUiState> = _state.asStateFlow()

    fun select(identity: Identity) =
        _state.update { it.copy(selected = identity, error = null) }

    fun onPassphraseChange(value: String) =
        _state.update { it.copy(passphrase = value, error = null) }

    fun submit(onSuccess: (Identity) -> Unit) {
        val current = _state.value
        val identity = current.selected ?: return
        if (current.passphrase.isBlank()) {
            _state.update { it.copy(error = "Enter the passphrase for this identity.") }
            return
        }
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val result = AuthRepository.signIn(identity, current.passphrase)) {
                is AuthResult.Success -> {
                    // Bind the device only after the server has confirmed the role claim.
                    secureStore.boundIdentity = result.identity
                    PresenceRepository.bind(result.identity)
                    _state.update { it.copy(busy = false, passphrase = "") }
                    onSuccess(result.identity)
                }
                is AuthResult.Failure ->
                    _state.update { it.copy(busy = false, error = result.message, passphrase = "") }
            }
        }
    }
}
