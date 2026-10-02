package com.dsi.app.core

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Small encrypted key/value store for the few things that must survive a restart:
 * which identity this device is bound to, and whether setup has been completed.
 *
 * Backed by EncryptedSharedPreferences (AES-256-GCM, key held in the Android Keystore).
 * No message content and no credentials are stored here — the Firebase Auth SDK keeps
 * its own refresh token in app-private storage.
 */
class SecureStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            "dsi_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var boundIdentity: Identity?
        get() = Identity.fromKey(prefs.getString(KEY_IDENTITY, null))
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_IDENTITY) else putString(KEY_IDENTITY, value.key)
        }.apply()

    /** True once an identity has been chosen and successfully authenticated at least once. */
    val isProvisioned: Boolean get() = boundIdentity != null

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val KEY_IDENTITY = "bound_identity"
    }
}
