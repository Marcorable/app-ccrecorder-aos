package com.marcorable.ccrecorder.core.media

interface AudioEngine {
    val playback: PlaybackController

    fun newRecordingController(): RecordingController
}

class DefaultAudioEngine(
    private val recorderBackendFactory: RecorderBackendFactory,
    private val clock: AudioClock,
    private val recordingFileCleanup: RecordingFileCleanup,
    playerBackendFactory: PlayerBackendFactory,
    audioSourceResolver: AudioSourceResolver,
) : AudioEngine {
    override val playback = PlaybackController(playerBackendFactory, audioSourceResolver)

    override fun newRecordingController(): RecordingController = RecordingController(
        backendFactory = recorderBackendFactory,
        clock = clock,
        fileCleanup = recordingFileCleanup,
    )
}
