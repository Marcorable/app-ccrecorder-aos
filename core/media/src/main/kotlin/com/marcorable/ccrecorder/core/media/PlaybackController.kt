package com.marcorable.ccrecorder.core.media

import java.io.File
import java.util.concurrent.CopyOnWriteArraySet

sealed interface AudioSourceResolution {
    data class Resolved(val sourceUri: String) : AudioSourceResolution

    data class Rejected(val error: PlaybackError) : AudioSourceResolution
}

fun interface AudioSourceResolver {
    fun resolve(relativePath: String): AudioSourceResolution
}

interface PlayerBackend {
    val currentPositionMs: Long

    fun prepare(sourceUri: String)

    fun play()

    fun pause()

    fun seekTo(positionMs: Long)

    fun setPlaybackRate(rate: Float)

    fun release()
}

interface PlayerBackendListener {
    fun onReady(durationMs: Long)

    fun onEnded()

    fun onInterrupted()

    fun onError(cause: Throwable)
}

fun interface PlayerBackendFactory {
    fun create(listener: PlayerBackendListener): PlayerBackend
}

class AppAudioSourceResolver(private val filesRoot: File) : AudioSourceResolver {
    override fun resolve(relativePath: String): AudioSourceResolution {
        if (
            relativePath.isBlank() ||
            File(relativePath).isAbsolute ||
            !relativePath.startsWith(ALLOWED_PLAYBACK_DIRECTORY)
        ) {
            return AudioSourceResolution.Rejected(PlaybackError.InvalidPath(relativePath))
        }
        val root = runCatching { filesRoot.canonicalFile }.getOrNull()
            ?: return AudioSourceResolution.Rejected(PlaybackError.InvalidPath(relativePath))
        val candidate = runCatching { File(root, relativePath).canonicalFile }.getOrNull()
            ?: return AudioSourceResolution.Rejected(PlaybackError.InvalidPath(relativePath))
        val insideRoot = candidate.path.startsWith(root.path + File.separator)
        if (!insideRoot) return AudioSourceResolution.Rejected(PlaybackError.InvalidPath(relativePath))
        if (!candidate.isFile) {
            return AudioSourceResolution.Rejected(PlaybackError.MissingFile(relativePath))
        }
        return AudioSourceResolution.Resolved(candidate.toURI().toString())
    }

    private companion object {
        const val ALLOWED_PLAYBACK_DIRECTORY = "audio/recordings/"
    }
}

class PlaybackController(
    private val backendFactory: PlayerBackendFactory,
    private val sourceResolver: AudioSourceResolver,
) {
    private val stateObservers = CopyOnWriteArraySet<StateObserver<PlaybackState>>()
    private val eventObservers = CopyOnWriteArraySet<EventObserver<PlaybackEvent>>()
    private var backend: PlayerBackend? = null
    private var generation = 0L

    @Volatile
    var state: PlaybackState = PlaybackState.Released
        private set

    fun addStateObserver(observer: StateObserver<PlaybackState>) {
        stateObservers += observer
        observer.onStateChanged(state)
    }

    fun removeStateObserver(observer: StateObserver<PlaybackState>) {
        stateObservers -= observer
    }

    fun addEventObserver(observer: EventObserver<PlaybackEvent>) {
        eventObservers += observer
    }

    fun removeEventObserver(observer: EventObserver<PlaybackEvent>) {
        eventObservers -= observer
    }

    @Synchronized
    fun prepare(mediaId: String, relativePath: String): AudioCommandResult<PlaybackState> {
        releaseBackend()
        val token = ++generation
        publish(PlaybackState.Preparing(mediaId))
        val resolved = sourceResolver.resolve(relativePath)
        if (resolved is AudioSourceResolution.Rejected) {
            return failure(PlaybackState.Error(mediaId, resolved.error), resolved.error)
        }
        resolved as AudioSourceResolution.Resolved
        return try {
            backend = backendFactory.create(backendListener(token, mediaId))
            backend.requireBackend().prepare(resolved.sourceUri)
            AudioCommandResult.Success(state)
        } catch (cause: Throwable) {
            releaseBackend()
            val error = PlaybackError.PrepareFailed(cause)
            failure(PlaybackState.Error(mediaId, error), error)
        }
    }

    @Synchronized
    fun play(): AudioCommandResult<PlaybackState> {
        val ready = state as? PlaybackState.Ready
            ?: return invalidState("play is only allowed from Ready")
        return try {
            val player = backend.requireBackend()
            publish(ready.copyAsPlaying(positionMs = player.currentPositionMs))
            player.play()
            AudioCommandResult.Success(state)
        } catch (cause: Throwable) {
            backendFailure(cause)
        }
    }

    @Synchronized
    fun pause(): AudioCommandResult<PlaybackState> {
        val playing = state as? PlaybackState.Playing
            ?: return invalidState("pause is only allowed from Playing")
        return runBackendCommand {
            pause()
            playing.copyAsReady(positionMs = currentPositionMs)
        }
    }

    @Synchronized
    fun seekTo(positionMs: Long): AudioCommandResult<PlaybackState> {
        val duration = state.durationOrNull()
            ?: return invalidState("seek is only allowed from Ready or Playing")
        val clamped = positionMs.coerceIn(0L, duration)
        return runBackendCommand {
            seekTo(clamped)
            state.withPosition(clamped)
        }
    }

    @Synchronized
    fun setPlaybackRate(rate: Float): AudioCommandResult<PlaybackState> {
        if (rate !in MIN_RATE..MAX_RATE) return AudioCommandResult.Failure(PlaybackError.InvalidRate(rate))
        if (state !is PlaybackState.Ready && state !is PlaybackState.Playing) {
            return invalidState("rate is only allowed from Ready or Playing")
        }
        return runBackendCommand {
            setPlaybackRate(rate)
            state.withRate(rate)
        }
    }

    @Synchronized
    fun refreshPosition(): PlaybackState {
        if (state is PlaybackState.Ready || state is PlaybackState.Playing) {
            val position = backend?.currentPositionMs ?: 0L
            val duration = requireNotNull(state.durationOrNull())
            publish(state.withPosition(position.coerceIn(0L, duration)))
        }
        return state
    }

    @Synchronized
    fun release(): PlaybackState {
        generation++
        releaseBackend()
        publish(PlaybackState.Released)
        return state
    }

    private fun backendListener(token: Long, mediaId: String): PlayerBackendListener =
        object : PlayerBackendListener {
            override fun onReady(durationMs: Long) = synchronized(this@PlaybackController) {
                if (!isCurrent(token, mediaId)) return@synchronized
                publish(
                    PlaybackState.Ready(
                        mediaId = mediaId,
                        durationMs = durationMs.coerceAtLeast(0L),
                        positionMs = 0L,
                        rate = DEFAULT_RATE,
                    ),
                )
            }

            override fun onEnded() = synchronized(this@PlaybackController) {
                val playing = state as? PlaybackState.Playing ?: return@synchronized
                if (!isCurrent(token, mediaId)) return@synchronized
                runCatching { backend?.seekTo(0L) }
                publish(playing.copyAsReady(positionMs = 0L))
            }

            override fun onInterrupted() = synchronized(this@PlaybackController) {
                val playing = state as? PlaybackState.Playing ?: return@synchronized
                if (!isCurrent(token, mediaId)) return@synchronized
                runCatching { backend?.pause() }
                val position = (backend?.currentPositionMs ?: playing.positionMs)
                    .coerceIn(0L, playing.durationMs)
                publish(playing.copyAsReady(position))
                eventObservers.forEach { it.onEvent(PlaybackEvent.Interrupted(mediaId)) }
            }

            override fun onError(cause: Throwable) = synchronized(this@PlaybackController) {
                if (!isCurrent(token, mediaId)) return@synchronized
                releaseBackend()
                val error = PlaybackError.PrepareFailed(cause)
                publish(PlaybackState.Error(mediaId, error))
            }
        }

    private fun isCurrent(token: Long, mediaId: String): Boolean =
        token == generation && when (val current = state) {
            is PlaybackState.Preparing -> current.mediaId == mediaId
            is PlaybackState.Ready -> current.mediaId == mediaId
            is PlaybackState.Playing -> current.mediaId == mediaId
            is PlaybackState.Error -> current.mediaId == mediaId
            PlaybackState.Released -> false
        }

    private fun runBackendCommand(
        command: PlayerBackend.() -> PlaybackState,
    ): AudioCommandResult<PlaybackState> = try {
        val next = backend.requireBackend().command()
        publish(next)
        AudioCommandResult.Success(next)
    } catch (cause: Throwable) {
        backendFailure(cause)
    }

    private fun backendFailure(cause: Throwable): AudioCommandResult.Failure {
        val mediaId = state.mediaIdOrNull().orEmpty()
        releaseBackend()
        val error = PlaybackError.PrepareFailed(cause)
        return failure(PlaybackState.Error(mediaId, error), error)
    }

    private fun releaseBackend() {
        runCatching { backend?.release() }
        backend = null
    }

    private fun PlayerBackend?.requireBackend(): PlayerBackend =
        this ?: error("Player backend is unavailable")

    private fun publish(next: PlaybackState) {
        state = next
        stateObservers.forEach { it.onStateChanged(next) }
    }

    private fun failure(state: PlaybackState, error: PlaybackError): AudioCommandResult.Failure {
        publish(state)
        return AudioCommandResult.Failure(error)
    }

    private fun invalidState(message: String): AudioCommandResult.Failure =
        AudioCommandResult.Failure(PlaybackError.InvalidState(message))

    private fun PlaybackState.durationOrNull(): Long? = when (this) {
        is PlaybackState.Ready -> durationMs
        is PlaybackState.Playing -> durationMs
        else -> null
    }

    private fun PlaybackState.mediaIdOrNull(): String? = when (this) {
        is PlaybackState.Preparing -> mediaId
        is PlaybackState.Ready -> mediaId
        is PlaybackState.Playing -> mediaId
        is PlaybackState.Error -> mediaId
        PlaybackState.Released -> null
    }

    private fun PlaybackState.withPosition(positionMs: Long): PlaybackState = when (this) {
        is PlaybackState.Ready -> copy(positionMs = positionMs)
        is PlaybackState.Playing -> copy(positionMs = positionMs)
        else -> this
    }

    private fun PlaybackState.withRate(rate: Float): PlaybackState = when (this) {
        is PlaybackState.Ready -> copy(rate = rate)
        is PlaybackState.Playing -> copy(rate = rate)
        else -> this
    }

    private fun PlaybackState.Ready.copyAsPlaying(positionMs: Long): PlaybackState.Playing =
        PlaybackState.Playing(mediaId, durationMs, positionMs, rate)

    private fun PlaybackState.Playing.copyAsReady(positionMs: Long): PlaybackState.Ready =
        PlaybackState.Ready(mediaId, durationMs, positionMs, rate)

    private companion object {
        const val MIN_RATE = 0.5F
        const val MAX_RATE = 2.0F
        const val DEFAULT_RATE = 1.0F
    }
}
