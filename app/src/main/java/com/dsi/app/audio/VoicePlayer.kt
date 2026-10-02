package com.dsi.app.audio

import android.media.AudioAttributes
import android.media.MediaPlayer

/**
 * One player for the whole screen: starting a new note stops the previous one, which is
 * the behaviour people expect and avoids two voice notes talking over each other.
 */
class VoicePlayer {

    private var player: MediaPlayer? = null
    private var playingId: String? = null

    fun currentlyPlaying(): String? = playingId

    fun toggle(messageId: String, url: String, onStateChange: (String?) -> Unit) {
        if (playingId == messageId) {
            stop()
            onStateChange(null)
            return
        }
        stop()
        runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(url)
                setOnPreparedListener { start(); playingId = messageId; onStateChange(messageId) }
                setOnCompletionListener { stop(); onStateChange(null) }
                setOnErrorListener { _, _, _ -> stop(); onStateChange(null); true }
                prepareAsync()
            }
        }.onFailure { onStateChange(null) }
    }

    fun stop() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        playingId = null
    }
}
