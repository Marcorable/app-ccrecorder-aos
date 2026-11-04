package com.marcorable.ccrecorder.core.media

import java.util.concurrent.CopyOnWriteArraySet

interface AudioClock {
    fun elapsedRealtimeMs(): Long
}

interface RecordingFileCleanup {
    fun delete(path: String): Boolean
}

interface RecorderBackend {
    fun prepare()

    fun start()

    fun pause()

    fun resume()

    fun stop()

    fun release()
}

fun interface RecorderBackendFactory {
    fun create(outputPath: String): RecorderBackend
}

class RecordingController(
    private val backendFactory: RecorderBackendFactory,
    private val clock: AudioClock,
    private val fileCleanup: RecordingFileCleanup,
) {
    private val observers = CopyOnWriteArraySet<StateObserver<RecordingState>>()
    private var backend: RecorderBackend? = null
    private var outputPath: String? = null
    private var accumulatedMs = 0L
    private var activeStartedAtMs: Long? = null

    @Volatile
    private var currentState: RecordingState = RecordingState.NoSession

    val state: RecordingState
        @Synchronized get() = snapshotState()

    fun addObserver(observer: StateObserver<RecordingState>) {
        observers += observer
        observer.onStateChanged(state)
    }

    fun removeObserver(observer: StateObserver<RecordingState>) {
        observers -= observer
    }

    @Synchronized
    fun prepare(sessionId: String, outputPath: String): AudioCommandResult<RecordingState> {
        if (currentState !is RecordingState.NoSession && currentState !is RecordingState.Finished) {
            return invalidState("prepare is only allowed without an active session")
        }
        resetTiming()
        this.outputPath = outputPath
        publish(RecordingState.Preparing(sessionId))
        return try {
            val createdBackend = backendFactory.create(outputPath)
            backend = createdBackend
            createdBackend.prepare()
            success(RecordingState.Ready(sessionId))
        } catch (cause: Throwable) {
            val error = cleanupAfterFailure(RecordingError.PrepareFailed(cause))
            failure(RecordingState.PrepareFailed(sessionId, error), error)
        }
    }

    @Synchronized
    fun start(sessionId: String): AudioCommandResult<RecordingState> {
        val ready = currentState as? RecordingState.Ready
            ?: return invalidState("start is only allowed from Ready")
        if (ready.sessionId != sessionId) return wrongSession(sessionId)
        return try {
            backend.requireBackend().start()
            activeStartedAtMs = clock.elapsedRealtimeMs()
            success(RecordingState.Recording(sessionId, accumulatedMs))
        } catch (cause: Throwable) {
            val error = cleanupAfterFailure(RecordingError.StartFailed(cause))
            failure(RecordingState.PrepareFailed(sessionId, error), error)
        }
    }

    @Synchronized
    fun pause(sessionId: String): AudioCommandResult<RecordingState> {
        val recording = currentState as? RecordingState.Recording
            ?: return invalidState("pause is only allowed from Recording")
        if (recording.sessionId != sessionId) return wrongSession(sessionId)
        return try {
            backend.requireBackend().pause()
            accumulatedMs = elapsedNow()
            activeStartedAtMs = null
            success(RecordingState.Paused(sessionId, accumulatedMs))
        } catch (cause: Throwable) {
            terminalRecorderFailure(sessionId, cause)
        }
    }

    @Synchronized
    fun resume(sessionId: String): AudioCommandResult<RecordingState> {
        val paused = currentState as? RecordingState.Paused
            ?: return invalidState("resume is only allowed from Paused")
        if (paused.sessionId != sessionId) return wrongSession(sessionId)
        return try {
            backend.requireBackend().resume()
            activeStartedAtMs = clock.elapsedRealtimeMs()
            success(RecordingState.Recording(sessionId, accumulatedMs))
        } catch (cause: Throwable) {
            terminalRecorderFailure(sessionId, cause)
        }
    }

    @Synchronized
    fun finish(sessionId: String): AudioCommandResult<FinishedRecording> {
        val elapsed = when (val state = currentState) {
            is RecordingState.Recording -> {
                if (state.sessionId != sessionId) return wrongSession(sessionId)
                elapsedNow()
            }
            is RecordingState.Paused -> {
                if (state.sessionId != sessionId) return wrongSession(sessionId)
                accumulatedMs
            }
            else -> return invalidState("finish is only allowed from Recording or Paused")
        }
        if (elapsed <= 0L) return invalidState("finish requires elapsedMs > 0")
        publish(RecordingState.Saving(sessionId, elapsed))
        return try {
            backend.requireBackend().stop()
            releaseBackend()
            val result = FinishedRecording(
                sessionId = sessionId,
                outputPath = requireNotNull(outputPath),
                elapsedMs = elapsed,
                kind = RecordingFinishKind.COMPLETED,
            )
            publish(RecordingState.Finished(result))
            AudioCommandResult.Success(result)
        } catch (cause: Throwable) {
            val error = cleanupAfterFailure(RecordingError.FinishFailed(cause))
            failure(RecordingState.SaveFailed(sessionId, error), error)
        } finally {
            activeStartedAtMs = null
            accumulatedMs = elapsed
        }
    }

    @Synchronized
    fun cancel(sessionId: String): AudioCommandResult<FinishedRecording> {
        val stateSessionId = currentSessionId()
        if (currentState is RecordingState.Finished) {
            val result = (currentState as RecordingState.Finished).result
            return if (result.sessionId == sessionId && result.kind == RecordingFinishKind.CANCELLED) {
                AudioCommandResult.Success(result)
            } else {
                wrongSession(sessionId)
            }
        }
        if (stateSessionId == null) return invalidState("there is no session to cancel")
        if (stateSessionId != sessionId) return wrongSession(sessionId)
        publish(RecordingState.Cancelling(sessionId))
        val path = outputPath.orEmpty()
        val cleanupSucceeded = releaseAndDelete()
        if (!cleanupSucceeded) {
            activeStartedAtMs = null
            val error = RecordingError.CleanupFailed(path)
            return failure(RecordingState.SaveFailed(sessionId, error), error)
        }
        val result = FinishedRecording(
            sessionId = sessionId,
            outputPath = path,
            elapsedMs = elapsedNow(),
            kind = RecordingFinishKind.CANCELLED,
        )
        activeStartedAtMs = null
        publish(RecordingState.Finished(result))
        return AudioCommandResult.Success(result)
    }

    @Synchronized
    fun closeFailure(sessionId: String): AudioCommandResult<RecordingState> {
        val stateSessionId = currentSessionId()
        if (stateSessionId != sessionId) return wrongSession(sessionId)
        val error = when (val state = currentState) {
            is RecordingState.PrepareFailed -> state.error
            is RecordingState.SaveFailed -> state.error
            else -> return invalidState("closeFailure is only allowed from a failure state")
        }
        if (error is RecordingError.CleanupFailed && !releaseAndDelete()) {
            return AudioCommandResult.Failure(error)
        }
        val result = FinishedRecording(
            sessionId = sessionId,
            outputPath = outputPath.orEmpty(),
            elapsedMs = accumulatedMs,
            kind = RecordingFinishKind.CANCELLED,
        )
        return success(RecordingState.Finished(result))
    }

    private fun snapshotState(): RecordingState = when (val state = currentState) {
        is RecordingState.Recording -> state.copy(elapsedMs = elapsedNow())
        else -> state
    }

    private fun elapsedNow(): Long {
        val startedAt = activeStartedAtMs ?: return accumulatedMs
        return accumulatedMs + (clock.elapsedRealtimeMs() - startedAt).coerceAtLeast(0L)
    }

    private fun resetTiming() {
        accumulatedMs = 0L
        activeStartedAtMs = null
    }

    private fun releaseBackend() {
        runCatching { backend?.release() }
        backend = null
    }

    private fun releaseAndDelete(): Boolean {
        releaseBackend()
        return outputPath?.let { runCatching { fileCleanup.delete(it) }.getOrDefault(false) } ?: true
    }

    private fun cleanupAfterFailure(primaryError: RecordingError): RecordingError =
        if (releaseAndDelete()) primaryError else RecordingError.CleanupFailed(outputPath.orEmpty())

    private fun RecorderBackend?.requireBackend(): RecorderBackend =
        this ?: error("Recorder backend is unavailable")

    private fun currentSessionId(): String? = when (val state = currentState) {
        RecordingState.NoSession -> null
        is RecordingState.Preparing -> state.sessionId
        is RecordingState.Ready -> state.sessionId
        is RecordingState.Recording -> state.sessionId
        is RecordingState.Paused -> state.sessionId
        is RecordingState.Saving -> state.sessionId
        is RecordingState.PrepareFailed -> state.sessionId
        is RecordingState.SaveFailed -> state.sessionId
        is RecordingState.Cancelling -> state.sessionId
        is RecordingState.Finished -> state.result.sessionId
    }

    private fun publish(state: RecordingState) {
        currentState = state
        observers.forEach { it.onStateChanged(state) }
    }

    private fun success(state: RecordingState): AudioCommandResult<RecordingState> {
        publish(state)
        return AudioCommandResult.Success(state)
    }

    private fun failure(state: RecordingState, error: RecordingError): AudioCommandResult.Failure {
        publish(state)
        return AudioCommandResult.Failure(error)
    }

    private fun invalidState(message: String): AudioCommandResult.Failure =
        AudioCommandResult.Failure(RecordingError.InvalidState(message))

    private fun wrongSession(sessionId: String): AudioCommandResult.Failure =
        invalidState("command session $sessionId does not match the active session")

    private fun terminalRecorderFailure(
        sessionId: String,
        cause: Throwable,
    ): AudioCommandResult.Failure {
        accumulatedMs = elapsedNow()
        activeStartedAtMs = null
        val error = cleanupAfterFailure(RecordingError.FinishFailed(cause))
        return failure(RecordingState.SaveFailed(sessionId, error), error)
    }
}
