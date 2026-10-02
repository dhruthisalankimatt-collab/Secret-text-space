package com.dsi.app.push

import android.util.Base64
import android.util.Log
import com.dsi.app.DsiApplication
import com.dsi.app.R
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.concurrent.atomic.AtomicReference

/**
 * Sends direct Firebase Cloud Messaging (FCM HTTP v1) push notifications to the peer device
 * without needing an external backend or running script.
 */
object FcmSender {
    private const val TAG = "FcmSender"

    private data class CachedToken(val token: String, val expiryTime: Long)
    private val tokenCache = AtomicReference<CachedToken?>(null)

    private var cachedPrivateKey: PrivateKey? = null
    private var cachedClientEmail: String? = null
    private var cachedProjectId: String? = null

    private fun loadServiceAccount() {
        if (cachedPrivateKey != null && cachedClientEmail != null && cachedProjectId != null) return
        try {
            val stream = DsiApplication.instance.resources.openRawResource(R.raw.service_account)
            val jsonStr = stream.bufferedReader().use { it.readText() }
            val json = JSONObject(jsonStr)
            cachedClientEmail = json.getString("client_email")
            cachedProjectId = json.getString("project_id")
            val privateKeyPem = json.getString("private_key")

            val cleanKey = privateKeyPem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\\s+".toRegex(), "")
            val decoded = Base64.decode(cleanKey, Base64.DEFAULT)
            val spec = PKCS8EncodedKeySpec(decoded)
            val factory = KeyFactory.getInstance("RSA")
            cachedPrivateKey = factory.generatePrivate(spec)
            Log.d(TAG, "Service account loaded successfully. Project: $cachedProjectId, Email: $cachedClientEmail")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load service account: ${e.message}", e)
        }
    }

    suspend fun sendPushNotification(senderRole: String, messageText: String, messageId: String) = withContext(Dispatchers.IO) {
        try {
            val recipientRole = if (senderRole == "queen") "heera" else "queen"
            val snapshot = Firebase.firestore.collection("users")
                .whereEqualTo("role", recipientRole)
                .get()
                .await()

            val tokens = snapshot.documents.mapNotNull { it.getString("fcmToken")?.trim() }
                .filter { it.isNotEmpty() }
                .distinct()

            if (tokens.isEmpty()) {
                Log.d(TAG, "No push tokens found in Firestore for $recipientRole")
                return@withContext
            }

            val isCircular = messageText.equals("Circular", ignoreCase = true) ||
                messageText.equals("Waiting", ignoreCase = true) ||
                messageText.equals("Wating", ignoreCase = true)
            val isImp = messageText == "Imp" || messageText == "imp"

            val body = when {
                isCircular -> "Circular"
                isImp -> "Alter"
                else -> "Notification"
            }
            val sound = if (isCircular) "shrine" else "default"

            val accessToken = getAccessToken() ?: run {
                Log.e(TAG, "Could not acquire Google OAuth2 access token")
                return@withContext
            }

            for (token in tokens) {
                sendToToken(token, body, sound, messageId, accessToken)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Push dispatch error: ${e.message}", e)
        }
    }

    private fun sendToToken(fcmToken: String, body: String, sound: String, messageId: String, accessToken: String) {
        val projectId = cachedProjectId ?: "dsi-private-chat"
        val url = URL("https://fcm.googleapis.com/v1/projects/$projectId/messages:send")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json; UTF-8")
            doOutput = true
            connectTimeout = 10000
            readTimeout = 10000
        }

        val payload = JSONObject().apply {
            put("message", JSONObject().apply {
                put("token", fcmToken)
                put("data", JSONObject().apply {
                    put("body", body)
                    put("messageId", messageId)
                    put("sound", sound)
                })
                put("android", JSONObject().apply {
                    put("priority", "high")
                })
            })
        }

        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
        val responseCode = conn.responseCode
        if (responseCode in 200..299) {
            Log.d(TAG, "FCM push successfully delivered! Response: $responseCode")
        } else {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
            Log.e(TAG, "FCM Send Error ($responseCode): $err")
        }
    }

    private fun getAccessToken(): String? {
        val now = System.currentTimeMillis()
        val cached = tokenCache.get()
        if (cached != null && cached.expiryTime > now + 60_000) {
            return cached.token
        }

        loadServiceAccount()
        val jwt = createSignedJwt() ?: return null
        val url = URL("https://oauth2.googleapis.com/token")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            doOutput = true
            connectTimeout = 10000
            readTimeout = 10000
        }

        val body = "grant_type=" + java.net.URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", "UTF-8") +
                "&assertion=" + java.net.URLEncoder.encode(jwt, "UTF-8")

        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
        val responseCode = conn.responseCode
        if (responseCode in 200..299) {
            val resp = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(resp)
            val token = json.getString("access_token")
            val expiresIn = json.optLong("expires_in", 3600)
            tokenCache.set(CachedToken(token, now + (expiresIn * 1000)))
            Log.d(TAG, "Acquired fresh Google OAuth2 access token")
            return token
        } else {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
            Log.e(TAG, "OAuth Token Error ($responseCode): $err")
            return null
        }
    }

    private fun createSignedJwt(): String? {
        try {
            loadServiceAccount()
            val email = cachedClientEmail ?: return null
            val privateKey = cachedPrivateKey ?: return null

            val nowSec = System.currentTimeMillis() / 1000
            val expSec = nowSec + 3600

            val header = JSONObject().apply {
                put("alg", "RS256")
                put("typ", "JWT")
            }
            val payload = JSONObject().apply {
                put("iss", email)
                put("scope", "https://www.googleapis.com/auth/firebase.messaging")
                put("aud", "https://oauth2.googleapis.com/token")
                put("exp", expSec)
                put("iat", nowSec)
            }

            val encodedHeader = base64UrlEncode(header.toString().toByteArray(Charsets.UTF_8))
            val encodedPayload = base64UrlEncode(payload.toString().toByteArray(Charsets.UTF_8))
            val signInput = "$encodedHeader.$encodedPayload"

            val signature = Signature.getInstance("SHA256withRSA").apply {
                initSign(privateKey)
                update(signInput.toByteArray(Charsets.UTF_8))
            }
            val signedBytes = signature.sign()
            val encodedSignature = base64UrlEncode(signedBytes)

            return "$signInput.$encodedSignature"
        } catch (e: Exception) {
            Log.e(TAG, "JWT Signing Error: ${e.message}", e)
            return null
        }
    }

    private fun base64UrlEncode(bytes: ByteArray): String {
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
