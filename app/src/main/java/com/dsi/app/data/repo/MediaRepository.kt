package com.dsi.app.data.repo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.firebase.storage.ktx.storage
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

data class UploadedImage(val path: String, val width: Int, val height: Int)

object MediaRepository {

    private const val MAX_DIMENSION = 800
    private const val JPEG_QUALITY = 65
    const val MAX_IMAGE_BYTES = 8L * 1024 * 1024
    const val MAX_AUDIO_BYTES = 12L * 1024 * 1024

    private val storage get() = Firebase.storage

    /**
     * Downscales to at most 800px on the long edge, applies the EXIF rotation and strips
     * metadata by re-encoding the bitmap at compressed JPEG quality.
     */
    suspend fun uploadImage(
        context: Context,
        uri: Uri,
        role: String,
        clientId: String
    ): UploadedImage = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Could not read the selected image.")
        require(bytes.size <= MAX_IMAGE_BYTES) { "That image is too large to send." }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight)
        val decoded = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: error("That image format is not supported.")

        val rotation = context.contentResolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f

        val oriented = if (rotation == 0f) decoded else Bitmap.createBitmap(
            decoded, 0, 0, decoded.width, decoded.height,
            Matrix().apply { postRotate(rotation) }, true
        )
        val scaled = scaleToMax(oriented)

        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)

        val path = FirebasePaths.imagePath(role, clientId)
        val metadata = com.google.firebase.storage.StorageMetadata.Builder()
            .setContentType("image/jpeg")
            .build()
        storage.reference.child(path).putBytes(out.toByteArray(), metadata).await()

        UploadedImage(path, scaled.width, scaled.height)
    }

    suspend fun uploadAudio(file: File, role: String, clientId: String): String =
        withContext(Dispatchers.IO) {
            require(file.length() in 1..MAX_AUDIO_BYTES) { "That voice note could not be sent." }
            val path = FirebasePaths.audioPath(role, clientId)
            val metadata = com.google.firebase.storage.StorageMetadata.Builder()
                .setContentType("audio/mp4")
                .build()
            storage.reference.child(path).putFile(Uri.fromFile(file), metadata).await()
            path
        }

    suspend fun deleteMedia(path: String) = withContext(Dispatchers.IO) {
        runCatching { storage.reference.child(path).delete().await() }
    }

    /** Storage objects are private; playback and display go through short-lived signed URLs. */
    suspend fun downloadUrl(path: String): String =
        storage.reference.child(path).downloadUrl.await().toString()

    private fun calculateInSampleSize(width: Int, height: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (w / 2 >= MAX_DIMENSION && h / 2 >= MAX_DIMENSION) {
            w /= 2; h /= 2; sample *= 2
        }
        return sample
    }

    private fun scaleToMax(bitmap: Bitmap): Bitmap {
        val longEdge = maxOf(bitmap.width, bitmap.height)
        if (longEdge <= MAX_DIMENSION) return bitmap
        val ratio = MAX_DIMENSION.toFloat() / longEdge
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }
}
