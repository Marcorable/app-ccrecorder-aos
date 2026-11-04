package com.marcorable.ccrecorder.core.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.io.File

class AndroidAudioClock : AudioClock {
    override fun elapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()
}

class AndroidRecordingFileCleanup : RecordingFileCleanup {
    override fun delete(path: String): Boolean = File(path).let { !it.exists() || it.delete() }
}

class AndroidMediaRecorderFactory(private val context: Context) : RecorderBackendFactory {
    override fun create(outputPath: String): RecorderBackend =
        AndroidMediaRecorderBackend(createRecorder(), outputPath)

    @Suppress("DEPRECATION")
    private fun createRecorder(): MediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        MediaRecorder(context)
    } else {
        MediaRecorder()
    }
}

private class AndroidMediaRecorderBackend(
    private val recorder: MediaRecorder,
    private val outputPath: String,
) : RecorderBackend {
    override fun prepare() {
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        recorder.setAudioSamplingRate(SAMPLE_RATE_HZ)
        recorder.setAudioEncodingBitRate(BIT_RATE_BPS)
        recorder.setOutputFile(outputPath)
        recorder.prepare()
    }

    override fun start() = recorder.start()

    override fun pause() = recorder.pause()

    override fun resume() = recorder.resume()

    override fun stop() = recorder.stop()

    override fun release() = recorder.release()

    private companion object {
        const val SAMPLE_RATE_HZ = 44_100
        const val BIT_RATE_BPS = 128_000
    }
}

class AndroidMedia3PlayerFactory(private val context: Context) : PlayerBackendFactory {
    override fun create(listener: PlayerBackendListener): PlayerBackend =
        AndroidMedia3PlayerBackend(context, listener)
}

private class AndroidMedia3PlayerBackend(
    context: Context,
    private val backendListener: PlayerBackendListener,
) : PlayerBackend, Player.Listener {
    private var readyReported = false
    private val player = ExoPlayer.Builder(context).build().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build(),
            true,
        )
        addListener(this@AndroidMedia3PlayerBackend)
    }

    override val currentPositionMs: Long
        get() = player.currentPosition.coerceAtLeast(0L)

    override fun prepare(sourceUri: String) {
        player.setMediaItem(MediaItem.fromUri(sourceUri.toUri()))
        player.prepare()
    }

    override fun play() = player.play()

    override fun pause() = player.pause()

    override fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    override fun setPlaybackRate(rate: Float) = player.setPlaybackSpeed(rate)

    override fun release() {
        player.removeListener(this)
        player.release()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_READY -> if (!readyReported) {
                readyReported = true
                backendListener.onReady(player.duration.coerceAtLeast(0L))
            }
            Player.STATE_ENDED -> backendListener.onEnded()
            Player.STATE_BUFFERING,
            Player.STATE_IDLE,
            -> Unit
        }
    }

    override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
        if (playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS) {
            backendListener.onInterrupted()
        }
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS) {
            backendListener.onInterrupted()
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        backendListener.onError(error)
    }
}
