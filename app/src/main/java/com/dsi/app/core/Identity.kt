package com.dsi.app.core

/**
 * The two — and only two — identities this app supports.
 *
 * [key] is the value stored in the Firebase Auth custom claim "role" and used as the
 * document/node key in Firestore and Realtime Database. The claim is set once by an
 * administrator (see scripts/provision_users.js) and cannot be changed from the client,
 * so picking an identity on the selection screen only chooses which account to sign in
 * to — it never grants that identity.
 */
enum class Identity(val key: String, val displayName: String) {
    QUEEN("queen", "Queen"),
    HEERA("heera", "Mrs.Heera");

    val other: Identity get() = if (this == QUEEN) HEERA else QUEEN

    companion object {
        fun fromKey(key: String?): Identity? = entries.firstOrNull { it.key == key }
    }
}
