package com.dsi.app.data.repo

import com.dsi.app.core.Identity
import com.dsi.app.data.model.Presence
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Presence and typing use Realtime Database rather than Firestore for one reason:
 * `onDisconnect()`. The RTDB server runs the "set me offline" write itself when the
 * socket dies, so a killed app, a dead battery or a lost signal cannot leave someone
 * showing as online. Firestore has no equivalent.
 */
object PresenceRepository {

    const val RTDB_URL = "https://dsi-private-chat-default-rtdb.asia-southeast1.firebasedatabase.app"

    private val db get() = Firebase.database(RTDB_URL)
    private var myRole: Identity? = null

    fun bind(identity: Identity) {
        myRole = identity
        goOnline()
    }

    fun goOnline() {
        val role = myRole ?: return
        val presenceRef = db.getReference(FirebasePaths.presenceNode(role.key))
        val typingRef = db.getReference(FirebasePaths.typingNode(role.key))

        // Set online immediately
        presenceRef.setValue(mapOf("online" to true, "lastSeen" to ServerValue.TIMESTAMP))

        // .info/connected flips on every reconnect, so the onDisconnect handler is
        // re-armed each time rather than only once at startup.
        db.getReference(".info/connected").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.getValue(Boolean::class.java) != true) return
                presenceRef.onDisconnect().setValue(
                    mapOf("online" to false, "lastSeen" to ServerValue.TIMESTAMP)
                )
                typingRef.onDisconnect().setValue(false)
                presenceRef.setValue(mapOf("online" to true, "lastSeen" to ServerValue.TIMESTAMP))
            }

            override fun onCancelled(error: DatabaseError) = Unit
        })
    }

    fun goOffline() {
        val role = myRole ?: return
        db.getReference(FirebasePaths.presenceNode(role.key))
            .setValue(mapOf("online" to false, "lastSeen" to ServerValue.TIMESTAMP))
    }

    fun setTyping(typing: Boolean) {
        val role = myRole ?: return
        db.getReference(FirebasePaths.typingNode(role.key)).setValue(typing)
    }

    fun observePresence(of: Identity): Flow<Presence> = callbackFlow {
        val ref = db.getReference(FirebasePaths.presenceNode(of.key))
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val online = snapshot.child("online").getValue(Boolean::class.java) ?: false
                val lastSeen = snapshot.child("lastSeen").getValue(Long::class.java) ?: 0L
                trySend(Presence(online, lastSeen))
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(Presence(false, 0L))
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    fun observeTyping(of: Identity): Flow<Boolean> = callbackFlow {
        val ref = db.getReference(FirebasePaths.typingNode(of.key))
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(snapshot.getValue(Boolean::class.java) ?: false)
            }

            override fun onCancelled(error: DatabaseError) { trySend(false) }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }
}
