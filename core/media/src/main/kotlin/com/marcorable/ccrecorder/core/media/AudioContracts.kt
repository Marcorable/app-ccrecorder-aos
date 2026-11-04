package com.marcorable.ccrecorder.core.media

sealed interface AudioCommandResult<out T> {
    data class Success<T>(val value: T) : AudioCommandResult<T>

    data class Failure(val error: AudioError) : AudioCommandResult<Nothing>
}

sealed interface AudioError

sealed interface RecordingError : AudioError {
    data object PermissionDenied : RecordingError

    data class PrepareFailed(val cause: Throwable) : RecordingError

    data class StartFailed(val cause: Throwable) : RecordingError

    data class FinishFailed(val cause: Throwable) : RecordingError

    data class CleanupFailed(val outputPath: String) : RecordingError

    data class InvalidState(val message: String) : RecordingError
}

sealed interface PlaybackError : AudioError {
    data class InvalidPath(val relativePath: String) : PlaybackError

    data class MissingFile(val relativePath: String) : PlaybackError

    data class PrepareFailed(val cause: Throwable) : PlaybackError

    data class InvalidState(val message: String) : PlaybackError

    data class InvalidRate(val rate: Float) : PlaybackError
}

enum class RecordingFinishKind {
    COMPLETED,
    CANCELLED,
}

data class FinishedRecording(
    val sessionId: String,
    val outputPath: String,
    val elapsedMs: Long,
    val kind: RecordingFinishKind,
)

sealed interface RecordingState {
    data object NoSession : RecordingState

    data class Preparing(val sessionId: String) : RecordingState

    data class Ready(val sessionId: String, val elapsedMs: Long = 0L) : RecordingState

    data class Recording(val sessionId: String, val elapsedMs: Long) : RecordingState

    data class Paused(val sessionId: String, val elapsedMs: Long) : RecordingState

    data class Saving(val sessionId: String, val elapsedMs: Long) : RecordingState

    data class PrepareFailed(val sessionId: String, val error: RecordingError) : RecordingState

    data class SaveFailed(val sessionId: String, val error: RecordingError) : RecordingState

    data class Cancelling(val sessionId: String) : RecordingState

    data class Finished(val result: FinishedRecording) : RecordingState
}

sealed interface PlaybackState {
    data object Released : PlaybackState

    data class Preparing(val mediaId: String) : PlaybackState

    data class Ready(
        val mediaId: String,
        val durationMs: Long,
        val positionMs: Long,
        val rate: Float,
    ) : PlaybackState

    data class Playing(
        val mediaId: String,
        val durationMs: Long,
        val positionMs: Long,
        val rate: Float,
    ) : PlaybackState

    data class Error(val mediaId: String, val error: PlaybackError) : PlaybackState
}

sealed interface PlaybackEvent {
    data class Interrupted(val mediaId: String) : PlaybackEvent
}

fun interface StateObserver<T> {
    fun onStateChanged(state: T)
}

fun interface EventObserver<T> {
    fun onEvent(event: T)
}
