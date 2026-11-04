package com.marcorable.ccrecorder.core.data

import com.marcorable.ccrecorder.core.data.file.AppAudioFileStore
import com.marcorable.ccrecorder.core.model.PersistenceError
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AudioFileStoreTest {
    private lateinit var directory: File
    private lateinit var store: AppAudioFileStore

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("audio-store").toFile()
        store = AppAudioFileStore(directory)
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun pendingFileFinalizesToContractRelativePath() = runTest {
        val id = "00000000-0000-4000-8000-000000000101"
        assertEquals("audio/pending/$id.pending.m4a", store.preparePending(id))
        store.fileFor(store.pendingRelativePath(id)).writeBytes(byteArrayOf(1, 2, 3))

        assertEquals("audio/recordings/$id.m4a", store.finalizePending(id))
        assertFalse(store.fileFor(store.pendingRelativePath(id)).exists())
        assertTrue(store.recordingExists(store.finalRelativePath(id)))
    }

    @Test
    fun rejectsTraversalAbsoluteBackslashAndSymlinkEscape() = runTest {
        val invalidPaths = listOf(
            "../audio/recordings/00000000-0000-4000-8000-000000000101.m4a",
            "/audio/recordings/00000000-0000-4000-8000-000000000101.m4a",
            "audio\\recordings\\00000000-0000-4000-8000-000000000101.m4a",
            "audio//recordings/00000000-0000-4000-8000-000000000101.m4a",
        )
        invalidPaths.forEach { path ->
            assertTrue(runCatching { store.recordingExists(path) }.exceptionOrNull() is PersistenceError.AudioPathInvalid)
        }

        val external = Files.createTempDirectory("external-audio")
        File(directory, "audio").mkdirs()
        Files.createSymbolicLink(File(directory, "audio/recordings").toPath(), external)
        assertTrue(
            runCatching {
                store.recordingExists("audio/recordings/00000000-0000-4000-8000-000000000101.m4a")
            }.exceptionOrNull() is PersistenceError.AudioPathInvalid,
        )
        external.toFile().deleteRecursively()
    }

    @Test
    fun emptyPendingFileCannotBeFinalizedAndCanBeCleanedIdempotently() = runTest {
        val id = "00000000-0000-4000-8000-000000000102"
        store.preparePending(id)
        assertTrue(runCatching { store.finalizePending(id) }.exceptionOrNull() is PersistenceError.AudioFileMissing)
        assertTrue(store.deletePending(id))
        assertTrue(store.deletePending(id))
    }
}
