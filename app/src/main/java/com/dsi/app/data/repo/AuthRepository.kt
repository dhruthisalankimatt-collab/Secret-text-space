package com.dsi.app.data.repo

import com.dsi.app.core.Identity
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.ktx.messaging
import kotlinx.coroutines.tasks.await

sealed interface AuthResult {
    data class Success(val identity: Identity) : AuthResult
    data class Failure(val message: String) : AuthResult
}

/**
 * Sign-in is credential-based. The identity screen decides *which* of the two accounts to
 * sign in to; the server decides whether that sign-in is allowed and what role it carries.
 *
 * The role lives in a Firebase Auth custom claim. A client cannot set or edit a claim, so
 * selecting "Queen" without Queen's passphrase gets you nothing.
 */
object AuthRepository {

    private val auth get() = Firebase.auth
    private val db get() = Firebase.firestore

    val currentUid: String? get() = auth.currentUser?.uid

    /** Email addresses are synthetic and never shown; they exist because Auth requires them. */
    private fun emailFor(identity: Identity) = "${identity.key}@dsi.invalid"

    suspend fun signIn(identity: Identity, passphrase: String): AuthResult {
        return try {
            val result = auth.signInWithEmailAndPassword(emailFor(identity), passphrase).await()
            val uid = result.user?.uid ?: return AuthResult.Failure("Sign-in failed. Try again.")

            // Force a token refresh so a freshly provisioned claim is present.
            val claims = result.user!!.getIdToken(true).await().claims
            val claimedRole = Identity.fromKey(claims["role"] as? String)

            if (claimedRole == null) {
                auth.signOut()
                return AuthResult.Failure("This account has not been set up yet.")
            }
            if (claimedRole != identity) {
                // Signed in successfully but as the other person. Refuse rather than adapt.
                auth.signOut()
                return AuthResult.Failure("These credentials belong to a different identity.")
            }

            registerPushToken(uid, claimedRole)
            AuthResult.Success(claimedRole)
        } catch (e: Exception) {
            AuthResult.Failure("Could not sign in. Check the passphrase and your connection.")
        }
    }

    /** Refreshes the stored role and push token on app start; returns null if signed out. */
    suspend fun restoreSession(): Identity? {
        val user = auth.currentUser ?: return null
        return try {
            val claims = user.getIdToken(false).await().claims
            val role = Identity.fromKey(claims["role"] as? String) ?: return null
            registerPushToken(user.uid, role)
            role
        } catch (e: Exception) {
            // Offline: fall back to the cached token's claims rather than locking the user out.
            Identity.fromKey(
                runCatching { user.getIdToken(false).await().claims["role"] as? String }.getOrNull()
            )
        }
    }

    private suspend fun registerPushToken(uid: String, role: Identity, explicitToken: String? = null) {
        try {
            val token = explicitToken ?: Firebase.messaging.token.await()
            if (token.isNotBlank()) {
                db.document(FirebasePaths.userDoc(uid))
                    .set(
                        mapOf(
                            "role" to role.key,
                            "fcmToken" to token,
                            "tokenUpdatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    ).await()
            }
        } catch (e: Exception) {
            // Non-fatal: the user can still chat, they just may not get pushes until next launch.
        }
    }

    suspend fun updatePushToken(token: String) {
        val user = auth.currentUser ?: return
        val role = Identity.fromKey(
            runCatching { user.getIdToken(false).await().claims["role"] as? String }.getOrNull()
        ) ?: return
        registerPushToken(user.uid, role, token)
    }

    suspend fun signOut() {
        val uid = currentUid
        PresenceRepository.goOffline()
        if (uid != null) {
            runCatching {
                db.document(FirebasePaths.userDoc(uid))
                    .set(mapOf("fcmToken" to null), SetOptions.merge()).await()
            }
        }
        auth.signOut()
    }
}
