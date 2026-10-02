package com.dsi.app.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File

/**
 * AAC in an MP4 container at 32 kbps mono — roughly 4 KB per second of speech, which keeps
 * a two-minute note under half a megabyte and plays on every Android version we support.
 */
class VoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAt = 0L

    val isRecording: Boolean get() = recorder != null

    fun start(): Boolean {
        if (isRecording) return false
        val dir = File(context.cacheDir, "voice").apply { mkdirs() }
        val file = File(dir, "note_${System.currentTimeMillis()}.m4a")

        return try {
            val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION") MediaRecorder()
            }
            rec.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(32_000)
                setAudioSamplingRate(44_100)
                setAudioChannels(1)
                setMaxDuration(MAX_DURATION_MS)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = rec
            outputFile = file
            startedAt = SystemClock.elapsedRealtime()
            true
        } catch (e: Exception) {
            cleanup()
            false
        }
    }

    /** Returns the finished file and its duration, or null if the take was unusable. */
    fun stop(): Pair<File, Long>? {
        val rec = recorder ?: return null
        val file = outputFile
        val duration = SystemClock.elapsedRealtime() - startedAt
        return try {
            rec.stop()
            rec.release()
            recorder = null
            outputFile = null
            if (file == null || duration < MIN_DURATION_MS || file.length() == 0L) {
                file?.delete()
                null
            } else {
                file to duration
            }
        } catch (e: Exception) {
            // stop() throws if the recording was too short to produce a valid file.
            runCatching { rec.release() }
            recorder = null
            file?.delete()
            outputFile = null
            null
        }
    }

    /** Discards the current take, e.g. when the user slides to cancel or leaves the screen. */
    fun cancel() {
        val rec = recorder ?: return
        runCatching { rec.stop() }
        runCatching { rec.release() }
        recorder = null
        outputFile?.delete()
        outputFile = null
    }

    fun elapsedMs(): Long = if (isRecording) SystemClock.elapsedRealtime() - startedAt else 0L

    private fun cleanup() {
        runCatching { recorder?.release() }
        recorder = null
        outputFile?.delete()
        outputFile = null
    }

    companion object {
        const val MAX_DURATION_MS = 5 * 60 * 1000
        const val MIN_DURATION_MS = 700
    }
}
