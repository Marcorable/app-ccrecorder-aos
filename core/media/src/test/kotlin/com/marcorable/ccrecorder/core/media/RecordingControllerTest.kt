package com.marcorable.ccrecorder.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RecordingControllerTest {
    private lateinit var clock: FakeClock
    private lateinit var backend: FakeRecorderBackend
    private lateinit var cleanup: FakeFileCleanup
    private lateinit var controller: RecordingController

    @Before
    fun setUp() {
        clock = FakeClock()
        backend = FakeRecorderBackend()
        cleanup = FakeFileCleanup()
        controller = RecordingController(
            backendFactory = RecorderBackendFactory { backend },
            clock = clock,
            fileCleanup = cleanup,
        )
    }

    @Test
    fun `prepare start pause resume and finish preserve active elapsed time`() {
        assertIs<AudioCommandResult.Success<*>>(controller.prepare(SESSION_ID, OUTPUT_PATH))
        assertEquals(RecordingState.Ready(SESSION_ID), controller.state)

        controller.start(SESSION_ID)
        clock.advance(1_200)
        assertEquals(RecordingState.Recording(SESSION_ID, 1_200), controller.state)

        controller.pause(SESSION_ID)
        clock.advance(5_000)
        assertEquals(RecordingState.Paused(SESSION_ID, 1_200), controller.state)

        controller.resume(SESSION_ID)
        clock.advance(800)
        val result = controller.finish(SESSION_ID)

        assertEquals(
            AudioCommandResult.Success(
                FinishedRecording(
                    sessionId = SESSION_ID,
                    outputPath = OUTPUT_PATH,
                    elapsedMs = 2_000,
                    kind = RecordingFinishKind.COMPLETED,
                ),
            ),
            result,
        )
        assertTrue(backend.stopped)
        assertTrue(backend.released)
        assertTrue(cleanup.deletedPaths.isEmpty())
    }

    @Test
    fun `finish from Ready is rejected without changing the safe state`() {
        controller.prepare(SESSION_ID, OUTPUT_PATH)

        val result = controller.finish(SESSION_ID)

        val failure = assertIs<AudioCommandResult.Failure>(result)
        assertIs<RecordingError.InvalidState>(failure.error)
        assertEquals(RecordingState.Ready(SESSION_ID), controller.state)
        assertFalse(backend.stopped)
    }

    @Test
    fun `prepare failure releases recorder and deletes the pending file`() {
        backend.prepareFailure = IllegalStateException("prepare failed")

        val result = controller.prepare(SESSION_ID, OUTPUT_PATH)

        val failure = assertIs<AudioCommandResult.Failure>(result)
        assertIs<RecordingError.PrepareFailed>(failure.error)
        assertIs<RecordingState.PrepareFailed>(controller.state)
        assertTrue(backend.released)
        assertEquals(listOf(OUTPUT_PATH), cleanup.deletedPaths)
    }

    @Test
    fun `start failure releases recorder deletes pending file and becomes terminal prepare failure`() {
        backend.startFailure = IllegalStateException("start failed")
        controller.prepare(SESSION_ID, OUTPUT_PATH)

        val result = controller.start(SESSION_ID)

        val failure = assertIs<AudioCommandResult.Failure>(result)
        assertIs<RecordingError.StartFailed>(failure.error)
        assertIs<RecordingState.PrepareFailed>(controller.state)
        assertTrue(backend.released)
        assertEquals(listOf(OUTPUT_PATH), cleanup.deletedPaths)
    }

    @Test
    fun `finish failure releases recorder and deletes unusable pending file`() {
        controller.prepare(SESSION_ID, OUTPUT_PATH)
        controller.start(SESSION_ID)
        clock.advance(1)
        backend.stopFailure = IllegalStateException("stop failed")

        val result = controller.finish(SESSION_ID)

        val failure = assertIs<AudioCommandResult.Failure>(result)
        assertIs<RecordingError.FinishFailed>(failure.error)
        assertIs<RecordingState.SaveFailed>(controller.state)
        assertTrue(backend.released)
        assertEquals(listOf(OUTPUT_PATH), cleanup.deletedPaths)
    }

    @Test
    fun `cancel is idempotent and always removes the pending file`() {
        controller.prepare(SESSION_ID, OUTPUT_PATH)
        controller.start(SESSION_ID)
        clock.advance(250)

        val first = controller.cancel(SESSION_ID)
        val second = controller.cancel(SESSION_ID)

        assertEquals(first, second)
        assertEquals(listOf(OUTPUT_PATH), cleanup.deletedPaths)
        val finished = assertIs<RecordingState.Finished>(controller.state)
        assertEquals(RecordingFinishKind.CANCELLED, finished.result.kind)
        assertEquals(250L, finished.result.elapsedMs)
    }

    @Test
    fun `cancel reports cleanup failure instead of claiming the file is gone`() {
        cleanup.deleteSucceeds = false
        controller.prepare(SESSION_ID, OUTPUT_PATH)

        val result = controller.cancel(SESSION_ID)

        val failure = assertIs<AudioCommandResult.Failure>(result)
        assertIs<RecordingError.CleanupFailed>(failure.error)
        assertIs<RecordingState.SaveFailed>(controller.state)

        cleanup.deleteSucceeds = true
        val closed = controller.closeFailure(SESSION_ID)

        assertIs<AudioCommandResult.Success<*>>(closed)
        assertIs<RecordingState.Finished>(controller.state)
        assertEquals(listOf(OUTPUT_PATH, OUTPUT_PATH), cleanup.deletedPaths)
    }

    @Test
    fun `command for a stale session is rejected and active session is unchanged`() {
        controller.prepare(SESSION_ID, OUTPUT_PATH)

        val result = controller.start("stale")

        val failure = assertIs<AudioCommandResult.Failure>(result)
        assertIs<RecordingError.InvalidState>(failure.error)
        assertEquals(RecordingState.Ready(SESSION_ID), controller.state)
        assertFalse(backend.started)
    }

    private class FakeClock : AudioClock {
        private var nowMs = 0L

        override fun elapsedRealtimeMs(): Long = nowMs

        fun advance(durationMs: Long) {
            nowMs += durationMs
        }
    }

    private class FakeFileCleanup : RecordingFileCleanup {
        val deletedPaths = mutableListOf<String>()
        var deleteSucceeds = true

        override fun delete(path: String): Boolean {
            deletedPaths += path
            return deleteSucceeds
        }
    }

    private class FakeRecorderBackend : RecorderBackend {
        var started = false
        var stopped = false
        var released = false
        var prepareFailure: Throwable? = null
        var startFailure: Throwable? = null
        var stopFailure: Throwable? = null

        override fun prepare() {
            prepareFailure?.let { throw it }
        }

        override fun start() {
            startFailure?.let { throw it }
            started = true
        }

        override fun pause() = Unit

        override fun resume() = Unit

        override fun stop() {
            stopFailure?.let { throw it }
            stopped = true
        }

        override fun release() {
            released = true
        }
    }

    private companion object {
        const val SESSION_ID = "session-1"
        const val OUTPUT_PATH = "/private/audio/pending/session-1.pending.m4a"
    }
}
