package com.marcorable.ccrecorder.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlaybackControllerTest {
    private lateinit var factory: FakePlayerBackendFactory
    private lateinit var resolver: FakeSourceResolver
    private lateinit var controller: PlaybackController

    @Before
    fun setUp() {
        factory = FakePlayerBackendFactory()
        resolver = FakeSourceResolver()
        controller = PlaybackController(factory, resolver)
    }

    @Test
    fun `missing file enters a distinguishable error without creating a player`() {
        resolver.result = AudioSourceResolution.Rejected(PlaybackError.MissingFile(RELATIVE_PATH))

        val result = controller.prepare(MEDIA_ID, RELATIVE_PATH)

        val failure = assertIs<AudioCommandResult.Failure>(result)
        assertIs<PlaybackError.MissingFile>(failure.error)
        assertIs<PlaybackState.Error>(controller.state)
        assertEquals(0, factory.backends.size)
    }

    @Test
    fun `ready media supports play pause clamped seek and bounded speed`() {
        controller.prepare(MEDIA_ID, RELATIVE_PATH)
        val backend = factory.backends.single()
        backend.listener.onReady(10_000)

        controller.seekTo(12_000)
        assertEquals(10_000L, backend.seekPositionMs)
        controller.setPlaybackRate(1.75F)
        controller.play()
        backend.currentPosition = 2_500
        controller.pause()

        assertEquals(1.75F, backend.rate)
        assertTrue(backend.playCalled)
        assertTrue(backend.pauseCalled)
        assertEquals(
            PlaybackState.Ready(MEDIA_ID, 10_000, 2_500, 1.75F),
            controller.state,
        )

        val invalidRate = controller.setPlaybackRate(2.25F)
        val failure = assertIs<AudioCommandResult.Failure>(invalidRate)
        assertIs<PlaybackError.InvalidRate>(failure.error)
        assertEquals(1.75F, backend.rate)
    }

    @Test
    fun `natural end returns to Ready at position zero`() {
        controller.prepare(MEDIA_ID, RELATIVE_PATH)
        val backend = factory.backends.single()
        backend.listener.onReady(5_000)
        controller.play()
        backend.currentPosition = 5_000

        backend.listener.onEnded()

        assertEquals(PlaybackState.Ready(MEDIA_ID, 5_000, 0, 1.0F), controller.state)
        assertEquals(0L, backend.seekPositionMs)
    }

    @Test
    fun `audio focus interruption pauses once emits one event and never auto resumes`() {
        val events = mutableListOf<PlaybackEvent>()
        controller.addEventObserver { events += it }
        controller.prepare(MEDIA_ID, RELATIVE_PATH)
        val backend = factory.backends.single()
        backend.listener.onReady(5_000)
        controller.play()
        backend.currentPosition = 1_500

        backend.listener.onInterrupted()
        backend.listener.onInterrupted()

        assertEquals(PlaybackState.Ready(MEDIA_ID, 5_000, 1_500, 1.0F), controller.state)
        assertEquals(listOf(PlaybackEvent.Interrupted(MEDIA_ID)), events)
        assertTrue(backend.pauseCalled)
        assertFalse(backend.playCalledAfterInterruption)
    }

    @Test
    fun `release is idempotent and callbacks from an old player are ignored`() {
        controller.prepare(MEDIA_ID, RELATIVE_PATH)
        val oldBackend = factory.backends.single()

        controller.release()
        controller.release()
        oldBackend.listener.onReady(5_000)
        oldBackend.listener.onError(IllegalStateException("late"))

        assertEquals(PlaybackState.Released, controller.state)
        assertTrue(oldBackend.released)
    }

    @Test
    fun `player preparation error releases player and exposes prepare failure`() {
        controller.prepare(MEDIA_ID, RELATIVE_PATH)
        val backend = factory.backends.single()

        backend.listener.onError(IllegalArgumentException("corrupt"))

        val error = assertIs<PlaybackState.Error>(controller.state)
        assertIs<PlaybackError.PrepareFailed>(error.error)
        assertTrue(backend.released)
    }

    private class FakeSourceResolver : AudioSourceResolver {
        var result: AudioSourceResolution = AudioSourceResolution.Resolved("file:///audio.m4a")

        override fun resolve(relativePath: String): AudioSourceResolution = result
    }

    private class FakePlayerBackendFactory : PlayerBackendFactory {
        val backends = mutableListOf<FakePlayerBackend>()

        override fun create(listener: PlayerBackendListener): PlayerBackend =
            FakePlayerBackend(listener).also(backends::add)
    }

    private class FakePlayerBackend(
        val listener: PlayerBackendListener,
    ) : PlayerBackend {
        var currentPosition = 0L
        var seekPositionMs: Long? = null
        var rate = 1.0F
        var playCalled = false
        var pauseCalled = false
        var released = false
        var interrupted = false

        val playCalledAfterInterruption: Boolean get() = interrupted && playCalled && !pauseCalled

        override val currentPositionMs: Long get() = currentPosition

        override fun prepare(sourceUri: String) = Unit

        override fun play() {
            playCalled = true
        }

        override fun pause() {
            interrupted = true
            pauseCalled = true
        }

        override fun seekTo(positionMs: Long) {
            seekPositionMs = positionMs
        }

        override fun setPlaybackRate(rate: Float) {
            this.rate = rate
        }

        override fun release() {
            released = true
        }
    }

    private companion object {
        const val MEDIA_ID = "media-1"
        const val RELATIVE_PATH = "recordings/media-1.m4a"
    }
}
